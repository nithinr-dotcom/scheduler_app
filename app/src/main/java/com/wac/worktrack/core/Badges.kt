package com.wac.worktrack.core

import android.content.Context
import com.wac.worktrack.data.GoalType
import com.wac.worktrack.notify.Notifs
import java.time.YearMonth

data class Badge(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val earned: Boolean,
    /** e.g. "12 / 30" while not earned yet. */
    val progress: String,
)

/** Milestones worked out from the data itself — nothing extra is stored except which were announced. */
object Badges {
    private const val HISTORY_DAYS = 3650L

    suspend fun compute(context: Context, today: Long = Day.today()): List<Badge> {
        val db = context.db
        // Once earned, a badge stays earned (e.g. "Budget keeper" after a later over-budget month).
        val announced = Prefs(context).earnedBadges
        val done = db.tasks().doneCount()
        val rest = db.care().restDatesNow(today - HISTORY_DAYS, today).toSet()
        val dayStreak = Scores.streaks(db.tasks().aggsNow(today - HISTORY_DAYS, today), today, rest).best
        val logs = db.habits().logsNow(today - HISTORY_DAYS, today).groupBy { it.habitId }
        val habitBest = db.habits().allNow().maxOfOrNull { h ->
            Habits.streak(h, Habits.doneDays(h, logs[h.id].orEmpty()), today, rest).best
        } ?: 0
        val saved = db.finance().savedPerGoalNow().associate { it.goalId to it.totalPaise }
        val goalsDone = db.goals().allNow().count { g ->
            val value = if (g.type == GoalType.MONEY) saved[g.id] ?: 0 else g.current
            g.target > 0 && value >= g.target
        }
        val journal = db.life().journalCount()
        val focusHours = db.life().focusTotalMinutes() / 60
        val finished = db.life().learningDoneCount()
        val assets = db.finance().assetsNow().size
        val reviews = db.reviews().count()
        val budgetKept = lastMonthWithinBudget(context, today)

        fun b(id: String, emoji: String, title: String, desc: String, value: Int, goal: Int) =
            Badge(id, emoji, title, desc, value >= goal, "${minOf(value, goal)} / $goal")

        return listOf(
            b("first_task", "✅", "First step", "Finish your first task", done, 1),
            b("tasks_100", "💯", "Century", "Finish 100 tasks", done, 100),
            b("tasks_1000", "🏔️", "Mountain mover", "Finish 1,000 tasks", done, 1000),
            b("streak_7", "🔥", "On fire", "7-day streak", dayStreak, 7),
            b("streak_30", "🌟", "Unstoppable", "30-day streak", dayStreak, 30),
            b("streak_100", "👑", "Legend", "100-day streak", dayStreak, 100),
            b("habit_21", "🌱", "Habit formed", "Keep a habit 21 days in a row", habitBest, 21),
            b("habit_66", "🌳", "Second nature", "Keep a habit 66 days in a row", habitBest, 66),
            b("goal_1", "🎯", "Goal crusher", "Reach a goal", goalsDone, 1),
            Badge("budget_month", "🛡️", "Budget keeper", "Finish a month inside every budget", budgetKept, if (budgetKept) "Done" else "Last month: not yet"),
            b("journal_7", "📓", "Reflective", "Write 7 journal entries", journal, 7),
            b("journal_100", "📚", "Chronicler", "Write 100 journal entries", journal, 100),
            b("focus_10h", "⏱️", "Deep worker", "10 hours of focus sessions", focusHours, 10),
            b("focus_100h", "🧠", "Flow master", "100 hours of focus sessions", focusHours, 100),
            b("learn_1", "📖", "Finisher", "Finish a book or course", finished, 1),
            b("learn_12", "🎓", "Lifelong learner", "Finish 12 books or courses", finished, 12),
            b("networth", "🧭", "Big picture", "Track your net worth", assets, 1),
            b("reviews_4", "🔍", "Reviewer", "Do 4 weekly reviews", reviews, 4),
        ).map { if (!it.earned && it.id in announced) it.copy(earned = true, progress = "Done") else it }
    }

    /** Last complete month had budgets set and every budgeted category stayed within its limit. */
    private suspend fun lastMonthWithinBudget(context: Context, today: Long): Boolean {
        val budgets = context.db.finance().budgetsNow()
        if (budgets.isEmpty()) return false
        val last = YearMonth.from(Day.date(today)).minusMonths(1)
        val spent = context.db.finance()
            .expensesByCategoryNow(last.atDay(1).toEpochDay(), last.atEndOfMonth().toEpochDay())
            .associate { it.category to it.totalPaise }
        if (spent.isEmpty()) return false
        return budgets.all { (spent[it.category] ?: 0) <= it.limitPaise }
    }

    /** Announces badges earned since the last check. The first run only records, so old ones aren't spammed. */
    suspend fun announceNew(context: Context) {
        val prefs = Prefs(context)
        val earned = compute(context).filter { it.earned }
        val known = prefs.earnedBadges
        val firstRun = known.isEmpty() && !prefs.badgesInitialised
        val fresh = earned.filter { it.id !in known }
        if (!firstRun) fresh.forEach { Notifs.badge(context, it) }
        prefs.earnedBadges = known + earned.map { it.id }
        prefs.badgesInitialised = true
    }

    /**
     * Life score for the 7 days up to [today] — see [Scores.lifeScore]. Each part is left out
     * until there's something to measure.
     */
    suspend fun lifeScore(context: Context, today: Long = Day.today()): Int? {
        val db = context.db
        val from = today - 6
        val rest = db.care().restDatesNow(from, today).toSet()
        val taskAvg = db.tasks().aggsNow(from, today).filter { it.date !in rest }.mapNotNull { it.score }
            .takeIf { it.isNotEmpty() }?.average()?.toInt()
        val habits = db.habits().activeNow()
        val habitPct = if (habits.isEmpty()) null else {
            val logs = db.habits().logsNow(from, today)
            var possible = 0
            var hit = 0
            for (h in habits) {
                val done = Habits.doneDays(h, logs)
                // Rest days don't count against you, and neither does today until it's done.
                val days = (maxOf(from, h.createdDate)..today).filter { it in done || (it !in rest && it != today) }
                possible += days.size
                hit += done.count { it in days }
            }
            if (possible == 0) null else hit * 100 / possible
        }
        val budgets = db.finance().budgetsNow()
        val budgetPct = if (budgets.isEmpty()) null else {
            val month = Day.monthRange(today)
            val spent = db.finance().expensesByCategoryNow(month.first, month.last).associate { it.category to it.totalPaise }
            budgets.count { (spent[it.category] ?: 0) <= it.limitPaise } * 100 / budgets.size
        }
        val journal = db.life().journalRangeNow(from, today)
        val journalDays = if (db.life().journalCount() == 0) null else journal.count { it.text.isNotBlank() || it.mood != null }
        return Scores.lifeScore(taskAvg, habitPct, budgetPct, journalDays)
    }
}
