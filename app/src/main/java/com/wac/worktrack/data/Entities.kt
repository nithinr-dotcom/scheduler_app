package com.wac.worktrack.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Dates are stored as epoch days (LocalDate.toEpochDay) so day queries are simple integer ranges.
// Money is stored in paise to avoid floating-point rounding.

enum class Repeat { DAILY, WEEKDAYS, ONCE }

/** A task definition that generates a [DailyTask] on each matching day. */
@Entity(tableName = "task_templates")
data class TaskTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String,
    val priority: Int = PRIORITY_NORMAL,
    val repeat: Repeat = Repeat.DAILY,
    /** Bit 0 = Monday … bit 6 = Sunday; used when [repeat] is WEEKDAYS. */
    val daysMask: Int = ALL_DAYS,
    /** Used when [repeat] is ONCE. */
    val onceDate: Long? = null,
    /** Minutes after midnight, or null for "any time today". */
    val timeMinutes: Int? = null,
    val goalId: Long? = null,
    val active: Boolean = true,
    val estimateMinutes: Int? = null,
    val energy: Int = ENERGY_ANY,
    /** Checklist copied into each generated task; see [com.wac.worktrack.core.Checklist]. */
    val checklist: String = "",
) {
    companion object {
        const val ALL_DAYS = 0b1111111
    }
}

enum class TaskStatus { PENDING, DONE, SKIPPED, MISSED }

const val PRIORITY_LOW = 0
const val PRIORITY_NORMAL = 1
const val PRIORITY_HIGH = 2

/** How much focus a task needs; "Auto-schedule" puts high-energy tasks in the peak hours. */
const val ENERGY_ANY = 0
const val ENERGY_LOW = 1
const val ENERGY_HIGH = 2

/** One task on one day — either generated from a template or added just for that day. */
@Entity(
    tableName = "daily_tasks",
    indices = [Index(value = ["date", "templateId"], unique = true), Index("date")],
)
data class DailyTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val templateId: Long? = null,
    val title: String,
    val category: String,
    val priority: Int = PRIORITY_NORMAL,
    val timeMinutes: Int? = null,
    val goalId: Long? = null,
    val status: TaskStatus = TaskStatus.PENDING,
    val isTop3: Boolean = false,
    val completedAt: Long? = null,
    val estimateMinutes: Int? = null,
    /** Minutes actually spent — from focus sessions or entered by hand. */
    val actualMinutes: Int = 0,
    val energy: Int = ENERGY_ANY,
    val checklist: String = "",
)

/** App-only reminder (never written to Google Calendar). */
@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val timeMillis: Long,
    val repeatDaily: Boolean = false,
    val enabled: Boolean = true,
    /** Location reminder: fires on arriving at this [Place] instead of at [timeMillis]. */
    val placeId: Long? = null,
)

/** Health Connect value that can tick a habit automatically. */
enum class HealthSource { STEPS, SLEEP_MINUTES, EXERCISE_MINUTES }

/** A yes/no habit has [target] 1; a count habit (water glasses, pages) has a higher target. */
@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdDate: Long,
    val archived: Boolean = false,
    val target: Int = 1,
    val unit: String = "",
    val autoSource: HealthSource? = null,
)

/** A day's check-in; the habit counts as done that day when [count] reaches the habit's target. */
@Entity(tableName = "habit_logs", primaryKeys = ["habitId", "date"], indices = [Index("date")])
data class HabitLog(
    val habitId: Long,
    val date: Long,
    val count: Int = 1,
)

enum class GoalType { COUNT, MONEY }

/**
 * COUNT goals track [current] directly (linked tasks add 1 when done).
 * MONEY goals sum accepted transactions tagged with this goal's id.
 */
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: GoalType = GoalType.COUNT,
    /** Count target, or paise for MONEY goals. */
    val target: Long,
    val current: Long = 0,
    val unit: String = "",
    val deadline: Long? = null,
    val createdDate: Long,
    val archived: Boolean = false,
    /** MONEY goals: suggest putting this share of every accepted income aside (via Pending). */
    val autoSharePercent: Int = 0,
    /** The goal shown as "Emergency fund" on Finance, measured in months of spending. */
    @ColumnInfo(defaultValue = "0") val isEmergency: Boolean = false,
)

enum class TxnType { INCOME, EXPENSE }

/** PENDING entries are waiting in the review inbox and never count in totals until accepted. */
enum class TxnStatus { PENDING, ACCEPTED, REJECTED }

enum class TxnSource { MANUAL, SMS, NOTIFICATION, RECURRING, AUTO_SAVE, BILL }

@Entity(
    tableName = "transactions",
    // Rejected rows are kept so the same bank reference is never captured twice.
    indices = [Index(value = ["ref"], unique = true), Index("date"), Index("status")],
)
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val type: TxnType,
    val category: String,
    val date: Long,
    val timeMillis: Long,
    val note: String = "",
    val method: String = "UPI",
    val status: TxnStatus = TxnStatus.ACCEPTED,
    val source: TxnSource = TxnSource.MANUAL,
    val ref: String? = null,
    val merchant: String? = null,
    val goalId: Long? = null,
)

/** Monthly spending limit for an expense category (same limit every month). */
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val category: String,
    val limitPaise: Long,
)

