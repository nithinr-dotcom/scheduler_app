package com.wac.worktrack.core

import android.content.Context
import com.wac.worktrack.data.DailyTask
import com.wac.worktrack.data.Repeat
import com.wac.worktrack.data.TaskStatus
import com.wac.worktrack.data.TaskTemplate
import com.wac.worktrack.data.Txn
import com.wac.worktrack.data.TxnSource
import com.wac.worktrack.data.TxnStatus
import com.wac.worktrack.health.HealthSync
import com.wac.worktrack.notify.Alarms
import com.wac.worktrack.notify.Notifs
import com.wac.worktrack.widget.Widgets
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.YearMonth

/**
 * Owns the daily cycle: turns templates into each day's tasks, marks unfinished past tasks as
 * missed, and posts recurring money entries. All task writes go through here so alarms, goal
 * progress and the home-screen widget stay in sync.
 */
object DayPlanner {
    /** If the app wasn't opened for a while, fill in at most this many past days. */
    private const val BACKFILL_DAYS = 14

    /** Health Connect sync and badge checks are skipped if they ran this recently. */
    private const val EXTRAS_INTERVAL_MILLIS = 15 * 60_000L

    private val mutex = Mutex()
    private var extrasAt = 0L

    /** True once [ensure] has already planned [today], i.e. calling it would only redo the cheap parts. */
    fun planned(context: Context, today: Long = Day.today()) = Prefs(context).lastPlannedDay == today

    /**
     * Brings the day up to date. Generating tasks and marking missed ones only happen once per day;
     * on later calls this just posts due recurring money, re-arms today's alarms, plus the Health
     * Connect sync and badge check at most every [EXTRAS_INTERVAL_MILLIS] (or always if [force]).
     */
    suspend fun ensure(context: Context, today: Long = Day.today(), force: Boolean = false) = mutex.withLock {
        // Callers include lifecycleScope (main thread); the badge and streak maths shouldn't run there.
        withContext(Dispatchers.Default) { ensureLocked(context, today, force) }
    }

    private suspend fun ensureLocked(context: Context, today: Long, force: Boolean) {
        val db = context.db
        val prefs = Prefs(context)
        val last = prefs.lastPlannedDay
        val newDay = last != today
        if (newDay) {
            val from = if (last == 0L) today else maxOf(minOf(last + 1, today), today - BACKFILL_DAYS)
            val templates = db.tasks().activeTemplates()
            db.withTransaction {
                for (day in from..today) generate(context, templates, day)
                db.tasks().markMissedBefore(today)
            }
            prefs.lastPlannedDay = today
        }
        postRecurring(context, today)
        db.tasks().tasksOn(today).forEach { Alarms.scheduleTask(context, it) }
        val now = System.currentTimeMillis()
        if (newDay || force || now - extrasAt >= EXTRAS_INTERVAL_MILLIS) {
            extrasAt = now
            runCatching { HealthSync.sync(context) }
            runCatching { Badges.announceNew(context) }
        }
        // Every data change refreshes the widgets itself; only a new day needs it here.
        if (newDay || force) Widgets.refresh(context)
    }

    /** Creates [day]'s tasks from templates; safe to call repeatedly (existing ones are kept). */
    suspend fun generate(context: Context, day: Long) =
        generate(context, context.db.tasks().activeTemplates(), day)

    private suspend fun generate(context: Context, templates: List<TaskTemplate>, day: Long) {
        templates.filter { it.matches(day) }.forEach { context.db.tasks().insertIgnore(it.toTask(day)) }
    }

    fun TaskTemplate.matches(day: Long): Boolean = when (repeat) {
        Repeat.DAILY -> true
        Repeat.WEEKDAYS -> daysMask and (1 shl (Day.date(day).dayOfWeek.value - 1)) != 0
        Repeat.ONCE -> onceDate == day
    }

    private fun TaskTemplate.toTask(day: Long) = DailyTask(
        date = day, templateId = id, title = title, category = category, priority = priority,
        timeMinutes = timeMinutes, goalId = goalId, estimateMinutes = estimateMinutes, energy = energy,
        checklist = Checklist.reset(checklist),
    )

    suspend fun saveTemplate(context: Context, template: TaskTemplate) {
        val tasks = context.db.tasks()
        val id = tasks.upsertTemplate(template)
        val saved = if (template.id == 0L) template.copy(id = id) else template
        val today = Day.today()
        // Open copies from today on follow the edit; finished/missed history is left alone.
        for (t in tasks.openFromTemplate(saved.id, today)) {
            if (saved.active && saved.matches(t.date)) {
                // Keep ticks on an open copy unless the checklist's items themselves changed.
                val sameItems = Checklist.parse(t.checklist).map { it.text } == Checklist.parse(saved.checklist).map { it.text }
                tasks.upsertTask(
                    t.copy(
                        title = saved.title, category = saved.category, priority = saved.priority,
                        timeMinutes = saved.timeMinutes, goalId = saved.goalId, estimateMinutes = saved.estimateMinutes,
                        energy = saved.energy, checklist = if (sameItems) t.checklist else Checklist.reset(saved.checklist),
                    )
                )
            } else {
                tasks.deleteTask(t)
                Alarms.cancelTask(context, t.id)
            }
        }
        if (saved.active && saved.matches(today)) tasks.insertIgnore(saved.toTask(today))
        refreshToday(context)
    }

