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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wac.worktrack.calendar.CalendarItem
import com.wac.worktrack.core.Categories
import com.wac.worktrack.core.Checklist
import com.wac.worktrack.core.timeLabel
import com.wac.worktrack.data.DailyTask
import com.wac.worktrack.data.ENERGY_ANY
import com.wac.worktrack.data.ENERGY_HIGH
import com.wac.worktrack.data.ENERGY_LOW
import com.wac.worktrack.data.Goal
import com.wac.worktrack.data.PRIORITY_HIGH
import com.wac.worktrack.data.PRIORITY_LOW
import com.wac.worktrack.data.PRIORITY_NORMAL
import com.wac.worktrack.data.TaskStatus
import com.wac.worktrack.ui.Bad
import com.wac.worktrack.ui.Choice
import com.wac.worktrack.ui.Gap
import com.wac.worktrack.ui.TimePickerDialog

val priorityLabels = mapOf(PRIORITY_LOW to "Low", PRIORITY_NORMAL to "Normal", PRIORITY_HIGH to "High")

val energyLabels = mapOf(ENERGY_ANY to "Any energy", ENERGY_LOW to "Low energy", ENERGY_HIGH to "⚡ High focus")

private val estimateOptions = listOf(15, 30, 45, 60, 90, 120)

fun minutesLabel(m: Int): String = when {
    m < 60 -> "${m}m"
    m % 60 == 0 -> "${m / 60}h"
    else -> "${m / 60}h ${m % 60}m"
}

