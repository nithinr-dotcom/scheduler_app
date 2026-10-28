package com.wac.worktrack.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wac.worktrack.calendar.CalendarItem
import com.wac.worktrack.calendar.Calendars
import com.wac.worktrack.cloud.Auth
import com.wac.worktrack.cloud.CloudBackup
import com.wac.worktrack.core.Module
import com.wac.worktrack.core.Prefs
import com.wac.worktrack.core.db
import com.wac.worktrack.ui.screens.AchievementsScreen
import com.wac.worktrack.ui.screens.BillsScreen
import com.wac.worktrack.ui.screens.CloseDayScreen
import com.wac.worktrack.ui.screens.DatesScreen
import com.wac.worktrack.ui.screens.MedicinesScreen
import com.wac.worktrack.ui.screens.SetupScreen
import com.wac.worktrack.ui.screens.DebtsScreen
import com.wac.worktrack.ui.screens.FinanceScreen
import com.wac.worktrack.ui.screens.FocusScreen
import com.wac.worktrack.ui.screens.InboxScreen
import com.wac.worktrack.ui.screens.JournalScreen
import com.wac.worktrack.ui.screens.LearningScreen
import com.wac.worktrack.ui.screens.NetWorthScreen
import com.wac.worktrack.ui.screens.PeriodReviewScreen
import com.wac.worktrack.ui.screens.GoalsScreen
import com.wac.worktrack.ui.screens.HabitsScreen
import com.wac.worktrack.ui.screens.MoreScreen
import com.wac.worktrack.ui.screens.PlanScreen
import com.wac.worktrack.ui.screens.RemindersScreen
import com.wac.worktrack.ui.screens.SettingsScreen
import com.wac.worktrack.ui.screens.StatsScreen
import com.wac.worktrack.ui.screens.TemplatesScreen
import com.wac.worktrack.ui.screens.TodayScreen
import com.wac.worktrack.ui.screens.WeeklyReviewScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Tab(val route: String, val label: String, val icon: ImageVector, val module: Module? = null)

private val tabs = listOf(
    Tab("today", "Today", Icons.Default.Today),
    Tab("plan", "Plan", Icons.Default.EditCalendar),
    Tab("finance", "Finance", Icons.Default.AccountBalanceWallet, Module.MONEY),
    Tab("stats", "Stats", Icons.Default.Insights),
    Tab("more", "More", Icons.AutoMirrored.Filled.List),
)

@Composable
fun AppRoot(today: Long, resumeTick: Int, pendingRoute: String?, onRouteHandled: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    // Signed in (when Firebase is set up), or the user chose to run offline.
    var gatePassed by remember {
        mutableStateOf(Auth.user(context) != null || prefs.offlineMode)
    }
    var setupDone by remember { mutableStateOf(prefs.setupDone) }
    CompositionLocalProvider(LocalToday provides today, LocalResumeTick provides resumeTick) {
        if (!gatePassed) {
            SignInScreen(onDone = { gatePassed = true })
        } else if (!setupDone) {
            // First run: pick which parts of the app to show.
            SetupScreen(firstRun = true, onDone = {
                prefs.setupDone = true
                setupDone = true
            })
        } else {
            MainScaffold(pendingRoute, onRouteHandled, onSignedOut = {
                prefs.offlineMode = false
                gatePassed = false
            })
        }
    }
}

