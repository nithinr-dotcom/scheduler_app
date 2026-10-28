@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wac.worktrack.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.wac.worktrack.core.Categories
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.Money
import com.wac.worktrack.core.Prefs
import com.wac.worktrack.core.db
import com.wac.worktrack.core.millisTimeLabel
import com.wac.worktrack.data.Budget
import com.wac.worktrack.data.Goal
import com.wac.worktrack.data.GoalType
import com.wac.worktrack.data.RecurringTxn
import com.wac.worktrack.data.Txn
import com.wac.worktrack.data.TxnSource
import com.wac.worktrack.data.TxnStatus
import com.wac.worktrack.data.TxnType
import com.wac.worktrack.core.Bills
import com.wac.worktrack.finance.Capture
import com.wac.worktrack.finance.Export
import com.wac.worktrack.finance.MoneyOps
import com.wac.worktrack.ui.AmountDialog
import com.wac.worktrack.ui.Bad
import com.wac.worktrack.ui.Choice
import com.wac.worktrack.ui.DatePickerDialogFor
import com.wac.worktrack.ui.Good
import com.wac.worktrack.ui.Hint
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.ProgressRow
import com.wac.worktrack.ui.SectionTitle
import com.wac.worktrack.ui.StatBox
import com.wac.worktrack.ui.Stepper
import com.wac.worktrack.ui.Warn
import com.wac.worktrack.ui.rememberUndo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

/** Day of the month from which the month-end forecast is shown. */
private const val FORECAST_FROM_DAY = 7

private val tabTitles = listOf("Overview", "Transactions", "Pending", "Budgets", "Recurring")

/** Budget bar colour: warn from 80% of the limit, red once over it. */
fun budgetColor(fraction: Float) = when {
    fraction >= 1f -> Bad
    fraction >= 0.8f -> Warn
    else -> Good
}

