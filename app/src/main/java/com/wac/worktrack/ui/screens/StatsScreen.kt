@file:OptIn(ExperimentalMaterial3Api::class)

package com.wac.worktrack.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.material3.TextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.wac.worktrack.core.Badges
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.Habits
import com.wac.worktrack.core.Money
import com.wac.worktrack.core.Scores
import com.wac.worktrack.core.db
import com.wac.worktrack.data.DayAgg
import com.wac.worktrack.data.TxnType
import com.wac.worktrack.ui.Bad
import com.wac.worktrack.ui.Good
import com.wac.worktrack.ui.Hint
import com.wac.worktrack.ui.LocalResumeTick
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.ProgressRow
import com.wac.worktrack.ui.ScoreRing
import com.wac.worktrack.ui.SectionTitle
import com.wac.worktrack.ui.StatBox
import com.wac.worktrack.ui.scoreColor

private const val HEATMAP_WEEKS = 18

@Composable
fun StatsScreen(nav: NavController) {
    val context = LocalContext.current
    val today = LocalToday.current
    val db = context.db
    val month = Day.monthRange(today)
    val week = Day.weekStart(today)

    val aggs by remember(today) { db.tasks().aggs(today - 400, today).distinctUntilChanged() }.collectAsStateWithLifecycle(emptyList())
    val habits by remember { db.habits().active() }.collectAsStateWithLifecycle(emptyList())
    val weekLogs by remember(week) { db.habits().logs(week, week + 6) }.collectAsStateWithLifecycle(emptyList())
    val goals by remember { db.goals().active() }.collectAsStateWithLifecycle(emptyList())
    val savedList by remember { db.finance().savedPerGoal() }.collectAsStateWithLifecycle(emptyList())
    val income by remember(month) { db.finance().total(TxnType.INCOME, month.first, month.last) }.collectAsStateWithLifecycle(0L)
    val spent by remember(month) { db.finance().spending(month.first, month.last) }.collectAsStateWithLifecycle(0L)
    val byCategory by remember(month) { db.finance().spendingByCategory(month.first, month.last) }.collectAsStateWithLifecycle(emptyList())
    val budgets by remember { db.finance().budgets() }.collectAsStateWithLifecycle(emptyList())
    val focusToday by remember(today) { db.life().focusMinutes(today, today) }.collectAsStateWithLifecycle(0)
    val focusWeek by remember(today) { db.life().focusMinutes(today - 6, today) }.collectAsStateWithLifecycle(0)
    val estimates by remember(today) { db.tasks().estimateAccuracy(today - 29, today) }
        .collectAsStateWithLifecycle(com.wac.worktrack.data.EstimateAgg(0, 0, 0))
    val journal by remember(today) { db.life().journalRange(today - 89, today) }.collectAsStateWithLifecycle(emptyList())
    val tick = LocalResumeTick.current
    val life by produceState<Int?>(null, today, tick, aggs) { value = withContext(Dispatchers.IO) { Badges.lifeScore(context, today) } }

    val byDate = remember(aggs) { aggs.associateBy { it.date } }
    val rest = com.wac.worktrack.ui.rememberRestDays(today - 400, today)
    val streaks = remember(aggs, today, rest) { Scores.streaks(aggs, today, rest) }
    val todayAgg = byDate[today]
    val saved = savedList.associate { it.goalId to it.totalPaise }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stats") },
                actions = { TextButton(onClick = { nav.navigate("achievements") }) { Text("🏅 Badges") } },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Card(onClick = { nav.navigate("achievements") }, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        ScoreRing(life, size = 64.dp, stroke = 7.dp)
                        Spacer(Modifier.padding(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Life score", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Tasks, habits, budgets and journal over the last 7 days",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        ScoreRing(todayAgg?.score)
                        Spacer(Modifier.padding(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Today", style = MaterialTheme.typography.titleMedium)
                            Row {
                                StatBox("Done", "${todayAgg?.done ?: 0}", Modifier.weight(1f), Good)
                                StatBox("Missed", "${todayAgg?.missed ?: 0}", Modifier.weight(1f), Bad)
                                StatBox("Skipped", "${todayAgg?.skipped ?: 0}", Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            item {
                Row(Modifier.padding(horizontal = 16.dp)) {
                    Card(Modifier.weight(1f).padding(end = 6.dp)) {
                        StatBox("Current streak", "🔥 ${streaks.current}", Modifier.fillMaxWidth().padding(12.dp))
                    }
                    Card(Modifier.weight(1f).padding(start = 6.dp)) {
                        StatBox("Best streak", "🏆 ${streaks.best}", Modifier.fillMaxWidth().padding(12.dp))
                    }
                }
                Hint(
                    "A streak day is ≥ ${Scores.STREAK_THRESHOLD}% done. Top 3 tasks count double. " +
                        "Streak freezes saved: ❄️ ${streaks.freezes} (one per ${Scores.DAYS_PER_FREEZE} good days, covers a missed day).",
                )
            }

            item {
                SectionTitle("Last 7 days")
                ScoreBars((today - 6..today).map { it to byDate[it]?.score }, labels = true)
            }
            item {
                val last30 = (today - 29..today).mapNotNull { byDate[it]?.score }
                SectionTitle("Last 30 days · avg ${if (last30.isEmpty()) "–" else "${last30.average().toInt()}%"}")
                ScoreBars((today - 29..today).map { it to byDate[it]?.score }, labels = false)
            }
            item {
                SectionTitle("Consistency · last $HEATMAP_WEEKS weeks")
                Heatmap(byDate, today)
            }

            if (habits.isNotEmpty()) {
                item {
                    val daysSoFar = (today - week + 1).toInt()
                    val possible = habits.size * daysSoFar
                    var total = 0
                    SectionTitle("Habits this week")
                    habits.forEach { h ->
                        val n = Habits.doneDays(h, weekLogs).size
                        total += n
                        ProgressRow(h.title, "$n / $daysSoFar", n.toFloat() / daysSoFar)
                    }
                    if (possible > 0) Hint("Overall ${total * 100 / possible}% of habit check-ins this week.")
                }
            }

            item {
                SectionTitle("Time") { TextButton(onClick = { nav.navigate("focus") }) { Text("Focus") } }
                Row(Modifier.padding(horizontal = 16.dp)) {
                    StatBox("Focus today", minutesLabel(focusToday), Modifier.weight(1f))
                    StatBox("Focus 7 days", minutesLabel(focusWeek), Modifier.weight(1f))
                }
                if (estimates.tasks > 0 && estimates.estimated > 0) {
                    val ratio = estimates.actual * 100 / estimates.estimated
                    Hint(
                        "Estimate vs actual (30 days, ${estimates.tasks} tasks): planned ${minutesLabel(estimates.estimated)}, " +
                            "took ${minutesLabel(estimates.actual)} — " +
                            when {
                                ratio > 115 -> "tasks take you about ${ratio}% of your estimate. Plan a bit more time."
                                ratio < 85 -> "you finish faster than you think (${ratio}%)."
                                else -> "your estimates are spot on."
                            },
                    )
                } else {
                    Hint("Add estimates to tasks and track time with Focus to see how accurate your planning is.")
                }
            }

            // Mood vs score: average task score on days with each mood.
            val moodDays = journal.filter { it.mood != null }
            if (moodDays.size >= 3) {
                item {
                    SectionTitle("Mood vs score · 90 days")
                    (1..5).forEach { m ->
                        val scores = moodDays.filter { it.mood == m }.mapNotNull { byDate[it.date]?.score }
                        if (scores.isNotEmpty()) {
                            val avg = scores.average().toInt()
                            ProgressRow("${moods[m - 1]}  ${scores.size} days", "avg $avg%", avg / 100f, scoreColor(avg))
                        }
                    }
                    Hint("See which kind of day leaves you feeling good.")
                }
            }

            if (goals.isNotEmpty()) {
                item { SectionTitle("Goals") }
                goals.forEach { g ->
                    item(key = "g${g.id}") {
                        val v = g.progress(saved)
                        ProgressRow(g.title, g.progressLabel(v), if (g.target == 0L) 0f else v.toFloat() / g.target)
                    }
                }
            }

            item {
                SectionTitle("Money this month")
                Row(Modifier.padding(horizontal = 16.dp)) {
                    StatBox("Income", Money.format(income), Modifier.weight(1f), Good)
                    StatBox("Spent", Money.format(spent), Modifier.weight(1f), Bad)
                    StatBox(if (income >= spent) "Saved" else "Over", Money.format(kotlin.math.abs(income - spent)), Modifier.weight(1f))
                }
                val over = byCategory.mapNotNull { c ->
                    budgets.firstOrNull { it.category == c.category }?.let { b -> c.category.takeIf { c.totalPaise > b.limitPaise } }
                }
                if (over.isNotEmpty()) Hint("Over budget: ${over.joinToString()}")
            }

            item {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { nav.navigate("review") }) { Text("Weekly review") }
                    OutlinedButton(onClick = { nav.navigate("finance") }) { Text("Finance") }
                }
            }
        }
    }
}

/** One bar per day, height = score; empty days show a faint stub. */
@Composable
private fun ScoreBars(points: List<Pair<Long, Int?>>, labels: Boolean) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column(Modifier.padding(horizontal = 16.dp)) {
        Canvas(Modifier.fillMaxWidth().height(if (labels) 110.dp else 70.dp)) {
            val gap = if (points.size > 10) 3.dp.toPx() else 8.dp.toPx()
            val w = (size.width - gap * (points.size - 1)) / points.size
            points.forEachIndexed { i, (_, score) ->
                val x = i * (w + gap)
                drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(4.dp.toPx()))
                if (score != null) {
                    val h = maxOf(size.height * score / 100f, 3.dp.toPx())
                    drawRoundRect(scoreColor(score), Offset(x, size.height - h), Size(w, h), CornerRadius(4.dp.toPx()))
                }
            }
        }
        if (labels) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                points.forEach { (day, score) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Day.date(day).dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall)
                        Text(score?.let { "$it%" } ?: "–", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** GitHub-style grid: columns are weeks (oldest left), rows Monday→Sunday, colour by score. */
@Composable
private fun Heatmap(byDate: Map<Long, DayAgg>, today: Long) {
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val firstMonday = Day.weekStart(today) - (HEATMAP_WEEKS - 1) * 7
    Column(Modifier.padding(horizontal = 16.dp)) {
        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            val gap = 3.dp.toPx()
            val cell = minOf((size.width - gap * (HEATMAP_WEEKS - 1)) / HEATMAP_WEEKS, (size.height - gap * 6) / 7)
            for (w in 0 until HEATMAP_WEEKS) for (d in 0 until 7) {
                val day = firstMonday + w * 7 + d
                if (day > today) continue
                val score = byDate[day]?.score
                val color = if (score == null) empty else scoreColor(score).copy(alpha = 0.35f + 0.65f * score / 100f)
                drawRoundRect(color, Offset(w * (cell + gap), d * (cell + gap)), Size(cell, cell), CornerRadius(2.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Day.short(firstMonday), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
