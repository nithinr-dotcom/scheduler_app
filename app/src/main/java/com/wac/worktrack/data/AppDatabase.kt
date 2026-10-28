package com.wac.worktrack.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskTemplate::class, DailyTask::class, Reminder::class, Habit::class, HabitLog::class,
        Goal::class, Txn::class, Budget::class, RecurringTxn::class, MerchantRule::class,
        WeeklyReview::class, PeriodReview::class, Bill::class, FocusSession::class, JournalEntry::class,
        InboxItem::class, Place::class, LearningItem::class, Debt::class, Asset::class, NetWorthSnapshot::class,
        Medicine::class, MedLog::class, ImportantDate::class, RestDay::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun reminders(): ReminderDao
    abstract fun habits(): HabitDao
    abstract fun goals(): GoalDao
    abstract fun finance(): FinanceDao
    abstract fun reviews(): ReviewDao
    abstract fun life(): LifeDao
    abstract fun care(): CareDao

    companion object {
        const val NAME = "worktrack.db"

        /** v2: estimates/energy/checklists, count & health habits, location reminders, and the life/money tables. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "ALTER TABLE task_templates ADD COLUMN estimateMinutes INTEGER",
                    "ALTER TABLE task_templates ADD COLUMN energy INTEGER NOT NULL DEFAULT 0",
                    "ALTER TABLE task_templates ADD COLUMN checklist TEXT NOT NULL DEFAULT ''",
                    "ALTER TABLE daily_tasks ADD COLUMN estimateMinutes INTEGER",
                    "ALTER TABLE daily_tasks ADD COLUMN actualMinutes INTEGER NOT NULL DEFAULT 0",
                    "ALTER TABLE daily_tasks ADD COLUMN energy INTEGER NOT NULL DEFAULT 0",
                    "ALTER TABLE daily_tasks ADD COLUMN checklist TEXT NOT NULL DEFAULT ''",
                    "ALTER TABLE reminders ADD COLUMN placeId INTEGER",
                    "ALTER TABLE habits ADD COLUMN target INTEGER NOT NULL DEFAULT 1",
                    "ALTER TABLE habits ADD COLUMN unit TEXT NOT NULL DEFAULT ''",
                    "ALTER TABLE habits ADD COLUMN autoSource TEXT",
                    "ALTER TABLE habit_logs ADD COLUMN count INTEGER NOT NULL DEFAULT 1",
                    "ALTER TABLE goals ADD COLUMN autoSharePercent INTEGER NOT NULL DEFAULT 0",
                    "CREATE TABLE IF NOT EXISTS `period_reviews` (`period` TEXT NOT NULL, `wentWell` TEXT NOT NULL, `improve` TEXT NOT NULL, PRIMARY KEY(`period`))",
                    "CREATE TABLE IF NOT EXISTS `bills` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, `category` TEXT NOT NULL, `cycle` TEXT NOT NULL, `dueDay` INTEGER NOT NULL, `dueMonth` INTEGER NOT NULL, `isSubscription` INTEGER NOT NULL, `remindDaysBefore` INTEGER NOT NULL, `lastPaidPeriod` TEXT, `active` INTEGER NOT NULL)",
                    "CREATE TABLE IF NOT EXISTS `focus_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `taskId` INTEGER, `title` TEXT NOT NULL, `date` INTEGER NOT NULL, `startMillis` INTEGER NOT NULL, `minutes` INTEGER NOT NULL)",
                    "CREATE INDEX IF NOT EXISTS `index_focus_sessions_date` ON `focus_sessions` (`date`)",
                    "CREATE INDEX IF NOT EXISTS `index_focus_sessions_taskId` ON `focus_sessions` (`taskId`)",
                    "CREATE TABLE IF NOT EXISTS `journal` (`date` INTEGER NOT NULL, `text` TEXT NOT NULL, `mood` INTEGER, PRIMARY KEY(`date`))",
                    "CREATE TABLE IF NOT EXISTS `inbox` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `note` TEXT NOT NULL, `createdMillis` INTEGER NOT NULL)",
                    "CREATE TABLE IF NOT EXISTS `places` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL, `radiusMeters` INTEGER NOT NULL)",
                    "CREATE TABLE IF NOT EXISTS `learning` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `kind` TEXT NOT NULL, `total` INTEGER NOT NULL, `progress` INTEGER NOT NULL, `unit` TEXT NOT NULL, `status` TEXT NOT NULL, `startedDate` INTEGER NOT NULL, `finishedDate` INTEGER)",
                    "CREATE TABLE IF NOT EXISTS `debts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `person` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, `direction` TEXT NOT NULL, `date` INTEGER NOT NULL, `dueDate` INTEGER, `note` TEXT NOT NULL, `repaidPaise` INTEGER NOT NULL)",
                    "CREATE TABLE IF NOT EXISTS `assets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `valuePaise` INTEGER NOT NULL, `liability` INTEGER NOT NULL, `updatedMillis` INTEGER NOT NULL)",
                    "CREATE TABLE IF NOT EXISTS `net_worth` (`month` TEXT NOT NULL, `assetsPaise` INTEGER NOT NULL, `liabilitiesPaise` INTEGER NOT NULL, PRIMARY KEY(`month`))",
                ).forEach(db::execSQL)
            }
        }

        /** v3: medicines, important dates, rest days and the emergency-fund goal flag. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "ALTER TABLE goals ADD COLUMN isEmergency INTEGER NOT NULL DEFAULT 0",
                    "CREATE TABLE IF NOT EXISTS `medicines` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `dose` TEXT NOT NULL, `times` TEXT NOT NULL, `stock` INTEGER, `refillAt` INTEGER NOT NULL, `active` INTEGER NOT NULL)",
                    "CREATE TABLE IF NOT EXISTS `med_logs` (`medId` INTEGER NOT NULL, `date` INTEGER NOT NULL, `minutes` INTEGER NOT NULL, `takenAt` INTEGER NOT NULL, PRIMARY KEY(`medId`, `date`, `minutes`))",
                    "CREATE TABLE IF NOT EXISTS `important_dates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `kind` TEXT NOT NULL, `month` INTEGER NOT NULL, `day` INTEGER NOT NULL, `year` INTEGER, `remindDaysBefore` INTEGER NOT NULL)",
                    "CREATE TABLE IF NOT EXISTS `rest_days` (`date` INTEGER NOT NULL, `reason` TEXT NOT NULL, PRIMARY KEY(`date`))",
                ).forEach(db::execSQL)
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { instance = it }
        }
    }
}
