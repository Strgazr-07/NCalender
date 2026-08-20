package com.ncalendar.app.data

import android.content.Context
import com.ncalendar.app.data.ics.IcsSubscription
import java.time.LocalTime

enum class ThemeMode { SYSTEM, DARK, LIGHT }
enum class DarkBackgroundStyle { AMOLED, GRAY }

class Prefs(context: Context) : CalendarVisibilityStore {
    private val sp = context.getSharedPreferences("ncalendar_prefs", Context.MODE_PRIVATE)

    /**
     * Runs once at process start (from NCalendarApp.onCreate, before anything else reads
     * prefs) to carry forward behavior for upgrading users when a stored default changes.
     * Bump the schema and add a branch whenever that happens — never change a default's
     * fallback value directly, or existing users silently inherit the new behavior too.
     */
    fun migrate() {
        var schema = sp.getInt("prefs_schema", 0)
        if (schema < 1) {
            // Anything already written (e.g. from onboarding or Settings) means this is an
            // upgrading install, not a fresh one.
            val isExistingInstall = sp.all.isNotEmpty()
            if (isExistingInstall) {
                val edit = sp.edit()
                // The SYSTEM theme default introduced in this schema must not change an
                // existing user's theme out from under them.
                if (!sp.contains("theme_mode")) edit.putString("theme_mode", ThemeMode.DARK.name)
                // Any per-calendar visibility already set means the user has already
                // expressed intent — skip the first-run "review your calendars" picker.
                if (sp.all.keys.any { it.startsWith("cal_vis_") }) edit.putBoolean("cal_filter_initialized", true)
                edit.apply()
            }
            schema = 1
            sp.edit().putInt("prefs_schema", schema).apply()
        }
    }

    /** Subscribed .ics calendar feeds (the mirrored events live in the system calendar). */
    var icsSubscriptions: List<IcsSubscription>
        get() = IcsSubscription.listFromJson(sp.getString("ics_subs", "") ?: "")
        set(v) = sp.edit().putString("ics_subs", IcsSubscription.listToJson(v)).apply()

    var ndotNumerals: Boolean
        get() = sp.getBoolean("ndot", true)
        set(v) = sp.edit().putBoolean("ndot", v).apply()

    /** 0 = Sunday, 1 = Monday */
    var weekStart: Int
        get() = sp.getInt("week_start", 0)
        set(v) = sp.edit().putInt("week_start", v).apply()

    var widgetStyleNdot: Boolean
        get() = sp.getBoolean("widget_style_ndot", true)
        set(v) = sp.edit().putBoolean("widget_style_ndot", v).apply()

    var accentColorArgb: Int
        get() = sp.getInt("accent", DEFAULT_ACCENT)
        set(v) = sp.edit().putInt("accent", v).apply()

    /** Default reminder (minutes before) applied to newly created events. */
    var defaultReminderMinutes: Int
        get() = sp.getInt("default_reminder", 10)
        set(v) = sp.edit().putInt("default_reminder", v).apply()

    /** Time of day an all-day event's reminder fires at, N days before — all-day events have
     *  no clock time of their own, so "1 day before" needs an explicit time to mean anything.
     *  Stored as minutes since midnight; default 09:00. Was previously computed as literally
     *  N days before midnight, which for the common 10-minute default fired at 23:50 the
     *  night before, under most people's Do Not Disturb window. */
    var allDayReminderTime: LocalTime
        get() = LocalTime.ofSecondOfDay(sp.getInt("all_day_reminder_minute", 9 * 60).toLong() * 60)
        set(v) = sp.edit().putInt("all_day_reminder_minute", v.hour * 60 + v.minute).apply()

    /** Ongoing live countdown for the next event (a live-activity chip on Nothing OS). */
    var liveUpdates: Boolean
        get() = sp.getBoolean("live_updates", true)
        set(v) = sp.edit().putBoolean("live_updates", v).apply()

    /**
     * Also write reminders into CalendarContract, so Google Calendar (and anything else on the
     * account) notifies too. **Default off, and it must stay off** — NCalendar deliberately
     * keeps reminders app-side so it's the single notifier; flipping this on by default would
     * give every existing user duplicate notifications for every event, which is the exact
     * problem CalendarProvider's deleteReminders call was written to prevent.
     */
    var syncRemindersToProvider: Boolean
        get() = sp.getBoolean("sync_reminders_to_provider", false)
        set(v) = sp.edit().putBoolean("sync_reminders_to_provider", v).apply()

    /**
     * Privacy opt-out: when true the app never touches the system/Google
     * calendars, even if permission is granted — all events live in the
     * on-device Room store only.
     */
    var localOnly: Boolean
        get() = sp.getBoolean("local_only", false)
        set(v) = sp.edit().putBoolean("local_only", v).apply()

