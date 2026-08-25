package com.ncalendar.app.data

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Plain in-memory CalendarVisibilityStore — no SharedPreferences/Context involved. */
private class FakeStore : CalendarVisibilityStore {
    val calendars = HashMap<String, Boolean>()
    val accounts = HashMap<String, Boolean>()
    override fun isCalendarVisible(id: String) = calendars[id] != false
    override fun setCalendarVisible(id: String, visible: Boolean) { calendars[id] = visible }
    override fun isAccountVisible(accountKey: String) = accounts[accountKey] != false
    override fun setAccountVisible(accountKey: String, visible: Boolean) { accounts[accountKey] = visible }
}

class CalendarFilterTest {

    private fun cal(id: String, account: String) =
        CalendarInfo(id = id, name = id, color = Color(0xFF000000), accountName = account)

    @Test
    fun everythingIsVisibleByDefault() {
        val filter = CalendarFilter(FakeStore())
        val cals = listOf(cal("1", "a@x.com"), cal("2", "b@x.com"))

        assertThat(filter.visibleIds(cals)).containsExactly("1", "2")
    }

    @Test
    fun disablingAnAccountHidesAllItsCalendars() {
        val store = FakeStore()
        val filter = CalendarFilter(store)
        val cals = listOf(cal("1", "a@x.com"), cal("2", "a@x.com"), cal("3", "b@x.com"))

        filter.setAccountEnabled("a@x.com", false)

        assertThat(filter.visibleIds(cals)).containsExactly("3")
    }

    @Test
    fun reEnablingAnAccountRestoresPerCalendarChoicesMadeWhileItWasOff() {
        // Disabling an account must not clobber the individual calendar prefs — only gate on
        // top of them — so re-enabling the account brings back exactly what was chosen before.
        val store = FakeStore()
        val filter = CalendarFilter(store)
        val cals = listOf(cal("1", "a@x.com"), cal("2", "a@x.com"))

        filter.setCalendarEnabled("2", false) // user had already hidden just calendar 2
        filter.setAccountEnabled("a@x.com", false) // then hid the whole account
        filter.setAccountEnabled("a@x.com", true) // then re-enabled the account

        assertThat(filter.visibleIds(cals)).containsExactly("1")
    }

    @Test
    fun emptyCalendarListProducesAnEmptyVisibleSetNotAnError() {
        val filter = CalendarFilter(FakeStore())
        assertThat(filter.visibleIds(emptyList())).isEmpty()
    }

    @Test
    fun blankAccountNameGroupsUnderOnThisDevice() {
        assertThat(cal("1", "").accountKey).isEqualTo("On this device")
        assertThat(cal("1", "user@gmail.com").accountKey).isEqualTo("user@gmail.com")
    }
}
