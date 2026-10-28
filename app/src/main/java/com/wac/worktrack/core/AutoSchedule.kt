package com.wac.worktrack.core

import com.wac.worktrack.data.DailyTask
import com.wac.worktrack.data.ENERGY_HIGH
import com.wac.worktrack.data.ENERGY_LOW
import com.wac.worktrack.data.PRIORITY_HIGH

/**
 * Gives times to a day's "any time" tasks: high-energy tasks go into the peak-focus hours,
 * the rest fill the gaps after it. Existing timed tasks and calendar events are left alone and
 * treated as busy.
 */
object AutoSchedule {
    const val DEFAULT_MINUTES = 30
    private const val STEP = 15
    private const val DAY_END = 22 * 60

    data class Busy(val start: Int, val end: Int)

    /** Returns task id → start minute for each task that could be placed. */
    fun plan(
        tasks: List<DailyTask>,
        busy: List<Busy>,
        peakStart: Int,
        peakEnd: Int,
        dayStart: Int,
    ): Map<Long, Int> {
        val taken = busy.toMutableList()
        tasks.filter { it.timeMinutes != null }.forEach {
            taken += Busy(it.timeMinutes!!, it.timeMinutes + (it.estimateMinutes ?: DEFAULT_MINUTES))
        }
        val open = tasks.filter { it.timeMinutes == null }
            .sortedWith(
                compareByDescending<DailyTask> { it.energy == ENERGY_HIGH }
                    .thenByDescending { it.isTop3 }
                    .thenByDescending { it.priority == PRIORITY_HIGH }
                    .thenBy { it.energy == ENERGY_LOW },
            )
        val out = mutableMapOf<Long, Int>()
        val start = roundUp(dayStart)
        for (t in open) {
            val length = t.estimateMinutes ?: DEFAULT_MINUTES
            // High-energy work tries the peak window first; low-energy work avoids it if possible.
            val windows = when (t.energy) {
                ENERGY_HIGH -> listOf(maxOf(start, peakStart) to peakEnd, start to DAY_END)
                ENERGY_LOW -> listOf(maxOf(start, peakEnd) to DAY_END, start to DAY_END)
                else -> listOf(start to DAY_END)
            }
            val at = windows.firstNotNullOfOrNull { (from, to) -> firstFree(taken, roundUp(from), to, length) } ?: continue
            out[t.id] = at
            taken += Busy(at, at + length)
        }
        return out
    }

    private fun roundUp(m: Int) = (m + STEP - 1) / STEP * STEP

    private fun firstFree(taken: List<Busy>, from: Int, to: Int, length: Int): Int? {
        var at = from
        while (at + length <= to) {
            val clash = taken.firstOrNull { at < it.end && it.start < at + length }
            if (clash == null) return at
            at = roundUp(clash.end)
        }
        return null
    }
}
