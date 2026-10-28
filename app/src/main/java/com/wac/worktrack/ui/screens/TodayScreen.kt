@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wac.worktrack.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.wac.worktrack.core.Categories
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.DayPlanner
import com.wac.worktrack.core.Focus
import com.wac.worktrack.core.HabitOps
import com.wac.worktrack.core.Habits
import com.wac.worktrack.core.ImportantDates
import com.wac.worktrack.core.Module
import com.wac.worktrack.core.Scores
import com.wac.worktrack.core.db
import com.wac.worktrack.core.millisTimeLabel
import com.wac.worktrack.data.DailyTask
import com.wac.worktrack.data.GoalType
import com.wac.worktrack.data.JournalEntry
import com.wac.worktrack.data.RestDay
import com.wac.worktrack.data.TaskStatus
import com.wac.worktrack.calendar.CalendarItem
import com.wac.worktrack.ui.Good
import com.wac.worktrack.ui.Hint
import com.wac.worktrack.ui.LocalResumeTick
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.ModuleState
import com.wac.worktrack.ui.QuickAddDialog
import com.wac.worktrack.ui.Warn
import com.wac.worktrack.ui.ScoreRing
import com.wac.worktrack.ui.SectionTitle
import com.wac.worktrack.ui.StatBox
import com.wac.worktrack.ui.rememberGoogleItems
import com.wac.worktrack.ui.rememberRestDays
import com.wac.worktrack.ui.rememberUndo
import kotlinx.coroutines.launch

