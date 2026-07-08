package com.ncalendar.app.data.ics

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A remote .ics / webcal feed the user subscribes to. Its events are mirrored into a
 * device-local Android calendar (see [SubscriptionCalendars]) so they flow through
 * the normal CalendarProvider pipeline — every view and widget picks them up for free.
 *
 * The registry lives in [com.ncalendar.app.data.Prefs] as a JSON array; the mirrored
 * events themselves live in the system calendar, not here.
 */
data class IcsSubscription(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val name: String,
    val colorArgb: Int,
    /** _ID of the mirrored CalendarContract calendar, or null until first created. */
    val calendarId: Long? = null,
    /** Epoch millis of the last successful sync; 0 = never. */
    val lastSyncEpoch: Long = 0L,
    /** Message from the last failed sync, or null when the last sync succeeded. */
    val lastError: String? = null,
) {
    private fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("url", url)
        put("name", name)
        put("color", colorArgb)
        put("calendarId", calendarId ?: JSONObject.NULL)
        put("lastSync", lastSyncEpoch)
        put("lastError", lastError ?: JSONObject.NULL)
    }

    companion object {
        private fun fromJson(o: JSONObject) = IcsSubscription(
            id = o.getString("id"),
            url = o.getString("url"),
            name = o.optString("name").ifBlank { o.getString("url") },
            colorArgb = o.getInt("color"),
            calendarId = if (o.isNull("calendarId")) null else o.getLong("calendarId"),
            lastSyncEpoch = o.optLong("lastSync", 0L),
            lastError = if (o.isNull("lastError")) null else o.optString("lastError").ifBlank { null },
        )

        fun listToJson(list: List<IcsSubscription>): String =
            JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(s: String): List<IcsSubscription> {
            if (s.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(s)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            }.getOrDefault(emptyList())
        }

        /** iCloud/Google hand out `webcal://` links; the fetcher needs http(s). */
        fun normalizeUrl(raw: String): String {
            val t = raw.trim()
            return when {
                t.startsWith("webcal://", true) -> "https://" + t.substring(9)
                t.startsWith("webcals://", true) -> "https://" + t.substring(10)
                t.startsWith("http://", true) || t.startsWith("https://", true) -> t
                else -> "https://$t"
            }
        }
    }
}
