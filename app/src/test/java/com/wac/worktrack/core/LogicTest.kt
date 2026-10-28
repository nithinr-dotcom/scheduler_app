package com.wac.worktrack.core

import com.wac.worktrack.data.Bill
import com.wac.worktrack.data.BillCycle
import com.wac.worktrack.data.DailyTask
import com.wac.worktrack.data.ENERGY_HIGH
import com.wac.worktrack.data.ENERGY_LOW
import com.wac.worktrack.data.Habit
import com.wac.worktrack.data.HabitLog
import com.wac.worktrack.finance.MoneyOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LogicTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day).toEpochDay()

    // --- Streak freezes ---

    @Test
    fun sevenGoodDaysEarnAFreezeThatCoversAMiss() {
        val results = (1L..7L).associateWith { true } + (8L to false) + (9L to true)
        val s = Scores.run(results, today = 9)
        assertEquals(8, s.current) // 7 + day 9; day 8 used the freeze
        assertEquals(0, s.freezes)
    }

    @Test
    fun missWithoutFreezeBreaksStreak() {
        val results = mapOf(1L to true, 2L to true, 3L to false, 4L to true)
        assertEquals(Scores.Streaks(current = 1, best = 2, freezes = 0), Scores.run(results, today = 4))
    }

    @Test
    fun freezesAreCapped() {
        val results = (1L..30L).associateWith { true }
        assertEquals(Scores.MAX_FREEZES, Scores.run(results, today = 30).freezes)
    }

    @Test
    fun countHabitIsDoneOnlyAtTarget() {
        val water = Habit(id = 1, title = "Water", createdDate = 0, target = 8)
        val logs = listOf(HabitLog(1, 10, 8), HabitLog(1, 11, 5), HabitLog(2, 11, 9))
        assertEquals(setOf(10L), Habits.doneDays(water, logs))
        assertEquals(5, Habits.countOn(water, logs, 11))
    }

    @Test
    fun neverMissTwiceWarning() {
        val h = Habit(id = 1, title = "Read", createdDate = 0)
        assertTrue(Habits.atRisk(h, setOf(8L), today = 10))
        assertFalse(Habits.atRisk(h, setOf(9L), today = 10))
        assertFalse(Habits.atRisk(h, setOf(10L), today = 10))
    }

    // --- Checklist ---

    @Test
    fun checklistRoundTripAndReset() {
        val text = Checklist.format(listOf(Checklist.Item("Pack", true), Checklist.Item("Book cab")))
        assertEquals("[x] Pack\n[ ] Book cab", text)
        assertEquals(1 to 2, Checklist.progress(text))
        assertEquals("[ ] Pack\n[ ] Book cab", Checklist.reset(text))
        assertEquals(listOf(Checklist.Item("plain line")), Checklist.parse("plain line\n\n"))
    }

    // --- Bills ---

    @Test
    fun monthlyBillDueDates() {
        val rent = Bill(id = 1, title = "Rent", amountPaise = 1_500_000, dueDay = 5)
        val today = d(2026, 10, 28)
        assertTrue(Bills.isOverdue(rent, today))
        assertEquals(d(2026, 10, 5), Bills.nextDue(rent, today))
        val paid = rent.copy(lastPaidPeriod = "2026-10")
        assertFalse(Bills.isOverdue(paid, today))
        assertEquals(d(2026, 11, 5), Bills.nextDue(paid, today))
        assertTrue(Bills.needsAttention(rent, today))
        assertFalse(Bills.needsAttention(paid, today))
    }

    @Test
    fun billDueDayIsClampedToShortMonths() {
        val b = Bill(id = 1, title = "Card", amountPaise = 100, dueDay = 31, lastPaidPeriod = "2027-01")
        assertEquals(d(2027, 2, 28), Bills.nextDue(b, d(2027, 1, 31)))
    }

    @Test
    fun yearlySubscription() {
        val prime = Bill(id = 1, title = "Prime", amountPaise = 149_900, cycle = BillCycle.YEARLY, dueDay = 10, dueMonth = 11, remindDaysBefore = 7)
        val today = d(2026, 11, 4)
        assertEquals(d(2026, 11, 10), Bills.nextDue(prime, today))
        assertTrue(Bills.needsAttention(prime, today))
        assertEquals(149_900L / 12, Bills.monthlyCost(prime))
    }

    // --- Auto-schedule ---

    private fun task(id: Long, energy: Int = 0, estimate: Int? = 30, time: Int? = null) =
        DailyTask(id = id, date = 0, title = "t$id", category = "Work", energy = energy, estimateMinutes = estimate, timeMinutes = time)

    @Test
    fun highEnergyGoesToPeakAndAvoidsBusySlots() {
        val tasks = listOf(task(1, ENERGY_LOW), task(2, ENERGY_HIGH, 60), task(3, time = 9 * 60))
        val busy = listOf(AutoSchedule.Busy(10 * 60, 11 * 60))
        val out = AutoSchedule.plan(tasks, busy, peakStart = 9 * 60, peakEnd = 12 * 60, dayStart = 8 * 60)
        // 9:00–9:30 is taken by task 3 and 10–11 by a meeting, so the hour of focus fits at 11:00.
        assertEquals(11 * 60, out[2])
        // Low-energy work waits until after the peak.
        assertEquals(12 * 60, out[1])
        assertNull(out[3])
    }

    @Test
    fun tasksThatDontFitAreLeftOut() {
        val out = AutoSchedule.plan(listOf(task(1, estimate = 120)), emptyList(), 9 * 60, 12 * 60, dayStart = 21 * 60)
        assertTrue(out.isEmpty())
    }

    // --- Life score & forecast ---

    @Test
    fun lifeScoreReweightsMissingParts() {
        assertNull(Scores.lifeScore(null, null, null, null))
        assertEquals(80, Scores.lifeScore(80, null, null, null))
        // 40*100 + 25*50 + 20*100 (7 journal days) over 85
        assertEquals((4000 + 1250 + 2000) / 85, Scores.lifeScore(100, 50, null, 7))
    }

    @Test
    fun monthEndForecast() {
        // ₹10,000 spent in 10 days of a 30-day month, ₹5,000 of bills still due.
        assertEquals(3_500_000L, MoneyOps.forecast(1_000_000, 10, 30, 500_000))
    }
}