@Composable
fun TodayScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalToday.current
    val db = context.db

    val tasks by remember(today) { db.tasks().tasksFor(today) }.collectAsStateWithLifecycle(emptyList())
    val aggs by remember(today) { db.tasks().aggs(today - 400, today) }.collectAsStateWithLifecycle(emptyList())
    val habits by remember { db.habits().active() }.collectAsStateWithLifecycle(emptyList())
    // Yesterday too, for "never miss twice".
    val habitLogs by remember(today) { db.habits().logs(today - 1, today) }.collectAsStateWithLifecycle(emptyList())
    val journal by remember(today) { db.life().journal(today) }.collectAsStateWithLifecycle(null)
    val tick = LocalResumeTick.current
    var focus by remember { mutableStateOf(Focus.state(context)) }
    androidx.compose.runtime.LaunchedEffect(tick) { focus = Focus.state(context) }
    var quickAdd by remember { mutableStateOf(false) }
    val goals by remember { db.goals().active() }.collectAsStateWithLifecycle(emptyList())
    val google = rememberGoogleItems(today)
    val meds by remember { db.care().activeMedicines() }.collectAsStateWithLifecycle(emptyList())
    val medLogs by remember(today) { db.care().medLogs(today, today) }.collectAsStateWithLifecycle(emptyList())
    val dates by remember { db.care().dates() }.collectAsStateWithLifecycle(emptyList())
    val restDays = rememberRestDays(today - 400, today)
    val isRest = today in restDays
    val undo = rememberUndo()

    var editing by remember { mutableStateOf<DailyTask?>(null) }
    val todayAgg = aggs.lastOrNull { it.date == today }
    val streaks = remember(aggs, today, restDays) { Scores.streaks(aggs, today, restDays) }

    fun toggleDone(t: DailyTask) = scope.launch {
        DayPlanner.setStatus(context, t, if (t.status == TaskStatus.DONE) TaskStatus.PENDING else TaskStatus.DONE)
    }

    fun star(t: DailyTask) = scope.launch {
        if (!DayPlanner.toggleTop3(context, t)) Toast.makeText(context, "You already have 3 priorities", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Today")
                        Text(Day.long(today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { quickAdd = true }) { Icon(Icons.Default.Bolt, "Quick add") }
                    IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = DailyTask(date = today, title = "", category = Categories.tasks.first())
            }) { Icon(Icons.Default.Add, "Add task") }
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            if (ModuleState.has(Module.DATES)) {
                val soon = ImportantDates.upcoming(dates, today)
                if (soon.isNotEmpty()) {
                    item {
                        Card(
                            onClick = { nav.navigate("dates") },
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                soon.forEach { (d, _) -> Text(ImportantDates.label(d, today), style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                }
            }
            if (focus.running && focus.endAt > System.currentTimeMillis()) {
                item {
                    Card(
                        onClick = { nav.navigate("focus") },
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Text(
                            (if (focus.isBreak) "☕ Break" else "⏱ Focusing" + if (focus.title.isNotBlank()) ": ${focus.title}" else "") +
                                " · until ${millisTimeLabel(focus.endAt)}",
                            Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        ScoreRing(if (isRest) null else todayAgg?.score)
                        Spacer(Modifier.padding(8.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                StatBox("Done", "${todayAgg?.done ?: 0}", Modifier.weight(1f), Good)
                                StatBox("Open", "${tasks.count { it.status == TaskStatus.PENDING }}", Modifier.weight(1f))
                                StatBox("Skipped", "${todayAgg?.skipped ?: 0}", Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                (if (isRest) "🌿 Rest day · " else "") +
                                    "🔥 ${streaks.current} day streak · best ${streaks.best}" +
                                    if (streaks.freezes > 0) " · ❄️ ${streaks.freezes}" else "",
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                            )
                            // Sick or need a break: the day isn't scored and streaks pause instead of breaking.
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        if (isRest) db.care().deleteRestDay(today) else db.care().upsertRestDay(RestDay(today))
                                    }
                                },
                                contentPadding = PaddingValues(0.dp),
                            ) { Text(if (isRest) "Back to a normal day" else "Take a rest / sick day") }
                        }
                    }
                }
            }

            if (tasks.isEmpty()) {
                item {
                    Hint("Nothing planned for today yet.")
                    Row(Modifier.padding(horizontal = 16.dp)) {
                        OutlinedButton(onClick = { nav.navigate("plan") }) { Text("Plan my day") }
                        Spacer(Modifier.padding(4.dp))
                        TextButton(onClick = { nav.navigate("templates") }) { Text("Set up repeating tasks") }
                    }
                }
            }

            val top3 = tasks.filter { it.isTop3 }
            if (top3.isNotEmpty()) {
                item { SectionTitle("Top 3 priorities") }
                items(top3, key = { "top" + it.id }) { t -> TaskRow(t, { toggleDone(t) }, { star(t) }, { editing = t }) }
            }

            val allDayGoogle = google.filter { it.minutes == null }
            if (allDayGoogle.isNotEmpty()) {
                item { SectionTitle("From Google (all day)") }
                items(allDayGoogle, key = { it.key }) { GoogleItemRow(it) }
            }

            // Timed tasks and timed calendar events merged into one timeline.
            val rest = tasks.filter { !it.isTop3 }
            val timed = rest.filter { it.timeMinutes != null }
            val timedGoogle = google.filter { it.minutes != null }
            if (timed.isNotEmpty() || timedGoogle.isNotEmpty()) {
                item { SectionTitle("Schedule") }
                val merged: List<Pair<Int, Any>> =
                    (timed.map { it.timeMinutes!! to it } + timedGoogle.map { it.minutes!! to it }).sortedBy { it.first }
                items(merged, key = { (_, v) -> if (v is DailyTask) "t" + v.id else (v as CalendarItem).key }) { (_, v) ->
                    if (v is DailyTask) TaskRow(v, { toggleDone(v) }, { star(v) }, { editing = v })
                    else GoogleItemRow(v as CalendarItem)
                }
            }

            val anyTime = rest.filter { it.timeMinutes == null }
            if (anyTime.isNotEmpty()) {
                item { SectionTitle("Any time") }
                items(anyTime, key = { "a" + it.id }) { t -> TaskRow(t, { toggleDone(t) }, { star(t) }, { editing = t }) }
            }

            if (ModuleState.has(Module.MEDICINE) && meds.isNotEmpty()) {
                item {
                    SectionTitle("Medicines") { TextButton(onClick = { nav.navigate("medicines") }) { Text("All") } }
                    Column(Modifier.padding(horizontal = 16.dp)) { meds.forEach { DoseChips(it, medLogs, today, showName = true) } }
                }
            }

            if (ModuleState.has(Module.HABITS) && habits.isNotEmpty()) {
                item {
                    SectionTitle("Habits") { TextButton(onClick = { nav.navigate("habits") }) { Text("All") } }
                    FlowRow(Modifier.padding(horizontal = 16.dp)) {
                        habits.forEach { h ->
                            val done = Habits.doneDays(h, habitLogs)
                            val count = Habits.countOn(h, habitLogs, today)
                            val risk = Habits.atRisk(h, done, today, restDays)
                            FilterChip(
                                selected = today in done,
                                onClick = { scope.launch { HabitOps.tap(context, h, today) } },
                                label = { Text((if (risk) "⚠ " else "") + Habits.label(h, count)) },
                                modifier = Modifier.padding(end = 6.dp),
                            )
                        }
                    }
                    if (habits.any { Habits.atRisk(it, Habits.doneDays(it, habitLogs), today, restDays) }) {
                        Text(
                            "⚠ Missed yesterday — don't miss twice.", color = Warn, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }

            if (ModuleState.has(Module.JOURNAL)) item {
                SectionTitle("How was today?") { TextButton(onClick = { nav.navigate("journal") }) { Text("Journal") } }
                MoodPicker(journal?.mood) { mood ->
                    scope.launch {
                        val entry = journal ?: JournalEntry(today)
                        db.life().upsertJournal(entry.copy(mood = if (entry.mood == mood) null else mood))
                    }
                }
                journal?.text?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it, maxLines = 2, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            item {
                OutlinedButton(onClick = { nav.navigate("closeday") }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("🌙 Close the day")
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
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
            extraActions = if (t.id == 0L) null else ({
                FlowRow {
                    if (t.status != TaskStatus.SKIPPED) {
                        OutlinedButton(
                            onClick = { scope.launch { DayPlanner.setStatus(context, t, TaskStatus.SKIPPED) }; editing = null },
                            modifier = Modifier.padding(end = 6.dp),
                        ) { Text("Skip today") }
                    }
                    if (t.status != TaskStatus.PENDING) {
                        OutlinedButton(
                            onClick = { scope.launch { DayPlanner.setStatus(context, t, TaskStatus.PENDING) }; editing = null },
                            modifier = Modifier.padding(end = 6.dp),
                        ) { Text("Reopen") }
                    }
                    if (t.status == TaskStatus.PENDING) {
                        OutlinedButton(
                            onClick = { scope.launch { DayPlanner.moveTo(context, t, today + 1) }; editing = null },
                        ) { Text("Move to tomorrow") }
                    }
                }
            }),
            onFocus = { updated ->
                scope.launch {
                    DayPlanner.saveTask(context, updated)
                    Focus.start(context, updated.id, updated.title)
                    nav.navigate("focus")
                }
                editing = null
            },
        )
    }
    if (quickAdd) QuickAddDialog(onDismiss = { quickAdd = false })
}

val moods = listOf("😞", "😕", "😐", "🙂", "😄")

/** 1–5 mood; tapping the chosen one clears it. */
@Composable
fun MoodPicker(selected: Int?, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
        moods.forEachIndexed { i, emoji ->
            val mood = i + 1
            FilterChip(
                selected = selected == mood,
                onClick = { onPick(mood) },
                label = { Text(emoji, style = MaterialTheme.typography.titleLarge) },
            )
        }
    }
}
