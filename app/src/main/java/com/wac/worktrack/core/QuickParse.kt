package com.wac.worktrack.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

/**
 * Turns a typed or spoken line into a task, reminder or inbox item, e.g.
 * "remind me to pay rent on the 5th at 9am", "call mom tomorrow 6pm", "someday learn guitar".
 * Everything runs on the phone; nothing is sent anywhere.
 */
object QuickParse {
    enum class Kind { TASK, REMINDER, INBOX }

    data class Parsed(
        val kind: Kind,
        val title: String,
        /** Epoch day, or null when no date was said. */
        val day: Long?,
        /** Minutes after midnight, or null when no time was said. */
        val minutes: Int?,
        val highPriority: Boolean = false,
    )

    private val O = RegexOption.IGNORE_CASE

    private val reminderPrefix = Regex("""^\s*(remind me to|remind me|reminder:?)\s+""", O)
    private val inboxPrefix = Regex("""^\s*(someday|some day|later|inbox:?)\s+""", O)
    private val priority = Regex("""(^|\s)(!+|urgent|important)(?=\s|$)""", O)

    private val inDuration = Regex("""\bin\s+(an?|\d+)\s+(minutes?|mins?|hours?|hrs?|days?|weeks?)\b""", O)
    private val dayAfterTomorrow = Regex("""\b(the\s+)?day after tomorrow\b""", O)
    private val tomorrow = Regex("""\b(tomorrow|tmrw|tmr)\b""", O)
    private val today = Regex("""\btoday\b""", O)
    private val tonight = Regex("""\btonight\b""", O)
    private val partOfDay = Regex("""\b(?:in the |this )?(morning|afternoon|evening)\b""", O)
    private val weekday = Regex(
        """\b(?:on\s+)?(next\s+|this\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""", O,
    )
    /** Short names are ordinary words too ("sun cream", "wed"), so they need "on", "next" or "this". */
    private val weekdayShort = Regex(
        """\b(on\s+next\s+|on\s+this\s+|on\s+|next\s+|this\s+)(mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun)\b""", O,
    )
    private const val MONTHS = "jan|january|feb|february|mar|march|apr|april|may|jun|june|jul|july|aug|august|sep|sept|september|oct|october|nov|november|dec|december"
    private val dayMonth = Regex("""\b(?:on\s+)?(?:the\s+)?(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?($MONTHS)\b""", O)
    private val monthDay = Regex("""\b(?:on\s+)?($MONTHS)\s+(\d{1,2})(?:st|nd|rd|th)?\b""", O)
    private val dayOfMonth = Regex("""\bon\s+(?:the\s+)?(\d{1,2})(?:st|nd|rd|th)?\b""", O)
    private val clock = Regex("""\b(?:at\s+)?(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)""", O)
    private val atHour = Regex("""\bat\s+(\d{1,2})(?:[:.](\d{2}))?\b""", O)
    private val noon = Regex("""\b(?:at\s+)?(noon|midday|midnight)\b""", O)

