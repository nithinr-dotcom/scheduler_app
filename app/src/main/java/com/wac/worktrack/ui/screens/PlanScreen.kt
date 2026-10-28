@file:OptIn(ExperimentalMaterial3Api::class)

package com.wac.worktrack.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.wac.worktrack.core.AutoSchedule
import com.wac.worktrack.core.Categories
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.DayPlanner
import com.wac.worktrack.core.Prefs
import com.wac.worktrack.core.db
import com.wac.worktrack.data.DailyTask
import com.wac.worktrack.data.GoalType
import com.wac.worktrack.data.TaskStatus
import com.wac.worktrack.ui.Hint
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.SectionTitle
import com.wac.worktrack.ui.Stepper
import com.wac.worktrack.ui.rememberGoogleItems
import com.wac.worktrack.ui.scoreColor
import com.wac.worktrack.ui.rememberUndo
import kotlinx.coroutines.launch

/**
 * "Plan my day": carry over what was missed, pick the top 3, give tasks times, and see the
 * day's Google Calendar alongside. Works for today or any future day.
 */
@Composable
fun PlanScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val undo = rememberUndo()
    val today = LocalToday.current
    val db = context.db

    var day by remember { mutableLongStateOf(today) }
    // Past midnight while open: never plan a day that's already gone.
    LaunchedEffect(today) { if (day < today) day = today }

    // Make sure a future day's repeating tasks exist so they can be planned ahead.
    LaunchedEffect(day) { DayPlanner.generate(context, day) }

    val tasks by remember(day) { db.tasks().tasksFor(day) }.collectAsStateWithLifecycle(emptyList())
    val previous by remember(day) { db.tasks().tasksFor(day - 1) }.collectAsStateWithLifecycle(emptyList())
    val prevAgg by remember(day) { db.tasks().aggs(day - 1, day - 1) }.collectAsStateWithLifecycle(emptyList())
    val goals by remember { db.goals().active() }.collectAsStateWithLifecycle(emptyList())
    val google = rememberGoogleItems(day)
    val inboxCount by remember { db.life().inboxCount() }.collectAsStateWithLifecycle(0)

    fun autoSchedule() = scope.launch {
        val prefs = Prefs(context)
        val now = Day.minutesOfMillis(System.currentTimeMillis())
        val start = if (day == today) maxOf(prefs.dayStartMinutes, now) else prefs.dayStartMinutes
        val busy = google.filter { it.minutes != null }
            .map { AutoSchedule.Busy(it.minutes!!, it.endMinutes ?: (it.minutes + AutoSchedule.DEFAULT_MINUTES)) }
        val candidates = tasks.filter { it.timeMinutes != null || it.status == TaskStatus.PENDING }
        val times = AutoSchedule.plan(candidates, busy, prefs.peakStartMinutes, prefs.peakEndMinutes, start)
        DayPlanner.applyTimes(context, tasks, times)
        val left = candidates.count { it.timeMinutes == null } - times.size
        Toast.makeText(
            context,
            when {
                times.isEmpty() && left == 0 -> "Every task already has a time."
                left > 0 -> "Scheduled ${times.size}; $left didn't fit before 10 PM."
                else -> "Scheduled ${times.size} tasks — focus work in your peak hours."
            },
            Toast.LENGTH_LONG,
        ).show()
    }

    var editing by remember { mutableStateOf<DailyTask?>(null) }

    // Leftovers from the day before that can be carried into this day.
    val carry = previous.filter {
        if (day == today) it.status == TaskStatus.MISSED || it.status == TaskStatus.PENDING
        else it.status == TaskStatus.PENDING
    }.filter { leftover -> tasks.none { it.title == leftover.title } }

    fun star(t: DailyTask) = scope.launch {
        if (!DayPlanner.toggleTop3(context, t)) Toast.makeText(context, "Only 3 priorities per day", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Plan my day") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = DailyTask(date = day, title = "", category = Categories.tasks.first())
            }) { Icon(Icons.Default.Add, "Add task") }
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Stepper(
                    Day.label(day, today) + " · " + Day.short(day),
                    onPrev = { if (day > today) day-- },
                    onNext = { day++ },
                )
            }

            item {
                val score = prevAgg.firstOrNull()?.score
                val prevLabel = Day.label(day - 1, today)
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$prevLabel:", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.padding(4.dp))
                            Text(
                                score?.let { "$it%" } ?: "no tasks",
                                style = MaterialTheme.typography.titleSmall, color = scoreColor(score),
                            )
                            Spacer(Modifier.weight(1f))
                            if (carry.isNotEmpty()) {
                                TextButton(onClick = {
                                    scope.launch { carry.forEach { DayPlanner.moveTo(context, it, day) } }
                                }) { Text("Move all ${carry.size}") }
                            }
                        }
                        if (carry.isEmpty()) {
                            Text("Nothing left over. 👍", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            carry.forEach { t ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "• ${t.title}" + if (t.status == TaskStatus.MISSED) " (missed)" else "",
                                        Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                                    )
                                    TextButton(onClick = { scope.launch { DayPlanner.moveTo(context, t, day) } }) { Text("Move") }
                                }
                            }
                        }
                    }
                }
            }

            val top = tasks.filter { it.isTop3 }
            item {
                SectionTitle("Top 3 priorities (${top.size}/3)")
                if (top.isEmpty()) Hint("Tap ☆ on the tasks that matter most. They count double in your score.")
            }
            items(top, key = { "top" + it.id }) { t -> TaskRow(t, null, { star(t) }, { editing = t }) }

            item {
                SectionTitle("Tasks for ${Day.label(day, today).lowercase()}") {
                    TextButton(onClick = { nav.navigate("templates") }) { Text("Repeating…") }
                }
                if (tasks.isEmpty()) {
                    Hint("No tasks yet. Add one with +, or set up repeating tasks.")
                } else {
                    val open = tasks.filter { it.status == TaskStatus.PENDING }
                    val estimated = open.sumOf { it.estimateMinutes ?: 0 }
                    val unestimated = open.count { it.estimateMinutes == null }
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (estimated > 0) "~${minutesLabel(estimated)} of work" else "No estimates yet") +
                                if (unestimated > 0) " · $unestimated without estimate" else "",
                            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (open.any { it.timeMinutes == null }) TextButton(onClick = { autoSchedule() }) { Text("Auto-schedule") }
                    }
                }
            }
            items(tasks.filter { !it.isTop3 }, key = { "t" + it.id }) { t -> TaskRow(t, null, { star(t) }, { editing = t }) }

            if (google.isNotEmpty()) {
                item { SectionTitle("Google Calendar (read-only)") }
                items(google, key = { it.key }) { GoogleItemRow(it) }
            }

            if (inboxCount > 0) {
                item {
                    SectionTitle("Someday list") { TextButton(onClick = { nav.navigate("inbox") }) { Text("Open") } }
                    Hint("$inboxCount ideas waiting — schedule one for ${Day.label(day, today).lowercase()}?")
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedButton(onClick = { nav.navigate("today") }) { Text("Done planning") }
                }
                Spacer(Modifier.height(88.dp))
            }
        }
    }

    editing?.let { t ->
        TaskDialog(
            initial = t,
            goals = goals.filter { it.type == GoalType.COUNT },
            onDismiss = { editing = null },
            onSave = { scope.launch { DayPlanner.saveTask(context, it) }; editing = null },
            onDelete = if (t.id == 0L) null else ({
                scope.launch {
                    DayPlanner.deleteTask(context, t)
                    undo.show("Deleted “${t.title}”") { DayPlanner.restoreTask(context, t) }
                }
                editing = null
            }),
        )
    }
}
