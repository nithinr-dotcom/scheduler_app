package com.wac.worktrack.core

import com.wac.worktrack.core.QuickParse.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class QuickParseTest {
    // Wednesday, 28 Oct 2026, 2:00 PM
    private val today = LocalDate.of(2026, 10, 28).toEpochDay()
    private val now = 14 * 60

    private fun parse(s: String) = QuickParse.parse(s, today, now)
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    @Test
    fun plainTaskHasNoDateOrTime() {
        val p = parse("buy milk")
        assertEquals(Kind.TASK, p.kind)
        assertEquals("Buy milk", p.title)
        assertNull(p.day)
        assertNull(p.minutes)
    }

    @Test
    fun reminderWithDayOfMonthAndTime() {
        val p = parse("remind me to pay rent on the 5th at 9am")
        assertEquals(Kind.REMINDER, p.kind)
        assertEquals("Pay rent", p.title)
        assertEquals(day(2026, 11, 5), p.day) // the 5th has passed this month
        assertEquals(9 * 60, p.minutes)
    }

    @Test
    fun tomorrowWithPm() {
        val p = parse("call mom tomorrow 6pm")
        assertEquals("Call mom", p.title)
        assertEquals(today + 1, p.day)
        assertEquals(18 * 60, p.minutes)
    }

    @Test
    fun weekdayAndHalfHour() {
        val p = parse("meeting prep friday at 10:30")
        assertEquals("Meeting prep", p.title)
        assertEquals(day(2026, 10, 30), p.day)
        assertEquals(10 * 60 + 30, p.minutes)
    }

    @Test
    fun nextWeekdayIsAfterToday() {
        // Today is Wednesday, so "wednesday" means next week.
        assertEquals(today + 7, parse("gym wednesday").day)
    }

    @Test
    fun monthNameDates() {
        assertEquals(day(2026, 12, 25), parse("gifts on 25 dec").day)
        assertEquals(day(2027, 1, 3), parse("renew passport jan 3rd").day)
        // A date earlier this year rolls to next year.
        assertEquals(day(2027, 3, 1), parse("taxes 1 march").day)
    }

    @Test
    fun somedayGoesToInbox() {
        val p = parse("someday learn guitar")
        assertEquals(Kind.INBOX, p.kind)
        assertEquals("Learn guitar", p.title)
    }

    @Test
    fun priorityMarker() {
        val p = parse("file taxes ! tomorrow")
        assertTrue(p.highPriority)
        assertEquals("File taxes", p.title)
    }

    @Test
    fun passedTimeWithoutDateMeansTomorrow() {
        val p = parse("stretch at 9am")
        assertEquals(today + 1, p.day)
        assertEquals(9 * 60, p.minutes)
    }

    @Test
    fun bareSmallHourIsEvening() {
        assertEquals(18 * 60, parse("dinner with sam at 6").minutes)
        assertEquals(20 * 60, parse("read tonight").minutes)
    }

    @Test
    fun relativeDurations() {
        val p = parse("check oven in 30 minutes")
        assertEquals("Check oven", p.title)
        assertEquals(today, p.day)
        assertEquals(now + 30, p.minutes)
        assertEquals(today + 3, parse("follow up in 3 days").day)
    }

    @Test
    fun tonightKeepsItsOwnTime() {
        val p = parse("call mom tonight at 9")
        assertEquals("Call mom", p.title)
        assertEquals(today, p.day)
        assertEquals(21 * 60, p.minutes)
        assertEquals(22 * 60 + 30, parse("movie tonight 10:30pm").minutes)
    }

    @Test
    fun shortWeekdayNamesNeedOnOrNext() {
        val cream = parse("buy sun cream")
        assertEquals("Buy sun cream", cream.title)
        assertNull(cream.day)
        assertEquals(day(2026, 11, 1), parse("brunch on sun").day)
        assertEquals(day(2026, 10, 30), parse("call next fri").day)
    }
}
