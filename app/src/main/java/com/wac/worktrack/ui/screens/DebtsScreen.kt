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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.Money
import com.wac.worktrack.core.db
import com.wac.worktrack.data.Debt
import com.wac.worktrack.data.DebtDirection
import com.wac.worktrack.finance.MoneyOps
import com.wac.worktrack.ui.AmountDialog
import com.wac.worktrack.ui.BackTopBar
import com.wac.worktrack.ui.Bad
import com.wac.worktrack.ui.DatePickerDialogFor
import com.wac.worktrack.ui.Good
import com.wac.worktrack.ui.Hint
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.SectionTitle
import com.wac.worktrack.ui.StatBox
import com.wac.worktrack.ui.rememberUndo
import kotlinx.coroutines.launch

/** Who owes whom. Kept separate from income/expenses — it's money that should come back. */
@Composable
fun DebtsScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val undo = rememberUndo()
    val today = LocalToday.current
    val finance = context.db.finance()
    // null until the first query returns, so "nothing here" hints don't flash while loading.
    val debtsOrNull by remember { finance.debts() }.collectAsStateWithLifecycle(null)
    val debts = debtsOrNull.orEmpty()
    var editing by remember { mutableStateOf<Debt?>(null) }
    var repaying by remember { mutableStateOf<Debt?>(null) }

    val open = debts.filter { it.outstanding > 0 }
    val owedToMe = open.filter { it.direction == DebtDirection.LENT }.sumOf { it.outstanding }
    val iOwe = open.filter { it.direction == DebtDirection.BORROWED }.sumOf { it.outstanding }

    Scaffold(
        topBar = { BackTopBar("Lent & borrowed", { nav.popBackStack() }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Debt(person = "", amountPaise = 0, direction = DebtDirection.LENT, date = today) }) {
                Icon(Icons.Default.Add, "Add")
            }
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(Modifier.padding(vertical = 16.dp)) {
                        StatBox("Owed to you", Money.format(owedToMe), Modifier.weight(1f), Good)
                        StatBox("You owe", Money.format(iOwe), Modifier.weight(1f), Bad)
                    }
                }
            }
            if (debtsOrNull?.isEmpty() == true) item { Hint("Lent a friend ₹500? Borrowed for a trip? Track it here so nothing is forgotten.") }
            listOf(open to "Open", debts.filter { it.outstanding == 0L } to "Settled").forEach { (group, title) ->
                if (group.isNotEmpty()) {
                    item { SectionTitle(title) }
                    items(group, key = { it.id }) { d ->
                        val lent = d.direction == DebtDirection.LENT
                        Card(onClick = { editing = d }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(if (lent) "${d.person} owes you" else "You owe ${d.person}", style = MaterialTheme.typography.bodyLarge)
                                    val meta = listOfNotNull(
                                        Day.short(d.date),
                                        d.note.takeIf { it.isNotBlank() },
                                        d.dueDate?.let { if (it < today && d.outstanding > 0) "overdue since ${Day.short(it)}" else "due ${Day.short(it)}" },
                                        if (d.repaidPaise > 0 && d.outstanding > 0) "${Money.format(d.repaidPaise)} paid back" else null,
                                    ).joinToString(" · ")
                                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(Money.format(if (d.outstanding > 0) d.outstanding else d.amountPaise), fontWeight = FontWeight.SemiBold, color = if (lent) Good else Bad)
                                    if (d.outstanding > 0) TextButton(onClick = { repaying = d }) { Text("Repaid…") }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    repaying?.let { d ->
        AmountDialog(
            title = "Amount paid back", initial = Money.input(d.outstanding),
            onDismiss = { repaying = null },
            onSave = { text ->
                Money.parse(text)?.let { paise -> scope.launch {
                        finance.upsertDebt(d.copy(repaidPaise = (d.repaidPaise + paise).coerceAtMost(d.amountPaise)))
                        MoneyOps.snapshotNetWorth(context)
                    } }
                repaying = null
            },
        )
    }
    editing?.let { d ->
        DebtDialog(
            d, today,
            onDismiss = { editing = null },
            onSave = { scope.launch { finance.upsertDebt(it); MoneyOps.snapshotNetWorth(context) }; editing = null },
            onDelete = if (d.id == 0L) null else ({
                scope.launch {
                    finance.deleteDebt(d)
                    MoneyOps.snapshotNetWorth(context)
                    undo.show("Deleted ${d.person}") {
                        finance.upsertDebt(d)
                        MoneyOps.snapshotNetWorth(context)
                    }
                }
                editing = null
            }),
        )
    }
}

@Composable
private fun DebtDialog(initial: Debt, today: Long, onDismiss: () -> Unit, onSave: (Debt) -> Unit, onDelete: (() -> Unit)?) {
    var person by remember { mutableStateOf(initial.person) }
    var amount by remember { mutableStateOf(if (initial.amountPaise == 0L) "" else Money.input(initial.amountPaise)) }
    var direction by remember { mutableStateOf(initial.direction) }
    var date by remember { mutableLongStateOf(initial.date) }
    var due by remember { mutableStateOf(initial.dueDate) }
    var note by remember { mutableStateOf(initial.note) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingDue by remember { mutableStateOf(false) }
    val paise = Money.parse(amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New entry" else "Edit") },
        text = {
            Column {
                FlowRow {
                    FilterChip(direction == DebtDirection.LENT, { direction = DebtDirection.LENT }, { Text("I lent") }, Modifier.padding(end = 6.dp))
                    FilterChip(direction == DebtDirection.BORROWED, { direction = DebtDirection.BORROWED }, { Text("I borrowed") })
                }
                OutlinedTextField(person, { person = it }, label = { Text("Person") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    amount, { amount = it }, label = { Text("Amount (₹)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickingDate = true }) { Text(Day.label(date, today)) }
                    Spacer(Modifier.padding(4.dp))
                    OutlinedButton(onClick = { pickingDue = true }) { Text(due?.let { "Due ${Day.short(it)}" } ?: "Due date") }
                    if (due != null) TextButton(onClick = { due = null }) { Text("None") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = person.isNotBlank() && paise != null, onClick = {
                onSave(
                    initial.copy(
                        person = person.trim(), amountPaise = paise!!, direction = direction, date = date, dueDate = due,
                        note = note.trim(), repaidPaise = initial.repaidPaise.coerceAtMost(paise),
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
    if (pickingDate) DatePickerDialogFor(date, { pickingDate = false }) { date = minOf(it, today); pickingDate = false }
    if (pickingDue) DatePickerDialogFor(due ?: (today + 30), { pickingDue = false }) { due = it; pickingDue = false }
}