@Composable
private fun MainScaffold(pendingRoute: String?, onRouteHandled: () -> Unit, onSignedOut: () -> Unit) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val pending by remember { context.db.finance().pendingCount() }.collectAsStateWithLifecycle(0)
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val shownTabs = tabs.filter { it.module == null || ModuleState.has(it.module) }
    val snackbar = remember { SnackbarHostState() }
    val undoScope = rememberCoroutineScope()
    val undo = remember { UndoHost(snackbar, undoScope) }

    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            nav.navigate(pendingRoute) { launchSingleTop = true }
            onRouteHandled()
        }
    }

    CompositionLocalProvider(LocalUndo provides undo) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (shownTabs.any { route?.startsWith(it.route) == true }) {
                    NavigationBar {
                        shownTabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route?.startsWith(tab.route) == true,
                                onClick = { nav.switchTab(tab.route) },
                                icon = {
                                    if (tab.route == "finance" && pending > 0) {
                                        BadgedBox(badge = { Badge { Text("$pending") } }) { Icon(tab.icon, null) }
                                    } else {
                                        Icon(tab.icon, null)
                                    }
                                },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            // Only the bottom inset is handled here; each screen's own top bar handles the status bar.
            val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
            NavHost(nav, startDestination = "today", modifier = Modifier.padding(bottom).consumeWindowInsets(bottom)) {
                composable("today") { TodayScreen(nav) }
                composable("plan") { PlanScreen(nav) }
                composable("finance") { FinanceScreen(nav, initialTab = 0) }
                composable("finance/pending") { FinanceScreen(nav, initialTab = 2) }
                composable("stats") { StatsScreen(nav) }
                composable("more") { MoreScreen(nav) }
                composable("templates") { TemplatesScreen(nav) }
                composable("reminders") { RemindersScreen(nav) }
                composable("habits") { HabitsScreen(nav) }
                composable("goals") { GoalsScreen(nav) }
                composable("review") { WeeklyReviewScreen(nav) }
                composable("settings") { SettingsScreen(nav, onSignedOut) }
                composable("review/month") { PeriodReviewScreen(nav, yearly = false) }
                composable("review/year") { PeriodReviewScreen(nav, yearly = true) }
                composable("focus") { FocusScreen(nav) }
                composable("journal") { JournalScreen(nav) }
                composable("inbox") { InboxScreen(nav) }
                composable("learning") { LearningScreen(nav) }
                composable("bills") { BillsScreen(nav) }
                composable("debts") { DebtsScreen(nav) }
                composable("networth") { NetWorthScreen(nav) }
                composable("achievements") { AchievementsScreen(nav) }
                composable("closeday") { CloseDayScreen(nav) }
                composable("medicines") { MedicinesScreen(nav) }
                composable("dates") { DatesScreen(nav) }
                composable("features") { SetupScreen(firstRun = false, onDone = { nav.popBackStack() }) }
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun SignInScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ready = remember { Auth.firebaseReady(context) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("WorkTrack", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your daily system — tasks, habits, goals and money in one place.",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        if (ready) {
            Button(
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        Auth.signIn(context as Activity)
                            .onSuccess {
                                // First sign-in on a phone: pull the cloud copy if one exists.
                                runCatching { if (context.db.tasks().activeTemplates().isEmpty()) CloudBackup.restore(context) }
                                onDone()
                            }
                            .onFailure { error = it.message ?: "Sign-in failed" }
                        busy = false
                    }
                },
            ) { Text(if (busy) "Signing in…" else "Sign in with Google") }
        } else {
            Text(
                "Google Sign-In and cloud backup turn on after you add app/google-services.json from " +
                    "Firebase and rebuild. Until then everything is saved on this phone only.",
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                Prefs(context).offlineMode = true
                onDone()
            }) { Text("Continue on this phone") }
        }
        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            if (ready) {
                TextButton(onClick = {
                    // Remembered, so the sign-in screen doesn't come back on every start.
                    Prefs(context).offlineMode = true
                    onDone()
                }) { Text("Continue without signing in") }
            }
        }
    }
}

/** Rest days in a range, as a set of epoch days. */
@Composable
fun rememberRestDays(from: Long, to: Long): Set<Long> {
    val context = LocalContext.current
    val days by remember(from, to) { context.db.care().restDays(from, to) }.collectAsStateWithLifecycle(emptyList())
    return remember(days) { days.map { it.date }.toSet() }
}

/** Read-only Google Calendar events + Google Tasks for a day range, refreshed on resume. */
@Composable
fun rememberGoogleItems(from: Long, to: Long = from): List<CalendarItem> {
    val context = LocalContext.current
    val tick = LocalResumeTick.current
    val items by produceState(emptyList<CalendarItem>(), from, to, tick) {
        value = withContext(Dispatchers.IO) { runCatching { Calendars.items(context, from, to) }.getOrDefault(emptyList()) }
    }
    return items
}
