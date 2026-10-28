package com.wac.worktrack.core

import android.content.Context
import com.wac.worktrack.calendar.Calendars
import com.wac.worktrack.data.TaskStatus
import com.wac.worktrack.data.TxnType
import com.wac.worktrack.health.HealthSync
import com.wac.worktrack.notify.Notifs
import kotlinx.coroutines.withTimeoutOrNull
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Content for the morning and evening notifications, the 9 AM dates check and the monthly money summary. */
object Routines {
    private const val HEALTH_TIMEOUT_MILLIS = 3_000L

    suspend fun morning(context: Context, today: Long = Day.today()): Notifs.Morning {
        val db = context.db
        val prefs = Prefs(context)
        val tasks = db.tasks().tasksOn(today)
        val google = runCatching { Calendars.items(context, today, today, googleTasks = false) }.getOrDefault(emptyList())
        val first = (
            tasks.filter { it.status == TaskStatus.PENDING && it.timeMinutes != null }.map { it.timeMinutes!! to it.title } +
                google.filter { it.minutes != null }.map { it.minutes!! to it.title }
            ).minByOrNull { it.first }?.let { (m, title) -> "${timeLabel(m)} $title" }
        // This runs inside an alarm broadcast's ~10 s budget; leave sleep out rather than be killed.
        val sleep = if (prefs.healthConnect) {
            withTimeoutOrNull(HEALTH_TIMEOUT_MILLIS) { runCatching { HealthSync.read(context, today).sleepMinutes }.getOrNull() }
        } else {
            null
        }
        val doses = if (prefs.has(Module.MEDICINE)) db.care().activeMedicinesNow().sumOf { Meds.parseTimes(it.times).size } else 0
        val dates = if (prefs.has(Module.DATES)) {
            ImportantDates.upcoming(db.care().datesNow(), today).filter { it.second == today }.map { ImportantDates.label(it.first, today) }
        } else {
            emptyList()
        }
        return Notifs.Morning(
            taskCount = tasks.size,
            top3 = tasks.filter { it.isTop3 }.map { it.title },
            first = first,
            sleepMinutes = sleep,
            medDoses = doses,
            datesToday = dates,
            restDay = today in db.care().restDatesNow(today, today),
        )
    }

    /** One thing that went right today, or null if there's nothing yet. */
    suspend fun win(context: Context, today: Long): String? {
        val db = context.db
        val done = db.tasks().tasksOn(today).filter { it.status == TaskStatus.DONE }
        val top3 = done.filter { it.isTop3 }
        if (top3.size == 3) return "All three priorities done!"
        top3.firstOrNull()?.let { return "You finished “${it.title}”." }
        if (done.size >= 2) return "You finished ${done.size} tasks."
        done.firstOrNull()?.let { return "You finished “${it.title}”." }
        val prefs = Prefs(context)
        if (prefs.has(Module.HABITS)) {
            val habits = db.habits().activeNow()
            val logs = db.habits().logsNow(today, today)
            val kept = habits.count { today in Habits.doneDays(it, logs) }
            if (kept > 0) return if (kept == 1) "You kept a habit going." else "You kept $kept habits going."
        }
        val focus = db.life().focusMinutesNow(today, today)
        if (focus >= 25) return "$focus minutes of focused work."
        if (today in db.care().restDatesNow(today, today)) return "You rested — that counts too."
        return null
    }

    /** Morning heads-up for birthdays and other dates within their reminder window. */
    suspend fun notifyDates(context: Context, today: Long = Day.today()) {
        val upcoming = ImportantDates.upcoming(context.db.care().datesNow(), today)
        Notifs.dates(context, upcoming.map { ImportantDates.label(it.first, today) })
    }

    /** On the 1st: how last month went, compared with the month before. */
    suspend fun monthlyMoney(context: Context, today: Long) {
        val finance = context.db.finance()
        val last = YearMonth.from(Day.date(today)).minusMonths(1)
        val before = last.minusMonths(1)
        fun range(m: YearMonth) = m.atDay(1).toEpochDay() to m.atEndOfMonth().toEpochDay()
        val (from, to) = range(last)
        val income = finance.totalNow(TxnType.INCOME, from, to)
        val spent = finance.livingCostNow(from, to)
        if (income == 0L && spent == 0L) return
        val (pFrom, pTo) = range(before)
        val prevSpent = finance.livingCostNow(pFrom, pTo)
        val top = finance.expensesByCategoryNow(from, to).filter { it.category != "Savings" }.maxByOrNull { it.totalPaise }
        val lines = mutableListOf(
            "Income ${Money.format(income)} · Spent ${Money.format(spent)}",
            if (income >= spent) "Kept ${Money.format(income - spent)} 👍" else "Spent ${Money.format(spent - income)} more than came in",
        )
        if (prevSpent > 0) {
            val change = (spent - prevSpent) * 100 / prevSpent
            lines += when {
                change > 0 -> "Spending up $change% on the month before"
                change < 0 -> "Spending down ${-change}% on the month before 🎉"
                else -> "Spending same as the month before"
            }
        }
        top?.let { lines += "Biggest category: ${it.category} (${Money.format(it.totalPaise)})" }
        Notifs.moneySummary(context, "${last.format(DateTimeFormatter.ofPattern("MMMM"))} money summary", lines.joinToString("\n"))
    }
}