@Composable
fun TaskRow(
    task: DailyTask,
    onToggleDone: (() -> Unit)?,
    onStar: () -> Unit,
    onClick: () -> Unit,
) {
    val done = task.status == TaskStatus.DONE
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onToggleDone != null) {
            Checkbox(checked = done, onCheckedChange = { onToggleDone() })
        } else {
            Gap(12.dp)
        }
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done || task.status == TaskStatus.SKIPPED) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            val meta = buildList {
                task.timeMinutes?.let { add(timeLabel(it)) }
                add(task.category)
                if (task.priority == PRIORITY_HIGH) add("High")
                if (task.energy == ENERGY_HIGH) add("⚡")
                val (ticked, total) = Checklist.progress(task.checklist)
                if (total > 0) add("☑ $ticked/$total")
                when {
                    task.estimateMinutes != null && task.actualMinutes > 0 -> add("${minutesLabel(task.actualMinutes)} of ~${minutesLabel(task.estimateMinutes)}")
                    task.estimateMinutes != null -> add("~${minutesLabel(task.estimateMinutes)}")
                    task.actualMinutes > 0 -> add("${minutesLabel(task.actualMinutes)} spent")
                }
                if (task.templateId != null) add("Repeats")
                when (task.status) {
                    TaskStatus.SKIPPED -> add("Skipped")
                    TaskStatus.MISSED -> add("Missed")
                    else -> {}
                }
            }.joinToString(" · ")
            Text(
                meta, style = MaterialTheme.typography.bodySmall,
                color = if (task.status == TaskStatus.MISSED) Bad else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onStar) {
            Icon(
                if (task.isTop3) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Top 3",
                tint = if (task.isTop3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
fun GoogleItemRow(item: CalendarItem) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (item.kind == CalendarItem.Kind.EVENT) Icons.Default.CalendarMonth else Icons.Default.TaskAlt,
            null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.tertiary,
        )
        Gap(16.dp)
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val time = when {
                item.minutes == null -> "All day"
                item.endMinutes != null -> "${timeLabel(item.minutes)} – ${timeLabel(item.endMinutes)}"
                else -> timeLabel(item.minutes)
            }
            val kind = if (item.kind == CalendarItem.Kind.EVENT) "📅 ${item.source}" else "☑ ${item.source}"
            Text("$time · $kind", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Add/edit a single day's task. [onDelete] is shown only when editing. */
@Composable
fun TaskDialog(
    initial: DailyTask,
    goals: List<Goal>,
    onDismiss: () -> Unit,
    onSave: (DailyTask) -> Unit,
    onDelete: (() -> Unit)? = null,
    extraActions: (@Composable () -> Unit)? = null,
    onFocus: ((DailyTask) -> Unit)? = null,
) {
    var title by remember { mutableStateOf(initial.title) }
    var category by remember { mutableStateOf(initial.category) }
    var priority by remember { mutableIntStateOf(initial.priority) }
    var time by remember { mutableStateOf(initial.timeMinutes) }
    var goalId by remember { mutableStateOf(initial.goalId) }
    var estimate by remember { mutableStateOf(initial.estimateMinutes) }
    var energy by remember { mutableIntStateOf(initial.energy) }
    var checklist by remember { mutableStateOf(Checklist.parse(initial.checklist)) }
    var actual by remember { mutableStateOf(if (initial.actualMinutes > 0) initial.actualMinutes.toString() else "") }
    var pickingTime by remember { mutableStateOf(false) }

    fun current() = initial.copy(
        title = title.trim(), category = category, priority = priority, timeMinutes = time, goalId = goalId,
        estimateMinutes = estimate, energy = energy, checklist = Checklist.format(checklist),
        actualMinutes = actual.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New task" else "Edit task") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it }, label = { Text("Task") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Choice("Category", (Categories.tasks + category).distinct(), category, { category = it }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                PriorityChips(priority) { priority = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickingTime = true }) { Text(time?.let { "Time: ${timeLabel(it)}" } ?: "Set time") }
                    if (time != null) TextButton(onClick = { time = null }) { Text("Any time") }
                }
                EstimateChips(estimate) { estimate = it }
                EnergyChips(energy) { energy = it }
                ChecklistEditor(checklist) { checklist = it }
                if (initial.id != 0L) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        actual, { actual = it.filter(Char::isDigit).take(4) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Time spent (minutes)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                GoalChoice(goals, goalId) { goalId = it }
                if (onFocus != null && initial.id != 0L && initial.status == TaskStatus.PENDING) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { onFocus(current()) }) { Text("⏱ Start focus session") }
                }
                extraActions?.let {
                    Spacer(Modifier.height(8.dp))
                    it()
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onSave(current()) },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
    if (pickingTime) TimePickerDialog(time, onDismiss = { pickingTime = false }) { time = it; pickingTime = false }
}

@Composable
fun PriorityChips(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow {
        priorityLabels.forEach { (p, label) ->
            FilterChip(selected = selected == p, onClick = { onSelect(p) }, label = { Text(label) }, modifier = Modifier.padding(end = 6.dp))
        }
    }
}

/** Link to a COUNT goal — finishing the task adds 1 to the goal. */
@Composable
fun GoalChoice(goals: List<Goal>, goalId: Long?, onSelect: (Long?) -> Unit) {
    if (goals.isEmpty()) return
    val options = listOf<Goal?>(null) + goals
    Spacer(Modifier.height(8.dp))
    Choice(
        "Goal", options, goals.firstOrNull { it.id == goalId }, { onSelect(it?.id) }, Modifier.fillMaxWidth(),
        display = { it?.title ?: "None" },
    )
}

/** "How long will this take?" — used by Auto-schedule and the estimate-vs-actual stats. */
@Composable
fun EstimateChips(selected: Int?, onSelect: (Int?) -> Unit) {
    Text("Estimate", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    FlowRow {
        estimateOptions.forEach { m ->
            FilterChip(selected == m, { onSelect(if (selected == m) null else m) }, { Text(minutesLabel(m)) }, Modifier.padding(end = 4.dp))
        }
    }
}

@Composable
fun EnergyChips(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow {
        energyLabels.forEach { (e, label) ->
            FilterChip(selected == e, { onSelect(e) }, { Text(label) }, Modifier.padding(end = 4.dp))
        }
    }
}

/** Sub-steps of a task: tick, remove, or add new ones. */
@Composable
fun ChecklistEditor(items: List<Checklist.Item>, onChange: (List<Checklist.Item>) -> Unit) {
    var newItem by remember { mutableStateOf("") }
    Text("Checklist", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    items.forEachIndexed { i, item ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(item.done, { done -> onChange(items.toMutableList().also { it[i] = item.copy(done = done) }) })
            Text(
                item.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
            )
            IconButton(onClick = { onChange(items.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Default.Close, "Remove") }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            newItem, { newItem = it }, singleLine = true, modifier = Modifier.weight(1f),
            placeholder = { Text("Add a step") },
        )
        TextButton(enabled = newItem.isNotBlank(), onClick = {
            onChange(items + Checklist.Item(newItem.trim()))
            newItem = ""
        }) { Text("Add") }
    }
}
