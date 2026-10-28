package com.wac.worktrack

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.DayPlanner
import com.wac.worktrack.core.Prefs
import com.wac.worktrack.notify.Geofences
import com.wac.worktrack.ui.AppLock
import com.wac.worktrack.ui.AppRoot
import com.wac.worktrack.ui.ModuleState
import com.wac.worktrack.ui.ThemeState
import com.wac.worktrack.ui.WorkTrackTheme
import kotlinx.coroutines.launch

// FragmentActivity (not ComponentActivity) because the biometric prompt needs it.
class MainActivity : FragmentActivity() {

    private var today by mutableLongStateOf(Day.today())
    private var resumeTick by mutableIntStateOf(0)
    private var locked by mutableStateOf(false)
    private var lockError by mutableStateOf<String?>(null)
    private var backgroundedAt = 0L
    private var prompting = false

    /** Screen requested by a notification tap; consumed by the nav host. */
    private var pendingRoute by mutableStateOf<String?>(null)

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeState.load(this)
        ModuleState.load(this)
        // A recreated activity (e.g. after a language change) keeps its unlocked state and doesn't
        // re-open the screen the launching notification asked for.
        locked = savedInstanceState?.getBoolean(STATE_LOCKED) ?: Prefs(this).appLock
        backgroundedAt = savedInstanceState?.getLong(STATE_BACKGROUNDED_AT) ?: 0L
        applyRecentsPrivacy()
        if (savedInstanceState == null) pendingRoute = intent.getStringExtra(EXTRA_ROUTE)
        maybeRequestNotificationPermission()
        setContent {
            WorkTrackTheme {
                if (locked) {
                    LockScreen()
                } else {
                    AppRoot(
                        today = today,
                        resumeTick = resumeTick,
                        pendingRoute = pendingRoute,
                        onRouteHandled = { pendingRoute = null },
                    )
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun LockScreen() {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Default.Lock, null, Modifier.padding(16.dp))
                Text("WorkTrack is locked", style = MaterialTheme.typography.titleLarge)
                lockError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(24.dp))
                Button(onClick = { unlock() }) { Text("Unlock") }
            }
        }
    }

    private fun unlock() {
        if (prompting) return
        // If the phone has no screen lock any more, there is nothing to check against.
        if (!AppLock.canLock(this)) {
            locked = false
            return
        }
        prompting = true
        AppLock.prompt(
            this,
            onUnlocked = { prompting = false; locked = false; lockError = null; backgroundedAt = 0 },
            onFailed = { prompting = false; lockError = it },
        )
    }

    /** Hides the app's content in the recent-apps screen while the lock is on. */
    fun applyRecentsPrivacy() {
        val lock = Prefs(this).appLock
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(!lock)
        } else if (lock) {
            // Older Android has no recents-only switch; FLAG_SECURE also blocks screenshots.
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_LOCKED, locked)
        outState.putLong(STATE_BACKGROUNDED_AT, backgroundedAt)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { pendingRoute = it }
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onResume() {
        super.onResume()
        if (Prefs(this).appLock && !locked && backgroundedAt > 0 &&
            SystemClock.elapsedRealtime() - backgroundedAt > AppLock.RELOCK_AFTER_MILLIS
        ) {
            locked = true
        }
        if (locked) unlock()
        today = Day.today()
        resumeTick++
        lifecycleScope.launch {
            DayPlanner.ensure(this@MainActivity)
            // Switching location off drops every geofence; put location reminders back.
            Geofences.sync(this@MainActivity)
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        private const val STATE_LOCKED = "locked"
        private const val STATE_BACKGROUNDED_AT = "backgroundedAt"
    }
}
