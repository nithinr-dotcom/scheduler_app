package com.wac.worktrack.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.DayPlanner
import com.wac.worktrack.core.Focus
import com.wac.worktrack.core.Habits
import com.wac.worktrack.core.Meds
import com.wac.worktrack.core.Module
import com.wac.worktrack.core.Prefs
import com.wac.worktrack.core.Routines
import com.wac.worktrack.core.QuickAdd
import com.wac.worktrack.core.db
import com.wac.worktrack.data.TaskStatus
import com.wac.worktrack.finance.MoneyOps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Runs a suspending block off the main thread while keeping the broadcast alive. */
fun BroadcastReceiver.launchAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Alarms.EXTRA_ID, 0)
        launchAsync {
            when (intent.action) {
                Alarms.ACTION_TASK -> {
                    val task = context.db.tasks().task(id)
                    if (task != null && task.status == TaskStatus.PENDING) Notifs.task(context, task)
                }
                Alarms.ACTION_TASK_DONE -> {
                    context.db.tasks().task(id)?.let { DayPlanner.setStatus(context, it, TaskStatus.DONE) }
                    Notifs.cancelTask(context, id)
                }
                Alarms.ACTION_TASK_SNOOZE -> {
                    Notifs.cancelTask(context, id)
                    Alarms.snoozeTask(context, id)
                }
                Alarms.ACTION_REMINDER -> {
                    val r = context.db.reminders().get(id) ?: return@launchAsync
                    if (!r.enabled || r.placeId != null) return@launchAsync
                    Notifs.reminder(context, r)
                    if (r.repeatDaily) Alarms.scheduleReminder(context, r)
                    else context.db.reminders().upsert(r.copy(enabled = false))
                }
                Alarms.ACTION_MORNING -> {
                    DayPlanner.ensure(context)
                    Notifs.morning(context, Routines.morning(context))
                    Alarms.scheduleRoutines(context)
                }
                Alarms.ACTION_NIGHT -> {
                    val today = Day.today()
                    val agg = context.db.tasks().aggsNow(today, today).firstOrNull()
                    val open = context.db.tasks().tasksOn(today).count { it.status == TaskStatus.PENDING }
                    val rest = context.db.care().restDatesNow(today - 1, today).toSet()
                    val atRisk = if (!Prefs(context).has(Module.HABITS)) emptyList() else {
                        val logs = context.db.habits().logsNow(today - 1, today)
                        context.db.habits().activeNow()
                            .filter { Habits.atRisk(it, Habits.doneDays(it, logs), today, rest) }.map { it.title }
                    }
                    Notifs.night(context, agg?.score, open, Routines.win(context, today), today in rest, atRisk)
                    Alarms.scheduleRoutines(context)
                }
                Alarms.ACTION_WIND_DOWN -> {
                    Notifs.windDown(context, com.wac.worktrack.core.timeLabel(Prefs(context).bedtimeMinutes))
                    Alarms.scheduleRoutines(context)
                }
                Alarms.ACTION_MED -> {
                    val med = context.db.care().medicine(id) ?: return@launchAsync
                    val day = intent.getLongExtra(Alarms.EXTRA_DAY, Day.today())
                    val slot = intent.getIntExtra(Alarms.EXTRA_SLOT, 0)
                    val logs = context.db.care().medLogsNow(day, day)
                    if (med.active && Prefs(context).has(Module.MEDICINE) && !Meds.taken(logs, med.id, day, slot)) {
                        Notifs.med(context, med, slot)
                    }
                    Alarms.scheduleMed(context, med)
                }
                Alarms.ACTION_MED_TAKEN -> {
                    val med = context.db.care().medicine(id)
                    val slot = intent.getIntExtra(Alarms.EXTRA_SLOT, 0)
                    if (med != null) Meds.take(context, med, intent.getLongExtra(Alarms.EXTRA_DAY, Day.today()), slot)
                    Notifs.cancelMed(context, id)
                }
                Alarms.ACTION_MED_SNOOZE -> {
                    Notifs.cancelMed(context, id)
                    Alarms.snoozeMed(context, id, intent.getLongExtra(Alarms.EXTRA_DAY, Day.today()), intent.getIntExtra(Alarms.EXTRA_SLOT, 0))
                }
                Alarms.ACTION_WEEKLY -> {
                    Notifs.weekly(context)
                    Alarms.scheduleRoutines(context)
                }
                Alarms.ACTION_ROLLOVER -> {
                    DayPlanner.ensure(context)
                    Alarms.rescheduleAll(context)
                    // Tasks timed for midnight are already due by the time they're created.
                    val now = System.currentTimeMillis()
                    context.db.tasks().tasksOn(Day.today())
                        .filter { it.status == TaskStatus.PENDING && it.timeMinutes != null && Day.millisAt(it.date, it.timeMinutes) <= now }
                        .forEach { if (Prefs(context).taskAlerts) Notifs.task(context, it) }
                    val date = Day.date(Day.today())
                    if (date.dayOfMonth == 1) {
                        Notifs.periodReview(context, yearly = date.dayOfYear == 1)
                        if (Prefs(context).has(Module.MONEY)) Routines.monthlyMoney(context, Day.today())
                    }
                }
                Alarms.ACTION_BILLS -> {
                    // The 9:00 AM daily check: bills due and important dates coming up.
                    if (Prefs(context).has(Module.MONEY)) MoneyOps.notifyDueBills(context)
                    if (Prefs(context).has(Module.DATES)) Routines.notifyDates(context)
                    Alarms.scheduleRoutines(context)
                }
                Alarms.ACTION_FOCUS_END -> Focus.finished(context)
                Alarms.ACTION_FOCUS_BREAK -> Focus.startBreak(context)
                Alarms.ACTION_FOCUS_AGAIN -> {
                    val s = Focus.state(context)
                    Focus.start(context, s.taskId, s.title)
                }
                Alarms.ACTION_FOCUS_STOP -> {
                    Focus.stop(context)
                    Notifs.cancelFocus(context)
                }
                Alarms.ACTION_QUICK_ADD -> {
                    val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifs.KEY_QUICK_ADD)?.toString()
                    val result = if (text.isNullOrBlank()) null else QuickAdd.save(context, text)
                    // Re-post so the reply box is ready for the next one (and shows what was added).
                    Notifs.quickAdd(context, lastAdded = result)
                }
            }
        }
    }
}

/** Alarms are cleared on reboot, time changes and app updates — set them all again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        launchAsync {
            DayPlanner.ensure(context)
            Alarms.rescheduleAll(context)
        }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
