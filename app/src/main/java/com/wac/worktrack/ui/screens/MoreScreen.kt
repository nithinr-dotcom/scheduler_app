@file:OptIn(ExperimentalMaterial3Api::class)

package com.wac.worktrack.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import com.wac.worktrack.core.Module
import com.wac.worktrack.ui.ModuleState
import com.wac.worktrack.ui.SectionTitle

private data class Entry(val route: String, val title: String, val subtitle: String, val icon: ImageVector, val module: Module? = null)

private val groups = listOf(
    "Plan & do" to listOf(
        Entry("closeday", "Close the day", "Wrap up today and pick tomorrow's top 3", Icons.Default.Bedtime),
        Entry("templates", "Repeating tasks", "Tasks that appear on their days automatically", Icons.Default.Repeat),
        Entry("reminders", "Reminders", "Time and location reminders + Google Calendar (read-only)", Icons.Default.Alarm),
        Entry("focus", "Focus timer", "Pomodoro sessions; time is logged against tasks", Icons.Default.Timer, Module.FOCUS),
        Entry("inbox", "Someday list", "Ideas that don't have a date yet", Icons.Default.Inbox),
    ),
    "Grow" to listOf(
        Entry("habits", "Habits", "Yes/no and count habits, streaks and freezes", Icons.Default.Spa, Module.HABITS),
        Entry("goals", "Goals", "Long-term targets and savings goals", Icons.Default.Flag, Module.GOALS),
        Entry("learning", "Learning", "Books and courses you're working through", Icons.Default.School, Module.LEARNING),
        Entry("journal", "Journal & mood", "A few lines and a mood each day", Icons.AutoMirrored.Filled.MenuBook, Module.JOURNAL),
        Entry("achievements", "Achievements", "Life score and badges", Icons.Default.EmojiEvents),
    ),
    "Care" to listOf(
        Entry("medicines", "Medicines", "Dose reminders, taken or not, refill alerts", Icons.Default.Medication, Module.MEDICINE),
        Entry("dates", "Important dates", "Birthdays and anniversaries with a heads-up", Icons.Default.Cake, Module.DATES),
    ),
    "Money" to listOf(
        Entry("bills", "Bills & subscriptions", "Due dates, reminders and \"mark paid\"", Icons.Default.Receipt, Module.MONEY),
        Entry("debts", "Lent & borrowed", "Who owes whom", Icons.Default.Handshake, Module.MONEY),
        Entry("networth", "Net worth", "What you own minus what you owe", Icons.Default.AccountBalance, Module.MONEY),
    ),
    "Review" to listOf(
        Entry("review", "Weekly review", "How the week went and what to improve", Icons.Default.AutoGraph),
        Entry("review/month", "Monthly review", "The month in numbers, plus your focus for next month", Icons.Default.CalendarMonth),
        Entry("review/year", "Yearly review", "Your year at a glance", Icons.Default.CalendarMonth),
    ),
    "App" to listOf(
        Entry("features", "Features", "Choose which parts of the app you use", Icons.Default.Settings),
        Entry("settings", "Settings", "Backup, appearance, app lock, notifications, permissions", Icons.Default.Settings),
    ),
)

@Composable
fun MoreScreen(nav: NavController) {
    Scaffold(topBar = { TopAppBar(title = { Text("More") }) }) { padding ->
        LazyColumn(contentPadding = padding) {
            groups.forEach { (title, all) ->
                val entries = all.filter { it.module == null || ModuleState.has(it.module) }
                if (entries.isEmpty()) return@forEach
                item(key = title) { SectionTitle(title) }
                items(entries, key = { it.route }) { e ->
                    ListItem(
                        headlineContent = { Text(e.title) },
                        supportingContent = { Text(e.subtitle) },
                        leadingContent = { Icon(e.icon, null) },
                        modifier = Modifier.clickable { nav.navigate(e.route) },
                    )
                }
            }
        }
    }
}
