package com.wac.worktrack.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.Prefs
import com.wac.worktrack.core.db
import com.wac.worktrack.core.Meds
import com.wac.worktrack.core.Module
import com.wac.worktrack.data.DailyTask
import com.wac.worktrack.data.Medicine
import com.wac.worktrack.data.Reminder
import com.wac.worktrack.data.TaskStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Exact alarms for task times, reminders and the daily routine; all delivered to [AlarmReceiver]. */
object Alarms {
    const val ACTION_TASK = "com.wac.worktrack.TASK"
    const val ACTION_TASK_DONE = "com.wac.worktrack.TASK_DONE"
    const val ACTION_TASK_SNOOZE = "com.wac.worktrack.TASK_SNOOZE"
    const val ACTION_REMINDER = "com.wac.worktrack.REMINDER"
    const val ACTION_MORNING = "com.wac.worktrack.MORNING"
    const val ACTION_NIGHT = "com.wac.worktrack.NIGHT"
    const val ACTION_WEEKLY = "com.wac.worktrack.WEEKLY"
    const val ACTION_ROLLOVER = "com.wac.worktrack.ROLLOVER"
    const val ACTION_BILLS = "com.wac.worktrack.BILLS"
    const val ACTION_FOCUS_END = "com.wac.worktrack.FOCUS_END"
    const val ACTION_FOCUS_BREAK = "com.wac.worktrack.FOCUS_BREAK"
    const val ACTION_FOCUS_AGAIN = "com.wac.worktrack.FOCUS_AGAIN"
    const val ACTION_FOCUS_STOP = "com.wac.worktrack.FOCUS_STOP"
    const val ACTION_QUICK_ADD = "com.wac.worktrack.QUICK_ADD"
    const val ACTION_MED = "com.wac.worktrack.MED"
    const val ACTION_MED_TAKEN = "com.wac.worktrack.MED_TAKEN"
    const val ACTION_MED_SNOOZE = "com.wac.worktrack.MED_SNOOZE"
    const val ACTION_WIND_DOWN = "com.wac.worktrack.WIND_DOWN"
    const val EXTRA_ID = "id"
    /** Dose time (minutes after midnight) a medicine alarm or button is for. */
    const val EXTRA_SLOT = "slot"
    /** Day the dose is for. */
    const val EXTRA_DAY = "day"

    private const val SNOOZE_MILLIS = 10 * 60_000L
    private const val WEEKLY_MINUTES = 19 * 60
    private const val BILLS_MINUTES = 9 * 60

    // Request codes keep each alarm distinct; ids are offset per kind.
    private const val CODE_MORNING = 1
    private const val CODE_NIGHT = 2
    private const val CODE_WEEKLY = 3
    private const val CODE_ROLLOVER = 4
    private const val CODE_BILLS = 5
    private const val CODE_FOCUS_END = 6
    private const val CODE_FOCUS_BREAK = 7
    private const val CODE_FOCUS_AGAIN = 8
    private const val CODE_FOCUS_STOP = 9
    private const val CODE_QUICK_ADD = 10
    private const val CODE_WIND_DOWN = 11
    private const val TASK_BASE = 100_000
    private const val REMINDER_BASE = 200_000
    private const val DONE_BASE = 300_000
    private const val SNOOZE_BASE = 400_000
    /** Snoozed task alerts have their own alarm, so re-planning the day can't wipe out a snooze. */
    private const val TASK_SNOOZE_BASE = 900_000
    private const val MED_BASE = 600_000
    private const val MED_TAKEN_BASE = 700_000
    private const val MED_SNOOZE_BASE = 800_000

    private fun intent(context: Context, action: String, id: Long = 0) =
        Intent(context, AlarmReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id)

