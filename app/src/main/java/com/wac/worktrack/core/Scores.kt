package com.wac.worktrack.core

import com.wac.worktrack.data.DayAgg

object Scores {
    /** A day counts toward a streak when its score is at least this. */
    const val STREAK_THRESHOLD = 80

    /** Every this many good days in a row earns a streak freeze… */
    const val DAYS_PER_FREEZE = 7

    /** …and at most this many can be saved up. */
    const val MAX_FREEZES = 2

    data class Streaks(val current: Int, val best: Int, val freezes: Int = 0)

    /**
     * Days with nothing planned are neutral (they neither extend nor break a streak).
     * Today only counts once it reaches the threshold — an unfinished today never breaks it.
     * [rest] days are neutral too, whatever was planned on them.
     */
    fun streaks(aggs: List<DayAgg>, today: Long, rest: Set<Long> = emptySet()): Streaks =
        run(aggs.filter { it.score != null && it.date !in rest }.associate { it.date to (it.score!! >= STREAK_THRESHOLD) }, today)

    /**
     * Generic streak over day → hit/miss (days missing from the map are neutral).
     * A miss while a freeze is saved uses the freeze and keeps the run alive; a miss today is
     * ignored because the day isn't over yet.
     */
    fun run(results: Map<Long, Boolean>, today: Long): Streaks {
        var run = 0
        var best = 0
        var freezes = 0
        var sinceFreeze = 0
        for ((day, hit) in results.toSortedMap()) {
            if (day > today) break
            if (hit) {
                run++
                sinceFreeze++
                if (sinceFreeze == DAYS_PER_FREEZE) {
                    freezes = minOf(MAX_FREEZES, freezes + 1)
                    sinceFreeze = 0
                }
            } else if (day == today) {
                continue
            } else if (freezes > 0 && run > 0) {
                freezes--
            } else {
                run = 0
                sinceFreeze = 0
            }
            best = maxOf(best, run)
        }
        return Streaks(run, best, freezes)
    }

    /**
     * One number for how life is going over the last week, from whatever is being tracked:
     * tasks 40, habits 25, money (budgets kept) 15, reflection (journal days) 20.
     * Missing parts are left out and the rest re-weighted. Null when nothing is tracked yet.
     */
    fun lifeScore(taskAvg: Int?, habitPct: Int?, budgetPct: Int?, journalDays: Int?): Int? {
        val parts = listOfNotNull(
            taskAvg?.let { it to 40 },
            habitPct?.let { it to 25 },
            budgetPct?.let { it to 15 },
            journalDays?.let { minOf(100, it * 100 / 7) to 20 },
        )
        if (parts.isEmpty()) return null
        return parts.sumOf { it.first * it.second } / parts.sumOf { it.second }
    }
}
