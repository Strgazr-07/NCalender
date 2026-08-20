package com.ncalendar.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import com.ncalendar.app.data.CalendarFormats
import com.ncalendar.app.data.EventItem
import com.ncalendar.app.data.Prefs
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Schedules exact alarms for each event reminder so notifications fire even when the app isn't
 * running. Keeps the set of scheduled events in prefs so alarms for deleted events can be
 * cancelled on the next sync.
 */
object ReminderScheduler {

    private const val PREFS = "ncalendar_reminders"
    private const val KEY_KNOWN_IDS = "known_event_ids"
    private const val KEY_FINGERPRINTS = "event_fingerprints"
    private const val KEY_LEGACY_MIGRATED = "legacy_request_codes_migrated"
    private const val KEY_LIVE_EVENT_ID = "live_event_id"
    private const val MAX_SLOTS = 8

    /** Only alarms within this window are registered; syncAll (called from ReminderSyncWorker,
     *  the daily alarm below, app open, and boot) rolls it forward. */
    private const val HORIZON_DAYS = 90L

    /** Caps how many alarms get (re-)armed per sync — OEMs and the platform both rate-limit
     *  exact alarms, and this app has no need for more than a few hundred armed at once. The
     *  soonest events are kept; the tail rolls in on the next sync as the window advances. */
    private const val MAX_ALARMS = 300

    // Request-code space, partitioned by purpose. The pre-V2 scheme used the FULL Int hash
    // range for reminder codes and then added untracked +1_000_000 / +2_000_000 offsets for
    // snooze/re-arm, which were neither disjoint from that space nor overflow-safe — a
    // collision could cancel a different event's already-armed alarm, or relabel it with the
    // wrong title (see cancelLegacyAlarms for the one-time cleanup this required).
    private const val CODE_MASK = 0x0FFFFFFF
    private const val SNOOZE_BASE = 0x40000000
    private const val REARM_BASE = 0x50000000
    private const val LIVE_BASE = 0x70000000
    private const val DAILY_SYNC_CODE = 0x60000001
    private const val LIVE_START_CODE = 0x60000002
    private const val LIVE_END_CODE = 0x60000003

    fun syncAll(context: Context, events: List<EventItem>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previouslyKnown = prefs.getStringSet(KEY_KNOWN_IDS, emptySet()) ?: emptySet()

        // Only events that can actually fire soon get an alarm — scheduling all ~2 years of
        // recurring instances would blow the per-app alarm budget and burn main-thread time in
        // thousands of PendingIntent lookups. Soonest-first so the MAX_ALARMS cap keeps the
        // events that matter most; the rest roll in as the window advances on a later sync.
        val now = System.currentTimeMillis()
        val horizonEnd = now + HORIZON_DAYS * 24 * 60 * 60 * 1000
        val zone = ZoneId.systemDefault()
        val candidates = events
            .filter { e ->
                if (e.reminders.isEmpty()) return@filter false
                val startMillis = e.start.atZone(zone).toInstant().toEpochMilli()
                if (startMillis > horizonEnd) return@filter false
                // An all-day event's start is midnight, but its reminder fires later that
                // morning — so today's all-day events are still live candidates well after
                // their nominal "start". Timed events are done once they've begun.
                val overAt = if (e.allDay) {
                    e.endDate.plusDays(1).atStartOfDay().atZone(zone).toInstant().toEpochMilli()
                } else startMillis
                overAt > now
            }
            .sortedBy { it.start }
            .take(MAX_ALARMS)
        val currentIds = candidates.map { it.id }.toSet()

        // Cancel alarms for events that no longer exist or left the window.
        (previouslyKnown - currentIds).forEach { cancelEvent(context, it) }

        // Skip the cancel-then-reschedule PendingIntent walk entirely for events whose start
        // time and reminder set haven't changed since the last sync — on a ContentObserver
        // tick fired by some OTHER account's sync, that's normally every candidate.
        val fingerprints = readFingerprints(prefs).toMutableMap()
        var fingerprintsChanged = false
        candidates.forEach { e ->
            val fp = fingerprint(e)
            if (fingerprints[e.id] != fp) {
                scheduleEvent(context, e)
                fingerprints[e.id] = fp
                fingerprintsChanged = true
            }
        }
        if (fingerprints.keys.retainAll(currentIds)) fingerprintsChanged = true

        val edit = prefs.edit().putStringSet(KEY_KNOWN_IDS, currentIds)
        if (fingerprintsChanged) edit.putString(KEY_FINGERPRINTS, writeFingerprints(fingerprints))
        edit.apply()

        syncLiveUpdate(context, events, now, prefs)
    }

