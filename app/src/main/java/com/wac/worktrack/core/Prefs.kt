package com.wac.worktrack.core

import android.content.Context
import androidx.core.content.edit

/** Small settings store. Not part of the cloud backup on purpose — these are per-device. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var morningMinutes: Int
        get() = sp.getInt("morning", 7 * 60)
        set(v) = sp.edit { putInt("morning", v) }

    var nightMinutes: Int
        get() = sp.getInt("night", 22 * 60)
        set(v) = sp.edit { putInt("night", v) }

    var taskAlerts: Boolean
        get() = sp.getBoolean("taskAlerts", true)
        set(v) = sp.edit { putBoolean("taskAlerts", v) }

    var routineAlerts: Boolean
        get() = sp.getBoolean("routineAlerts", true)
        set(v) = sp.edit { putBoolean("routineAlerts", v) }

    /** Last day whose tasks were generated from templates. */
    var lastPlannedDay: Long
        get() = sp.getLong("lastPlannedDay", 0)
        set(v) = sp.edit { putLong("lastPlannedDay", v) }

    var offlineMode: Boolean
        get() = sp.getBoolean("offlineMode", false)
        set(v) = sp.edit { putBoolean("offlineMode", v) }

    var smsCapture: Boolean
        get() = sp.getBoolean("smsCapture", false)
        set(v) = sp.edit { putBoolean("smsCapture", v) }

    /** Read GPay / PhonePe / Paytm notifications (needs notification access too). */
    var notificationCapture: Boolean
        get() = sp.getBoolean("notificationCapture", true)
        set(v) = sp.edit { putBoolean("notificationCapture", v) }

    var calendarEnabled: Boolean
        get() = sp.getBoolean("calendarEnabled", false)
        set(v) = sp.edit { putBoolean("calendarEnabled", v) }

    var googleTasksEnabled: Boolean
        get() = sp.getBoolean("googleTasks", false)
        set(v) = sp.edit { putBoolean("googleTasks", v) }

    var autoBackup: Boolean
        get() = sp.getBoolean("autoBackup", true)
        set(v) = sp.edit { putBoolean("autoBackup", v) }

    var lastBackupMillis: Long
        get() = sp.getLong("lastBackup", 0)
        set(v) = sp.edit { putLong("lastBackup", v) }

    // --- Planning ---

    /** Peak-focus hours used by "Auto-schedule" for high-energy tasks. */
    var peakStartMinutes: Int
        get() = sp.getInt("peakStart", 9 * 60)
        set(v) = sp.edit { putInt("peakStart", v) }

    var peakEndMinutes: Int
        get() = sp.getInt("peakEnd", 12 * 60)
        set(v) = sp.edit { putInt("peakEnd", v) }

    var dayStartMinutes: Int
        get() = sp.getInt("dayStart", 8 * 60)
        set(v) = sp.edit { putInt("dayStart", v) }

    // --- Focus timer (a running session survives the app being closed) ---

    var focusMinutes: Int
        get() = sp.getInt("focusMinutes", 25)
        set(v) = sp.edit { putInt("focusMinutes", v) }

    var breakMinutes: Int
        get() = sp.getInt("breakMinutes", 5)
        set(v) = sp.edit { putInt("breakMinutes", v) }

    /** End time of the running focus/break, or 0 when idle. */
    var focusEndAt: Long
        get() = sp.getLong("focusEndAt", 0)
        set(v) = sp.edit { putLong("focusEndAt", v) }

    var focusStartAt: Long
        get() = sp.getLong("focusStartAt", 0)
        set(v) = sp.edit { putLong("focusStartAt", v) }

    var focusIsBreak: Boolean
        get() = sp.getBoolean("focusIsBreak", false)
        set(v) = sp.edit { putBoolean("focusIsBreak", v) }

    /** Task the running session is for, or 0. */
    var focusTaskId: Long
        get() = sp.getLong("focusTaskId", 0)
        set(v) = sp.edit { putLong("focusTaskId", v) }

    var focusTitle: String
        get() = sp.getString("focusTitle", "") ?: ""
        set(v) = sp.edit { putString("focusTitle", v) }

    // --- Look & security ---

    /** "system", "light" or "dark". */
    var themeMode: String
        get() = sp.getString("themeMode", "system") ?: "system"
        set(v) = sp.edit { putString("themeMode", v) }

    /** Wallpaper colours (Android 12+); when off, [accent] is used. */
    var dynamicColor: Boolean
        get() = sp.getBoolean("dynamicColor", true)
        set(v) = sp.edit { putBoolean("dynamicColor", v) }

    var accent: Int
        get() = sp.getInt("accent", 0)
        set(v) = sp.edit { putInt("accent", v) }

    var appLock: Boolean
        get() = sp.getBoolean("appLock", false)
        set(v) = sp.edit { putBoolean("appLock", v) }

    // --- Extras ---

    /** Persistent "Add task" notification with a reply box. */
    var quickAddNotification: Boolean
        get() = sp.getBoolean("quickAddNotif", false)
        set(v) = sp.edit { putBoolean("quickAddNotif", v) }

    var healthConnect: Boolean
        get() = sp.getBoolean("healthConnect", false)
        set(v) = sp.edit { putBoolean("healthConnect", v) }

    /** Badge ids already announced, so each is celebrated once. */
    var earnedBadges: Set<String>
        get() = sp.getStringSet("badges", emptySet()) ?: emptySet()
        set(v) = sp.edit { putStringSet("badges", v) }

    var badgesInitialised: Boolean
        get() = sp.getBoolean("badgesInit", false)
        set(v) = sp.edit { putBoolean("badgesInit", v) }

    // --- Features shown (first-run setup; changeable in Settings) ---

    /** Keys of the [Module]s that are switched on. Everything is on until setup says otherwise. */
    var modules: Set<String>
        get() = sp.getStringSet("modules", null) ?: Module.entries.map { it.key }.toSet()
        set(v) = sp.edit { putStringSet("modules", v) }

    fun has(m: Module) = m.key in modules

    var setupDone: Boolean
        get() = sp.getBoolean("setupDone", false)
        set(v) = sp.edit { putBoolean("setupDone", v) }

    // --- Sleep ---

    var windDown: Boolean
        get() = sp.getBoolean("windDown", false)
        set(v) = sp.edit { putBoolean("windDown", v) }

    var bedtimeMinutes: Int
        get() = sp.getInt("bedtime", 23 * 60)
        set(v) = sp.edit { putInt("bedtime", v) }

    /** How long before bedtime the wind-down reminder comes. */
    var windDownLead: Int
        get() = sp.getInt("windDownLead", 30)
        set(v) = sp.edit { putInt("windDownLead", v) }

    // --- Money ---

    /** Emergency fund target, in months of everyday spending. */
    var emergencyMonths: Int
        get() = sp.getInt("emergencyMonths", 6)
        set(v) = sp.edit { putInt("emergencyMonths", v) }
}
