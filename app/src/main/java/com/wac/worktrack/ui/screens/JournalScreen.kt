@file:OptIn(ExperimentalMaterial3Api::class)

package com.wac.worktrack.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.wac.worktrack.WorkTrackApp
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.db
import com.wac.worktrack.data.JournalEntry
import com.wac.worktrack.ui.BackTopBar
import com.wac.worktrack.ui.Hint
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.SectionTitle
import com.wac.worktrack.ui.Stepper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A few lines a day plus a mood. Stays on the phone (and in your own backup). */
@Composable
fun JournalScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalToday.current
    val db = context.db

    var day by remember { mutableLongStateOf(today) }
    val entry by remember(day) { db.life().journal(day) }.collectAsStateWithLifecycle(null)
    // null until the first query returns, so "nothing here" hints don't flash while loading.
    val allOrNull by remember { db.life().journalAll() }.collectAsStateWithLifecycle(null)
    val all = allOrNull.orEmpty()
    var text by remember(day) { mutableStateOf("") }
    var loaded by remember(day) { mutableStateOf(false) }
    // Typed but not yet saved. Saved a second after typing stops, and when leaving the day or the screen.
    var dirty by remember(day) { mutableStateOf(false) }
    LaunchedEffect(entry) {
        if (!loaded && entry != null) {
            if (!dirty) text = entry!!.text
            loaded = true
        }
    }
    // App scope, so a save started while leaving the screen still finishes.
    val appScope = (context.applicationContext as WorkTrackApp).scope

    fun persist(d: Long, t: String, base: JournalEntry?) = appScope.launch {
        db.life().upsertJournal((base ?: JournalEntry(d)).copy(text = t.trim()))
    }

    fun save(mood: Int? = entry?.mood) = scope.launch {
        dirty = false
        db.life().upsertJournal((entry ?: JournalEntry(day)).copy(text = text.trim(), mood = mood))
    }

    LaunchedEffect(day, text) {
        if (!dirty) return@LaunchedEffect
        delay(1000)
        persist(day, text, entry)
        dirty = false
    }
    DisposableEffect(day) {
        onDispose { if (dirty) persist(day, text, entry) }
    }

    Scaffold(topBar = { BackTopBar("Journal", { nav.popBackStack() }) }) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Stepper(Day.label(day, today), onPrev = { day-- }, onNext = if (day >= today) null else ({ day++ }))
                SectionTitle("Mood")
                MoodPicker(entry?.mood) { m -> save(if (entry?.mood == m) null else m) }
                SectionTitle("Notes")
                Column(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        text, { text = it; dirty = true }, modifier = Modifier.fillMaxWidth(), minLines = 5,
                        placeholder = { Text("What happened? What are you grateful for? What did you learn?") },
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { save() }) { Text("Save") }
                }
                SectionTitle("Past entries")
                if (allOrNull?.isEmpty() == true) Hint("Your entries will show up here.")
            }
            items(all.filter { it.text.isNotBlank() || it.mood != null }, key = { it.date }) { e ->
                Column(Modifier.fillMaxWidth().clickable { day = e.date }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row {
                        Text(Day.label(e.date, today), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        e.mood?.let { Text(moods[it - 1]) }
                    }
                    if (e.text.isNotBlank()) {
                        Text(e.text, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                HorizontalDivider()
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
