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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.wac.worktrack.core.Bills
import com.wac.worktrack.core.Categories
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.Money
import com.wac.worktrack.core.db
import com.wac.worktrack.data.Bill
import com.wac.worktrack.data.BillCycle
import com.wac.worktrack.finance.MoneyOps
import com.wac.worktrack.ui.AmountDialog
import com.wac.worktrack.ui.Bad
import com.wac.worktrack.ui.BackTopBar
import com.wac.worktrack.ui.Choice
import com.wac.worktrack.ui.Good
import com.wac.worktrack.ui.Hint
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.SectionTitle
import com.wac.worktrack.ui.StatBox
import com.wac.worktrack.ui.Warn
import com.wac.worktrack.ui.rememberUndo
import kotlinx.coroutines.launch

private val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

@Composable
fun BillsScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val undo = rememberUndo()
    val today = LocalToday.current
    val finance = context.db.finance()
    val bills by remember { finance.bills() }.collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<Bill?>(null) }
    var paying by remember { mutableStateOf<Bill?>(null) }

    val active = bills.filter { it.active }
    val subs = active.filter { it.isSubscription }
    val dueThisMonth = active.filter { b ->
        val due = Bills.nextDue(b, today)
        due in Day.monthRange(today) && b.lastPaidPeriod != Bills.periodOf(b, due)
    }

    Scaffold(
        topBar = { BackTopBar("Bills & subscriptions", { nav.popBackStack() }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Bill(title = "", amountPaise = 0, dueDay = Day.date(today).dayOfMonth) }) {
                Icon(Icons.Default.Add, "Add bill")
            }
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(Modifier.padding(vertical = 16.dp)) {
                        StatBox("Still to pay this month", Money.format(dueThisMonth.sumOf { it.amountPaise }), Modifier.weight(1f), Bad)
                        StatBox("Subscriptions / month", Money.format(subs.sumOf { Bills.monthlyCost(it) }), Modifier.weight(1f))
                    }
                }
                Hint("You'll get a reminder before each due date. \"Mark paid\" adds the expense to Finance.")
            }
            if (bills.isEmpty()) item { Hint("No bills yet. Add rent, electricity, phone, Netflix…") }
            val sorted = active.sortedBy { Bills.nextDue(it, today) } + bills.filter { !it.active }
            items(sorted, key = { it.id }) { b ->
                val due = Bills.nextDue(b, today)
                val paid = b.lastPaidPeriod == Bills.periodOf(b, today)
                val overdue = Bills.isOverdue(b, today)
                val status = when {
                    !b.active -> "Paused"
                    overdue -> "Overdue since ${Day.short(due)}"
                    paid -> "Paid · next ${Day.short(due)}"
                    due == today -> "Due today"
                    else -> "Due ${Day.label(due, today).lowercase()} · in ${due - today} days"
                }
                Card(onClick = { editing = b }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text((if (b.isSubscription) "🔁 " else "") + b.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                status, style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    overdue -> Bad
                                    paid -> Good
                                    due - today <= b.remindDaysBefore -> Warn
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Text(Bills.cycleLabel(b), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Money.format(b.amountPaise), fontWeight = FontWeight.SemiBold)
                            if (b.active && !paid) TextButton(onClick = { paying = b }) { Text("Mark paid") }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    paying?.let { b ->
        AmountDialog(
            title = "Paid ${b.title}", initial = Money.input(b.amountPaise),
            onDismiss = { paying = null },
            onSave = { text ->
                Money.parse(text)?.let { paise -> scope.launch { MoneyOps.markPaid(context, b, paise) } }
                paying = null
            },
        )
    }
    editing?.let { b ->
        BillDialog(
            b,
            onDismiss = { editing = null },
            onSave = { scope.launch { finance.upsertBill(it) }; editing = null },
            onDelete = if (b.id == 0L) null else ({
                scope.launch {
                    finance.deleteBill(b)
                    undo.show("Deleted ${b.title}") { finance.upsertBill(b) }
                }
                editing = null
            }),
        )
    }
}

@Composable
private fun BillDialog(initial: Bill, onDismiss: () -> Unit, onSave: (Bill) -> Unit, onDelete: (() -> Unit)?) {
    var title by remember { mutableStateOf(initial.title) }
    var amount by remember { mutableStateOf(if (initial.amountPaise == 0L) "" else Money.input(initial.amountPaise)) }
    var category by remember { mutableStateOf(initial.category) }
    var cycle by remember { mutableStateOf(initial.cycle) }
    var dueDay by remember { mutableStateOf(initial.dueDay.toString()) }
    var dueMonth by remember { mutableStateOf(initial.dueMonth) }
    var subscription by remember { mutableStateOf(initial.isSubscription) }
    var remind by remember { mutableStateOf(initial.remindDaysBefore) }
    var active by remember { mutableStateOf(initial.active) }
    val paise = Money.parse(amount)
    val day = dueDay.toIntOrNull()?.takeIf { it in 1..31 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New bill" else "Edit bill") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it }, label = { Text("Name (Rent, Electricity, Netflix…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    amount, { amount = it }, label = { Text("Amount (₹)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.height(8.dp))
                Choice("Category", Categories.expense, category, { category = it }, Modifier.fillMaxWidth())
                FlowRow(Modifier.padding(top = 8.dp)) {
                    FilterChip(cycle == BillCycle.MONTHLY, { cycle = BillCycle.MONTHLY }, { Text("Monthly") }, Modifier.padding(end = 6.dp))
                    FilterChip(cycle == BillCycle.YEARLY, { cycle = BillCycle.YEARLY }, { Text("Yearly") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        dueDay, { dueDay = it.filter(Char::isDigit).take(2) }, label = { Text("Due day (1–31)") }, singleLine = true,
                        modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    if (cycle == BillCycle.YEARLY) {
                        Spacer(Modifier.padding(4.dp))
                        Choice("Month", (1..12).toList(), dueMonth, { dueMonth = it }, Modifier.weight(1f), display = { monthNames[it - 1] })
                    }
                }
                Text("Remind me", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                FlowRow {
                    listOf(0 to "On the day", 1 to "1 day before", 3 to "3 days before", 7 to "A week before").forEach { (d, label) ->
                        FilterChip(remind == d, { remind = d }, { Text(label) }, Modifier.padding(end = 4.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Subscription", Modifier.weight(1f))
                    Switch(subscription, { subscription = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Active", Modifier.weight(1f))
                    Switch(active, { active = it })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && paise != null && day != null, onClick = {
                onSave(
                    initial.copy(
                        title = title.trim(), amountPaise = paise!!, category = category, cycle = cycle, dueDay = day!!,
                        dueMonth = dueMonth, isSubscription = subscription, remindDaysBefore = remind, active = active,
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
