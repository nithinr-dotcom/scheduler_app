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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.ImportantDates
import com.wac.worktrack.core.db
import com.wac.worktrack.data.DateKind
import com.wac.worktrack.data.ImportantDate
import com.wac.worktrack.ui.BackTopBar
import com.wac.worktrack.ui.Choice
import com.wac.worktrack.ui.Hint
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.rememberUndo
import kotlinx.coroutines.launch
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/** Birthdays, anniversaries and other yearly dates, soonest first. */
@Composable
fun DatesScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalToday.current
    val undo = rememberUndo()
    val care = context.db.care()
    // null until the first query returns, so "nothing here" hints don't flash while loading.
    val datesOrNull by remember { care.dates() }.collectAsStateWithLifecycle(null)
    val dates = datesOrNull.orEmpty()
    val sorted = remember(dates, today) { dates.sortedBy { ImportantDates.next(it, today) } }
    var editing by remember { mutableStateOf<ImportantDate?>(null) }

    Scaffold(
        topBar = { BackTopBar("Important dates", { nav.popBackStack() }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val d = Day.date(today)
                editing = ImportantDate(title = "", month = d.monthValue, day = d.dayOfMonth)
            }) { Icon(Icons.Default.Add, "Add date") }
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item { Hint("You'll get a heads-up at 9 AM a few days before each one, so there's time to call, plan or buy a gift.") }
            if (datesOrNull?.isEmpty() == true) item { Hint("No dates yet. Tap + to add a birthday or anniversary.") }
            items(sorted, key = { it.id }) { d ->
                Column(Modifier.fillMaxWidth().clickable { editing = d }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        listOfNotNull("${ImportantDates.emoji(d)} ${d.title}", ImportantDates.milestone(d, today)).joinToString(" · "),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    val left = ImportantDates.next(d, today) - today
                    Text(
                        "${monthName(d.month)} ${d.day} · " + when (left) {
                            0L -> "today 🎉"
                            1L -> "tomorrow"
                            else -> "in $left days"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (left <= d.remindDaysBefore) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    editing?.let { d ->
        DateDialog(
            initial = d,
            onDismiss = { editing = null },
            onSave = { scope.launch { care.upsertDate(it) }; editing = null },
            onDelete = if (d.id == 0L) null else ({
                scope.launch {
                    care.deleteDate(d)
                    undo.show("Deleted ${d.title}") { care.upsertDate(d) }
                }
                editing = null
            }),
        )
    }
}

private fun monthName(m: Int) = Month.of(m.coerceIn(1, 12)).getDisplayName(TextStyle.SHORT, Locale.getDefault())

@Composable
private fun DateDialog(initial: ImportantDate, onDismiss: () -> Unit, onSave: (ImportantDate) -> Unit, onDelete: (() -> Unit)?) {
    var title by remember { mutableStateOf(initial.title) }
    var kind by remember { mutableStateOf(initial.kind) }
    var month by remember { mutableStateOf(initial.month) }
    var day by remember { mutableStateOf(initial.day.toString()) }
    var year by remember { mutableStateOf(initial.year?.toString() ?: "") }
    var before by remember { mutableStateOf(initial.remindDaysBefore.toString()) }
    val maxDay = Month.of(month).maxLength()
    val dayValue = day.toIntOrNull()?.takeIf { it in 1..maxDay }
    val yearValue = year.toIntOrNull()?.takeIf { it in 1900..Day.date(Day.today()).year }
    val valid = title.isNotBlank() && dayValue != null && (year.isBlank() || yearValue != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New date" else "Edit date") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it }, label = { Text("Whose / what (e.g. Mom)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(Modifier.padding(top = 8.dp)) {
                    listOf(DateKind.BIRTHDAY to "Birthday", DateKind.ANNIVERSARY to "Anniversary", DateKind.OTHER to "Other").forEach { (k, label) ->
                        FilterChip(kind == k, { kind = k }, { Text(label) }, Modifier.padding(end = 6.dp))
                    }
                }
                Row {
                    Choice("Month", (1..12).toList(), month, { month = it }, Modifier.weight(1f), display = ::monthName)
                    Spacer(Modifier.padding(4.dp))
                    OutlinedTextField(
                        day, { day = it.filter(Char::isDigit).take(2) }, label = { Text("Day") }, singleLine = true,
                        modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                OutlinedTextField(
                    year, { year = it.filter(Char::isDigit).take(4) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Year (optional — shows \"turns 30\")") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    before, { before = it.filter(Char::isDigit).take(2) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Remind me this many days before") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(
                    initial.copy(
                        title = title.trim(), kind = kind, month = month, day = dayValue!!, year = yearValue,
                        remindDaysBefore = (before.toIntOrNull() ?: 3).coerceIn(0, 30),
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
}
