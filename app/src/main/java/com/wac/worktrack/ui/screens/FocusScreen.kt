@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wac.worktrack.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.Focus
import com.wac.worktrack.core.Prefs
import com.wac.worktrack.core.db
import com.wac.worktrack.core.millisTimeLabel
import com.wac.worktrack.data.DailyTask
import com.wac.worktrack.data.TaskStatus
import com.wac.worktrack.ui.BackTopBar
import com.wac.worktrack.ui.Choice
import com.wac.worktrack.ui.Hint
import com.wac.worktrack.ui.LocalResumeTick
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.SectionTitle
import com.wac.worktrack.ui.StatBox
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val focusLengths = listOf(15, 25, 45, 60, 90)

/** Pomodoro timer. Keeps running (with a countdown notification) when the app is closed. */
@Composable
fun FocusScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalToday.current
    val tick = LocalResumeTick.current
    val prefs = remember { Prefs(context) }
    val db = context.db

    val tasks by remember(today) { db.tasks().tasksFor(today) }.collectAsStateWithLifecycle(emptyList())
    // null until the first query returns, so "nothing here" hints don't flash while loading.
    val sessionsOrNull by remember(today) { db.life().focus(today - 6, today) }.collectAsStateWithLifecycle(null)
    val sessions = sessionsOrNull.orEmpty()
    var state by remember { mutableStateOf(Focus.state(context)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var length by remember { mutableIntStateOf(prefs.focusMinutes) }
    var breakLength by remember { mutableIntStateOf(prefs.breakMinutes) }
    var task by remember { mutableStateOf<DailyTask?>(null) }

    // Re-read on resume (the alarm may have ended the session) and tick once a second while running.
    LaunchedEffect(tick) { state = Focus.state(context) }
    LaunchedEffect(state.running) {
        while (state.running) {
            now = System.currentTimeMillis()
            if (now >= state.endAt) {
                Focus.finished(context)
                state = Focus.state(context)
            }
            delay(1000)
        }
    }

    val open = tasks.filter { it.status == TaskStatus.PENDING }
    val todayMinutes = sessions.filter { it.date == today }.sumOf { it.minutes }
    val weekMinutes = sessions.sumOf { it.minutes }

    Scaffold(topBar = { BackTopBar("Focus", { nav.popBackStack() }) }) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (state.running) {
                            val left = ((state.endAt - now) / 1000).coerceAtLeast(0)
                            val total = ((state.endAt - state.startAt) / 1000).coerceAtLeast(1)
                            Text(if (state.isBreak) "☕ Break" else "⏱ Focus", style = MaterialTheme.typography.titleMedium)
                            if (state.title.isNotBlank()) Text(state.title, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(16.dp))
                            CircularProgressIndicator(
                                progress = { left.toFloat() / total }, modifier = Modifier.padding(8.dp),
                                strokeWidth = 6.dp,
                            )
                            Text(
                                "%d:%02d".format(left / 60, left % 60),
                                style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold,
                            )
                            Text("until ${millisTimeLabel(state.endAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(onClick = {
                                scope.launch {
                                    Focus.stop(context)
                                    state = Focus.state(context)
                                }
                            }) { Text(if (state.isBreak) "End break" else "Stop (time so far is kept)") }
                        } else {
                            Text("Ready to focus?", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(12.dp))
                            Choice(
                                "On", listOf<DailyTask?>(null) + open, task, { task = it }, Modifier.fillMaxWidth(),
                                display = { it?.title ?: "No specific task" },
                            )
                            Text("Focus length", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                            FlowRow(horizontalArrangement = Arrangement.Center) {
                                focusLengths.forEach { m ->
                                    FilterChip(length == m, { length = m }, { Text("$m min") }, Modifier.padding(end = 4.dp))
                                }
                            }
                            Text("Break", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                            FlowRow {
                                listOf(5, 10, 15).forEach { m ->
                                    FilterChip(breakLength == m, { breakLength = m }, { Text("$m min") }, Modifier.padding(end = 4.dp))
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = {
                                prefs.focusMinutes = length
                                prefs.breakMinutes = breakLength
                                Focus.start(context, task?.id, task?.title ?: "", length)
                                state = Focus.state(context)
                            }) { Text("Start $length min") }
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp)) {
                    StatBox("Today", minutesLabel(todayMinutes), Modifier.weight(1f))
                    StatBox("Last 7 days", minutesLabel(weekMinutes), Modifier.weight(1f))
                    StatBox("Sessions", "${sessions.size}", Modifier.weight(1f))
                }
                Hint("Focus time on a task is added to its \"time spent\", so you can compare it with your estimate.")
                SectionTitle("Recent sessions")
                if (sessionsOrNull?.isEmpty() == true) Hint("No sessions yet.")
            }
            items(sessions, key = { it.id }) { f ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(f.title, Modifier.weight(1f))
                    Text(
                        "${Day.label(f.date, today)} · ${minutesLabel(f.minutes)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
