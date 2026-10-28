@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wac.worktrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.HabitOps
import com.wac.worktrack.core.Habits
import com.wac.worktrack.core.Prefs
import com.wac.worktrack.core.Scores
import com.wac.worktrack.core.db
import com.wac.worktrack.data.Habit
import com.wac.worktrack.data.HealthSource
import com.wac.worktrack.ui.Good
import com.wac.worktrack.ui.Hint
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.Warn
import kotlinx.coroutines.launch

private val healthLabels = mapOf(
    HealthSource.STEPS to "Steps",
    HealthSource.SLEEP_MINUTES to "Sleep (min)",
    HealthSource.EXERCISE_MINUTES to "Exercise (min)",
)

private val healthDefaults = mapOf(
    HealthSource.STEPS to (8000 to "steps"),
    HealthSource.SLEEP_MINUTES to (420 to "min sleep"),
    HealthSource.EXERCISE_MINUTES to (30 to "min exercise"),
)

@Composable
fun HabitsScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalToday.current
    val db = context.db
    val habits by remember { db.habits().active() }.collectAsStateWithLifecycle(emptyList())
    val logs by remember(today) { db.habits().logs(today - 730, today) }.collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<Habit?>(null) }
    val rest = com.wac.worktrack.ui.rememberRestDays(today - 3650, today)
    var counting by remember { mutableStateOf<Pair<Habit, Long>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Habits") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Habit(title = "", createdDate = today) }) { Icon(Icons.Default.Add, "Add habit") }
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            if (habits.isEmpty()) {
                item {
                    Hint(
                        "Track yes/no habits (\"No sugar\") or counts (\"8 glasses of water\", \"20 pages\"). " +
                            "Link one to Health Connect and steps or sleep tick it for you.",
                    )
                }
            }
            items(habits, key = { it.id }) { h ->
                val done = Habits.doneDays(h, logs)
                val streak = Habits.streak(h, done, today, rest)
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { editing = h }) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(h.title, style = MaterialTheme.typography.titleMedium)
                                val sub = listOfNotNull(
                                    if (h.target > 1) "Goal ${h.target} ${h.unit}".trim() else null,
                                    h.autoSource?.let { "from Health Connect" },
                                ).joinToString(" · ")
                                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                "🔥 ${streak.current}" + if (streak.freezes > 0) "  ❄️ ${streak.freezes}" else "",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        val last30 = done.count { it > today - 30 }
                        Text(
                            "$last30 of last 30 days · best ${streak.best}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (Habits.atRisk(h, done, today, rest)) {
                            Text("⚠ Missed yesterday — don't miss twice.", color = Warn, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            for (d in today - 6..today) {
                                val on = d in done
                                val count = Habits.countOn(h, logs, d)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        Day.date(d).dayOfWeek.name.take(1), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Box(
                                        Modifier.padding(top = 4.dp).size(34.dp)
                                            .background(if (on) Good else if (count > 0) Good.copy(alpha = 0.3f) else Color.Transparent, CircleShape)
                                            .border(1.dp, if (on) Good else MaterialTheme.colorScheme.outline, CircleShape)
                                            .clickable {
                                                if (h.target > 1) counting = h to d
                                                else scope.launch { HabitOps.tap(context, h, d) }
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            if (h.target > 1 && count > 0) compactCount(count) else "${Day.date(d).dayOfMonth}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Hint("❄️ Every ${Scores.DAYS_PER_FREEZE} days in a row earns a streak freeze (max ${Scores.MAX_FREEZES}). A missed day uses one instead of breaking your streak.")
                Spacer(Modifier.height(88.dp))
            }
        }
    }

    editing?.let { h ->
        HabitDialog(
            initial = h,
            healthOn = Prefs(context).healthConnect,
            onDismiss = { editing = null },
            onSave = { scope.launch { db.habits().upsert(it) }; editing = null },
            // Archive keeps the history; it just stops showing up.
            onArchive = if (h.id == 0L) null else ({ scope.launch { db.habits().upsert(h.copy(archived = true)) }; editing = null }),
        )
    }
    counting?.let { (h, d) ->
        var value by remember(h, d) { mutableIntStateOf(Habits.countOn(h, logs, d)) }
        AlertDialog(
            onDismissRequest = { counting = null },
            title = { Text("${h.title} · ${Day.label(d, today)}") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { value = (value - 1).coerceAtLeast(0) }) { Text("−", style = MaterialTheme.typography.headlineMedium) }
                    Text("$value / ${h.target} ${h.unit}".trim(), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { value++ }) { Text("+", style = MaterialTheme.typography.headlineMedium) }
                }
            },
            confirmButton = {
                TextButton(onClick = { scope.launch { HabitOps.setCount(context, h, d, value) }; counting = null }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { counting = null }) { Text("Cancel") } },
        )
    }
}

private fun compactCount(n: Int) = if (n >= 1000) "${n / 1000}k" else "$n"

@Composable
private fun HabitDialog(initial: Habit, healthOn: Boolean, onDismiss: () -> Unit, onSave: (Habit) -> Unit, onArchive: (() -> Unit)?) {
    var title by remember { mutableStateOf(initial.title) }
    var counted by remember { mutableStateOf(initial.target > 1) }
    var target by remember { mutableStateOf(if (initial.target > 1) initial.target.toString() else "") }
    var unit by remember { mutableStateOf(initial.unit) }
    var source by remember { mutableStateOf(initial.autoSource) }
    val targetValue = if (counted) target.toIntOrNull()?.takeIf { it > 1 } else 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New habit" else "Edit habit") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it }, label = { Text("Habit") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(Modifier.padding(top = 8.dp)) {
                    FilterChip(!counted, { counted = false; source = null }, { Text("Yes / no") }, Modifier.padding(end = 6.dp))
                    FilterChip(counted, { counted = true }, { Text("Count") })
                }
                if (counted) {
                    OutlinedTextField(
                        target, { target = it.filter(Char::isDigit).take(6) }, label = { Text("Daily goal (e.g. 8)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(unit, { unit = it }, label = { Text("Unit (glasses, pages…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Text("Tick automatically from Health Connect", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                FlowRow {
                    FilterChip(source == null, { source = null }, { Text("Off") }, Modifier.padding(end = 4.dp))
                    healthLabels.forEach { (s, label) ->
                        FilterChip(source == s, {
                            source = s
                            counted = true
                            val (t, u) = healthDefaults.getValue(s)
                            target = t.toString()
                            unit = u
                        }, { Text(label) }, Modifier.padding(end = 4.dp))
                    }
                }
                if (source != null && !healthOn) {
                    Text(
                        "Turn on Health Connect in Settings for this to work.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && targetValue != null, onClick = {
                onSave(initial.copy(title = title.trim(), target = targetValue!!, unit = if (counted) unit.trim() else "", autoSource = source))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onArchive != null) TextButton(onClick = onArchive) { Text("Archive") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
