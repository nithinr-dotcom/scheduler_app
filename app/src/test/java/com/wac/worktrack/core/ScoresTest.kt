package com.wac.worktrack.core

import com.wac.worktrack.data.DayAgg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoresTest {

    private fun day(date: Long, done: Int, planned: Int) =
        DayAgg(date, planned, done, 0, planned - done, weightPlanned = planned, weightDone = done)

    @Test
    fun scoreIsWeightedPercentage() {
        assertEquals(50, DayAgg(1, 4, 2, 0, 2, weightPlanned = 4, weightDone = 2).score)
        assertNull(DayAgg(1, 0, 0, 0, 0, 0, 0).score)
    }

    @Test
    fun unfinishedTodayDoesNotBreakStreak() {
        val aggs = listOf(day(1, 5, 5), day(2, 4, 5), day(3, 1, 5))
        assertEquals(Scores.Streaks(current = 2, best = 2), Scores.streaks(aggs, today = 3))
    }

    @Test
    fun todayCountsOnceItReachesThreshold() {
        val aggs = listOf(day(1, 5, 5), day(2, 5, 5))
        assertEquals(2, Scores.streaks(aggs, today = 2).current)
    }

    @Test
    fun badPastDayBreaksStreakButBestIsKept() {
        val aggs = listOf(day(1, 5, 5), day(2, 5, 5), day(3, 5, 5), day(4, 1, 5), day(5, 5, 5))
        assertEquals(Scores.Streaks(current = 1, best = 3), Scores.streaks(aggs, today = 5))
    }

    @Test
    fun daysWithNothingPlannedAreNeutral() {
        // Day 2 had no tasks at all, so it neither breaks nor extends the run.
        val aggs = listOf(day(1, 5, 5), day(3, 5, 5))
        assertEquals(2, Scores.streaks(aggs, today = 3).current)
    }

    @Test
    fun moneyParsingAndFormatting() {
        assertEquals(125050L, Money.parse("1,250.5"))
        assertNull(Money.parse("0"))
        assertNull(Money.parse("abc"))
        assertEquals("₹1,00,000", Money.format(10_000_000))
        assertEquals("₹12.50", Money.format(1250))
        assertEquals("1250.5", Money.input(125050))
        assertEquals("₹12,34,56,789", Money.format(123_456_789_00))
        assertEquals("₹999", Money.format(99_900))
        assertEquals("-₹1,500", Money.format(-150_000))
    }

    @Test
    fun skippingEverythingIsNotAFreeDay() {
        val allSkipped = DayAgg(2, planned = 3, done = 0, skipped = 3, missed = 0, weightPlanned = 0, weightDone = 0)
        assertEquals(0, allSkipped.score)
        val aggs = listOf(day(1, 5, 5), allSkipped, day(3, 5, 5))
        assertEquals(1, Scores.streaks(aggs, today = 3).current)
        // Marked as a rest day instead, the streak carries on.
        assertEquals(2, Scores.streaks(aggs, today = 3, rest = setOf(2)).current)
    }
}
