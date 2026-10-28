@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wac.worktrack.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.wac.worktrack.WorkTrackApp
import com.wac.worktrack.core.Categories
import com.wac.worktrack.core.DayPlanner
import com.wac.worktrack.core.HabitOps
import com.wac.worktrack.core.Habits
import com.wac.worktrack.core.ImportantDates
import com.wac.worktrack.core.Module
import com.wac.worktrack.core.Routines
import com.wac.worktrack.core.db
import com.wac.worktrack.data.DailyTask
import com.wac.worktrack.data.JournalEntry
import com.wac.worktrack.data.RestDay
import com.wac.worktrack.data.TaskStatus
import com.wac.worktrack.ui.BackTopBar
import com.wac.worktrack.ui.Hint
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.ModuleState
import com.wac.worktrack.ui.ScoreRing
import com.wac.worktrack.ui.SectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The evening routine on one screen: start with a win, close open tasks, tick what's left,
 * write a line, and pick tomorrow's Top 3.
 */
@Composable
fun CloseDayScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalToday.current
    val tomorrow = today + 1
    val db = context.db

    val tasks by remember(today) { db.tasks().tasksFor(today) }.collectAsStateWithLifecycle(emptyList())
    val tomorrowTasks by remember(tomorrow) { db.tasks().tasksFor(tomorrow) }.collectAsStateWithLifecycle(emptyList())
    val agg by remember(today) { db.tasks().aggs(today, today) }.collectAsStateWithLifecycle(emptyList())
    val habits by remember { db.habits().active() }.collectAsStateWithLifecycle(emptyList())
    val habitLogs by remember(today) { db.habits().logs(today, today) }.collectAsStateWithLifecycle(emptyList())
    val meds by remember { db.care().activeMedicines() }.collectAsStateWithLifecycle(emptyList())
    val medLogs by remember(today) { db.care().medLogs(today, today) }.collectAsStateWithLifecycle(emptyList())
    val journal by remember(today) { db.life().journal(today) }.collectAsStateWithLifecycle(null)
    val restToday by remember(today) { db.care().restDay(today) }.collectAsStateWithLifecycle(null)
    val restTomorrow by remember(tomorrow) { db.care().restDay(tomorrow) }.collectAsStateWithLifecycle(null)
    val dates by remember { db.care().dates() }.collectAsStateWithLifecycle(emptyList())
    val win by produceState<String?>(null, tasks, habitLogs) { value = withContext(Dispatchers.IO) { Routines.win(context, today) } }

    var note by rememberSaveable(today) { mutableStateOf("") }
    var noteLoaded by rememberSaveable(today) { mutableStateOf(false) }
    LaunchedEffect(journal) {
        if (!noteLoaded && journal != null) {
            note = journal!!.text
            noteLoaded = true
        }
    }
    var newTask by rememberSaveable { mutableStateOf("") }

    // Make sure tomorrow's repeating tasks exist so they can be starred now.
    LaunchedEffect(tomorrow) { DayPlanner.generate(context, tomorrow) }

    // App scope: the note is also saved when leaving with the system back gesture.
    val appScope = (context.applicationContext as WorkTrackApp).scope

    fun saveNote() = appScope.launch {
        val entry = journal ?: JournalEntry(today)
        if (entry.text != note.trim()) db.life().upsertJournal(entry.copy(text = note.trim()))
    }
    DisposableEffect(Unit) { onDispose { if (noteLoaded || note.isNotBlank()) saveNote() } }

    fun star(t: DailyTask) = scope.launch {
        if (!DayPlanner.toggleTop3(context, t)) Toast.makeText(context, "Tomorrow already has 3 priorities", Toast.LENGTH_SHORT).show()
    }

    Scaffold(topBar = { BackTopBar("Close the day", { saveNote(); nav.popBackStack() }) }) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        ScoreRing(if (restToday != null) null else agg.firstOrNull()?.score, size = 72.dp, stroke = 8.dp)
                        Spacer(Modifier.padding(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                win?.let { "🏆 $it" } ?: if (restToday != null) "🌿 Rest day" else "Every day you show up counts.",
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium,
                            )
                            if (restToday != null) {
                                Text("Not scored, and your streaks are safe.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            val open = tasks.filter { it.status == TaskStatus.PENDING }
            item { SectionTitle(if (open.isEmpty()) "All tasks closed ✅" else "Still open (${open.size})") }
            items(open, key = { "o" + it.id }) { t ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text((if (t.isTop3) "★ " else "") + t.title, style = MaterialTheme.typography.bodyLarge)
                    Row {
                        TextButton(onClick = { scope.launch { DayPlanner.setStatus(context, t, TaskStatus.DONE) } }) { Text("Done") }
                        TextButton(onClick = { scope.launch { DayPlanner.moveTo(context, t, tomorrow) } }) { Text("Tomorrow") }
                        TextButton(onClick = { scope.launch { DayPlanner.setStatus(context, t, TaskStatus.SKIPPED) } }) { Text("Skip") }
                    }
                }
            }

            if (ModuleState.has(Module.HABITS)) {
                val left = habits.filter { today !in Habits.doneDays(it, habitLogs) }
                if (left.isNotEmpty()) {
                    item {
                        SectionTitle("Habits left today")
                        FlowRow(Modifier.padding(horizontal = 16.dp)) {
                            left.forEach { h ->
                                FilterChip(
                                    selected = false,
                                    onClick = { scope.launch { HabitOps.tap(context, h, today) } },
                                    label = { Text(Habits.label(h, Habits.countOn(h, habitLogs, today))) },
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            }
                        }
                    }
                }
            }

            if (ModuleState.has(Module.MEDICINE) && meds.isNotEmpty()) {
                item {
                    SectionTitle("Medicines today")
                    Column(Modifier.padding(horizontal = 16.dp)) { meds.forEach { DoseChips(it, medLogs, today, showName = true) } }
                }
            }

            if (ModuleState.has(Module.JOURNAL)) {
                item {
                    SectionTitle("How was today?")
                    MoodPicker(journal?.mood) { mood ->
                        scope.launch {
                            val entry = journal ?: JournalEntry(today)
                            db.life().upsertJournal(entry.copy(mood = if (entry.mood == mood) null else mood, text = note.trim()))
                        }
                    }
                    OutlinedTextField(
                        note, { note = it }, minLines = 2,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("One line: what went well, what you learned, what you're grateful for") },
                    )
                }
            }

            item {
                SectionTitle("Tomorrow's top 3")
                Hint("Tap ☆ to pick up to three. Deciding tonight makes the morning easier.")
            }
            items(tomorrowTasks, key = { "t" + it.id }) { t -> TaskRow(t, null, { star(t) }, { star(t) }) }
            item {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        newTask, { newTask = it }, singleLine = true, modifier = Modifier.weight(1f),
                        placeholder = { Text("Add a task for tomorrow") },
                    )
                    TextButton(enabled = newTask.isNotBlank(), onClick = {
                        val title = newTask.trim()
                        newTask = ""
                        scope.launch {
                            val top = db.tasks().top3Count(tomorrow) < 3
                            DayPlanner.saveTask(context, DailyTask(date = tomorrow, title = title, category = Categories.tasks.first(), isTop3 = top))
                        }
                    }) { Text("Add") }
                }
                val soon = ImportantDates.upcoming(dates, tomorrow).filter { it.second == tomorrow }
                soon.forEach { (d, _) -> Hint("Tomorrow: ${ImportantDates.label(d, tomorrow)}") }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Make tomorrow a rest day", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Not scored; streaks pause instead of breaking.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(restTomorrow != null, { on ->
                        scope.launch { if (on) db.care().upsertRestDay(RestDay(tomorrow)) else db.care().deleteRestDay(tomorrow) }
                    })
                }
                Button(
                    onClick = {
                        saveNote()
                        Toast.makeText(context, "Day closed. Sleep well 🌙", Toast.LENGTH_SHORT).show()
                        nav.popBackStack()
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text("Done — good night") }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