    /**
     * Arms the live countdown for the single next upcoming timed event. Deliberately one at a
     * time: promoting every event on a busy calendar would be hostile, and the OS surfaces
     * these as one prominent chip anyway.
     *
     * Self-perpetuating rather than polled — when the alarm fires, ReminderReceiver posts the
     * notification and calls back into here to arm the next one.
     */
    private fun syncLiveUpdate(context: Context, events: List<EventItem>, now: Long, store: SharedPreferences) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        // Whatever chip is currently showing may be for an event that's since been deleted,
        // moved, or hidden — clear it before deciding what (if anything) replaces it.
        store.getString(KEY_LIVE_EVENT_ID, null)?.let { previous ->
            if (events.none { it.id == previous }) LiveEventNotifier.cancel(context, liveNotificationId(previous))
        }
        cancelPendingIntent(context, alarmManager, LIVE_START_CODE, ReminderReceiver.ACTION_LIVE_START)
        cancelPendingIntent(context, alarmManager, LIVE_END_CODE, ReminderReceiver.ACTION_LIVE_END)
        if (!Prefs(context).liveUpdates) {
            store.getString(KEY_LIVE_EVENT_ID, null)?.let { LiveEventNotifier.cancel(context, liveNotificationId(it)) }
            store.edit().remove(KEY_LIVE_EVENT_ID).apply()
            return
        }

        val zone = ZoneId.systemDefault()
        val next = events
            .filterNot { it.allDay }
            .filter { it.start.atZone(zone).toInstant().toEpochMilli() > now }
            .minByOrNull { it.start } ?: run {
            store.edit().remove(KEY_LIVE_EVENT_ID).apply()
            return
        }
        val startMillis = next.start.atZone(zone).toInstant().toEpochMilli()
        val showAt = startMillis - LiveEventNotifier.LEAD_MINUTES * 60_000L
        store.edit().putString(KEY_LIVE_EVENT_ID, next.id).apply()

        if (showAt <= now) {
            // Already inside the lead window — show it straight away rather than waiting for
            // an alarm that would have fired in the past.
            LiveEventNotifier.show(context, next, liveNotificationId(next.id))
        } else {
            val startIntent = Intent(context, ReminderReceiver::class.java).apply {
                action = ReminderReceiver.ACTION_LIVE_START
                putExtra(ReminderReceiver.EXTRA_EVENT_ID, next.id)
            }
            val startPending = PendingIntent.getBroadcast(
                context, LIVE_START_CODE, startIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            scheduleExact(alarmManager, showAt, startPending)
        }

        // The chronometer is a pure display — the OS never stops or alerts on its own once it
        // reaches the target, it just keeps ticking into negative time forever. Without this
        // alarm the chip is effectively abandoned the moment the event starts: nothing ever
        // touches it again until some unrelated change happens to trigger another sync.
        val endIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_LIVE_END
            putExtra(ReminderReceiver.EXTRA_EVENT_ID, next.id)
        }
        val endPending = PendingIntent.getBroadcast(
            context, LIVE_END_CODE, endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        scheduleExact(alarmManager, startMillis, endPending)
    }

