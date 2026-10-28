package com.wac.worktrack.core

import com.wac.worktrack.data.Habit
import com.wac.worktrack.data.HabitLog

object Habits {
    /** Days on which [habit] was done (its count reached the target). */
    fun doneDays(habit: Habit, logs: List<HabitLog>): Set<Long> =
        logs.filter { it.habitId == habit.id && it.count >= habit.target }.map { it.date }.toSet()

    fun countOn(habit: Habit, logs: List<HabitLog>, day: Long): Int =
        logs.firstOrNull { it.habitId == habit.id && it.date == day }?.count ?: 0

    /**
     * Habit streak with the same freeze rules as the day streak; every day since creation counts
     * except [rest] days that weren't done (a habit done on a rest day still counts).
     */
    fun streak(habit: Habit, done: Set<Long>, today: Long, rest: Set<Long> = emptySet()): Scores.Streaks {
        val from = minOf(habit.createdDate, done.minOrNull() ?: habit.createdDate)
        return Scores.run((from..today).filter { it in done || it !in rest }.associateWith { it in done }, today)
    }

    /** "Never miss twice": yesterday was missed (and wasn't a rest day) and today isn't done yet. */
    fun atRisk(habit: Habit, done: Set<Long>, today: Long, rest: Set<Long> = emptySet()): Boolean =
        habit.createdDate < today && (today - 1) !in done && (today - 1) !in rest && today !in done && today !in rest

    fun label(habit: Habit, count: Int): String =
        if (habit.target <= 1) habit.title else "${habit.title} $count/${habit.target}"
}
