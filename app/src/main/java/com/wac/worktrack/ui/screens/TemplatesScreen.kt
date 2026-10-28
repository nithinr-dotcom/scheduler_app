@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wac.worktrack.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.wac.worktrack.core.Categories
import com.wac.worktrack.core.Checklist
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.DayPlanner
import com.wac.worktrack.core.db
import com.wac.worktrack.core.timeLabel
import com.wac.worktrack.data.Goal
import com.wac.worktrack.data.GoalType
import com.wac.worktrack.data.Repeat
import com.wac.worktrack.data.TaskTemplate
import com.wac.worktrack.ui.Choice
import com.wac.worktrack.ui.DatePickerDialogFor
import com.wac.worktrack.ui.Hint
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.TimePickerDialog
import com.wac.worktrack.ui.rememberUndo
import kotlinx.coroutines.launch

private val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

fun TaskTemplate.repeatLabel(): String = when (repeat) {
    Repeat.DAILY -> "Every day"
    Repeat.WEEKDAYS -> dayNames.filterIndexed { i, _ -> daysMask and (1 shl i) != 0 }.joinToString(", ").ifEmpty { "No days" }
    Repeat.ONCE -> "Once on " + (onceDate?.let { Day.short(it) } ?: "?")
}

/** Repeating tasks: each one becomes a task on every matching day. */
@Composable
fun TemplatesScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val undo = rememberUndo()
    val templates by remember { context.db.tasks().templates() }.collectAsStateWithLifecycle(emptyList())
    val goals by remember { context.db.goals().active() }.collectAsStateWithLifecycle(emptyList())
    val today = LocalToday.current
    var editing by remember { mutableStateOf<TaskTemplate?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Repeating tasks") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = TaskTemplate(title = "", category = Categories.tasks.first()) }) {
                Icon(Icons.Default.Add, "Add")
            }
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            if (templates.isEmpty()) {
                item {
                    Hint(
                        "Add the things you want to do regularly — e.g. \"Workout\" every day, \"Weekly planning\" " +
                            "on Sunday. They show up automatically on each matching day.",
                    )
                }
            }
            items(templates, key = { it.id }) { t ->
                Row(
                    Modifier.fillMaxWidth().clickable { editing = t }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.title, style = MaterialTheme.typography.bodyLarge,
                            color = if (t.active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        )
                        val meta = listOfNotNull(
                            t.repeatLabel(), t.timeMinutes?.let { timeLabel(it) }, t.category,
                            if (!t.active) "Paused" else null,
                        ).joinToString(" · ")
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    editing?.let { t ->
        TemplateDialog(
            initial = t,
            today = today,
            goals = goals.filter { it.type == GoalType.COUNT },
            onDismiss = { editing = null },
            onSave = { scope.launch { DayPlanner.saveTemplate(context, it) }; editing = null },
            onDelete = if (t.id == 0L) null else ({
                scope.launch {
                    DayPlanner.deleteTemplate(context, t)
                    undo.show("Deleted “${t.title}”") { DayPlanner.saveTemplate(context, t) }
                }
                editing = null
            }),
        )
    }
}

@Composable
private fun TemplateDialog(
    initial: TaskTemplate,
    today: Long,
    goals: List<Goal>,
    onDismiss: () -> Unit,
    onSave: (TaskTemplate) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var title by remember { mutableStateOf(initial.title) }
    var category by remember { mutableStateOf(initial.category) }
    var priority by remember { mutableIntStateOf(initial.priority) }
    var repeat by remember { mutableStateOf(initial.repeat) }
    var mask by remember { mutableIntStateOf(initial.daysMask) }
    var onceDate by remember { mutableLongStateOf(initial.onceDate ?: today) }
    var time by remember { mutableStateOf(initial.timeMinutes) }
    var goalId by remember { mutableStateOf(initial.goalId) }
    var active by remember { mutableStateOf(initial.active) }
    var estimate by remember { mutableStateOf(initial.estimateMinutes) }
    var energy by remember { mutableIntStateOf(initial.energy) }
    var checklist by remember { mutableStateOf(Checklist.parse(initial.checklist)) }
    var pickingTime by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New repeating task" else "Edit repeating task") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it }, label = { Text("Task") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Choice("Category", (Categories.tasks + category).distinct(), category, { category = it }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                PriorityChips(priority) { priority = it }
                Text("Repeat", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                FlowRow {
                    listOf(Repeat.DAILY to "Every day", Repeat.WEEKDAYS to "Some days", Repeat.ONCE to "Once").forEach { (r, label) ->
                        FilterChip(repeat == r, { repeat = r }, { Text(label) }, Modifier.padding(end = 6.dp))
                    }
                }
                if (repeat == Repeat.WEEKDAYS) {
                    FlowRow {
                        dayNames.forEachIndexed { i, name ->
                            val on = mask and (1 shl i) != 0
                            FilterChip(on, { mask = mask xor (1 shl i) }, { Text(name) }, Modifier.padding(end = 4.dp))
                        }
                    }
                }
                if (repeat == Repeat.ONCE) {
                    OutlinedButton(onClick = { pickingDate = true }) { Text("Date: ${Day.label(onceDate, today)}") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickingTime = true }) { Text(time?.let { "Time: ${timeLabel(it)}" } ?: "Set time") }
                    if (time != null) TextButton(onClick = { time = null }) { Text("Any time") }
                }
                EstimateChips(estimate) { estimate = it }
                EnergyChips(energy) { energy = it }
                ChecklistEditor(checklist) { checklist = Checklist.reset(Checklist.format(it)).let(Checklist::parse) }
                GoalChoice(goals, goalId) { goalId = it }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Active", Modifier.weight(1f))
                    Switch(active, { active = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && !(repeat == Repeat.WEEKDAYS && mask == 0),
                onClick = {
                    onSave(
                        initial.copy(
                            title = title.trim(), category = category, priority = priority, repeat = repeat,
                            daysMask = mask, onceDate = if (repeat == Repeat.ONCE) onceDate else null,
                            timeMinutes = time, goalId = goalId, active = active,
                            estimateMinutes = estimate, energy = energy, checklist = Checklist.format(checklist),
                        )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
    if (pickingTime) TimePickerDialog(time, { pickingTime = false }) { time = it; pickingTime = false }
    if (pickingDate) DatePickerDialogFor(onceDate, { pickingDate = false }) { onceDate = it; pickingDate = false }
}