/** Salary, rent, subscriptions… posted into the review inbox each month on [dayOfMonth]. */
@Entity(tableName = "recurring_txns")
data class RecurringTxn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amountPaise: Long,
    val type: TxnType,
    val category: String,
    val dayOfMonth: Int,
    /** "yyyy-MM" of the last month this was posted for. */
    val lastPostedMonth: String? = null,
    val active: Boolean = true,
)

/** Learned "this merchant is always this category" rule for auto-captured payments. */
@Entity(tableName = "merchant_rules")
data class MerchantRule(
    @PrimaryKey val merchant: String,
    val category: String,
)

@Entity(tableName = "weekly_reviews")
data class WeeklyReview(
    /** Epoch day of the Monday that starts the week. */
    @PrimaryKey val weekStart: Long,
    val wentWell: String = "",
    val improve: String = "",
)

@Entity(tableName = "period_reviews")
data class PeriodReview(
    /** "2026-10" for a month, "2026" for a year. */
    @PrimaryKey val period: String,
    val wentWell: String = "",
    val improve: String = "",
)

enum class BillCycle { MONTHLY, YEARLY }

/** A bill or subscription with a due date. "Mark paid" records the expense for that period. */
@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amountPaise: Long,
    val category: String = "Bills",
    val cycle: BillCycle = BillCycle.MONTHLY,
    val dueDay: Int,
    /** 1–12; used for YEARLY bills. */
    val dueMonth: Int = 1,
    val isSubscription: Boolean = false,
    val remindDaysBefore: Int = 2,
    /** Period last paid: "yyyy-MM" (monthly) or "yyyy" (yearly). */
    val lastPaidPeriod: String? = null,
    val active: Boolean = true,
)

@Entity(tableName = "focus_sessions", indices = [Index("date"), Index("taskId")])
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long? = null,
    val title: String,
    val date: Long,
    val startMillis: Long,
    val minutes: Int,
)

/** One entry per day: a short journal note and a 1–5 mood. */
@Entity(tableName = "journal")
data class JournalEntry(
    @PrimaryKey val date: Long,
    val text: String = "",
    val mood: Int? = null,
)

/** Someday / inbox: ideas that don't belong to a day yet. */
@Entity(tableName = "inbox")
data class InboxItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val createdMillis: Long,
)

/** A saved place for location reminders (e.g. "Office", saved while standing there). */
@Entity(tableName = "places")
data class Place(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lat: Double,
    val lng: Double,
    val radiusMeters: Int = 150,
)

enum class LearnKind { BOOK, COURSE, OTHER }

enum class LearnStatus { ACTIVE, PAUSED, DONE }

@Entity(tableName = "learning")
data class LearningItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val kind: LearnKind = LearnKind.BOOK,
    val total: Int,
    val progress: Int = 0,
    /** "pages", "lessons", "chapters"… */
    val unit: String,
    val status: LearnStatus = LearnStatus.ACTIVE,
    val startedDate: Long,
    val finishedDate: Long? = null,
)

enum class DebtDirection { LENT, BORROWED }

/** Money lent to / borrowed from someone. Settled once [repaidPaise] reaches [amountPaise]. */
@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val person: String,
    val amountPaise: Long,
    val direction: DebtDirection,
    val date: Long,
    val dueDate: Long? = null,
    val note: String = "",
    val repaidPaise: Long = 0,
) {
    val outstanding: Long get() = (amountPaise - repaidPaise).coerceAtLeast(0)
}

/** Something you own (account, investment…) or owe (loan, card). Values are updated by hand. */
@Entity(tableName = "assets")
data class Asset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
    val valuePaise: Long,
    val liability: Boolean = false,
    val updatedMillis: Long,
)

/** Net worth at the end of each month, kept for the trend chart. */
@Entity(tableName = "net_worth")
data class NetWorthSnapshot(
    /** "yyyy-MM" */
    @PrimaryKey val month: String,
    val assetsPaise: Long,
    val liabilitiesPaise: Long,
)

/** A medicine taken at fixed times each day. [stock] counts doses left; null = not tracked. */
@Entity(tableName = "medicines")
data class Medicine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dose: String = "",
    /** Dose times as minutes after midnight, comma-separated ("480,1260"); see [com.wac.worktrack.core.Meds]. */
    val times: String,
    val stock: Int? = null,
    /** Warn about a refill when [stock] drops to this. */
    val refillAt: Int = 5,
    val active: Boolean = true,
)

/** One dose taken: medicine, day and the scheduled time it was for. */
@Entity(tableName = "med_logs", primaryKeys = ["medId", "date", "minutes"])
data class MedLog(
    val medId: Long,
    val date: Long,
    val minutes: Int,
    val takenAt: Long,
)

enum class DateKind { BIRTHDAY, ANNIVERSARY, OTHER }

/** Birthday, anniversary… repeating every year on [month]/[day]. [year] is optional (for "turns 30"). */
@Entity(tableName = "important_dates")
data class ImportantDate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val kind: DateKind = DateKind.BIRTHDAY,
    val month: Int,
    val day: Int,
    val year: Int? = null,
    val remindDaysBefore: Int = 3,
)

/** A planned rest or sick day: it neither builds nor breaks streaks and isn't scored. */
@Entity(tableName = "rest_days")
data class RestDay(
    @PrimaryKey val date: Long,
    val reason: String = "",
)