    /**
     * Forces every alarm to be re-armed on the next [syncAll], bypassing the fingerprint skip.
     * Needed when something OUTSIDE the events themselves changes what time an alarm should
     * fire at — currently just the all-day reminder time, where the events and their reminder
     * lists are all untouched but every all-day trigger has moved.
     */
    fun resyncAll(context: Context, events: List<EventItem>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_FINGERPRINTS).apply()
        syncAll(context, events)
    }

    private fun fingerprint(e: EventItem): String = "${e.start}|${e.allDay}|${e.reminders.sorted()}"

    private fun readFingerprints(prefs: SharedPreferences): Map<String, String> {
        val raw = prefs.getString(KEY_FINGERPRINTS, "") ?: ""
        if (raw.isBlank()) return emptyMap()
        return raw.split('\n').mapNotNull { line ->
            val idx = line.indexOf('\t')
            if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
        }.toMap()
    }

    private fun writeFingerprints(map: Map<String, String>): String =
        map.entries.joinToString("\n") { "${it.key}\t${it.value}" }

    fun scheduleEvent(context: Context, event: EventItem) {
        // Only reached for a NEW or CHANGED event (see the fingerprint check in syncAll), so
        // this cancel-then-schedule is the uncommon case, not a per-sync cost for every event —
        // still needed here since a shrunk reminder list must orphan its old, now-unused slots.
        cancelEvent(context, event)
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        // An all-day event's "start" is midnight, but its reminder legitimately fires later
        // that morning — so it stays schedulable until the end of its last day, not from
        // midnight. Timed events are done once they've started.
        val overAt = if (event.allDay) {
            event.endDate.plusDays(1).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } else {
            event.start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        if (overAt <= now) return // already past — nothing to remind about

        val allDayTime = Prefs(context).allDayReminderTime
        event.reminders.take(MAX_SLOTS).forEachIndexed { slot, minutesBefore ->
            val triggerAt = ReminderTiming.triggerAt(event, minutesBefore, allDayTime, now)
            val pending = pendingIntent(context, event, slot, minutesBefore, create = true) ?: return@forEachIndexed
            scheduleExact(alarmManager, triggerAt, pending)
        }
    }

    fun cancelEvent(context: Context, event: EventItem) = cancelEvent(context, event.id)

    private fun cancelEvent(context: Context, eventId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        for (slot in 0 until MAX_SLOTS) {
            cancelPendingIntent(context, alarmManager, requestCode(eventId, slot), ReminderReceiver.ACTION_REMIND)
        }
    }

    private fun scheduleExact(alarmManager: AlarmManager, triggerAt: Long, pending: PendingIntent) {
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun pendingIntent(
        context: Context,
        event: EventItem,
        slot: Int,
        minutesBefore: Int,
        create: Boolean,
    ): PendingIntent? {
        val requestCode = requestCode(event.id, slot)
        val text = buildString {
            append(CalendarFormats.timeLabelFor(event))
            if (!event.location.isNullOrBlank()) append(" · ${event.location}")
        }
        val calName = event.calendarName.ifBlank { "Event" }
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMIND
            putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, requestCode)
            putExtra(ReminderReceiver.EXTRA_EVENT_ID, event.id)
            putExtra(ReminderReceiver.EXTRA_TITLE, "${event.title} · $calName")
            putExtra(ReminderReceiver.EXTRA_TEXT, text)
            putExtra(ReminderReceiver.EXTRA_WHEN, event.start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }
        val flags = (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or
            PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    /** Masked into the reminder region of the request-code space — see the partitioning note
     *  above. Also used directly by ReminderReceiver to derive the snooze/re-arm codes for the
     *  same slot, so they stay linked to the alarm they came from without overlapping it. */
    fun requestCode(eventId: String, slot: Int): Int = ("$eventId#$slot").hashCode() and CODE_MASK

    fun snoozeRequestCode(baseRequestCode: Int): Int = SNOOZE_BASE or (baseRequestCode and CODE_MASK)
    fun rearmRequestCode(baseRequestCode: Int): Int = REARM_BASE or (baseRequestCode and CODE_MASK)
    fun liveNotificationId(eventId: String): Int = LIVE_BASE or (eventId.hashCode() and CODE_MASK)

    private fun cancelPendingIntent(context: Context, alarmManager: AlarmManager, requestCode: Int, action: String) {
        val intent = Intent(context, ReminderReceiver::class.java).apply { this.action = action }
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pending != null) {
            alarmManager.cancel(pending)
            pending.cancel()
        }
    }

    /**
     * One-time cleanup for users upgrading from the pre-V2 request-code scheme (unmasked
     * `"$eventId#$slot".hashCode()`, with untracked +1_000_000 / +2_000_000 snooze/re-arm
     * offsets). Those alarms are invisible to the new [cancelEvent] — its masked codes don't
     * overlap the old scheme's — so left alone they'd fire forever as ghost notifications for
     * events the user has since edited or deleted. Idempotent; safe to call on every app start.
     */
    fun cancelLegacyAlarms(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_LEGACY_MIGRATED, false)) return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (alarmManager != null) {
            val knownIds = prefs.getStringSet(KEY_KNOWN_IDS, emptySet()) ?: emptySet()
            for (eventId in knownIds) {
                for (slot in 0 until MAX_SLOTS) {
                    val legacyCode = ("$eventId#$slot").hashCode() // the OLD, unmasked formula
                    cancelPendingIntent(context, alarmManager, legacyCode, ReminderReceiver.ACTION_REMIND)
                    // The old re-armed (post-snooze) alarm also fired via ACTION_REMIND, at
                    // notificationId + 2_000_000. The snooze BUTTON's own +1_000_000 pending
                    // intent was never registered with AlarmManager, so there's nothing to
                    // cancel for it.
                    cancelPendingIntent(context, alarmManager, legacyCode + 2_000_000, ReminderReceiver.ACTION_REMIND)
                }
            }
        }
        prefs.edit().putBoolean(KEY_LEGACY_MIGRATED, true).apply()
    }

    /** Re-arms itself daily at ~03:00 local time — a second, independent re-arm mechanism
     *  alongside ReminderSyncWorker's 6-hourly WorkManager job, since WorkManager periodic
     *  work is not reliably honored on Doze-heavy OEM ROMs. Idempotent (FLAG_UPDATE_CURRENT),
     *  so it's safe to call unconditionally on every app start and after boot. */
    fun armDailySync(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply { action = ReminderReceiver.ACTION_DAILY_SYNC }
        val pending = PendingIntent.getBroadcast(
            context, DAILY_SYNC_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerAt = nextDailySyncTrigger()
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun nextDailySyncTrigger(): Long {
        val now = LocalDateTime.now()
        var target = now.toLocalDate().atTime(3, 0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