@Composable
fun FinanceScreen(nav: NavController, initialTab: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val undo = rememberUndo()
    val today = LocalToday.current
    val finance = context.db.finance()

    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var anchor by remember { mutableLongStateOf(today) } // any day inside the shown month
    val month = Day.monthRange(anchor)

    // Lists are null until their first query returns; a tab waits for its data, so its
    // "nothing here" hint doesn't flash while loading.
    val acceptedOrNull by remember(month) { finance.accepted(month.first, month.last) }.collectAsStateWithLifecycle(null)
    val accepted = acceptedOrNull.orEmpty()
    val pendingOrNull by remember { finance.pending() }.collectAsStateWithLifecycle(null)
    val pending = pendingOrNull.orEmpty()
    val rejected by remember { finance.rejected() }.collectAsStateWithLifecycle(emptyList())
    val twins by produceState(emptyMap<Long, String>(), pending) {
        value = withContext(Dispatchers.IO) { pending.mapNotNull { t -> Capture.recordedTwin(context, t)?.let { t.id to it } }.toMap() }
    }
    var showRejected by rememberSaveable { mutableStateOf(false) }
    val prefs = remember { Prefs(context) }
    val emergency by remember { context.db.goals().emergency() }.collectAsStateWithLifecycle(null)
    val savedPerGoal by remember { finance.savedPerGoal() }.collectAsStateWithLifecycle(emptyList())
    val monthlyCost by produceState(0L, today, accepted) { value = withContext(Dispatchers.IO) { MoneyOps.monthlyLivingCost(context, today) } }
    val budgets by remember { finance.budgets() }.collectAsStateWithLifecycle(emptyList())
    val recurringOrNull by remember { finance.recurring() }.collectAsStateWithLifecycle(null)
    val recurring = recurringOrNull.orEmpty()
    val goals by remember { context.db.goals().active() }.collectAsStateWithLifecycle(emptyList())
    val moneyGoals = goals.filter { it.type == GoalType.MONEY }

    var editingTxn by remember { mutableStateOf<Txn?>(null) }
    var reviewing by remember { mutableStateOf<Txn?>(null) }
    var editingBudget by remember { mutableStateOf<String?>(null) }
    var editingRecurring by remember { mutableStateOf<RecurringTxn?>(null) }
    var menu by remember { mutableStateOf(false) }
    val bills by remember { finance.bills() }.collectAsStateWithLifecycle(emptyList())
    val monthLabel = Day.date(anchor).format(DateTimeFormatter.ofPattern("MMMM yyyy"))

    // Month-end forecast: only meaningful for the month we're in.
    val forecast = if (today in month) {
        val date = Day.date(today)
        val spentSoFar = accepted.filter { it.type == TxnType.EXPENSE && it.category != Categories.SAVINGS }.sumOf { it.amountPaise }
        val unpaid = bills.filter { b ->
            val due = Bills.nextDue(b, today)
            b.active && due in month && due >= today && b.lastPaidPeriod != Bills.periodOf(b, due)
        }.sumOf { it.amountPaise }
        // The first week is too little to go on: one big purchase on the 2nd isn't a monthly pace.
        if (date.dayOfMonth < FORECAST_FROM_DAY) null else MoneyOps.forecast(spentSoFar, date.dayOfMonth, date.lengthOfMonth(), unpaid)
    } else {
        null
    }

    fun exported(result: Result<Int>) = Toast.makeText(
        context,
        result.fold({ "Exported $it entries." }, { "Export failed: ${it.message}" }),
        Toast.LENGTH_LONG,
    ).show()

    val csvFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { exported(runCatching { Export.csv(context, month, uri) }) }
    }
    val pdfFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch { exported(runCatching { Export.pdf(context, month, monthLabel, uri) }) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Finance") },
                actions = {
                    TextButton(onClick = { nav.navigate("bills") }) { Text("Bills") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Bills & subscriptions") }, onClick = { menu = false; nav.navigate("bills") })
                            DropdownMenuItem(text = { Text("Lent & borrowed") }, onClick = { menu = false; nav.navigate("debts") })
                            DropdownMenuItem(text = { Text("Net worth") }, onClick = { menu = false; nav.navigate("networth") })
                            DropdownMenuItem(text = { Text("Export $monthLabel as CSV") }, onClick = {
                                menu = false
                                csvFile.launch("worktrack-${Day.date(anchor).format(DateTimeFormatter.ofPattern("yyyy-MM"))}.csv")
                            })
                            DropdownMenuItem(text = { Text("Export $monthLabel as PDF") }, onClick = {
                                menu = false
                                pdfFile.launch("worktrack-${Day.date(anchor).format(DateTimeFormatter.ofPattern("yyyy-MM"))}.pdf")
                            })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            when (tab) {
                0, 1 -> FloatingActionButton(onClick = {
                    editingTxn = Txn(
                        amountPaise = 0, type = TxnType.EXPENSE, category = Categories.expense.first(),
                        date = today, timeMillis = System.currentTimeMillis(),
                    )
                }) { Icon(Icons.Default.Add, "Add entry") }
                4 -> FloatingActionButton(onClick = {
                    editingRecurring = RecurringTxn(title = "", amountPaise = 0, type = TxnType.EXPENSE, category = "Bills", dayOfMonth = 1)
                }) { Icon(Icons.Default.Add, "Add recurring") }
                else -> {}
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                tabTitles.forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = {
                        if (i == 2 && pending.isNotEmpty()) {
                            BadgedBox(badge = { Badge { Text("${pending.size}") } }) { Text(title, Modifier.padding(end = 8.dp)) }
                        } else {
                            Text(title)
                        }
                    })
                }
            }
            if (tab != 2 && tab != 4) {
                Stepper(
                    monthLabel,
                    onPrev = { anchor = month.first - 1 },
                    onNext = if (month.last >= today) null else ({ anchor = month.last + 1 }),
                )
            }
            LazyColumn(Modifier.weight(1f)) {
                when (tab) {
                    0 -> {
                        emergencyFund(
                            goal = emergency,
                            saved = emergency?.let { g -> savedPerGoal.firstOrNull { it.goalId == g.id }?.totalPaise } ?: 0,
                            monthlyCost = monthlyCost,
                            months = prefs.emergencyMonths,
                            onOpen = { nav.navigate("goals") },
                            onCreate = {
                                scope.launch {
                                    // Round the target up to the next ₹1,000.
                                    val target = (monthlyCost * prefs.emergencyMonths + 99_999) / 100_000 * 100_000
                                    context.db.goals().upsert(
                                        Goal(title = "Emergency fund", type = GoalType.MONEY, target = target, createdDate = today, isEmergency = true)
                                    )
                                }
                            },
                        )
                        if (acceptedOrNull != null) overview(accepted, budgets, pending.size, forecast) { tab = 2 }
                    }
                    1 -> if (acceptedOrNull != null) transactions(accepted, today) { editingTxn = it }
                    2 -> if (pendingOrNull != null) pendingInbox(
                        pending, today,
                        onAccept = { scope.launch { Capture.accept(context, it) } },
                        onReject = { t ->
                            scope.launch {
                                Capture.reject(context, t)
                                undo.show("Rejected ${Money.format(t.amountPaise)}") { Capture.restore(context, t) }
                            }
                        },
                        onEdit = { reviewing = it },
                        twins = twins,
                        rejected = rejected,
                        showRejected = showRejected,
                        onToggleRejected = { showRejected = !showRejected },
                        onRestore = { scope.launch { Capture.restore(context, it) } },
                    )
                    3 -> budgetList(budgets, accepted) { editingBudget = it }
                    4 -> if (recurringOrNull != null) recurringList(recurring) { editingRecurring = it }
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }

    editingTxn?.let { t ->
        TxnDialog(
            initial = t, today = today, goals = moneyGoals, title = if (t.id == 0L) "New entry" else "Edit entry",
            confirmLabel = "Save",
            onDismiss = { editingTxn = null },
            onSave = {
                scope.launch {
                    val id = finance.upsert(it)
                    MoneyOps.onIncomeAccepted(context, if (it.id == 0L) it.copy(id = id) else it)
                }
                editingTxn = null
            },
            onDelete = if (t.id == 0L) null else ({
                scope.launch {
                    finance.delete(t)
                    undo.show("Deleted ${Money.format(t.amountPaise)} entry") { finance.upsert(t) }
                }
                editingTxn = null
            }),
        )
    }
    reviewing?.let { t ->
        TxnDialog(
            initial = t, today = today, goals = moneyGoals, title = "Review payment", confirmLabel = "Accept",
            onDismiss = { reviewing = null },
            onSave = { scope.launch { Capture.accept(context, it) }; reviewing = null },
            onDelete = {
                scope.launch {
                    Capture.reject(context, t)
                    undo.show("Rejected ${Money.format(t.amountPaise)}") { Capture.restore(context, t) }
                }
                reviewing = null
            },
            deleteLabel = "Reject",
        )
    }
    editingBudget?.let { category ->
        val existing = budgets.firstOrNull { it.category == category }
        AmountDialog(
            title = "Monthly budget · $category",
            initial = existing?.let { Money.input(it.limitPaise) } ?: "",
            onDismiss = { editingBudget = null },
            onSave = { text ->
                Money.parse(text)?.let { scope.launch { finance.upsertBudget(Budget(category, it)) } }
                editingBudget = null
            },
            extraButton = if (existing == null) null else ({
                TextButton(onClick = { scope.launch { finance.deleteBudget(existing) }; editingBudget = null }) { Text("Remove") }
            }),
        )
    }
    editingRecurring?.let { r ->
        RecurringDialog(
            initial = r,
            onDismiss = { editingRecurring = null },
            onSave = { scope.launch { finance.upsertRecurring(it) }; editingRecurring = null },
            onDelete = if (r.id == 0L) null else ({
                scope.launch {
                    finance.deleteRecurring(r)
                    undo.show("Deleted ${r.title}") { finance.upsertRecurring(r) }
                }
                editingRecurring = null
            }),
        )
    }
}

private fun LazyListScope.overview(accepted: List<Txn>, budgets: List<Budget>, pendingCount: Int, forecast: Long?, openPending: () -> Unit) {
    val income = accepted.filter { it.type == TxnType.INCOME }.sumOf { it.amountPaise }
    // Money moved into savings goals is kept, not spent: it's part of "Saved", not "Spent".
    val toGoals = accepted.filter { it.type == TxnType.EXPENSE && it.category == Categories.SAVINGS }.sumOf { it.amountPaise }
    val expenses = accepted.filter { it.type == TxnType.EXPENSE && it.category != Categories.SAVINGS }
    val spent = expenses.sumOf { it.amountPaise }
    val saved = income - spent
    item {
        Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.padding(vertical = 16.dp)) {
                StatBox("Income", Money.format(income), Modifier.weight(1f), Good)
                StatBox("Spent", Money.format(spent), Modifier.weight(1f), Bad)
                StatBox(if (saved >= 0) "Saved" else "Over", Money.format(kotlin.math.abs(saved)), Modifier.weight(1f))
            }
        }
    }
    if (toGoals > 0) {
        item {
            Text(
                "${Money.format(toGoals)} of it moved into savings goals.",
                Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (forecast != null && forecast > spent) {
        item {
            val budgetTotal = budgets.sumOf { it.limitPaise }
            val warn = (income > 0 && forecast > income) || (budgetTotal > 0 && forecast > budgetTotal)
            Text(
                "📈 At this pace you'll spend about ${Money.format(forecast)} by month end (including bills still due)" +
                    when {
                        income > 0 && forecast > income -> " — more than this month's income."
                        budgetTotal > 0 && forecast > budgetTotal -> " — over your total budgets of ${Money.format(budgetTotal)}."
                        else -> "."
                    },
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium,
                color = if (warn) Bad else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (pendingCount > 0) {
        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = openPending),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Text(
                    "$pendingCount captured payments waiting for review — not counted until you accept them.",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    item { SectionTitle("Spending by category") }
    val byCategory = expenses.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amountPaise } }
        .toList().sortedByDescending { it.second }
    if (byCategory.isEmpty()) item { Hint("No expenses this month.") }
    items(byCategory, key = { it.first }) { (category, total) ->
        val limit = budgets.firstOrNull { it.category == category }?.limitPaise
        if (limit != null) {
            val f = total.toFloat() / limit
            ProgressRow(
                category + if (f >= 1f) " · over budget" else if (f >= 0.8f) " · near limit" else "",
                "${Money.format(total)} / ${Money.format(limit)}", f, budgetColor(f),
            )
        } else {
            ProgressRow(category, Money.format(total), if (spent == 0L) 0f else total.toFloat() / spent)
        }
    }
}

/** How many months of everyday spending the emergency fund covers, or a nudge to start one. */
private fun LazyListScope.emergencyFund(
    goal: Goal?,
    saved: Long,
    monthlyCost: Long,
    months: Int,
    onOpen: () -> Unit,
    onCreate: () -> Unit,
) {
    item {
        Card(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("🛟 Emergency fund", style = MaterialTheme.typography.titleMedium)
                if (goal == null) {
                    Text(
                        if (monthlyCost > 0) "A cushion of $months months of spending is about ${Money.format(monthlyCost * months)}. " +
                            "It turns a lost job or a hospital bill into an inconvenience, not a crisis."
                        else "Save $months months of everyday spending for surprises. Add some expenses first and the app works out the target.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (monthlyCost > 0) TextButton(onClick = onCreate) { Text("Start my emergency fund") }
                } else {
                    val covered = if (monthlyCost > 0) saved.toFloat() / monthlyCost else 0f
                    Text(
                        "${Money.format(saved)} of ${Money.format(goal.target)}" +
                            if (monthlyCost > 0) " · covers %.1f of $months months".format(covered) else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { if (goal.target > 0) (saved.toFloat() / goal.target).coerceIn(0f, 1f) else 0f },
                        color = if (saved >= goal.target) Good else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().height(6.dp), drawStopIndicator = {},
                    )
                    TextButton(onClick = onOpen) { Text("Add savings") }
                }
            }
        }
    }
}

private fun LazyListScope.transactions(accepted: List<Txn>, today: Long, onClick: (Txn) -> Unit) {
    if (accepted.isEmpty()) item { Hint("No entries this month. Tap + to add income or an expense.") }
    accepted.groupBy { it.date }.forEach { (day, list) ->
        item(key = "d$day") {
            Text(Day.label(day, today), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
        }
        items(list, key = { it.id }) { t -> TxnRow(t, onClick = { onClick(t) }) }
    }
}

@Composable
private fun TxnRow(t: Txn, onClick: () -> Unit, subtitle: String? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(t.note.ifBlank { t.merchant ?: t.category }, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle ?: listOf(t.category, t.method).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            (if (t.type == TxnType.INCOME) "+" else "−") + Money.format(t.amountPaise),
            color = if (t.type == TxnType.INCOME) Good else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun LazyListScope.pendingInbox(
    pending: List<Txn>,
    today: Long,
    onAccept: (Txn) -> Unit,
    onReject: (Txn) -> Unit,
    onEdit: (Txn) -> Unit,
    twins: Map<Long, String>,
    rejected: List<Txn>,
    showRejected: Boolean,
    onToggleRejected: () -> Unit,
    onRestore: (Txn) -> Unit,
) {
    item { Hint("Payments captured from bank SMS, payment apps and recurring entries. Nothing here counts until you accept it.") }
    if (pending.isEmpty()) item { Hint("All caught up. ✅") }
    items(pending, key = { it.id }) { t ->
        val source = when (t.source) {
            TxnSource.SMS -> "Bank SMS"
            TxnSource.NOTIFICATION -> "Payment app"
            TxnSource.RECURRING -> "Recurring"
            TxnSource.AUTO_SAVE -> "Auto-save"
            TxnSource.BILL -> "Bill"
            TxnSource.MANUAL -> "Manual"
        }
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            TxnRow(
                t, onClick = { onEdit(t) },
                subtitle = "${t.category} · $source · ${Day.label(t.date, today)} ${millisTimeLabel(t.timeMillis)}",
            )
            twins[t.id]?.let {
                Text(it, color = Warn, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            }
            Row(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                TextButton(onClick = { onReject(t) }) { Text("Reject", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { onEdit(t) }) { Text("Edit") }
                Spacer(Modifier.padding(4.dp))
                Button(onClick = { onAccept(t) }, enabled = t.category != Categories.UNCATEGORIZED) { Text("Accept") }
            }
        }
    }
    rejectedList(rejected, today, showRejected, onToggleRejected, onRestore)
}

private fun LazyListScope.rejectedList(rejected: List<Txn>, today: Long, show: Boolean, onToggle: () -> Unit, onRestore: (Txn) -> Unit) {
    if (rejected.isEmpty()) return
    item {
        SectionTitle("Rejected (${rejected.size})") { TextButton(onClick = onToggle) { Text(if (show) "Hide" else "Show") } }
    }
    if (!show) return
    items(rejected, key = { "r" + it.id }) { t ->
        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                TxnRow(t, onClick = { onRestore(t) }, subtitle = "${Day.label(t.date, today)} ${millisTimeLabel(t.timeMillis)} · ${t.merchant ?: t.method}")
            }
            TextButton(onClick = { onRestore(t) }) { Text("Restore") }
        }
    }
}

private fun LazyListScope.budgetList(budgets: List<Budget>, thisMonth: List<Txn>, onEdit: (String) -> Unit) {
    item { Hint("Monthly limit per category. You'll see a warning at 80% and when you go over.") }
    val spentBy = thisMonth.filter { it.type == TxnType.EXPENSE }.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amountPaise } }
    val categories = (Categories.expense + budgets.map { it.category }).distinct()
    items(categories, key = { it }) { category ->
        val limit = budgets.firstOrNull { it.category == category }?.limitPaise
        val spent = spentBy[category] ?: 0
        Column(Modifier.clickable { onEdit(category) }) {
            if (limit == null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(category, Modifier.weight(1f))
                    Text("Set limit", color = MaterialTheme.colorScheme.primary)
                }
            } else {
                val f = spent.toFloat() / limit
                ProgressRow(category, "${Money.format(spent)} / ${Money.format(limit)}", f, budgetColor(f))
            }
            HorizontalDivider()
        }
    }
}

private fun LazyListScope.recurringList(recurring: List<RecurringTxn>, onEdit: (RecurringTxn) -> Unit) {
    item { Hint("Salary, rent, subscriptions… Each month on its day it's added to Pending for you to confirm the amount.") }
    if (recurring.isEmpty()) item { Hint("No recurring entries yet.") }
    items(recurring, key = { it.id }) { r ->
        Row(
            Modifier.fillMaxWidth().clickable { onEdit(r) }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(r.title, color = if (r.active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
                Text(
                    "Day ${r.dayOfMonth} every month · ${r.category}" + if (!r.active) " · Paused" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text((if (r.type == TxnType.INCOME) "+" else "−") + Money.format(r.amountPaise), fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider()
    }
}

@Composable
private fun TxnDialog(
    initial: Txn,
    today: Long,
    goals: List<Goal>,
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onSave: (Txn) -> Unit,
    onDelete: (() -> Unit)?,
    deleteLabel: String = "Delete",
) {
    var amount by remember { mutableStateOf(if (initial.amountPaise == 0L) "" else Money.input(initial.amountPaise)) }
    var type by remember { mutableStateOf(initial.type) }
    var category by remember { mutableStateOf(initial.category) }
    var date by remember { mutableLongStateOf(initial.date) }
    var method by remember { mutableStateOf(initial.method) }
    var note by remember { mutableStateOf(initial.note) }
    var goalId by remember { mutableStateOf(initial.goalId) }
    var pickingDate by remember { mutableStateOf(false) }
    val paise = Money.parse(amount)
    val categories = (if (type == TxnType.INCOME) Categories.income else Categories.expense)
    val valid = paise != null && category != Categories.UNCATEGORIZED

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                initial.merchant?.let {
                    Text("Merchant: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    amount, { amount = it }, label = { Text("Amount (₹)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                FlowRow(Modifier.padding(top = 8.dp)) {
                    FilterChip(type == TxnType.EXPENSE, {
                        type = TxnType.EXPENSE
                        if (category !in Categories.expense) category = Categories.expense.first()
                    }, { Text("Expense") }, Modifier.padding(end = 6.dp))
                    FilterChip(type == TxnType.INCOME, {
                        type = TxnType.INCOME
                        if (category !in Categories.income) category = Categories.income.first()
                        goalId = null
                    }, { Text("Income") })
                }
                Choice("Category", categories, category, { category = it }, Modifier.fillMaxWidth())
                if (category == Categories.UNCATEGORIZED) {
                    Text("Pick a category to accept.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.weight(1f)) { Text(Day.label(date, today)) }
                    Spacer(Modifier.padding(4.dp))
                    Choice("Via", Categories.methods, method, { method = it }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (type == TxnType.EXPENSE && category == "Savings" && goals.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Choice(
                        "Goal", listOf<Goal?>(null) + goals, goals.firstOrNull { it.id == goalId }, { goalId = it?.id },
                        Modifier.fillMaxWidth(), display = { it?.title ?: "None" },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val moved = date != initial.date
                onSave(
                    initial.copy(
                        amountPaise = paise!!, type = type, category = category, date = date, method = method,
                        note = note.trim(), goalId = if (category == "Savings") goalId else null,
                        timeMillis = if (moved) Day.millisAt(date, Day.minutesOfMillis(initial.timeMillis)) else initial.timeMillis,
                        status = if (initial.status == TxnStatus.REJECTED) TxnStatus.ACCEPTED else initial.status,
                    )
                )
            }) { Text(confirmLabel) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(deleteLabel, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
    if (pickingDate) DatePickerDialogFor(date, { pickingDate = false }) { date = minOf(it, today); pickingDate = false }
}

@Composable
private fun RecurringDialog(initial: RecurringTxn, onDismiss: () -> Unit, onSave: (RecurringTxn) -> Unit, onDelete: (() -> Unit)?) {
    var title by remember { mutableStateOf(initial.title) }
    var amount by remember { mutableStateOf(if (initial.amountPaise == 0L) "" else Money.input(initial.amountPaise)) }
    var type by remember { mutableStateOf(initial.type) }
    var category by remember { mutableStateOf(initial.category) }
    var dayText by remember { mutableStateOf(initial.dayOfMonth.toString()) }
    var active by remember { mutableStateOf(initial.active) }
    val paise = Money.parse(amount)
    val dom = dayText.toIntOrNull()?.takeIf { it in 1..31 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New recurring entry" else "Edit recurring entry") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(title, { title = it }, label = { Text("Name (e.g. Rent, Netflix)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    amount, { amount = it }, label = { Text("Usual amount (₹)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                FlowRow(Modifier.padding(top = 8.dp)) {
                    FilterChip(type == TxnType.EXPENSE, { type = TxnType.EXPENSE; category = Categories.expense.first() }, { Text("Expense") }, Modifier.padding(end = 6.dp))
                    FilterChip(type == TxnType.INCOME, { type = TxnType.INCOME; category = Categories.income.first() }, { Text("Income") })
                }
                Choice(
                    "Category", (if (type == TxnType.INCOME) Categories.income else Categories.expense), category,
                    { category = it }, Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    dayText, { dayText = it }, label = { Text("Day of month (1–31)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Active", Modifier.weight(1f))
                    Switch(active, { active = it })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && paise != null && dom != null, onClick = {
                onSave(initial.copy(title = title.trim(), amountPaise = paise!!, type = type, category = category, dayOfMonth = dom!!, active = active))
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