    private fun pending(context: Context, action: String, code: Int, id: Long = 0): PendingIntent =
        PendingIntent.getBroadcast(
            context, code, intent(context, action, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Notification buttons for the focus timer. */
    fun focusAction(context: Context, action: String): PendingIntent = pending(
        context, action,
        when (action) {
            ACTION_FOCUS_BREAK -> CODE_FOCUS_BREAK
            ACTION_FOCUS_AGAIN -> CODE_FOCUS_AGAIN
            else -> CODE_FOCUS_STOP
        },
    )

    /** Reply action of the quick-add notification; must be mutable so the typed text can be attached. */
    fun quickAddAction(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, CODE_QUICK_ADD, intent(context, ACTION_QUICK_ADD),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

    fun taskAction(context: Context, action: String, taskId: Long): PendingIntent {
        val base = if (action == ACTION_TASK_DONE) DONE_BASE else SNOOZE_BASE
        return pending(context, action, base + taskId.toInt(), taskId)
    }

    private fun medPending(context: Context, action: String, code: Int, medId: Long, day: Long, slot: Int, flags: Int = 0): PendingIntent? =
        PendingIntent.getBroadcast(
            context, code,
            intent(context, action, medId).putExtra(EXTRA_SLOT, slot).putExtra(EXTRA_DAY, day),
            flags or PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** "Taken" / "Snooze" buttons on a dose notification. */
    fun medAction(context: Context, action: String, medId: Long, slot: Int): PendingIntent {
        val base = if (action == ACTION_MED_TAKEN) MED_TAKEN_BASE else MED_SNOOZE_BASE
        return medPending(context, action, base + medId.toInt(), medId, Day.today(), slot)!!
    }

    /** Exact alarms need no special access before Android 12. */
    fun exactAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun set(context: Context, at: Long, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        if (exactAllowed(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            // Inexact fallback if exact-alarm access was revoked.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancel(context: Context, pi: PendingIntent) =
        context.getSystemService(AlarmManager::class.java).cancel(pi)

    // --- Tasks ---------------------------------------------------------------------------------

    fun scheduleTask(context: Context, task: DailyTask) {
        val pi = pending(context, ACTION_TASK, TASK_BASE + task.id.toInt(), task.id)
        val minutes = task.timeMinutes
        val at = if (minutes == null) 0 else Day.millisAt(task.date, minutes)
        val alertsOn = task.status == TaskStatus.PENDING && Prefs(context).taskAlerts
        if (!alertsOn || minutes == null || at <= System.currentTimeMillis()) cancel(context, pi) else set(context, at, pi)
        // A snooze only ends when the task is finished or alerts are turned off.
        if (!alertsOn) cancel(context, snoozePending(context, task.id))
    }

    private fun snoozePending(context: Context, taskId: Long) =
        pending(context, ACTION_TASK, TASK_SNOOZE_BASE + taskId.toInt(), taskId)

    fun snoozeTask(context: Context, taskId: Long) =
        set(context, System.currentTimeMillis() + SNOOZE_MILLIS, snoozePending(context, taskId))

    fun cancelTask(context: Context, taskId: Long) {
        cancel(context, pending(context, ACTION_TASK, TASK_BASE + taskId.toInt(), taskId))
        cancel(context, snoozePending(context, taskId))
    }

    // --- Reminders -----------------------------------------------------------------------------

    /** Next time [r] should fire, or null if it's a one-off already in the past. */
    fun nextFire(r: Reminder, now: Long = System.currentTimeMillis()): Long? {
        if (!r.repeatDaily) return r.timeMillis.takeIf { it > now }
        val minutes = Day.minutesOfMillis(r.timeMillis)
        val today = Day.today()
        val todayAt = Day.millisAt(today, minutes)
        return if (todayAt > now) todayAt else Day.millisAt(today + 1, minutes)
    }

    fun scheduleReminder(context: Context, r: Reminder) {
        val pi = pending(context, ACTION_REMINDER, REMINDER_BASE + r.id.toInt(), r.id)
        // Location reminders fire from a geofence, not a clock.
        val at = if (r.enabled && r.placeId == null) nextFire(r) else null
        if (at == null) cancel(context, pi) else set(context, at, pi)
    }

    fun cancelReminder(context: Context, id: Long) =
        cancel(context, pending(context, ACTION_REMINDER, REMINDER_BASE + id.toInt(), id))

    // --- Medicines -----------------------------------------------------------------------------

    /** Sets the alarm for [m]'s next dose (one alarm per medicine; each firing sets the next). */
    fun scheduleMed(context: Context, m: Medicine) {
        val next = if (m.active && Prefs(context).has(Module.MEDICINE)) {
            Meds.nextDose(m, Day.today(), Day.minutesOfMillis(System.currentTimeMillis()))
        } else {
            null
        }
        if (next == null) {
            cancelMed(context, m.id)
            return
        }
        val (day, slot) = next
        set(context, Day.millisAt(day, slot), medPending(context, ACTION_MED, MED_BASE + m.id.toInt(), m.id, day, slot)!!)
    }

    /** Re-shows the dose in 10 minutes. The regular schedule continues after it fires. */
    fun snoozeMed(context: Context, medId: Long, day: Long, slot: Int) =
        set(context, System.currentTimeMillis() + SNOOZE_MILLIS, medPending(context, ACTION_MED, MED_BASE + medId.toInt(), medId, day, slot)!!)

    fun cancelMed(context: Context, medId: Long) {
        medPending(context, ACTION_MED, MED_BASE + medId.toInt(), medId, 0, 0, PendingIntent.FLAG_NO_CREATE)?.let { cancel(context, it) }
    }

    /** True while a dose alarm (regular or snoozed) is waiting, so a reschedule doesn't replace a snooze. */
    private fun medAlarmSet(context: Context, medId: Long): Boolean =
        PendingIntent.getBroadcast(
            context, MED_BASE + medId.toInt(), intent(context, ACTION_MED, medId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) != null

    // --- Daily routine -------------------------------------------------------------------------

    private fun nextAt(minutes: Int): Long {
        val today = Day.today()
        val at = Day.millisAt(today, minutes)
        return if (at > System.currentTimeMillis()) at else Day.millisAt(today + 1, minutes)
    }

    fun scheduleRoutines(context: Context) {
        val prefs = Prefs(context)
        val morning = pending(context, ACTION_MORNING, CODE_MORNING)
        val night = pending(context, ACTION_NIGHT, CODE_NIGHT)
        val weekly = pending(context, ACTION_WEEKLY, CODE_WEEKLY)
        if (prefs.routineAlerts) {
            set(context, nextAt(prefs.morningMinutes), morning)
            set(context, nextAt(prefs.nightMinutes), night)
            val sunday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).toEpochDay()
            var weeklyAt = Day.millisAt(sunday, WEEKLY_MINUTES)
            if (weeklyAt <= System.currentTimeMillis()) weeklyAt = Day.millisAt(sunday + 7, WEEKLY_MINUTES)
            set(context, weeklyAt, weekly)
        } else {
            cancel(context, morning)
            cancel(context, night)
            cancel(context, weekly)
        }
        val windDown = pending(context, ACTION_WIND_DOWN, CODE_WIND_DOWN)
        if (prefs.windDown) {
            set(context, nextAt(Math.floorMod(prefs.bedtimeMinutes - prefs.windDownLead, 24 * 60)), windDown)
        } else {
            cancel(context, windDown)
        }
        // At midnight: generate the new day's tasks and mark yesterday's leftovers missed.
        set(context, Day.millisAt(Day.today() + 1, 0), pending(context, ACTION_ROLLOVER, CODE_ROLLOVER))
        // Bills due soon are checked every morning, whatever the routine setting.
        set(context, nextAt(BILLS_MINUTES), pending(context, ACTION_BILLS, CODE_BILLS))
    }

    fun scheduleFocusEnd(context: Context, at: Long) = set(context, at, pending(context, ACTION_FOCUS_END, CODE_FOCUS_END))

    fun cancelFocusEnd(context: Context) = cancel(context, pending(context, ACTION_FOCUS_END, CODE_FOCUS_END))

    suspend fun rescheduleAll(context: Context) {
        scheduleRoutines(context)
        context.db.tasks().tasksOn(Day.today()).forEach { scheduleTask(context, it) }
        val now = System.currentTimeMillis()
        for (r in context.db.reminders().enabled()) {
            if (r.placeId == null && !r.repeatDaily && r.timeMillis <= now) {
                // Its alarm was lost (phone off, app force-stopped): show it late rather than never.
                Notifs.reminder(context, r, missed = true)
                context.db.reminders().upsert(r.copy(enabled = false))
            } else {
                scheduleReminder(context, r)
            }
        }
        context.db.care().activeMedicinesNow().forEach { if (!medAlarmSet(context, it.id)) scheduleMed(context, it) }
        Geofences.sync(context)
        val focusEnd = Prefs(context).focusEndAt
        if (focusEnd > 0) scheduleFocusEnd(context, maxOf(focusEnd, System.currentTimeMillis() + 1000))
    }
}
