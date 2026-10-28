package com.wac.worktrack.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Per-day task totals used for scores. Top-3 tasks weigh double; skipped tasks don't count. */
data class DayAgg(
    val date: Long,
    val planned: Int,
    val done: Int,
    val skipped: Int,
    val missed: Int,
    val weightPlanned: Int,
    val weightDone: Int,
) {
    /**
     * 0–100, or null when nothing was planned. A day where every task was skipped scores 0 —
     * otherwise skipping everything would keep a streak alive. Days off are what rest days are for.
     */
    val score: Int?
        get() = when {
            planned == 0 -> null
            weightPlanned == 0 -> 0
            else -> weightDone * 100 / weightPlanned
        }
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM task_templates ORDER BY active DESC, timeMinutes IS NULL, timeMinutes, title")
    fun templates(): Flow<List<TaskTemplate>>

    @Query("SELECT * FROM task_templates WHERE active = 1")
    suspend fun activeTemplates(): List<TaskTemplate>

    @Upsert
    suspend fun upsertTemplate(t: TaskTemplate): Long

    @Delete
    suspend fun deleteTemplate(t: TaskTemplate)

    @Query("SELECT * FROM daily_tasks WHERE date = :date ORDER BY isTop3 DESC, timeMinutes IS NULL, timeMinutes, priority DESC, id")
    fun tasksFor(date: Long): Flow<List<DailyTask>>

    @Query("SELECT * FROM daily_tasks WHERE date = :date")
    suspend fun tasksOn(date: Long): List<DailyTask>

    @Query("SELECT * FROM daily_tasks WHERE templateId = :templateId AND date >= :from AND status = 'PENDING'")
    suspend fun openFromTemplate(templateId: Long, from: Long): List<DailyTask>

    @Query("SELECT * FROM daily_tasks WHERE id = :id")
    suspend fun task(id: Long): DailyTask?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(t: DailyTask): Long

    @Upsert
    suspend fun upsertTask(t: DailyTask): Long

    @Delete
    suspend fun deleteTask(t: DailyTask)

    @Query("UPDATE daily_tasks SET status = 'MISSED' WHERE status = 'PENDING' AND date < :today")
    suspend fun markMissedBefore(today: Long): Int

    @Query("SELECT COUNT(*) FROM daily_tasks WHERE date = :date AND isTop3 = 1")
    suspend fun top3Count(date: Long): Int

    @Query(
        """SELECT date, COUNT(*) AS planned,
            SUM(status = 'DONE') AS done,
            SUM(status = 'SKIPPED') AS skipped,
            SUM(status = 'MISSED') AS missed,
            SUM(CASE WHEN status != 'SKIPPED' THEN (CASE WHEN isTop3 THEN 2 ELSE 1 END) ELSE 0 END) AS weightPlanned,
            SUM(CASE WHEN status = 'DONE' THEN (CASE WHEN isTop3 THEN 2 ELSE 1 END) ELSE 0 END) AS weightDone
        FROM daily_tasks WHERE date BETWEEN :from AND :to GROUP BY date ORDER BY date"""
    )
    fun aggs(from: Long, to: Long): Flow<List<DayAgg>>

    @Query(
        """SELECT date, COUNT(*) AS planned,
            SUM(status = 'DONE') AS done,
            SUM(status = 'SKIPPED') AS skipped,
            SUM(status = 'MISSED') AS missed,
            SUM(CASE WHEN status != 'SKIPPED' THEN (CASE WHEN isTop3 THEN 2 ELSE 1 END) ELSE 0 END) AS weightPlanned,
            SUM(CASE WHEN status = 'DONE' THEN (CASE WHEN isTop3 THEN 2 ELSE 1 END) ELSE 0 END) AS weightDone
        FROM daily_tasks WHERE date BETWEEN :from AND :to GROUP BY date ORDER BY date"""
    )
    suspend fun aggsNow(from: Long, to: Long): List<DayAgg>

    @Query("SELECT COUNT(*) FROM daily_tasks WHERE status = 'DONE'")
    suspend fun doneCount(): Int

    @Query("UPDATE daily_tasks SET actualMinutes = actualMinutes + :minutes WHERE id = :id")
    suspend fun addActualMinutes(id: Long, minutes: Int)

    /** Done tasks that had both an estimate and tracked time — for "estimate vs actual". */
    @Query(
        """SELECT COALESCE(SUM(estimateMinutes), 0) AS estimated, COALESCE(SUM(actualMinutes), 0) AS actual, COUNT(*) AS tasks
        FROM daily_tasks WHERE status = 'DONE' AND estimateMinutes IS NOT NULL AND actualMinutes > 0
        AND date BETWEEN :from AND :to"""
    )
    fun estimateAccuracy(from: Long, to: Long): Flow<EstimateAgg>
}

