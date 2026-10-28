package com.wac.worktrack.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.wac.worktrack.MainActivity
import com.wac.worktrack.R
import com.wac.worktrack.core.Badge
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.Focus
import com.wac.worktrack.core.Money
import com.wac.worktrack.core.Prefs
import com.wac.worktrack.core.millisTimeLabel
import com.wac.worktrack.data.Bill
import com.wac.worktrack.data.DailyTask
import com.wac.worktrack.data.Medicine
import com.wac.worktrack.data.Reminder

object Notifs {
    const val CH_TASKS = "tasks"
    const val CH_REMINDERS = "reminders"
    const val CH_ROUTINE = "routine"
    const val CH_FINANCE = "finance"
    const val CH_FOCUS = "focus"
    const val CH_QUICK = "quick_add"
    const val CH_ACHIEVEMENTS = "achievements"
    const val CH_MEDS = "medicines"
    const val KEY_QUICK_ADD = "quick_add_text"

    private const val ID_MORNING = 1
    private const val ID_NIGHT = 2
    private const val ID_WEEKLY = 3
    private const val ID_PENDING = 4
    private const val ID_FOCUS = 5
    private const val ID_FOCUS_DONE = 6
    private const val ID_BILLS = 7
    private const val ID_QUICK = 8
    private const val ID_PERIOD = 9
    private const val ID_DATES = 10
    private const val ID_WIND_DOWN = 11
    private const val ID_MONEY = 12
    private const val MED_BASE = 600_000
    private const val REFILL_BASE = 650_000
    private const val BADGE_BASE = 500_000
    private const val TASK_BASE = 100_000
    private const val REMINDER_BASE = 200_000

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        listOf(
            NotificationChannel(CH_TASKS, "Task alerts", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CH_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CH_ROUTINE, "Daily plan & review", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CH_FINANCE, "Payments & bills", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CH_FOCUS, "Focus timer", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CH_QUICK, "Quick add", NotificationManager.IMPORTANCE_MIN),
            NotificationChannel(CH_ACHIEVEMENTS, "Achievements", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CH_MEDS, "Medicines", NotificationManager.IMPORTANCE_HIGH),
        ).forEach(nm::createNotificationChannel)
    }

    fun taskNotificationId(taskId: Long) = TASK_BASE + taskId.toInt()

    private fun openApp(context: Context, route: String, code: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun canPost(context: Context) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    private fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (!canPost(context)) return
        context.getSystemService(NotificationManager::class.java).notify(id, builder.build())
    }

    private fun base(context: Context, channel: String, title: String, text: String) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)

    fun task(context: Context, task: DailyTask) {
        val nid = taskNotificationId(task.id)
        val builder = base(context, CH_TASKS, task.title, "${task.category} · tap Done when finished")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(context, "today", nid))
            .addAction(0, "Done", Alarms.taskAction(context, Alarms.ACTION_TASK_DONE, task.id))
            .addAction(0, "Snooze 10 min", Alarms.taskAction(context, Alarms.ACTION_TASK_SNOOZE, task.id))
        post(context, nid, builder)
    }

    fun cancelTask(context: Context, taskId: Long) =
        context.getSystemService(NotificationManager::class.java).cancel(taskNotificationId(taskId))

    fun reminder(context: Context, r: Reminder, missed: Boolean = false) {
        val nid = REMINDER_BASE + r.id.toInt()
        val text = if (missed) "Missed at ${millisTimeLabel(r.timeMillis)} ${Day.label(Day.ofMillis(r.timeMillis)).lowercase()}" +
            r.note.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        else r.note.ifBlank { "Reminder" }
        val builder = base(context, CH_REMINDERS, r.title, text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(context, "reminders", nid))
        post(context, nid, builder)
    }

    /** What the morning notification shows; empty parts are left out. */
    data class Morning(
        val taskCount: Int,
        val top3: List<String>,
        /** "9:30 AM Standup" — the first timed task or calendar event. */
        val first: String?,
        val sleepMinutes: Long?,
        val medDoses: Int,
        val datesToday: List<String>,
        val restDay: Boolean,
    )

    fun morning(context: Context, m: Morning) {
        val lines = mutableListOf<String>()
        when {
            m.restDay -> lines += "Rest day 🌿 — no score today. Take it easy."
            m.top3.isNotEmpty() -> lines += "Top 3: " + m.top3.joinToString(" · ")
            m.taskCount == 0 -> lines += "Nothing planned yet — add today's tasks."
            else -> lines += "${m.taskCount} tasks today. Pick your top 3."
        }
        m.first?.let { lines += "First up: $it" }
        m.datesToday.forEach { lines += it }
        if (m.medDoses > 0) lines += "💊 ${m.medDoses} medicine doses today"
        m.sleepMinutes?.takeIf { it > 0 }?.let { lines += "😴 Slept ${it / 60}h ${it % 60}m" }
        val (title, route) = if (m.top3.isEmpty() && !m.restDay) "Plan your day" to "plan" else "Good morning" to "today"
        post(context, ID_MORNING, base(context, CH_ROUTINE, title, lines.joinToString("\n")).setContentIntent(openApp(context, route, ID_MORNING)))
    }

    /** Evening nudge to close the day. Starts with a win, so the day ends on what went right. */
    fun night(context: Context, score: Int?, open: Int, win: String?, restDay: Boolean, habitsAtRisk: List<String> = emptyList()) {
        val title = when {
            restDay -> "Rest well 🌿"
            score == null -> "Close the day"
            else -> "Today: $score%"
        }
        val lines = mutableListOf<String>()
        win?.let { lines += "🏆 $it" }
        lines += if (open == 0) "All tasks closed. Pick tomorrow's top 3?" else "$open tasks still open — finish, move or skip them."
        if (habitsAtRisk.isNotEmpty()) lines += "Don't miss twice: ${habitsAtRisk.joinToString()}."
        post(
            context, ID_NIGHT,
            base(context, CH_ROUTINE, title, lines.joinToString("\n"))
                .setContentIntent(openApp(context, "closeday", ID_NIGHT))
                .addAction(0, "Close the day", openApp(context, "closeday", ID_NIGHT + 1000)),
        )
    }

    fun windDown(context: Context, bedtime: String) {
        post(
            context, ID_WIND_DOWN,
            base(context, CH_ROUTINE, "Time to wind down 🌙", "Bed at $bedtime. Dim the screens, close the day and get tomorrow ready.")
                .setContentIntent(openApp(context, "closeday", ID_WIND_DOWN)),
        )
    }

    // --- Medicines ---

    fun med(context: Context, m: Medicine, minutes: Int) {
        val text = listOf(m.dose, "for ${com.wac.worktrack.core.timeLabel(minutes)}").filter { it.isNotBlank() }.joinToString(" · ")
        val builder = base(context, CH_MEDS, "💊 ${m.name}", text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .setContentIntent(openApp(context, "medicines", MED_BASE + m.id.toInt()))
            .addAction(0, "Taken", Alarms.medAction(context, Alarms.ACTION_MED_TAKEN, m.id, minutes))
            .addAction(0, "Snooze 10 min", Alarms.medAction(context, Alarms.ACTION_MED_SNOOZE, m.id, minutes))
        post(context, MED_BASE + m.id.toInt(), builder)
    }

    fun cancelMed(context: Context, medId: Long) =
        context.getSystemService(NotificationManager::class.java).cancel(MED_BASE + medId.toInt())

    fun refill(context: Context, m: Medicine) {
        post(
            context, REFILL_BASE + m.id.toInt(),
            base(context, CH_MEDS, "Refill ${m.name}", "Only ${m.stock} doses left — time to buy more.")
                .setContentIntent(openApp(context, "medicines", REFILL_BASE + m.id.toInt())),
        )
    }

    // --- Important dates ---

    fun dates(context: Context, lines: List<String>) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (lines.isEmpty()) {
            nm.cancel(ID_DATES)
            return
        }
        val title = if (lines.size == 1) lines[0].substringBefore(" · ") else "${lines.size} important dates coming up"
        post(context, ID_DATES, base(context, CH_ROUTINE, title, lines.joinToString("\n")).setContentIntent(openApp(context, "dates", ID_DATES)))
    }

    fun moneySummary(context: Context, title: String, text: String) {
        post(context, ID_MONEY, base(context, CH_FINANCE, title, text).setContentIntent(openApp(context, "finance", ID_MONEY)))
    }

    fun periodReview(context: Context, yearly: Boolean) {
        val (title, route) = if (yearly) "Your year in review" to "review/year" else "Your month in review" to "review/month"
        post(
            context, ID_PERIOD,
            base(context, CH_ROUTINE, title, "See how last ${if (yearly) "year" else "month"} went and set your focus.")
                .setContentIntent(openApp(context, route, ID_PERIOD)),
        )
    }

    // --- Focus timer ---

    fun focusRunning(context: Context, s: Focus.State) {
        val title = if (s.isBreak) "Break" else "Focus" + if (s.title.isNotBlank()) ": ${s.title}" else ""
        val builder = NotificationCompat.Builder(context, CH_FOCUS)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle(title)
            .setContentText("Ends at ${com.wac.worktrack.core.millisTimeLabel(s.endAt)}")
            .setWhen(s.endAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context, "focus", ID_FOCUS))
            .addAction(0, "Stop", Alarms.focusAction(context, Alarms.ACTION_FOCUS_STOP))
        post(context, ID_FOCUS, builder)
    }

    fun focusFinished(context: Context, wasBreak: Boolean, title: String) {
        val p = Prefs(context)
        val builder = if (wasBreak) {
            base(context, CH_TASKS, "Break's over", "Ready for another round?")
                .addAction(0, "Focus ${p.focusMinutes} min", Alarms.focusAction(context, Alarms.ACTION_FOCUS_AGAIN))
        } else {
            base(context, CH_TASKS, "Focus session done 🎉", (if (title.isNotBlank()) "$title · " else "") + "time for a break.")
                .addAction(0, "Break ${p.breakMinutes} min", Alarms.focusAction(context, Alarms.ACTION_FOCUS_BREAK))
                .addAction(0, "Another round", Alarms.focusAction(context, Alarms.ACTION_FOCUS_AGAIN))
        }
        post(context, ID_FOCUS_DONE, builder.setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(openApp(context, "focus", ID_FOCUS_DONE)))
    }

    fun cancelFocusDone(context: Context) =
        context.getSystemService(NotificationManager::class.java).cancel(ID_FOCUS_DONE)

    fun cancelFocus(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(ID_FOCUS)
        nm.cancel(ID_FOCUS_DONE)
    }

    // --- Money ---

    fun billsDue(context: Context, due: List<Pair<Bill, Long>>, today: Long) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (due.isEmpty()) {
            nm.cancel(ID_BILLS)
            return
        }
        val lines = due.joinToString("\n") { (b, day) ->
            val whenText = when {
                day < today -> "overdue since ${Day.short(day)}"
                day == today -> "due today"
                else -> "due ${Day.label(day, today).lowercase()}"
            }
            "${b.title} · ${Money.format(b.amountPaise)} · $whenText"
        }
        val title = if (due.size == 1) "Bill due: ${due[0].first.title}" else "${due.size} bills due"
        post(context, ID_BILLS, base(context, CH_FINANCE, title, lines).setContentIntent(openApp(context, "bills", ID_BILLS)))
    }

    // --- Achievements ---

    fun badge(context: Context, b: Badge) {
        post(
            context, BADGE_BASE + b.id.hashCode().and(0xFFFF),
            base(context, CH_ACHIEVEMENTS, "${b.emoji} Badge earned: ${b.title}", b.description)
                .setContentIntent(openApp(context, "achievements", BADGE_BASE)),
        )
    }

    // --- Quick add (persistent, opt-in) ---

    fun quickAdd(context: Context, lastAdded: String? = null) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!Prefs(context).quickAddNotification) {
            nm.cancel(ID_QUICK)
            return
        }
        val input = RemoteInput.Builder(KEY_QUICK_ADD).setLabel("e.g. call mom tomorrow 6pm").build()
        val action = NotificationCompat.Action.Builder(0, "Add task", Alarms.quickAddAction(context))
            .addRemoteInput(input)
            .build()
        val builder = NotificationCompat.Builder(context, CH_QUICK)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle("Quick add")
            .setContentText(lastAdded ?: "Tasks, \"remind me to…\" or \"someday…\"")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openApp(context, "today", ID_QUICK))
            .addAction(action)
        post(context, ID_QUICK, builder)
    }

    fun weekly(context: Context) {
        post(
            context, ID_WEEKLY,
            base(context, CH_ROUTINE, "Weekly review", "See how your week went and set one thing to improve.")
                .setContentIntent(openApp(context, "review", ID_WEEKLY)),
        )
    }

    fun pendingPayments(context: Context, count: Int) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (count == 0) {
            nm.cancel(ID_PENDING)
            return
        }
        val text = if (count == 1) "1 payment is waiting for your review." else "$count payments are waiting for your review."
        post(
            context, ID_PENDING,
            base(context, CH_FINANCE, "Review payments", text + " Nothing is added until you accept it.")
                .setContentIntent(openApp(context, "finance/pending", ID_PENDING)),
        )
    }
}
