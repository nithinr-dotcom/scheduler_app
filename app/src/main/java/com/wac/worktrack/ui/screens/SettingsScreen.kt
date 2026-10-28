@file:OptIn(ExperimentalMaterial3Api::class)

package com.wac.worktrack.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.fragment.app.FragmentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.wac.worktrack.MainActivity
import com.wac.worktrack.data.Snapshot
import com.wac.worktrack.health.HealthSync
import com.wac.worktrack.notify.Notifs
import com.wac.worktrack.ui.Accents
import com.wac.worktrack.ui.AppLock
import com.wac.worktrack.ui.ThemeState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.wac.worktrack.calendar.Calendars
import com.wac.worktrack.calendar.GoogleTasks
import com.wac.worktrack.cloud.Auth
import com.wac.worktrack.cloud.CloudBackup
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.Module
import com.wac.worktrack.core.Prefs
import com.wac.worktrack.core.millisTimeLabel
import com.wac.worktrack.core.timeLabel
import com.wac.worktrack.finance.Capture
import com.wac.worktrack.notify.Alarms
import com.wac.worktrack.ui.ConfirmDialog
import com.wac.worktrack.ui.LocalResumeTick
import com.wac.worktrack.ui.ModuleState
import com.wac.worktrack.ui.SectionTitle
import com.wac.worktrack.ui.TimePickerDialog
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(nav: NavController, onSignedOut: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val prefs = remember { Prefs(context) }
    val tick = LocalResumeTick.current // re-read permission state after returning from system settings

    var user by remember { mutableStateOf(Auth.user(context)) }
    var busy by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    var pickMorning by remember { mutableStateOf(false) }
    var pickNight by remember { mutableStateOf(false) }
    var pickPlanning by remember { mutableStateOf<String?>(null) }
    var pickBedtime by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf<android.net.Uri?>(null) }
    var version by remember { mutableIntStateOf(0) } // bump to re-read prefs after changes

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    fun changed() { version++ }

    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        prefs.calendarEnabled = granted
        if (!granted) toast("Calendar permission is needed to show your events.")
        changed()
    }
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        prefs.smsCapture = result[Manifest.permission.RECEIVE_SMS] == true
        if (!prefs.smsCapture) toast("SMS permission is needed for auto-capture.")
        changed()
    }
    val tasksConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        when (val auth = GoogleTasks.fromConsentResult(context, result.data)) {
            is GoogleTasks.Auth.Token -> { prefs.googleTasksEnabled = true; GoogleTasks.clearCache() }
            is GoogleTasks.Auth.Failed -> toast("Google Tasks: ${auth.message}")
            is GoogleTasks.Auth.NeedsConsent -> toast("Google Tasks access was not granted.")
        }
        changed()
    }

    val healthPermission = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        prefs.healthConnect = granted.containsAll(HealthSync.PERMISSIONS)
        if (prefs.healthConnect) scope.launch { HealthSync.sync(context) } else toast("Health Connect access wasn't granted.")
        changed()
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching { Snapshot.writeFile(context, uri) }
                .onSuccess { toast("Saved $it items to the backup file.") }
                .onFailure { toast("Export failed: ${it.message}") }
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmImport = uri }

    // Keyed on version/tick so toggles re-read prefs and permissions after each change or resume.
    val exactAllowed = remember(version, tick) { Alarms.exactAllowed(context) }
    val listenerOn = remember(version, tick) { Capture.notificationAccessGranted(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            // --- Account & backup ---
            SectionTitle("Account & cloud backup")
            if (!Auth.firebaseReady(context)) {
                Info("Firebase isn't set up yet, so everything is saved on this phone only. Add app/google-services.json and rebuild to enable Google Sign-In and backup.")
            } else if (user == null) {
                Info("Not signed in. Sign in to back up your data to your Google account.")
                ButtonRow {
                    OutlinedButton(enabled = busy == null, onClick = {
                        busy = "signin"
                        scope.launch {
                            Auth.signIn(activity).onSuccess { user = it }.onFailure { toast(it.message ?: "Sign-in failed") }
                            busy = null
                        }
                    }) { Text("Sign in with Google") }
                }
            } else {
                Info("Signed in as ${user?.email}")
                val last = prefs.lastBackupMillis
                Info(if (last == 0L) "Never backed up." else "Last backup: ${Day.label(Day.ofMillis(last))} ${millisTimeLabel(last)}")
                ButtonRow {
                    OutlinedButton(enabled = busy == null, onClick = {
                        busy = "backup"
                        scope.launch {
                            runCatching { CloudBackup.backup(context) }
                                .onSuccess { toast("Backed up $it items.") }
                                .onFailure { toast("Backup failed: ${it.message}") }
                            busy = null
                            changed()
                        }
                    }) { Text(if (busy == "backup") "Backing up…" else "Back up now") }
                    Spacer(Modifier.padding(4.dp))
                    OutlinedButton(enabled = busy == null, onClick = { confirmRestore = true }) { Text("Restore") }
                }
                Toggle("Back up automatically every day", prefs.autoBackup) {
                    prefs.autoBackup = it
                    CloudBackup.schedule(context)
                    changed()
                }
                ButtonRow {
                    TextButton(onClick = {
                        scope.launch {
                            Auth.signOut(context)
                            user = null
                            onSignedOut()
                        }
                    }) { Text("Sign out", color = MaterialTheme.colorScheme.error) }
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))

            // --- Backup file (works without Firebase) ---
            SectionTitle("Backup file")
            Info("Save everything to a file (e.g. on Google Drive or your phone) and restore it on any phone. Works without signing in.")
            ButtonRow {
                OutlinedButton(onClick = { exportFile.launch("worktrack-backup-${Day.date(Day.today())}.json") }) { Text("Export") }
                Spacer(Modifier.padding(4.dp))
                OutlinedButton(onClick = { importFile.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }) { Text("Import") }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))

            // --- Features ---
            SectionTitle("Features")
            Setting("Choose what the app shows", "Hidden features keep their data") { nav.navigate("features") }
            HorizontalDivider(Modifier.padding(top = 8.dp))

            // --- Appearance ---
            SectionTitle("Appearance")
            Row(Modifier.padding(horizontal = 16.dp)) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (mode, label) ->
                    FilterChip(ThemeState.mode == mode, {
                        ThemeState.mode = mode
                        ThemeState.save(context)
                    }, { Text(label) }, Modifier.padding(end = 6.dp))
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Toggle("Use wallpaper colours", ThemeState.dynamic) {
                    ThemeState.dynamic = it
                    ThemeState.save(context)
                }
            }
            if (!ThemeState.dynamic || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Accents.forEachIndexed { i, (name, color) ->
                        Box(
                            Modifier.padding(end = 10.dp).size(36.dp).background(color, CircleShape)
                                .border(3.dp, if (ThemeState.accent == i) MaterialTheme.colorScheme.onSurface else color, CircleShape)
                                .clickable {
                                    ThemeState.accent = i
                                    ThemeState.save(context)
                                },
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))

            // --- Privacy ---
            SectionTitle("Privacy")
            Toggle("App lock (fingerprint / face / screen lock)", prefs.appLock) { on ->
                val fragmentActivity = activity as FragmentActivity
                if (on && !AppLock.canLock(fragmentActivity)) {
                    toast("Set up a screen lock on your phone first.")
                } else if (on) {
                    // Confirm once so nobody locks themselves out by accident.
                    AppLock.prompt(fragmentActivity, onUnlocked = {
                        prefs.appLock = true
                        (activity as? MainActivity)?.applyRecentsPrivacy()
                        changed()
                    })
                } else {
                    prefs.appLock = false
                    (activity as? MainActivity)?.applyRecentsPrivacy()
                    changed()
                }
            }
            Info("Locks after 30 seconds in the background and hides the app in recent apps.")
            HorizontalDivider(Modifier.padding(top = 8.dp))

            // --- Planning ---
            SectionTitle("Planning")
            Setting("Day starts at", timeLabel(prefs.dayStartMinutes)) { pickPlanning = "dayStart" }
            Setting(
                "Peak focus hours (for ⚡ tasks)",
                "${timeLabel(prefs.peakStartMinutes)} – ${timeLabel(prefs.peakEndMinutes)} · tap to change start",
            ) { pickPlanning = "peakStart" }
            Setting("Peak focus ends", timeLabel(prefs.peakEndMinutes)) { pickPlanning = "peakEnd" }
            Toggle("Quick-add notification (type a task from the shade)", prefs.quickAddNotification) {
                prefs.quickAddNotification = it
                Notifs.quickAdd(context)
                changed()
            }
            Info("Tip: add the \"Quick add\" tile to Quick Settings (edit the tiles in your notification shade).")
            HorizontalDivider(Modifier.padding(top = 8.dp))

            // --- Health Connect ---
            SectionTitle("Health Connect (read-only)")
            Info("Steps, sleep and workouts can tick habits automatically. Nothing is written to Health Connect.")
            val hcStatus = remember(tick) { HealthConnectClient.getSdkStatus(context) }
            when (hcStatus) {
                HealthConnectClient.SDK_AVAILABLE -> Toggle("Use Health Connect", prefs.healthConnect) { on ->
                    if (on) healthPermission.launch(HealthSync.PERMISSIONS)
                    else { prefs.healthConnect = false; changed() }
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Setting("Health Connect needs an update", "Tap to open the Play Store") {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, "market://details?id=com.google.android.apps.healthdata".toUri())
                    )
                }
                else -> Info("Health Connect isn't available on this phone.")
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))

            // --- Notifications ---
            SectionTitle("Notifications")
            Toggle("Alerts for tasks with a time (with Done / Snooze)", prefs.taskAlerts) {
                prefs.taskAlerts = it
                scope.launch { Alarms.rescheduleAll(context) }
                changed()
            }
            Toggle("Morning plan & night review", prefs.routineAlerts) {
                prefs.routineAlerts = it
                Alarms.scheduleRoutines(context)
                changed()
            }
            if (prefs.routineAlerts) {
                Setting("Morning “Plan your day”", timeLabel(prefs.morningMinutes)) { pickMorning = true }
                Setting("Night review", timeLabel(prefs.nightMinutes)) { pickNight = true }
                Info("Weekly review: Sundays at 7:00 PM. Month and year reviews: on the 1st. Bills due: checked at 9:00 AM.")
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))

            // --- Sleep ---
            SectionTitle("Sleep")
            Toggle("Wind-down reminder before bed", prefs.windDown) {
                prefs.windDown = it
                Alarms.scheduleRoutines(context)
                changed()
            }
            if (prefs.windDown) {
                Setting("Bedtime", timeLabel(prefs.bedtimeMinutes)) { pickBedtime = true }
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Remind me", Modifier.padding(end = 8.dp))
                    listOf(15, 30, 45, 60).forEach { m ->
                        FilterChip(prefs.windDownLead == m, {
                            prefs.windDownLead = m
                            Alarms.scheduleRoutines(context)
                            changed()
                        }, { Text("$m min") }, Modifier.padding(end = 6.dp))
                    }
                }
                Info("before bedtime. With Health Connect on, the morning notification shows how long you slept.")
            }
            if (ModuleState.has(Module.MONEY)) {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                SectionTitle("Emergency fund")
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Target", Modifier.padding(end = 8.dp))
                    listOf(3, 6, 12).forEach { m ->
                        FilterChip(prefs.emergencyMonths == m, { prefs.emergencyMonths = m; changed() }, { Text("$m months") }, Modifier.padding(end = 6.dp))
                    }
                }
                Info("Months of everyday spending to keep aside. Shown on Finance → Overview.")
            }
            if (!exactAllowed) {
                Setting("Exact alarms are off", "Allow so alerts arrive on time") {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))

            // --- Google (read-only) ---
            SectionTitle("Google Calendar (read-only)")
            Info("The app only reads from Google. Your app tasks and reminders are never added to Google Calendar.")
            Toggle("Show Google Calendar events", prefs.calendarEnabled && Calendars.hasPermission(context)) { on ->
                if (on && !Calendars.hasPermission(context)) calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                else { prefs.calendarEnabled = on; changed() }
            }
            Toggle("Show Google Tasks / Reminders", prefs.googleTasksEnabled) { on ->
                if (!on) {
                    prefs.googleTasksEnabled = false
                    GoogleTasks.clearCache()
                    changed()
                } else {
                    scope.launch {
                        when (val auth = GoogleTasks.authorize(context)) {
                            is GoogleTasks.Auth.Token -> { prefs.googleTasksEnabled = true; GoogleTasks.clearCache() }
                            is GoogleTasks.Auth.NeedsConsent -> tasksConsent.launch(IntentSenderRequest.Builder(auth.intent.intentSender).build())
                            is GoogleTasks.Auth.Failed -> toast("Google Tasks: ${auth.message}")
                        }
                        changed()
                    }
                }
            }
            Info("Google Tasks only shares the due date (not the time), so they appear as all-day items.")
            HorizontalDivider(Modifier.padding(top = 8.dp))

            // --- Auto-capture ---
            SectionTitle("Payment auto-capture")
            Info("Captured payments go to Finance → Pending. Nothing is added to your expenses until you accept it. Message text is never stored.")
            Toggle("Read bank SMS", prefs.smsCapture && Capture.hasSmsPermission(context)) { on ->
                if (on && !Capture.hasSmsPermission(context)) {
                    smsPermission.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
                } else {
                    prefs.smsCapture = on
                    changed()
                }
            }
            if (prefs.smsCapture && Capture.hasSmsPermission(context)) {
                ButtonRow {
                    OutlinedButton(enabled = busy == null, onClick = {
                        busy = "scan"
                        scope.launch {
                            val n = Capture.scanInbox(context, 30)
                            toast(if (n == 0) "No new payments found in the last 30 days." else "$n payments added to Pending for review.")
                            busy = null
                        }
                    }) { Text(if (busy == "scan") "Scanning…" else "Scan last 30 days of SMS") }
                }
            }
            if (listenerOn) {
                Toggle("Read GPay / PhonePe / Paytm notifications", prefs.notificationCapture) {
                    prefs.notificationCapture = it
                    changed()
                }
            }
            Setting(
                if (listenerOn) "Notification access" else "GPay / PhonePe / Paytm notifications",
                if (listenerOn) "Allowed — tap to remove it in system settings" else "Off — tap to allow notification access",
            ) {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmRestore) {
        ConfirmDialog(
            "Restore from cloud?",
            "This replaces everything on this phone with your last cloud backup.",
            "Restore",
            onDismiss = { confirmRestore = false },
        ) {
            busy = "restore"
            scope.launch {
                runCatching { CloudBackup.restore(context) }
                    .onSuccess { toast(if (it < 0) "No backup found for this account." else "Restored $it items.") }
                    .onFailure { toast("Restore failed: ${it.message}") }
                busy = null
            }
        }
    }
    confirmImport?.let { uri ->
        ConfirmDialog(
            "Import backup file?",
            "This replaces everything on this phone with the data in the file.",
            "Import",
            onDismiss = { confirmImport = null },
        ) {
            scope.launch {
                runCatching { Snapshot.readFile(context, uri) }
                    .onSuccess { toast("Imported $it items.") }
                    .onFailure { toast("Import failed: ${it.message}") }
            }
        }
    }
    pickPlanning?.let { which ->
        val initial = when (which) {
            "dayStart" -> prefs.dayStartMinutes
            "peakStart" -> prefs.peakStartMinutes
            else -> prefs.peakEndMinutes
        }
        TimePickerDialog(initial, { pickPlanning = null }) {
            when (which) {
                "dayStart" -> prefs.dayStartMinutes = it
                "peakStart" -> prefs.peakStartMinutes = it
                else -> prefs.peakEndMinutes = it
            }
            if (prefs.peakEndMinutes <= prefs.peakStartMinutes) prefs.peakEndMinutes = minOf(prefs.peakStartMinutes + 180, 23 * 60)
            pickPlanning = null
            changed()
        }
    }
    if (pickBedtime) {
        TimePickerDialog(prefs.bedtimeMinutes, { pickBedtime = false }) {
            prefs.bedtimeMinutes = it
            Alarms.scheduleRoutines(context)
            pickBedtime = false
            changed()
        }
    }
    if (pickMorning) {
        TimePickerDialog(prefs.morningMinutes, { pickMorning = false }) {
            prefs.morningMinutes = it
            Alarms.scheduleRoutines(context)
            pickMorning = false
            changed()
        }
    }
    if (pickNight) {
        TimePickerDialog(prefs.nightMinutes, { pickNight = false }) {
            prefs.nightMinutes = it
            Alarms.scheduleRoutines(context)
            pickNight = false
            changed()
        }
    }
}

@Composable
private fun Info(text: String) {
    Text(
        text, Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChange)
    }
}

@Composable
private fun Setting(label: String, value: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ButtonRow(content: @Composable () -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}
