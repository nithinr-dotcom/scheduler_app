package com.wac.worktrack.core

import com.wac.worktrack.data.DateKind
import com.wac.worktrack.data.ImportantDate
import java.time.LocalDate
import java.time.YearMonth

object ImportantDates {
    /** Next day (today or later) this date falls on. 29 Feb falls on 28 Feb in other years. */
    fun next(d: ImportantDate, today: Long): Long {
        val now = Day.date(today)
        fun inYear(y: Int): LocalDate {
            val ym = YearMonth.of(y, d.month.coerceIn(1, 12))
            return ym.atDay(d.day.coerceIn(1, ym.lengthOfMonth()))
        }
        val thisYear = inYear(now.year)
        return (if (thisYear.isBefore(now)) inYear(now.year + 1) else thisYear).toEpochDay()
    }

    /** "turns 30" / "30 years" on the next occurrence, when the year is known. */
    fun milestone(d: ImportantDate, today: Long): String? {
        val year = d.year ?: return null
        val n = Day.date(next(d, today)).year - year
        if (n <= 0) return null
        return when (d.kind) {
            DateKind.BIRTHDAY -> "turns $n"
            DateKind.ANNIVERSARY -> "$n years"
            DateKind.OTHER -> "$n years"
        }
    }

    fun emoji(d: ImportantDate) = when (d.kind) {
        DateKind.BIRTHDAY -> "🎂"
        DateKind.ANNIVERSARY -> "💞"
        DateKind.OTHER -> "📌"
    }

    /** Dates that are today, or within their reminder window. Sorted soonest first. */
    fun upcoming(dates: List<ImportantDate>, today: Long): List<Pair<ImportantDate, Long>> =
        dates.map { it to next(it, today) }
            .filter { (d, day) -> day - today <= d.remindDaysBefore.toLong() }
            .sortedBy { it.second }

    fun label(d: ImportantDate, today: Long): String {
        val day = next(d, today)
        val left = day - today
        val whenText = when (left) {
            0L -> "today"
            1L -> "tomorrow"
            else -> "in $left days (${Day.short(day)})"
        }
        return listOfNotNull("${emoji(d)} ${d.title}", milestone(d, today)).joinToString(" ") + " · $whenText"
    }
}
