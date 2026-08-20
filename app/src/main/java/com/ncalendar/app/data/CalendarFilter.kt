package com.ncalendar.app.data

/** The subset of [Prefs] CalendarFilter needs — split out so the filtering logic is testable
 *  with a plain in-memory fake instead of a real Android SharedPreferences/Context. */
interface CalendarVisibilityStore {
    fun isCalendarVisible(id: String): Boolean
    fun setCalendarVisible(id: String, visible: Boolean)
    fun isAccountVisible(accountKey: String): Boolean
    fun setAccountVisible(accountKey: String, visible: Boolean)
}

/**
 * Denylist-based visibility for accounts and calendars: everything is visible unless
 * explicitly hidden, so a newly-synced account or calendar just shows up rather than requiring
 * opt-in — an allowlist would make "why aren't my new events showing" the default failure mode
 * instead of an edge case. Effective visibility is accountEnabled AND calendarEnabled; toggling
 * an account off does NOT touch its calendars' individual prefs, so re-enabling the account
 * restores exactly what the user had chosen calendar-by-calendar before.
 */
class CalendarFilter(private val store: CalendarVisibilityStore) {

    fun isAccountEnabled(accountKey: String): Boolean = store.isAccountVisible(accountKey)

    fun isCalendarEnabled(c: CalendarInfo): Boolean =
        store.isCalendarVisible(c.id) && isAccountEnabled(c.accountKey)

    fun setAccountEnabled(accountKey: String, enabled: Boolean) = store.setAccountVisible(accountKey, enabled)

    fun setCalendarEnabled(id: String, enabled: Boolean) = store.setCalendarVisible(id, enabled)

    /** Ids of every calendar in [all] that's currently visible. */
    fun visibleIds(all: List<CalendarInfo>): Set<String> =
        all.filter { isCalendarEnabled(it) }.mapTo(HashSet()) { it.id }
}
