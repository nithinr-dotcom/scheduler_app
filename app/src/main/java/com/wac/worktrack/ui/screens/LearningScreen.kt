@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wac.worktrack.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.wac.worktrack.core.db
import com.wac.worktrack.data.LearnKind
import com.wac.worktrack.data.LearnStatus
import com.wac.worktrack.data.LearningItem
import com.wac.worktrack.ui.BackTopBar
import com.wac.worktrack.ui.Good
import com.wac.worktrack.ui.Hint
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.SectionTitle
import com.wac.worktrack.ui.rememberUndo
import kotlinx.coroutines.launch

private val kindLabels = mapOf(LearnKind.BOOK to "📖 Book", LearnKind.COURSE to "🎓 Course", LearnKind.OTHER to "🧩 Other")
private val defaultUnits = mapOf(LearnKind.BOOK to "pages", LearnKind.COURSE to "lessons", LearnKind.OTHER to "parts")

/** Moves an item's progress and finishes it (or re-opens it) to match. */
fun LearningItem.withProgress(value: Int, today: Long): LearningItem {
    val p = value.coerceIn(0, total)
    return when {
        p >= total -> copy(progress = p, status = LearnStatus.DONE, finishedDate = finishedDate ?: today)
        status == LearnStatus.DONE -> copy(progress = p, status = LearnStatus.ACTIVE, finishedDate = null)
        else -> copy(progress = p)
    }
}

@Composable
fun LearningScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val undo = rememberUndo()
    val today = LocalToday.current
    val db = context.db
    val items by remember { db.life().learning() }.collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<LearningItem?>(null) }
    var updating by remember { mutableStateOf<LearningItem?>(null) }

    fun save(l: LearningItem) = scope.launch { db.life().upsertLearning(l) }

    Scaffold(
        topBar = { BackTopBar("Learning", { nav.popBackStack() }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = LearningItem(title = "", total = 0, unit = "pages", startedDate = today) }) {
                Icon(Icons.Default.Add, "Add")
            }
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            val year = Day.date(today).year
            val finishedThisYear = items.count { it.status == LearnStatus.DONE && it.finishedDate?.let { d -> Day.date(d).year == year } == true }
            item { Hint("Books, courses and anything you're learning. Finished this year: $finishedThisYear.") }
            listOf(LearnStatus.ACTIVE to "In progress", LearnStatus.PAUSED to "Paused", LearnStatus.DONE to "Finished").forEach { (status, title) ->
                val group = items.filter { it.status == status }
                if (group.isNotEmpty()) {
                    item { SectionTitle(title) }
                    items(group, key = { it.id }) { l ->
                        val f = if (l.total == 0) 0f else l.progress.toFloat() / l.total
                        Card(onClick = { editing = l }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${kindLabels.getValue(l.kind).take(2)} ${l.title}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                    Text("${(f * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                                }
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { f }, modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = if (l.status == LearnStatus.DONE) Good else MaterialTheme.colorScheme.primary, drawStopIndicator = {},
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${l.progress} / ${l.total} ${l.unit}" +
                                            (l.finishedDate?.let { " · finished ${Day.short(it)}" } ?: " · since ${Day.short(l.startedDate)}"),
                                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (l.status != LearnStatus.DONE) {
                                        TextButton(onClick = { save(l.withProgress(l.progress + 1, today)) }) { Text("+1") }
                                        TextButton(onClick = { updating = l }) { Text("Update") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    updating?.let { l ->
        var value by remember(l) { mutableStateOf(l.progress.toString()) }
        AlertDialog(
            onDismissRequest = { updating = null },
            title = { Text(l.title) },
            text = {
                OutlinedTextField(
                    value, { value = it.filter(Char::isDigit).take(6) }, singleLine = true,
                    label = { Text("Now at (${l.unit}, of ${l.total})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = { value.toIntOrNull()?.let { save(l.withProgress(it, today)) }; updating = null }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { updating = null }) { Text("Cancel") } },
        )
    }
    editing?.let { l ->
        LearningDialog(
            l, today,
            onDismiss = { editing = null },
            onSave = { save(it); editing = null },
            onDelete = if (l.id == 0L) null else ({
                scope.launch {
                    db.life().deleteLearning(l)
                    undo.show("Deleted “${l.title}”") { db.life().upsertLearning(l) }
                }
                editing = null
            }),
        )
    }
}

@Composable
private fun LearningDialog(initial: LearningItem, today: Long, onDismiss: () -> Unit, onSave: (LearningItem) -> Unit, onDelete: (() -> Unit)?) {
    var title by remember { mutableStateOf(initial.title) }
    var kind by remember { mutableStateOf(initial.kind) }
    var total by remember { mutableStateOf(if (initial.total > 0) initial.total.toString() else "") }
    var unit by remember { mutableStateOf(initial.unit) }
    var status by remember { mutableStateOf(initial.status) }
    val totalValue = total.toIntOrNull()?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Start learning" else "Edit") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(Modifier.padding(top = 8.dp)) {
                    kindLabels.forEach { (k, label) ->
                        FilterChip(kind == k, {
                            if (unit == defaultUnits[kind]) unit = defaultUnits.getValue(k)
                            kind = k
                        }, { Text(label) }, Modifier.padding(end = 4.dp))
                    }
                }
                Row {
                    OutlinedTextField(
                        total, { total = it.filter(Char::isDigit).take(6) }, label = { Text("Total") }, singleLine = true,
                        modifier = Modifier.weight(1f).padding(end = 8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(unit, { unit = it }, label = { Text("Unit") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                if (initial.id != 0L) {
                    FlowRow(Modifier.padding(top = 8.dp)) {
                        listOf(LearnStatus.ACTIVE to "Active", LearnStatus.PAUSED to "Paused", LearnStatus.DONE to "Finished").forEach { (s, label) ->
                            FilterChip(status == s, { status = s }, { Text(label) }, Modifier.padding(end = 4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && totalValue != null && unit.isNotBlank(), onClick = {
                val base = initial.copy(title = title.trim(), kind = kind, total = totalValue!!, unit = unit.trim())
                onSave(
                    when (status) {
                        LearnStatus.DONE -> base.copy(status = LearnStatus.DONE, progress = base.total, finishedDate = initial.finishedDate ?: today)
                        else -> base.copy(status = status, progress = base.progress.coerceAtMost(base.total), finishedDate = null)
                    }
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
