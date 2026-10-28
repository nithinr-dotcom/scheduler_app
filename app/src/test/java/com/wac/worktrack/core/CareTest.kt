package com.wac.worktrack.core

import com.wac.worktrack.data.DateKind
import com.wac.worktrack.data.DayAgg
import com.wac.worktrack.data.Habit
import com.wac.worktrack.data.ImportantDate
import com.wac.worktrack.data.MedLog
import com.wac.worktrack.data.Medicine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Rest days, medicines and important dates. */
class CareTest {

    private fun day(date: Long, done: Int, planned: Int) =
        DayAgg(date, planned, done, 0, planned - done, weightPlanned = planned, weightDone = done)

    // --- Rest days ---

    @Test
    fun restDayPausesStreakInsteadOfBreakingIt() {
        // Day 3 went badly but was a sick day.
        val aggs = listOf(day(1, 5, 5), day(2, 5, 5), day(3, 0, 5), day(4, 5, 5))
        assertEquals(1, Scores.streaks(aggs, today = 4).current)
        assertEquals(3, Scores.streaks(aggs, today = 4, rest = setOf(3)).current)
    }

    @Test
    fun habitStreakSkipsRestDaysButCountsOnesDoneAnyway() {
        val h = Habit(id = 1, title = "Walk", createdDate = 1)
        assertEquals(2, Habits.streak(h, done = setOf(1, 3), today = 3, rest = setOf(2)).current)
        // Done on the rest day too: it still adds to the run.
        assertEquals(3, Habits.streak(h, done = setOf(1, 2, 3), today = 3, rest = setOf(2)).current)
    }

    @Test
    fun restDayYesterdayIsNotAMiss() {
        val h = Habit(id = 1, title = "Walk", createdDate = 1)
        assertTrue(Habits.atRisk(h, done = setOf(1), today = 3))
        assertFalse(Habits.atRisk(h, done = setOf(1), today = 3, rest = setOf(2)))
        assertFalse(Habits.atRisk(h, done = setOf(1), today = 3, rest = setOf(3)))
    }

    // --- Medicines ---

    private val med = Medicine(id = 7, name = "Vitamin D", times = "1260, 480,480,bad,2000")

    @Test
    fun timesAreCleanedAndSorted() {
        assertEquals(listOf(480, 1260), Meds.parseTimes(med.times))
        assertEquals("480,1260", Meds.formatTimes(listOf(1260, 480, 480)))
    }

    @Test
    fun nextDoseIsLaterTodayOrFirstTomorrow() {
        assertEquals(10L to 480, Meds.nextDose(med, today = 10, nowMinutes = 400))
        assertEquals(10L to 1260, Meds.nextDose(med, today = 10, nowMinutes = 480))
        assertEquals(11L to 480, Meds.nextDose(med, today = 10, nowMinutes = 1300))
        assertNull(Meds.nextDose(med.copy(times = ""), today = 10, nowMinutes = 0))
    }

    @Test
    fun adherenceIsTakenOverDue() {
        val logs = listOf(MedLog(7, 10, 480, 0), MedLog(7, 10, 1260, 0), MedLog(7, 11, 480, 0), MedLog(99, 11, 480, 0))
        // 2 doses a day × 2 days = 4 due, 3 taken (the other medicine's log doesn't count).
        assertEquals(75, Meds.adherence(listOf(med), logs, 10L..11L))
        assertNull(Meds.adherence(emptyList(), logs, 10L..11L))
    }

    @Test
    fun lowStockAtRefillLevel() {
        assertTrue(Meds.lowStock(med.copy(stock = 5, refillAt = 5)))
        assertFalse(Meds.lowStock(med.copy(stock = 6, refillAt = 5)))
        assertFalse(Meds.lowStock(med.copy(stock = null)))
    }

    // --- Important dates ---

    private fun epoch(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    @Test
    fun nextOccurrenceRollsToNextYear() {
        val bday = ImportantDate(title = "Mom", month = 3, day = 10, year = 1965)
        assertEquals(epoch(2026, 3, 10), ImportantDates.next(bday, epoch(2026, 3, 10)))
        assertEquals(epoch(2027, 3, 10), ImportantDates.next(bday, epoch(2026, 3, 11)))
        assertEquals("turns 61", ImportantDates.milestone(bday, epoch(2026, 1, 1)))
        assertEquals("turns 62", ImportantDates.milestone(bday, epoch(2026, 12, 1)))
    }

    @Test
    fun leapDayFallsOn28thInOtherYears() {
        val leap = ImportantDate(title = "Sam", month = 2, day = 29)
        assertEquals(epoch(2027, 2, 28), ImportantDates.next(leap, epoch(2027, 1, 1)))
        assertEquals(epoch(2028, 2, 29), ImportantDates.next(leap, epoch(2028, 1, 1)))
    }

    @Test
    fun upcomingUsesEachReminderWindow() {
        val today = epoch(2026, 10, 28)
        val soon = ImportantDate(id = 1, title = "A", month = 10, day = 30, remindDaysBefore = 3)
        val tooFar = ImportantDate(id = 2, title = "B", month = 11, day = 5, remindDaysBefore = 3)
        val anniversary = ImportantDate(id = 3, title = "C", kind = DateKind.ANNIVERSARY, month = 11, day = 5, year = 2016, remindDaysBefore = 10)
        assertEquals(listOf(1L, 3L), ImportantDates.upcoming(listOf(tooFar, anniversary, soon), today).map { it.first.id })
        assertEquals("10 years", ImportantDates.milestone(anniversary, today))
    }
}
