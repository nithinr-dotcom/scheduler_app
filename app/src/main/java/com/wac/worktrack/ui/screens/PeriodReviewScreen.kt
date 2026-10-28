@file:OptIn(ExperimentalMaterial3Api::class)

package com.wac.worktrack.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.wac.worktrack.core.Habits
import com.wac.worktrack.core.Money
import com.wac.worktrack.core.db
import com.wac.worktrack.data.LearnStatus
import com.wac.worktrack.data.PeriodReview
import com.wac.worktrack.data.TxnType
import com.wac.worktrack.ui.BackTopBar
import com.wac.worktrack.ui.Bad
import com.wac.worktrack.ui.Good
import com.wac.worktrack.ui.Hint
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.ProgressRow
import com.wac.worktrack.ui.SectionTitle
import com.wac.worktrack.ui.StatBox
import com.wac.worktrack.ui.Stepper
import com.wac.worktrack.ui.scoreColor
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Month or year in review: the numbers, then two reflection questions. */
@Composable
fun PeriodReviewScreen(nav: NavController, yearly: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalToday.current
    val db = context.db

    // Opens on the period that just ended in its first week (when the reminder comes), else the current one.
    var anchor by remember {
        val d = Day.date(today)
        mutableLongStateOf(
            when {
                yearly && d.dayOfYear <= 7 -> d.minusYears(1).toEpochDay()
                !yearly && d.dayOfMonth <= 7 -> d.minusMonths(1).toEpochDay()
                else -> today
            }
        )
    }
    val date = Day.date(anchor)
    val from: Long
    val to: Long
    val key: String
    val prevKey: String
    val label: String
    if (yearly) {
        from = LocalDate.of(date.year, 1, 1).toEpochDay()
        to = LocalDate.of(date.year, 12, 31).toEpochDay()
        key = date.year.toString()
        prevKey = (date.year - 1).toString()
        label = key
    } else {
        val ym = YearMonth.from(date)
        from = ym.atDay(1).toEpochDay()
        to = ym.atEndOfMonth().toEpochDay()
        key = ym.toString()
        prevKey = ym.minusMonths(1).toString()
        label = date.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    }
    val until = minOf(to, today)

    val aggs by remember(from) { db.tasks().aggs(from, to) }.collectAsStateWithLifecycle(emptyList())
    val habits by remember { db.habits().active() }.collectAsStateWithLifecycle(emptyList())
    val logs by remember(from) { db.habits().logs(from, to) }.collectAsStateWithLifecycle(emptyList())
    val income by remember(from) { db.finance().total(TxnType.INCOME, from, to) }.collectAsStateWithLifecycle(0L)
    val spent by remember(from) { db.finance().spending(from, to) }.collectAsStateWithLifecycle(0L)
    val byCategory by remember(from) { db.finance().spendingByCategory(from, to) }.collectAsStateWithLifecycle(emptyList())
    val focusMinutes by remember(from) { db.life().focusMinutes(from, to) }.collectAsStateWithLifecycle(0)
    val journal by remember(from) { db.life().journalRange(from, to) }.collectAsStateWithLifecycle(emptyList())
    val learning by remember { db.life().learning() }.collectAsStateWithLifecycle(emptyList())
    val review by remember(key) { db.reviews().period(key) }.collectAsStateWithLifecycle(null)
    val previous by remember(prevKey) { db.reviews().period(prevKey) }.collectAsStateWithLifecycle(null)

    var wentWell by remember(key) { mutableStateOf("") }
    var improve by remember(key) { mutableStateOf("") }
    var loaded by remember(key) { mutableStateOf(false) }
    LaunchedEffect(review) {
        if (!loaded && review != null) {
            wentWell = review!!.wentWell
            improve = review!!.improve
            loaded = true
        }
    }

    val scored = aggs.mapNotNull { it.score }
    val avg = if (scored.isEmpty()) null else scored.average().toInt()
    val best = aggs.filter { it.score != null }.maxByOrNull { it.score!! }
    val moods = journal.mapNotNull { it.mood }
    val finished = learning.filter { it.status == LearnStatus.DONE && (it.finishedDate ?: -1) in from..to }
    val period = if (yearly) "year" else "month"

    Scaffold(topBar = { BackTopBar(if (yearly) "Year in review" else "Month in review", { nav.popBackStack() }) }) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Stepper(
                    label,
                    onPrev = { anchor = if (yearly) date.minusYears(1).toEpochDay() else date.minusMonths(1).toEpochDay() },
                    onNext = if (to >= today) null else ({ anchor = if (yearly) date.plusYears(1).toEpochDay() else date.plusMonths(1).toEpochDay() }),
                )
            }
            previous?.improve?.takeIf { it.isNotBlank() }?.let { focus ->
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Your focus for this $period", style = MaterialTheme.typography.labelLarge)
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
                        StatBox("Days ≥80%", "${scored.count { it >= 80 }}", Modifier.weight(1f))
                    }
                }
            }
            item {
                SectionTitle("Highlights")
                if (best == null) Hint("No tasks tracked this $period.") else Hint("Best day: ${Day.label(best.date, today)} (${best.score}%)")
                if (focusMinutes > 0) Hint("Focus time: ${minutesLabel(focusMinutes)}")
                if (moods.isNotEmpty()) Hint("Average mood: ${com.wac.worktrack.ui.screens.moods[(moods.average().toInt() - 1).coerceIn(0, 4)]} over ${moods.size} days")
                if (journal.any { it.text.isNotBlank() }) Hint("Journal entries: ${journal.count { it.text.isNotBlank() }}")
                if (finished.isNotEmpty()) Hint("Finished: ${finished.joinToString { it.title }}")
            }
            if (habits.isNotEmpty() && until >= from) {
                item {
                    SectionTitle("Habits")
                    habits.forEach { h ->
                        val start = maxOf(from, h.createdDate)
                        val days = (until - start + 1).toInt()
                        if (days > 0) {
                            val n = Habits.doneDays(h, logs).count { it in start..until }
                            ProgressRow(h.title, "$n / $days days", n.toFloat() / days)
                        }
                    }
                }
            }
            item {
                SectionTitle("Money")
                Row(Modifier.padding(horizontal = 16.dp)) {
                    StatBox("Income", Money.format(income), Modifier.weight(1f), Good)
                    StatBox("Spent", Money.format(spent), Modifier.weight(1f), Bad)
                    StatBox(if (income >= spent) "Saved" else "Over", Money.format(kotlin.math.abs(income - spent)), Modifier.weight(1f))
                }
                if (income > 0 && income >= spent) Hint("Savings rate: ${(income - spent) * 100 / income}%")
                byCategory.take(3).forEach { ProgressRow(it.category, Money.format(it.totalPaise), if (spent == 0L) 0f else it.totalPaise.toFloat() / spent) }
            }
            item {
                SectionTitle("Reflect")
                Column(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(wentWell, { wentWell = it }, label = { Text("What went well this $period?") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(improve, { improve = it }, label = { Text("Focus for next $period") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { scope.launch { db.reviews().upsertPeriod(PeriodReview(key, wentWell.trim(), improve.trim())) } }) {
                        Text(if (review == null) "Save review" else "Update review")
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
