package com.ncalendar.app.data.ics

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import java.util.TimeZone

/**
 * Owns the device-local Android calendars that mirror subscribed feeds. Each
 * subscription gets one read-only local calendar; events are written through
 * sync-adapter URIs (so the read-only access level doesn't block us) and fully
 * replaced on every refresh.
 *
 * Because these are real CalendarContract calendars, the app's existing read path,
 * visibility toggles, colors and widgets handle them with no extra wiring.
 */
object SubscriptionCalendars {
    /** Account the mirrored calendars belong to — also used to group them in the UI. */
    const val ACCOUNT_NAME = "NCalendar Subscriptions"
    private val ACCOUNT_TYPE = CalendarContract.ACCOUNT_TYPE_LOCAL // "LOCAL"

    private fun Uri.asSyncAdapter(): Uri = buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
        .build()

    private fun exists(context: Context, calendarId: Long): Boolean {
        val uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId)
        return runCatching {
            context.contentResolver.query(uri, arrayOf(CalendarContract.Calendars._ID), null, null, null)
                ?.use { it.moveToFirst() } ?: false
        }.getOrDefault(false)
    }

    /** Returns the existing calendar id for [sub], or creates one. Null on failure. */
    fun ensureCalendar(context: Context, sub: IcsSubscription): Long? {
        sub.calendarId?.let { if (exists(context, it)) { updateMeta(context, it, sub); return it } }
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
            put(CalendarContract.Calendars.NAME, sub.id)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, sub.name)
            put(CalendarContract.Calendars.CALENDAR_COLOR, sub.colorArgb)
            // Read-only: our own UI treats access < CONTRIBUTOR as non-writable, so a
            // subscription never shows up as an edit target; we write via sync-adapter URIs.
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_READ)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }
        val uri = runCatching {
            context.contentResolver.insert(CalendarContract.Calendars.CONTENT_URI.asSyncAdapter(), values)
        }.getOrNull() ?: return null
        return ContentUris.parseId(uri)
    }

    private fun updateMeta(context: Context, calendarId: Long, sub: IcsSubscription) {
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, sub.name)
            put(CalendarContract.Calendars.CALENDAR_COLOR, sub.colorArgb)
        }
        val uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId).asSyncAdapter()
        runCatching { context.contentResolver.update(uri, values, null, null) }
    }

    /** Replaces every event in the calendar with [events] (each still needs CALENDAR_ID). */
    fun replaceEvents(context: Context, calendarId: Long, events: List<ContentValues>) {
        val cr = context.contentResolver
        runCatching {
            cr.delete(
                CalendarContract.Events.CONTENT_URI.asSyncAdapter(),
                "${CalendarContract.Events.CALENDAR_ID} = ?",
                arrayOf(calendarId.toString()),
            )
        }
        val insertUri = CalendarContract.Events.CONTENT_URI.asSyncAdapter()
        for (v in events) {
            v.put(CalendarContract.Events.CALENDAR_ID, calendarId)
            runCatching { cr.insert(insertUri, v) }
        }
    }

    fun deleteCalendar(context: Context, calendarId: Long) {
        val uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId).asSyncAdapter()
        runCatching { context.contentResolver.delete(uri, null, null) }
    }
}
