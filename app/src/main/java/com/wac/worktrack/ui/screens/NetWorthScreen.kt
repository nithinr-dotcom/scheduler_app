@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wac.worktrack.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.Money
import com.wac.worktrack.core.db
import com.wac.worktrack.data.Asset
import com.wac.worktrack.data.DebtDirection
import com.wac.worktrack.data.NetWorthSnapshot
import com.wac.worktrack.finance.MoneyOps
import com.wac.worktrack.ui.BackTopBar
import com.wac.worktrack.ui.Bad
import com.wac.worktrack.ui.Choice
import com.wac.worktrack.ui.Good
import com.wac.worktrack.ui.Hint
import com.wac.worktrack.ui.SectionTitle
import com.wac.worktrack.ui.StatBox
import com.wac.worktrack.ui.rememberUndo
import kotlinx.coroutines.launch

private val assetKinds = listOf("Bank account", "Cash", "Fixed deposit", "Mutual funds", "Stocks", "PF / PPF", "Gold", "Property", "Other")
private val liabilityKinds = listOf("Home loan", "Car loan", "Personal loan", "Education loan", "Credit card", "Other")

/** What you own minus what you owe. Values are updated by hand; a point is saved each month. */
@Composable
fun NetWorthScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val undo = rememberUndo()
    val finance = context.db.finance()
    val assets by remember { finance.assets() }.collectAsStateWithLifecycle(emptyList())
    val debts by remember { finance.debts() }.collectAsStateWithLifecycle(emptyList())
    val history by remember { finance.netWorthHistory() }.collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<Asset?>(null) }

    // Make sure this month has a point even if nothing changed since last month.
    LaunchedEffect(Unit) { MoneyOps.snapshotNetWorth(context) }

    val lent = debts.filter { it.direction == DebtDirection.LENT }.sumOf { it.outstanding }
    val borrowed = debts.filter { it.direction == DebtDirection.BORROWED }.sumOf { it.outstanding }
    val owned = assets.filter { !it.liability }.sumOf { it.valuePaise } + lent
    val owed = assets.filter { it.liability }.sumOf { it.valuePaise } + borrowed

    fun save(a: Asset) = scope.launch {
        finance.upsertAsset(a)
        MoneyOps.snapshotNetWorth(context)
    }

    Scaffold(
        topBar = { BackTopBar("Net worth", { nav.popBackStack() }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Asset(name = "", kind = assetKinds.first(), valuePaise = 0, updatedMillis = 0) }) {
                Icon(Icons.Default.Add, "Add")
            }
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Net worth", style = MaterialTheme.typography.labelLarge)
                        Text(
                            Money.format(owned - owed), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                            color = if (owned >= owed) Good else Bad,
                        )
                        Row(Modifier.padding(top = 8.dp)) {
                            StatBox("You own", Money.format(owned), Modifier.weight(1f), Good)
                            StatBox("You owe", Money.format(owed), Modifier.weight(1f), Bad)
                        }
                    }
                }
            }
            if (history.size >= 2) {
                item {
                    SectionTitle("Trend")
                    NetWorthChart(history)
                }
            }
            if (assets.isEmpty()) item { Hint("Add your accounts, investments and loans. Update the values now and then — a point is saved every month.") }
            listOf(false to "What you own", true to "What you owe").forEach { (liability, title) ->
                val group = assets.filter { it.liability == liability }
                if (group.isNotEmpty()) {
                    item { SectionTitle(title) }
                    items(group, key = { it.id }) { a ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(a.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${a.kind} · updated ${Day.label(Day.ofMillis(a.updatedMillis)).lowercase()}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(Money.format(a.valuePaise), fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { editing = a }) { Text("Update") }
                        }
                        HorizontalDivider()
                    }
                }
            }
            if (lent > 0 || borrowed > 0) {
                item {
                    SectionTitle("From Lent & borrowed") { TextButton(onClick = { nav.navigate("debts") }) { Text("Open") } }
                    if (lent > 0) Hint("Owed to you: ${Money.format(lent)}")
                    if (borrowed > 0) Hint("You owe: ${Money.format(borrowed)}")
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    editing?.let { a ->
        AssetDialog(
            a,
            onDismiss = { editing = null },
            onSave = { save(it.copy(updatedMillis = System.currentTimeMillis())); editing = null },
            onDelete = if (a.id == 0L) null else ({
                scope.launch {
                    finance.deleteAsset(a)
                    MoneyOps.snapshotNetWorth(context)
                    undo.show("Deleted ${a.name}") {
                        finance.upsertAsset(a)
                        MoneyOps.snapshotNetWorth(context)
                    }
                }
                editing = null
            }),
        )
    }
}

@Composable
private fun NetWorthChart(history: List<NetWorthSnapshot>) {
    val points = history.takeLast(24).map { it.assetsPaise - it.liabilitiesPaise }
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.padding(horizontal = 16.dp)) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val min = minOf(points.min(), 0L).toFloat()
            val max = maxOf(points.max(), 1L).toFloat()
            val range = (max - min).coerceAtLeast(1f)
            fun y(v: Long) = size.height - (v - min) / range * size.height
            val step = size.width / (points.size - 1)
            drawLine(track, Offset(0f, y(0)), Offset(size.width, y(0)))
            for (i in 1 until points.size) {
                drawLine(color, Offset((i - 1) * step, y(points[i - 1])), Offset(i * step, y(points[i])), strokeWidth = 3.dp.toPx())
            }
            points.forEachIndexed { i, v -> drawCircle(color, 3.dp.toPx(), Offset(i * step, y(v)), style = Stroke(2.dp.toPx())) }
        }
        Row {
            Text(history.takeLast(24).first().month, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            Text(history.last().month, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AssetDialog(initial: Asset, onDismiss: () -> Unit, onSave: (Asset) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(initial.name) }
    var liability by remember { mutableStateOf(initial.liability) }
    var kind by remember { mutableStateOf(initial.kind) }
    var value by remember { mutableStateOf(if (initial.valuePaise == 0L) "" else Money.input(initial.valuePaise)) }
    val paise = if (value.trim() == "0") 0L else Money.parse(value)
    val kinds = if (liability) liabilityKinds else assetKinds

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "Add" else "Update ${initial.name}") },
        text = {
            Column {
                FlowRow {
                    FilterChip(!liability, { liability = false; kind = assetKinds.first() }, { Text("I own") }, Modifier.padding(end = 6.dp))
                    FilterChip(liability, { liability = true; kind = liabilityKinds.first() }, { Text("I owe") })
                }
                OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. HDFC savings)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Choice("Type", (kinds + kind).distinct(), kind, { kind = it }, Modifier.fillMaxWidth())
                OutlinedTextField(
                    value, { value = it }, label = { Text(if (liability) "Amount still owed (₹)" else "Current value (₹)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && paise != null, onClick = {
                onSave(initial.copy(name = name.trim(), kind = kind, liability = liability, valuePaise = paise!!))
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