    /** Whether the first-run permission priming screen has been shown/answered. */
    var permissionPrimed: Boolean
        get() = sp.getBoolean("perm_primed", false)
        set(v) = sp.edit().putBoolean("perm_primed", v).apply()

    /** Fresh installs follow the system theme; existing users keep whatever they had, pinned
     *  explicitly by [migrate] when this default changed from DARK. */
    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(sp.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
        set(v) = sp.edit().putString("theme_mode", v.name).apply()

    var darkBackgroundStyle: DarkBackgroundStyle
        get() = runCatching { DarkBackgroundStyle.valueOf(sp.getString("dark_bg_style", DarkBackgroundStyle.AMOLED.name) ?: DarkBackgroundStyle.AMOLED.name) }
            .getOrDefault(DarkBackgroundStyle.AMOLED)
        set(v) = sp.edit().putString("dark_bg_style", v.name).apply()

    /** Dark (default) vs light theme. Kept for older installs; new UI uses themeMode. */
    var darkTheme: Boolean
        get() = sp.getBoolean("dark_theme", true)
        set(v) = sp.edit().putBoolean("dark_theme", v).apply()

    /** Recent search queries, latest first, capped at 8. */
    var recentSearches: List<String>
        get() = (sp.getString("recent_searches", "") ?: "").split('\n').filter { it.isNotBlank() }
        set(v) = sp.edit().putString("recent_searches", v.take(8).joinToString("\n")).apply()

    override fun isCalendarVisible(id: String): Boolean = sp.getBoolean("cal_vis_$id", true)
    override fun setCalendarVisible(id: String, visible: Boolean) {
        sp.edit().putBoolean("cal_vis_$id", visible).apply()
    }

    /** Same denylist semantics as [isCalendarVisible]: absent = visible. Keyed by
     *  [com.ncalendar.app.data.CalendarInfo.accountKey], not an id (accounts don't have one). */
    override fun isAccountVisible(accountKey: String): Boolean = sp.getBoolean("acct_vis_$accountKey", true)
    override fun setAccountVisible(accountKey: String, visible: Boolean) {
        sp.edit().putBoolean("acct_vis_$accountKey", visible).apply()
    }

    /** Whether the first-run "review your calendars" picker has already run (or was skipped
     *  because there was nothing worth reviewing) — gates showing it again on every launch. */
    var calendarFilterInitialized: Boolean
        get() = sp.getBoolean("cal_filter_initialized", false)
        set(v) = sp.edit().putBoolean("cal_filter_initialized", v).apply()

    /** Applies everywhere events are shown — every view AND every widget, since both read from
     *  the same EventRepository.events. Default on: a user who does nothing keeps seeing
     *  holidays exactly as before this setting existed. */
    var showHolidays: Boolean
        get() = sp.getBoolean("show_holidays", true)
        set(v) = sp.edit().putBoolean("show_holidays", v).apply()

    // App-managed reminders for system-calendar events, keyed by base event id.
    // Kept out of the provider so Google Calendar doesn't fire its own duplicate
    // notification — NCalendar's AlarmManager pipeline is the single notifier.
    fun setEventReminders(eventId: String, minutes: List<Int>) {
        if (minutes.isEmpty()) sp.edit().remove("rem_$eventId").apply()
        else sp.edit().putString("rem_$eventId", minutes.joinToString(",")).apply()
    }

    fun clearEventReminders(eventId: String) = sp.edit().remove("rem_$eventId").apply()

    fun allEventReminders(): Map<String, List<Int>> = sp.all.entries
        .filter { it.key.startsWith("rem_") }
        .associate { (k, v) ->
            k.removePrefix("rem_") to (v as? String).orEmpty().split(',').mapNotNull(String::toIntOrNull)
        }

    /** A default reminder set applied to every event in [calendarId] that has none of its own —
     *  the fix for read-only synced calendars (Birthdays, Holidays) whose events never carry
     *  any CalendarContract.Reminders rows and can't be individually edited to add one. See
     *  CalendarProvider.queryInstances' reminder fallback chain and EventRepository.setReminders. */
    fun calendarDefaultReminders(calendarId: String): List<Int> =
        (sp.getString("cal_rem_$calendarId", "") ?: "").split(',').mapNotNull { it.trim().toIntOrNull() }

    fun setCalendarDefaultReminders(calendarId: String, minutes: List<Int>) {
        if (minutes.isEmpty()) sp.edit().remove("cal_rem_$calendarId").apply()
        else sp.edit().putString("cal_rem_$calendarId", minutes.joinToString(",")).apply()
    }

    companion object {
        const val DEFAULT_ACCENT = 0xFFD71921.toInt()
    }
}
