@file:OptIn(ExperimentalMaterial3Api::class)

package com.wac.worktrack.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.wac.worktrack.core.Badge
import com.wac.worktrack.core.Badges
import com.wac.worktrack.ui.BackTopBar
import com.wac.worktrack.ui.Hint
import com.wac.worktrack.ui.LocalResumeTick
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.ScoreRing
import com.wac.worktrack.ui.SectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AchievementsScreen(nav: NavController) {
    val context = LocalContext.current
    val today = LocalToday.current
    val tick = LocalResumeTick.current
    val badges by produceState(emptyList<Badge>(), today, tick) { value = withContext(Dispatchers.IO) { Badges.compute(context, today) } }
    val life by produceState<Int?>(null, today, tick) { value = withContext(Dispatchers.IO) { Badges.lifeScore(context, today) } }

    Scaffold(topBar = { BackTopBar("Achievements", { nav.popBackStack() }) }) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        ScoreRing(life)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Life score · last 7 days", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Tasks 40% · habits 25% · budgets 15% · journal 20%. Parts you don't use are left out.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            val earned = badges.filter { it.earned }
            item { SectionTitle("Badges · ${earned.size} of ${badges.size}") }
            if (badges.isEmpty()) item { Hint("Loading…") }
            items(earned + badges.filter { !it.earned }, key = { it.id }) { b ->
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).alpha(if (b.earned) 1f else 0.55f),
                    colors = if (b.earned) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(b.emoji, style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.title, style = MaterialTheme.typography.titleMedium)
                            Text(b.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(if (b.earned) "✓" else b.progress, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            item { Spacer(Modifier.padding(16.dp)) }
        }
    }
}