    suspend fun deleteTemplate(context: Context, template: TaskTemplate) {
        for (t in context.db.tasks().openFromTemplate(template.id, Day.today())) {
            context.db.tasks().deleteTask(t)
            Alarms.cancelTask(context, t.id)
        }
        context.db.tasks().deleteTemplate(template)
        Widgets.refresh(context)
    }

    suspend fun saveTask(context: Context, task: DailyTask) {
        val id = context.db.tasks().upsertTask(task)
        val saved = if (task.id == 0L) task.copy(id = id) else task
        Alarms.scheduleTask(context, saved)
        Widgets.refresh(context)
    }

    suspend fun deleteTask(context: Context, task: DailyTask) {
        if (task.status == TaskStatus.DONE && task.goalId != null) context.db.goals().addProgress(task.goalId, -1)
        context.db.tasks().deleteTask(task)
        Alarms.cancelTask(context, task.id)
        Widgets.refresh(context)
    }

    /** Puts back a task removed by [deleteTask] (same id, so undo is exact). */
    suspend fun restoreTask(context: Context, task: DailyTask) {
        if (task.status == TaskStatus.DONE && task.goalId != null) context.db.goals().addProgress(task.goalId, 1)
        saveTask(context, task)
    }

    suspend fun setStatus(context: Context, task: DailyTask, status: TaskStatus) {
        if (task.status == status) return
        val goalId = task.goalId
        if (goalId != null) {
            if (status == TaskStatus.DONE) context.db.goals().addProgress(goalId, 1)
            if (task.status == TaskStatus.DONE) context.db.goals().addProgress(goalId, -1)
        }
        val updated = task.copy(
            status = status,
            completedAt = if (status == TaskStatus.DONE) System.currentTimeMillis() else null,
        )
        context.db.tasks().upsertTask(updated)
        Alarms.scheduleTask(context, updated)
        Notifs.cancelTask(context, task.id)
        Widgets.refresh(context)
    }

    /** Ticks or unticks one checklist item. */
    suspend fun setChecklist(context: Context, task: DailyTask, checklist: String) {
        context.db.tasks().upsertTask(task.copy(checklist = checklist))
    }

    /** Applies "Auto-schedule" start times (see [AutoSchedule]). */
    suspend fun applyTimes(context: Context, tasks: List<DailyTask>, times: Map<Long, Int>) {
        val db = context.db
        val moved = db.withTransaction {
            tasks.mapNotNull { t -> times[t.id]?.let { t.copy(timeMinutes = it) } }.onEach { db.tasks().upsertTask(it) }
        }
        moved.forEach { Alarms.scheduleTask(context, it) }
        Widgets.refresh(context)
    }

    /** Returns false if [day] already has 3 priorities. */
    suspend fun toggleTop3(context: Context, task: DailyTask): Boolean {
        if (!task.isTop3 && context.db.tasks().top3Count(task.date) >= 3) return false
        context.db.tasks().upsertTask(task.copy(isTop3 = !task.isTop3))
        Widgets.refresh(context)
        return true
    }

    /**
     * Moves a task to [day]. A pending task simply moves; a missed/skipped one stays on its day
     * (so past scores stay honest) and a fresh copy is added.
     */
    suspend fun moveTo(context: Context, task: DailyTask, day: Long) {
        move(context, task, day)
        Widgets.refresh(context)
    }

    /** [moveTo] for several tasks at once, in one transaction and with one widget refresh. */
    suspend fun moveAll(context: Context, tasks: List<DailyTask>, day: Long) {
        context.db.withTransaction { tasks.forEach { move(context, it, day) } }
        Widgets.refresh(context)
    }

    private suspend fun move(context: Context, task: DailyTask, day: Long) {
        val moved = task.copy(date = day, templateId = null, status = TaskStatus.PENDING, isTop3 = false, completedAt = null)
        // A missed/skipped task stays where it was, so the copy starts with no tracked time.
        if (task.status == TaskStatus.PENDING) {
            context.db.tasks().upsertTask(moved)
            Alarms.scheduleTask(context, moved)
        } else {
            val copy = moved.copy(id = 0, actualMinutes = 0)
            val id = context.db.tasks().upsertTask(copy)
            Alarms.scheduleTask(context, copy.copy(id = id))
        }
    }

    private suspend fun refreshToday(context: Context) {
        context.db.tasks().tasksOn(Day.today()).forEach { Alarms.scheduleTask(context, it) }
        Widgets.refresh(context)
    }

    /** Posts each active recurring entry into the review inbox once per month, on its day. */
    private suspend fun postRecurring(context: Context, today: Long) {
        val finance = context.db.finance()
        val date = Day.date(today)
        val month = YearMonth.from(date)
        var posted = 0
        for (r in finance.activeRecurring()) {
            val dueDay = minOf(r.dayOfMonth, month.lengthOfMonth())
            if (date.dayOfMonth < dueDay || r.lastPostedMonth == month.toString()) continue
            val due = month.atDay(dueDay).toEpochDay()
            // Together, so a crash in between can't post the same entry twice.
            context.db.withTransaction {
                finance.insertIgnore(
                    Txn(
                        amountPaise = r.amountPaise, type = r.type, category = r.category, date = due,
                        timeMillis = Day.millisAt(due, 9 * 60), note = r.title, method = "Bank",
                        status = TxnStatus.PENDING, source = TxnSource.RECURRING,
                    )
                )
                finance.updateRecurring(r.copy(lastPostedMonth = month.toString()))
            }
            posted++
        }
        if (posted > 0) Notifs.pendingPayments(context, finance.pendingCountNow())
    }
}