data class EstimateAgg(val estimated: Int, val actual: Int, val tasks: Int)

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY enabled DESC, timeMillis")
    fun all(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE enabled = 1")
    suspend fun enabled(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE enabled = 1 AND placeId IS NOT NULL")
    suspend fun locationReminders(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Long): Reminder?

    @Upsert
    suspend fun upsert(r: Reminder): Long

    @Delete
    suspend fun delete(r: Reminder)
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY id")
    fun active(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY id")
    suspend fun activeNow(): List<Habit>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun get(id: Long): Habit?

    @Query("SELECT * FROM habits")
    suspend fun allNow(): List<Habit>

    @Upsert
    suspend fun upsert(h: Habit): Long

    @Delete
    suspend fun delete(h: Habit)

    @Query("SELECT * FROM habit_logs WHERE date BETWEEN :from AND :to")
    fun logs(from: Long, to: Long): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs WHERE date BETWEEN :from AND :to")
    suspend fun logsNow(from: Long, to: Long): List<HabitLog>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND date = :date")
    suspend fun logOn(habitId: Long, date: Long): HabitLog?

    @Upsert
    suspend fun upsertLog(l: HabitLog)

    @Query("SELECT date FROM habit_logs WHERE habitId = :habitId ORDER BY date DESC")
    fun datesFor(habitId: Long): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun log(l: HabitLog)

    @Delete
    suspend fun unlog(l: HabitLog)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId")
    suspend fun clearLogs(habitId: Long)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE archived = 0 ORDER BY deadline IS NULL, deadline, id")
    fun active(): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun get(id: Long): Goal?

    @Query("SELECT * FROM goals")
    suspend fun allNow(): List<Goal>

    @Query("SELECT * FROM goals WHERE archived = 0 AND type = 'MONEY' AND isEmergency = 1 LIMIT 1")
    fun emergency(): Flow<Goal?>

    @Query("SELECT * FROM goals WHERE archived = 0 AND type = 'MONEY' AND autoSharePercent > 0")
    suspend fun autoShareGoals(): List<Goal>

    @Upsert
    suspend fun upsert(g: Goal): Long

    @Delete
    suspend fun delete(g: Goal)

    @Query("UPDATE goals SET current = MAX(0, current + :delta) WHERE id = :id AND type = 'COUNT'")
    suspend fun addProgress(id: Long, delta: Long)
}

data class CategoryTotal(val category: String, val totalPaise: Long)

data class GoalSaved(val goalId: Long, val totalPaise: Long)

@Dao
interface FinanceDao {
    @Query("SELECT * FROM transactions WHERE status = 'ACCEPTED' AND date BETWEEN :from AND :to ORDER BY date DESC, timeMillis DESC")
    fun accepted(from: Long, to: Long): Flow<List<Txn>>

    @Query("SELECT * FROM transactions WHERE status = 'PENDING' ORDER BY timeMillis DESC")
    fun pending(): Flow<List<Txn>>

    @Query("SELECT COUNT(*) FROM transactions WHERE status = 'PENDING'")
    fun pendingCount(): Flow<Int>

    /** Recently rejected captures, so a wrong "Reject" can be undone. */
    @Query("SELECT * FROM transactions WHERE status = 'REJECTED' ORDER BY timeMillis DESC LIMIT 50")
    fun rejected(): Flow<List<Txn>>

    @Query("SELECT COUNT(*) FROM transactions WHERE status = 'PENDING'")
    suspend fun pendingCountNow(): Int

    @Query(
        """SELECT category, SUM(amountPaise) AS totalPaise FROM transactions
        WHERE status = 'ACCEPTED' AND type = :type AND date BETWEEN :from AND :to
        GROUP BY category ORDER BY totalPaise DESC"""
    )
    fun totalsByCategory(type: TxnType, from: Long, to: Long): Flow<List<CategoryTotal>>

    @Query(
        """SELECT COALESCE(SUM(amountPaise), 0) FROM transactions
        WHERE status = 'ACCEPTED' AND type = :type AND date BETWEEN :from AND :to"""
    )
    fun total(type: TxnType, from: Long, to: Long): Flow<Long>

    /** Money spent, not counting money moved into savings ("Savings" expenses) — that's kept, not gone. */
    @Query(
        """SELECT COALESCE(SUM(amountPaise), 0) FROM transactions
        WHERE status = 'ACCEPTED' AND type = 'EXPENSE' AND category != 'Savings' AND date BETWEEN :from AND :to"""
    )
    fun spending(from: Long, to: Long): Flow<Long>

    @Query(
        """SELECT category, SUM(amountPaise) AS totalPaise FROM transactions
        WHERE status = 'ACCEPTED' AND type = 'EXPENSE' AND category != 'Savings' AND date BETWEEN :from AND :to
        GROUP BY category ORDER BY totalPaise DESC"""
    )
    fun spendingByCategory(from: Long, to: Long): Flow<List<CategoryTotal>>

    @Query("SELECT goalId, SUM(amountPaise) AS totalPaise FROM transactions WHERE status = 'ACCEPTED' AND goalId IS NOT NULL GROUP BY goalId")
    fun savedPerGoal(): Flow<List<GoalSaved>>

    @Query("SELECT goalId, SUM(amountPaise) AS totalPaise FROM transactions WHERE status = 'ACCEPTED' AND goalId IS NOT NULL GROUP BY goalId")
    suspend fun savedPerGoalNow(): List<GoalSaved>

    @Query("SELECT * FROM transactions WHERE status = 'ACCEPTED' AND date BETWEEN :from AND :to ORDER BY date, timeMillis")
    suspend fun acceptedNow(from: Long, to: Long): List<Txn>

    @Query(
        """SELECT category, SUM(amountPaise) AS totalPaise FROM transactions
        WHERE status = 'ACCEPTED' AND type = 'EXPENSE' AND date BETWEEN :from AND :to GROUP BY category"""
    )
    suspend fun expensesByCategoryNow(from: Long, to: Long): List<CategoryTotal>

    @Query("SELECT * FROM budgets")
    suspend fun budgetsNow(): List<Budget>

    /** Spending without money put into savings, for "months of expenses" and the monthly summary. */
    @Query(
        """SELECT COALESCE(SUM(amountPaise), 0) FROM transactions
        WHERE status = 'ACCEPTED' AND type = 'EXPENSE' AND category != 'Savings' AND date BETWEEN :from AND :to"""
    )
    suspend fun livingCostNow(from: Long, to: Long): Long

    @Query(
        """SELECT COALESCE(SUM(amountPaise), 0) FROM transactions
        WHERE status = 'ACCEPTED' AND type = :type AND date BETWEEN :from AND :to"""
    )
    suspend fun totalNow(type: TxnType, from: Long, to: Long): Long

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): Txn?

    /**
     * The same payment already captured from the *other* source (bank SMS vs payment-app
     * notification): same amount and direction within the window, and no conflicting bank
     * reference. Two real payments from the same source are never merged.
     */
    @Query(
        """SELECT COUNT(*) FROM transactions WHERE amountPaise = :amount AND type = :type
        AND source IN ('SMS', 'NOTIFICATION') AND source != :source
        AND (:ref IS NULL OR ref IS NULL OR ref = :ref)
        AND ABS(timeMillis - :at) < :windowMillis"""
    )
    suspend fun similarCaptured(amount: Long, type: TxnType, source: TxnSource, ref: String?, at: Long, windowMillis: Long): Int

    /** An entry recorded by hand, from a bill or a recurring item that a capture may be repeating. */
    @Query(
        """SELECT * FROM transactions WHERE amountPaise = :amount AND type = :type AND status = 'ACCEPTED'
        AND source IN ('MANUAL', 'BILL', 'RECURRING') AND date BETWEEN :from AND :to LIMIT 1"""
    )
    suspend fun recordedLike(amount: Long, type: TxnType, from: Long, to: Long): Txn?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(t: Txn): Long

    @Upsert
    suspend fun upsert(t: Txn): Long

    @Delete
    suspend fun delete(t: Txn)

    @Query("SELECT * FROM budgets ORDER BY category")
    fun budgets(): Flow<List<Budget>>

    @Upsert
    suspend fun upsertBudget(b: Budget)

    @Delete
    suspend fun deleteBudget(b: Budget)

    @Query("SELECT * FROM recurring_txns ORDER BY dayOfMonth")
    fun recurring(): Flow<List<RecurringTxn>>

    @Query("SELECT * FROM recurring_txns WHERE active = 1")
    suspend fun activeRecurring(): List<RecurringTxn>

    @Upsert
    suspend fun upsertRecurring(r: RecurringTxn)

    @Update
    suspend fun updateRecurring(r: RecurringTxn)

    @Delete
    suspend fun deleteRecurring(r: RecurringTxn)

    @Query("SELECT category FROM merchant_rules WHERE merchant = :merchant")
    suspend fun ruleFor(merchant: String): String?

    @Upsert
    suspend fun upsertRule(r: MerchantRule)

    // --- Bills & subscriptions ---

    @Query("SELECT * FROM bills ORDER BY active DESC, title")
    fun bills(): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE active = 1")
    suspend fun activeBills(): List<Bill>

    @Upsert
    suspend fun upsertBill(b: Bill)

    @Delete
    suspend fun deleteBill(b: Bill)

    // --- Lent / borrowed ---

    @Query("SELECT * FROM debts ORDER BY (repaidPaise >= amountPaise), dueDate IS NULL, dueDate, date DESC")
    fun debts(): Flow<List<Debt>>

    @Query("SELECT * FROM debts")
    suspend fun debtsNow(): List<Debt>

    @Upsert
    suspend fun upsertDebt(d: Debt)

    @Delete
    suspend fun deleteDebt(d: Debt)

    // --- Net worth ---

    @Query("SELECT * FROM assets ORDER BY liability, valuePaise DESC")
    fun assets(): Flow<List<Asset>>

    @Query("SELECT * FROM assets")
    suspend fun assetsNow(): List<Asset>

    @Upsert
    suspend fun upsertAsset(a: Asset)

    @Delete
    suspend fun deleteAsset(a: Asset)

    @Query("SELECT * FROM net_worth ORDER BY month")
    fun netWorthHistory(): Flow<List<NetWorthSnapshot>>

    @Upsert
    suspend fun upsertSnapshot(s: NetWorthSnapshot)
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM weekly_reviews WHERE weekStart = :weekStart")
    fun get(weekStart: Long): Flow<WeeklyReview?>

    @Upsert
    suspend fun upsert(r: WeeklyReview)

    @Query("SELECT COUNT(*) FROM weekly_reviews")
    suspend fun count(): Int

    @Query("SELECT * FROM period_reviews WHERE period = :period")
    fun period(period: String): Flow<PeriodReview?>

    @Upsert
    suspend fun upsertPeriod(r: PeriodReview)
}

data class MoodScore(val mood: Int, val days: Int)

/** Personal life log: journal & mood, focus sessions, inbox, learning, places. */
@Dao
interface LifeDao {
    @Query("SELECT * FROM journal WHERE date = :date")
    fun journal(date: Long): Flow<JournalEntry?>

    @Query("SELECT * FROM journal ORDER BY date DESC")
    fun journalAll(): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal WHERE date BETWEEN :from AND :to")
    fun journalRange(from: Long, to: Long): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal WHERE date BETWEEN :from AND :to")
    suspend fun journalRangeNow(from: Long, to: Long): List<JournalEntry>

    @Query("SELECT COUNT(*) FROM journal WHERE text != ''")
    suspend fun journalCount(): Int

    @Upsert
    suspend fun upsertJournal(e: JournalEntry)

    @Query("SELECT * FROM focus_sessions WHERE date BETWEEN :from AND :to ORDER BY startMillis DESC")
    fun focus(from: Long, to: Long): Flow<List<FocusSession>>

    @Query("SELECT COALESCE(SUM(minutes), 0) FROM focus_sessions")
    suspend fun focusTotalMinutes(): Int

    @Query("SELECT COALESCE(SUM(minutes), 0) FROM focus_sessions WHERE date BETWEEN :from AND :to")
    fun focusMinutes(from: Long, to: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(minutes), 0) FROM focus_sessions WHERE date BETWEEN :from AND :to")
    suspend fun focusMinutesNow(from: Long, to: Long): Int

    @Insert
    suspend fun insertFocus(f: FocusSession): Long

    @Query("SELECT * FROM inbox ORDER BY createdMillis DESC")
    fun inbox(): Flow<List<InboxItem>>

    @Query("SELECT COUNT(*) FROM inbox")
    fun inboxCount(): Flow<Int>

    @Upsert
    suspend fun upsertInbox(i: InboxItem)

    @Delete
    suspend fun deleteInbox(i: InboxItem)

    @Query("SELECT * FROM learning ORDER BY status = 'DONE', status = 'PAUSED', startedDate DESC")
    fun learning(): Flow<List<LearningItem>>

    @Query("SELECT COUNT(*) FROM learning WHERE status = 'DONE'")
    suspend fun learningDoneCount(): Int

    @Upsert
    suspend fun upsertLearning(l: LearningItem)

    @Delete
    suspend fun deleteLearning(l: LearningItem)

    @Query("SELECT * FROM places ORDER BY name")
    fun places(): Flow<List<Place>>

    @Query("SELECT * FROM places")
    suspend fun placesNow(): List<Place>

    @Upsert
    suspend fun upsertPlace(p: Place): Long

    @Delete
    suspend fun deletePlace(p: Place)
}

/** Medicines, important dates and rest days. */
@Dao
interface CareDao {
    @Query("SELECT * FROM medicines ORDER BY active DESC, name")
    fun medicines(): Flow<List<Medicine>>

    @Query("SELECT * FROM medicines WHERE active = 1 ORDER BY name")
    fun activeMedicines(): Flow<List<Medicine>>

    @Query("SELECT * FROM medicines WHERE active = 1")
    suspend fun activeMedicinesNow(): List<Medicine>

    @Query("SELECT * FROM medicines WHERE id = :id")
    suspend fun medicine(id: Long): Medicine?

    @Upsert
    suspend fun upsertMedicine(m: Medicine): Long

    @Delete
    suspend fun deleteMedicine(m: Medicine)

    @Query("SELECT * FROM med_logs WHERE date BETWEEN :from AND :to")
    fun medLogs(from: Long, to: Long): Flow<List<MedLog>>

    @Query("SELECT * FROM med_logs WHERE date BETWEEN :from AND :to")
    suspend fun medLogsNow(from: Long, to: Long): List<MedLog>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun logDose(l: MedLog): Long

    @Query("DELETE FROM med_logs WHERE medId = :medId AND date = :date AND minutes = :minutes")
    suspend fun unlogDose(medId: Long, date: Long, minutes: Int): Int

    @Query("DELETE FROM med_logs WHERE medId = :medId")
    suspend fun clearDoses(medId: Long)

    @Query("UPDATE medicines SET stock = MAX(0, stock + :delta) WHERE id = :id AND stock IS NOT NULL")
    suspend fun addStock(id: Long, delta: Int)

    @Query("SELECT * FROM important_dates ORDER BY month, day")
    fun dates(): Flow<List<ImportantDate>>

    @Query("SELECT * FROM important_dates")
    suspend fun datesNow(): List<ImportantDate>

    @Upsert
    suspend fun upsertDate(d: ImportantDate)

    @Delete
    suspend fun deleteDate(d: ImportantDate)

    @Query("SELECT * FROM rest_days WHERE date BETWEEN :from AND :to")
    fun restDays(from: Long, to: Long): Flow<List<RestDay>>

    @Query("SELECT date FROM rest_days WHERE date BETWEEN :from AND :to")
    suspend fun restDatesNow(from: Long, to: Long): List<Long>

    @Query("SELECT * FROM rest_days WHERE date = :date")
    fun restDay(date: Long): Flow<RestDay?>

    @Upsert
    suspend fun upsertRestDay(r: RestDay)

    @Query("DELETE FROM rest_days WHERE date = :date")
    suspend fun deleteRestDay(date: Long)
}
