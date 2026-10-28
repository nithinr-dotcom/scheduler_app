package com.wac.worktrack.core

import android.content.Context
import com.wac.worktrack.data.AppDatabase
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

val Context.db: AppDatabase get() = AppDatabase.get(this)

object Day {
    fun today(): Long = LocalDate.now().toEpochDay()

    fun date(day: Long): LocalDate = LocalDate.ofEpochDay(day)

    fun startMillis(day: Long): Long =
        date(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Wall-clock time on [day], so 9:00 stays 9:00 on days the clocks change. */
    fun millisAt(day: Long, minutes: Int): Long {
        // Minutes past midnight roll over into the next day ("now + 1 hour" at 11:30 PM).
        val m = Math.floorMod(minutes, 24 * 60)
        val d = day + Math.floorDiv(minutes, 24 * 60)
        return date(d).atTime(LocalTime.of(m / 60, m % 60)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun ofMillis(millis: Long): Long =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    fun minutesOfMillis(millis: Long): Int {
        val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
        return t.hour * 60 + t.minute
    }

    /** Monday of the week containing [day]. */
    fun weekStart(day: Long): Long =
        date(day).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()

    fun monthRange(day: Long): LongRange {
        val d = date(day)
        return d.withDayOfMonth(1).toEpochDay()..d.withDayOfMonth(d.lengthOfMonth()).toEpochDay()
    }

    fun label(day: Long, today: Long = today()): String = when (day - today) {
        0L -> "Today"
        1L -> "Tomorrow"
        -1L -> "Yesterday"
        else -> date(day).format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }

    fun long(day: Long): String = date(day).format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))

    fun short(day: Long): String = date(day).format(DateTimeFormatter.ofPattern("d MMM"))
}

fun timeLabel(minutes: Int): String =
    LocalTime.of(minutes / 60, minutes % 60).format(DateTimeFormatter.ofPattern("h:mm a"))

fun millisTimeLabel(millis: Long): String = timeLabel(Day.minutesOfMillis(millis))

object Money {
    /** "₹1,25,000" / "₹12.50" — Indian lakh grouping, done by hand so it doesn't depend on the locale data. */
    fun format(paise: Long): String {
        val sign = if (paise < 0) "-" else ""
        val abs = kotlin.math.abs(paise)
        val rupees = (abs / 100).toString()
        val grouped = if (rupees.length <= 3) rupees else {
            val head = rupees.dropLast(3)
            head.reversed().chunked(2).joinToString(",").reversed() + "," + rupees.takeLast(3)
        }
        val fraction = abs % 100
        return "$sign₹$grouped" + if (fraction == 0L) "" else ".%02d".format(fraction)
    }

    /** "1,250.5" → 125050 paise; null if not a positive number. */
    fun parse(text: String): Long? {
        val n = text.replace(",", "").replace("₹", "").trim().toBigDecimalOrNull() ?: return null
        if (n <= BigDecimal.ZERO) return null
        return n.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    fun input(paise: Long): String =
        BigDecimal.valueOf(paise).movePointLeft(2).stripTrailingZeros().toPlainString()
}

object Categories {
    val tasks = listOf("Health", "Learning", "Work", "Finance", "Home", "Personal", "Social")
    val expense = listOf(
        "Food", "Groceries", "Transport", "Shopping", "Bills", "Rent", "Health",
        "Entertainment", "Education", "Savings", "Other",
    )
    val income = listOf("Salary", "Freelance", "Interest", "Gift", "Refund", "Other")
    val methods = listOf("UPI", "Card", "Cash", "Bank", "Other")
    const val UNCATEGORIZED = "Uncategorized"

    /** Expenses in this category are money set aside (goals, auto-save), not money spent. */
    const val SAVINGS = "Savings"
}
