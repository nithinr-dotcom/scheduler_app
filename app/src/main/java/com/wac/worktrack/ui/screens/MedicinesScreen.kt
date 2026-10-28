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
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.wac.worktrack.core.Meds
import com.wac.worktrack.core.db
import com.wac.worktrack.core.timeLabel
import com.wac.worktrack.data.MedLog
import com.wac.worktrack.data.Medicine
import com.wac.worktrack.ui.BackTopBar
import com.wac.worktrack.ui.Hint
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.TimePickerDialog
import com.wac.worktrack.ui.Warn
import com.wac.worktrack.ui.rememberUndo
import kotlinx.coroutines.launch

/** Medicines with dose times; tick doses as taken, see how regular you've been and when to refill. */
@Composable
fun MedicinesScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalToday.current
    val undo = rememberUndo()
    val care = context.db.care()
    val meds by remember { care.medicines() }.collectAsStateWithLifecycle(emptyList())
    val logs by remember(today) { care.medLogs(today - 6, today) }.collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<Medicine?>(null) }

    Scaffold(
        topBar = { BackTopBar("Medicines", { nav.popBackStack() }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Medicine(name = "", times = "${8 * 60}") }) { Icon(Icons.Default.Add, "Add medicine") }
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Hint("You'll get a reminder at each dose time with a \"Taken\" button. Add how many doses you have to get a refill warning.")
            }
            if (meds.isEmpty()) item { Hint("No medicines yet. Tap + to add one.") }
            items(meds, key = { it.id }) { m ->
                Card(onClick = { editing = m }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(m.name + if (m.active) "" else " · paused", style = MaterialTheme.typography.titleMedium)
                        val sub = listOfNotNull(
                            m.dose.ifBlank { null },
                            m.stock?.let { "$it left" },
                            Meds.adherence(listOf(m), logs, today - 6..today)?.let { "last 7 days: $it% taken" },
                        ).joinToString(" · ")
                        if (sub.isNotEmpty()) {
                            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (Meds.lowStock(m)) Text("Running low — time to refill.", color = Warn, style = MaterialTheme.typography.bodySmall)
                        if (m.active) {
                            Spacer(Modifier.height(8.dp))
                            DoseChips(m, logs, today)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    editing?.let { m ->
        MedicineDialog(
            initial = m,
            onDismiss = { editing = null },
            onSave = { scope.launch { Meds.save(context, it) }; editing = null },
            onDelete = if (m.id == 0L) null else ({
                scope.launch {
                    Meds.delete(context, m)
                    undo.show("Deleted ${m.name}") { Meds.save(context, m) }
                }
                editing = null
            }),
        )
    }
}

/** Today's doses for [m]; tap to mark taken or not taken. Also used on Today. */
@Composable
fun DoseChips(m: Medicine, logs: List<MedLog>, today: Long, showName: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    FlowRow {
        Meds.parseTimes(m.times).forEach { slot ->
            val taken = Meds.taken(logs, m.id, today, slot)
            FilterChip(
                selected = taken,
                onClick = {
                    scope.launch { if (taken) Meds.untake(context, m, today, slot) else Meds.take(context, m, today, slot) }
                },
                label = { Text((if (showName) "${m.name} " else "") + timeLabel(slot) + if (taken) " ✓" else "") },
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }
}

@Composable
private fun MedicineDialog(initial: Medicine, onDismiss: () -> Unit, onSave: (Medicine) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(initial.name) }
    var dose by remember { mutableStateOf(initial.dose) }
    var times by remember { mutableStateOf(Meds.parseTimes(initial.times)) }
    var stock by remember { mutableStateOf(initial.stock?.toString() ?: "") }
    var refillAt by remember { mutableStateOf(initial.refillAt.toString()) }
    var active by remember { mutableStateOf(initial.active) }
    var addingTime by remember { mutableStateOf(false) }
    val stockValue = stock.trim().toIntOrNull()
    val valid = name.isNotBlank() && times.isNotEmpty() && (stock.isBlank() || stockValue != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New medicine" else "Edit medicine") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dose, { dose = it }, label = { Text("Dose (e.g. 1 tablet, 5 ml)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Times", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                FlowRow {
                    times.forEach { t ->
                        InputChip(
                            selected = false, onClick = { times = times - t },
                            label = { Text("${timeLabel(t)}  ✕") }, modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                    TextButton(onClick = { addingTime = true }) { Text("+ Add time") }
                }
                if (times.isEmpty()) Text("Add at least one time.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    stock, { stock = it.filter(Char::isDigit).take(4) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Doses left (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (stock.isNotBlank()) {
                    OutlinedTextField(
                        refillAt, { refillAt = it.filter(Char::isDigit).take(3) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Warn me when this many are left") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Reminders on", Modifier.weight(1f))
                    Switch(active, { active = it })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(
                    initial.copy(
                        name = name.trim(), dose = dose.trim(), times = Meds.formatTimes(times), stock = stockValue,
                        refillAt = refillAt.toIntOrNull() ?: initial.refillAt, active = active,
                    )
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
    if (addingTime) {
        TimePickerDialog(times.lastOrNull()?.let { minOf(it + 12 * 60, 23 * 60) } ?: (8 * 60), { addingTime = false }) {
            times = (times + it).distinct().sorted()
            addingTime = false
        }
    }
}
