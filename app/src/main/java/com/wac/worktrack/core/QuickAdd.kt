package com.wac.worktrack.core

import android.content.Context
import com.wac.worktrack.data.DailyTask
import com.wac.worktrack.data.InboxItem
import com.wac.worktrack.data.PRIORITY_HIGH
import com.wac.worktrack.data.PRIORITY_NORMAL
import com.wac.worktrack.data.Reminder
import com.wac.worktrack.notify.Alarms

/** Saves a quick-add line (typed, spoken, or from the notification) and says what was created. */
object QuickAdd {
    private const val DEFAULT_REMINDER_MINUTES = 9 * 60

    fun preview(p: QuickParse.Parsed, today: Long): String {
        val whenText = listOfNotNull(p.day?.let { Day.label(it, today) }, p.minutes?.let { timeLabel(it) }).joinToString(" ")
        return when (p.kind) {
            QuickParse.Kind.INBOX -> "Someday list"
            QuickParse.Kind.REMINDER -> "Reminder · " + whenText.ifBlank { "Today" }
            QuickParse.Kind.TASK -> "Task · " + whenText.ifBlank { "Today, any time" }
        } + if (p.highPriority) " · High priority" else ""
    }

    suspend fun save(context: Context, p: QuickParse.Parsed): String {
        val today = Day.today()
        if (p.title.isBlank()) return "Nothing to add"
        when (p.kind) {
            QuickParse.Kind.INBOX -> context.db.life().upsertInbox(InboxItem(title = p.title, createdMillis = System.currentTimeMillis()))
            QuickParse.Kind.REMINDER -> {
                var day = p.day ?: today
                val minutes = p.minutes ?: DEFAULT_REMINDER_MINUTES
                if (Day.millisAt(day, minutes) <= System.currentTimeMillis()) day++
                val r = Reminder(title = p.title, timeMillis = Day.millisAt(day, minutes))
                val id = context.db.reminders().upsert(r)
                Alarms.scheduleReminder(context, r.copy(id = id))
            }
            QuickParse.Kind.TASK -> {
                val day = p.day ?: today
                DayPlanner.saveTask(
                    context,
                    DailyTask(
                        date = day, title = p.title, category = "Personal", timeMinutes = p.minutes,
                        priority = if (p.highPriority) PRIORITY_HIGH else PRIORITY_NORMAL,
                    ),
                )
            }
        }
        return "Added: ${p.title} — ${preview(p, today)}"
    }

    suspend fun save(context: Context, text: String): String =
        save(context, QuickParse.parse(text, Day.today(), Day.minutesOfMillis(System.currentTimeMillis())))
}
