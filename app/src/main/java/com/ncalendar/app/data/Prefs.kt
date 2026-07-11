package com.ncalendar.app.data

import android.content.Context
import com.ncalendar.app.data.ics.IcsSubscription

enum class ThemeMode { SYSTEM, DARK, LIGHT }
enum class DarkBackgroundStyle { AMOLED, GRAY }

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("ncalendar_prefs", Context.MODE_PRIVATE)

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

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(sp.getString("theme_mode", ThemeMode.DARK.name) ?: ThemeMode.DARK.name) }
            .getOrDefault(ThemeMode.DARK)
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

    fun isCalendarVisible(id: String): Boolean = sp.getBoolean("cal_vis_$id", true)
    fun setCalendarVisible(id: String, visible: Boolean) {
        sp.edit().putBoolean("cal_vis_$id", visible).apply()
    }

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

    companion object {
        const val DEFAULT_ACCENT = 0xFFD71921.toInt()
    }
}
