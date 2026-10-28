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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.Money
import com.wac.worktrack.core.db
import com.wac.worktrack.data.Goal
import com.wac.worktrack.data.GoalType
import com.wac.worktrack.data.Txn
import com.wac.worktrack.data.TxnType
import com.wac.worktrack.ui.AmountDialog
import com.wac.worktrack.ui.DatePickerDialogFor
import com.wac.worktrack.ui.Good
import com.wac.worktrack.ui.Hint
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.daysLeftLabel
import kotlinx.coroutines.launch

/** Progress of a goal: stored count, or money saved via transactions tagged with it. */
fun Goal.progress(saved: Map<Long, Long>): Long = if (type == GoalType.MONEY) saved[id] ?: 0 else current

fun Goal.progressLabel(value: Long): String =
    if (type == GoalType.MONEY) "${Money.format(value)} / ${Money.format(target)}"
    else "$value / $target ${unit}".trim()

@Composable
fun GoalsScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalToday.current
    val db = context.db
    val goals by remember { db.goals().active() }.collectAsStateWithLifecycle(emptyList())
    val savedList by remember { db.finance().savedPerGoal() }.collectAsStateWithLifecycle(emptyList())
    val saved = savedList.associate { it.goalId to it.totalPaise }
    var editing by remember { mutableStateOf<Goal?>(null) }
    var contributing by remember { mutableStateOf<Goal?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Goals") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Goal(title = "", target = 10, createdDate = today) }) { Icon(Icons.Default.Add, "Add goal") }
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            if (goals.isEmpty()) {
                item {
                    Hint(
                        "Set long-term goals — \"Read 24 books in 2026\" (count) or \"Save ₹1,00,000\" (money). " +
                            "Link repeating tasks to a count goal and every completion adds 1.",
                    )
                }
            }
            items(goals, key = { it.id }) { g ->
                val value = g.progress(saved)
                val fraction = if (g.target == 0L) 0f else value.toFloat() / g.target
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { editing = g }) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(g.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("${(fraction * 100).toInt().coerceAtMost(999)}%", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = if (fraction >= 1f) Good else MaterialTheme.colorScheme.primary, drawStopIndicator = {},
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            listOfNotNull(
                                g.progressLabel(value), g.deadline?.let { daysLeftLabel(it, today) },
                                if (g.autoSharePercent > 0) "auto-save ${g.autoSharePercent}%" else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(Modifier.padding(top = 4.dp)) {
                            if (g.type == GoalType.COUNT) {
                                TextButton(onClick = { scope.launch { db.goals().addProgress(g.id, -1) } }) { Text("−1") }
                                TextButton(onClick = { scope.launch { db.goals().addProgress(g.id, 1) } }) { Text("+1") }
                            } else {
                                TextButton(onClick = { contributing = g }) { Text("Add savings") }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    editing?.let { g ->
        GoalDialog(
            initial = g, today = today,
            onDismiss = { editing = null },
            onSave = { scope.launch { db.goals().upsert(it) }; editing = null },
            onArchive = if (g.id == 0L) null else ({ scope.launch { db.goals().upsert(g.copy(archived = true)) }; editing = null }),
        )
    }
    contributing?.let { g ->
        AmountDialog(
            title = "Add to “${g.title}”", initial = "",
            onDismiss = { contributing = null },
            onSave = { text ->
                Money.parse(text)?.let { paise ->
                    // Saving is money set aside, so it's recorded as a "Savings" expense tagged with the goal.
                    scope.launch {
                        db.finance().upsert(
                            Txn(
                                amountPaise = paise, type = TxnType.EXPENSE, category = "Savings", date = today,
                                timeMillis = System.currentTimeMillis(), note = g.title, method = "Bank", goalId = g.id,
                            )
                        )
                    }
                }
                contributing = null
            },
        )
    }
}

@Composable
private fun GoalDialog(initial: Goal, today: Long, onDismiss: () -> Unit, onSave: (Goal) -> Unit, onArchive: (() -> Unit)?) {
    var title by remember { mutableStateOf(initial.title) }
    var type by remember { mutableStateOf(initial.type) }
    var target by remember {
        mutableStateOf(if (initial.id == 0L) "" else if (initial.type == GoalType.MONEY) Money.input(initial.target) else initial.target.toString())
    }
    var unit by remember { mutableStateOf(initial.unit) }
    var deadline by remember { mutableStateOf(initial.deadline) }
    var share by remember { mutableStateOf(if (initial.autoSharePercent > 0) initial.autoSharePercent.toString() else "") }
    var emergency by remember { mutableStateOf(initial.isEmergency) }
    var pickingDate by remember { mutableStateOf(false) }
    val targetValue = if (type == GoalType.MONEY) Money.parse(target) else target.trim().toLongOrNull()?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New goal" else "Edit goal") },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text("Goal") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(Modifier.padding(top = 8.dp)) {
                    FilterChip(type == GoalType.COUNT, { type = GoalType.COUNT }, { Text("Count") }, Modifier.padding(end = 6.dp))
                    FilterChip(type == GoalType.MONEY, { type = GoalType.MONEY }, { Text("Money") })
                }
                OutlinedTextField(
                    target, { target = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (type == GoalType.MONEY) "Target (₹)" else "Target number") },
                    keyboardOptions = KeyboardOptions(keyboardType = if (type == GoalType.MONEY) KeyboardType.Decimal else KeyboardType.Number),
                )
                if (type == GoalType.COUNT) {
                    OutlinedTextField(unit, { unit = it }, label = { Text("Unit (e.g. books, km)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(
                        share, { share = it.filter(Char::isDigit).take(2) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Auto-save % of each income (optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Text(
                        "When you accept income, this share is suggested in Pending for you to confirm.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text("This is my emergency fund", Modifier.weight(1f))
                        Switch(emergency, { emergency = it })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { pickingDate = true }) { Text(deadline?.let { "Deadline: ${Day.short(it)}" } ?: "Set deadline") }
                    if (deadline != null) TextButton(onClick = { deadline = null }) { Text("None") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && targetValue != null, onClick = {
                onSave(
                    initial.copy(
                        title = title.trim(), type = type, target = targetValue!!, unit = unit.trim(), deadline = deadline,
                        autoSharePercent = if (type == GoalType.MONEY) (share.toIntOrNull() ?: 0).coerceIn(0, 90) else 0,
                        isEmergency = type == GoalType.MONEY && emergency,
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onArchive != null) TextButton(onClick = onArchive) { Text("Archive") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
    if (pickingDate) DatePickerDialogFor(deadline ?: (today + 30), { pickingDate = false }) { deadline = it; pickingDate = false }
}