    fun parse(input: String, todayDay: Long, nowMinutes: Int): Parsed {
        var text = " " + input.trim() + " "
        var kind = Kind.TASK
        reminderPrefix.find(text)?.let { kind = Kind.REMINDER; text = text.removeRange(it.range) }
        inboxPrefix.find(text)?.let { kind = Kind.INBOX; text = text.removeRange(it.range) }

        var high = false
        priority.find(text)?.let { high = true; text = text.replaceRange(it.range, " ") }

        val todayDate = LocalDate.ofEpochDay(todayDay)
        var day: Long? = null
        var minutes: Int? = null

        fun take(r: Regex, block: (MatchResult) -> Unit) {
            val m = r.find(text) ?: return
            block(m)
            text = text.replaceRange(m.range, " ")
        }

        take(inDuration) { m ->
            val n = m.groupValues[1].toIntOrNull() ?: 1
            val unit = m.groupValues[2].lowercase()
            when {
                unit.startsWith("m") -> addMinutes(nowMinutes + n, todayDay).let { day = it.first; minutes = it.second }
                unit.startsWith("h") -> addMinutes(nowMinutes + n * 60, todayDay).let { day = it.first; minutes = it.second }
                unit.startsWith("d") -> day = todayDay + n
                else -> day = todayDay + n * 7
            }
        }
        if (day == null) take(dayAfterTomorrow) { day = todayDay + 2 }
        if (day == null) take(tomorrow) { day = todayDay + 1 }
        if (day == null) take(today) { day = todayDay }
        // "tonight" alone means 8 PM; "tonight at 9" keeps its own time (and means 9 PM).
        var isTonight = false
        if (day == null) take(tonight) { day = todayDay; isTonight = true }
        if (day == null) take(dayMonth) { m ->
            day = dateInYear(todayDate, monthOf(m.groupValues[2]), m.groupValues[1].toInt())
        }
        if (day == null) take(monthDay) { m ->
            day = dateInYear(todayDate, monthOf(m.groupValues[1]), m.groupValues[2].toInt())
        }
        for (r in listOf(weekday, weekdayShort)) {
            if (day == null) take(r) { m ->
                val target = weekdayOf(m.groupValues[2])
                var d = todayDate.plusDays(1)
                while (d.dayOfWeek != target) d = d.plusDays(1)
                day = d.toEpochDay()
            }
        }
        // "on the 5th" — checked after the time patterns would be wrong ("on 5 at 9"), so only plain day numbers.
        if (day == null) take(dayOfMonth) { m ->
            val dom = m.groupValues[1].toInt()
            if (dom in 1..31) {
                val month = if (dom >= todayDate.dayOfMonth) todayDate else todayDate.plusMonths(1)
                day = month.withDayOfMonth(minOf(dom, month.lengthOfMonth())).toEpochDay()
            }
        }

        if (minutes == null) take(clock) { m ->
            var h = m.groupValues[1].toInt() % 12
            if (m.groupValues[3].lowercase().startsWith("p")) h += 12
            minutes = h * 60 + (m.groupValues[2].toIntOrNull() ?: 0)
        }
        if (minutes == null) take(noon) { m -> minutes = if (m.groupValues[1].lowercase() == "midnight") 0 else 12 * 60 }
        if (minutes == null) take(atHour) { m ->
            val h = m.groupValues[1].toInt()
            if (h in 0..23) {
                // "at 6" with no am/pm: small numbers are almost always the evening.
                val hour = if (h in 1..6) h + 12 else h
                minutes = hour * 60 + (m.groupValues[2].toIntOrNull() ?: 0)
            }
        }
        if (isTonight) minutes = minutes?.let { if (it < 12 * 60) it + 12 * 60 else it } ?: (20 * 60)
        if (minutes == null) take(partOfDay) { m ->
            minutes = when (m.groupValues[1].lowercase()) {
                "morning" -> 9 * 60
                "afternoon" -> 14 * 60
                else -> 18 * 60
            }
        }

        // A time that already passed today, with no date said, means tomorrow.
        if (day == null && minutes != null && minutes!! <= nowMinutes) day = todayDay + 1

        return Parsed(kind, cleanTitle(text), day, minutes, high)
    }

    private fun addMinutes(total: Int, today: Long): Pair<Long, Int> = (today + total / 1440) to (total % 1440)

    private fun dateInYear(today: LocalDate, month: Month, dom: Int): Long {
        fun at(year: Int): LocalDate {
            val first = LocalDate.of(year, month, 1)
            return first.withDayOfMonth(dom.coerceIn(1, first.lengthOfMonth()))
        }
        val d = at(today.year)
        return (if (d < today) at(today.year + 1) else d).toEpochDay()
    }

    private fun monthOf(s: String): Month {
        val k = s.lowercase().take(3)
        return Month.entries.first { it.name.lowercase().startsWith(k) }
    }

    private fun weekdayOf(s: String): DayOfWeek {
        val k = s.lowercase().take(3)
        return DayOfWeek.entries.first { it.name.lowercase().startsWith(k) }
    }

    private val trailingJunk = Regex("""(\s+(on|at|by|for|in|this|next|the))+\s*$""", O)
    private val leadingJunk = Regex("""^\s*(to|on|at)\s+""", O)

    private fun cleanTitle(text: String): String {
        var t = text.replace(Regex("""\s+"""), " ").trim()
        t = trailingJunk.replace(t, "").trim()
        t = leadingJunk.replace(t, "").trim()
        t = t.trim(',', '.', '-', ' ')
        return t.replaceFirstChar { it.uppercase() }
    }
}
