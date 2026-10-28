@file:OptIn(ExperimentalMaterial3Api::class)

package com.wac.worktrack.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.Money
import com.wac.worktrack.core.db
import com.wac.worktrack.data.TxnType
import com.wac.worktrack.data.WeeklyReview
import com.wac.worktrack.ui.Bad
import com.wac.worktrack.ui.Good
import com.wac.worktrack.ui.Hint
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.SectionTitle
import com.wac.worktrack.ui.StatBox
import com.wac.worktrack.ui.Stepper
import com.wac.worktrack.ui.scoreColor
import kotlinx.coroutines.launch

@Composable
fun WeeklyReviewScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalToday.current
    val db = context.db

    var week by remember { mutableLongStateOf(Day.weekStart(today)) }
    val end = week + 6
    val aggs by remember(week) { db.tasks().aggs(week, end) }.collectAsStateWithLifecycle(emptyList())
    val habits by remember { db.habits().active() }.collectAsStateWithLifecycle(emptyList())
    val logs by remember(week) { db.habits().logs(week, end) }.collectAsStateWithLifecycle(emptyList())
    val spent by remember(week) { db.finance().spending(week, end) }.collectAsStateWithLifecycle(0L)
    val spentBefore by remember(week) { db.finance().spending(week - 7, week - 1) }.collectAsStateWithLifecycle(0L)
    val review by remember(week) { db.reviews().get(week) }.collectAsStateWithLifecycle(null)
    val lastReview by remember(week) { db.reviews().get(week - 7) }.collectAsStateWithLifecycle(null)

    var wentWell by remember(week) { mutableStateOf("") }
    var improve by remember(week) { mutableStateOf("") }
    var loaded by remember(week) { mutableStateOf(false) }
    LaunchedEffect(review) {
        if (!loaded && review != null) {
            wentWell = review!!.wentWell
            improve = review!!.improve
            loaded = true
        }
    }

    val scored = aggs.filter { it.score != null }
    val avg = if (scored.isEmpty()) null else scored.map { it.score!! }.average().toInt()
    val best = scored.maxByOrNull { it.score!! }
    val worst = scored.minByOrNull { it.score!! }
    val daysSoFar = (minOf(end, today) - week + 1).toInt().coerceAtLeast(0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weekly review") },
                actions = {
                    androidx.compose.material3.TextButton(onClick = { nav.navigate("review/month") }) { Text("Month") }
                    androidx.compose.material3.TextButton(onClick = { nav.navigate("review/year") }) { Text("Year") }
                },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Stepper(
                    "${Day.short(week)} – ${Day.short(end)}",
                    onPrev = { week -= 7 },
                    onNext = if (end >= today) null else ({ week += 7 }),
                )
            }
            lastReview?.improve?.takeIf { it.isNotBlank() }?.let { focus ->
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Your focus for this week", style = MaterialTheme.typography.labelLarge)
                            Text(focus, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(Modifier.padding(vertical = 16.dp)) {
                        StatBox("Avg score", avg?.let { "$it%" } ?: "–", Modifier.weight(1f), scoreColor(avg))
                        StatBox("Done", "${aggs.sumOf { it.done }}", Modifier.weight(1f), Good)
                        StatBox("Missed", "${aggs.sumOf { it.missed }}", Modifier.weight(1f), Bad)
                        StatBox("Skipped", "${aggs.sumOf { it.skipped }}", Modifier.weight(1f))
                    }
                }
            }
            item {
                SectionTitle("Highlights")
                if (best == null) {
                    Hint("No tasks tracked this week yet.")
                } else {
                    Hint("Best day: ${Day.label(best.date, today)} (${best.score}%)")
                    if (worst != null && worst.date != best.date) Hint("Hardest day: ${Day.label(worst.date, today)} (${worst.score}%)")
                }
                if (habits.isNotEmpty() && daysSoFar > 0) {
                    val done = habits.sumOf { com.wac.worktrack.core.Habits.doneDays(it, logs).size }
                    Hint("Habits: $done of ${habits.size * daysSoFar} check-ins (${done * 100 / (habits.size * daysSoFar)}%)")
                }
                val diff = spent - spentBefore
                Hint(
                    "Spent ${Money.format(spent)}" +
                        if (spentBefore > 0) " · ${if (diff >= 0) "▲" else "▼"} ${Money.format(kotlin.math.abs(diff))} vs last week" else "",
                )
            }
            item {
                SectionTitle("Reflect")
                Column(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        wentWell, { wentWell = it }, label = { Text("What went well?") },
                        modifier = Modifier.fillMaxWidth(), minLines = 2,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        improve, { improve = it }, label = { Text("One thing to improve next week") },
                        modifier = Modifier.fillMaxWidth(), minLines = 2,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        scope.launch { db.reviews().upsert(WeeklyReview(week, wentWell.trim(), improve.trim())) }
                    }) { Text(if (review == null) "Save review" else "Update review") }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
