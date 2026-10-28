@file:OptIn(ExperimentalMaterial3Api::class)

package com.wac.worktrack.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.DayPlanner
import com.wac.worktrack.core.db
import com.wac.worktrack.data.DailyTask
import com.wac.worktrack.data.InboxItem
import com.wac.worktrack.ui.BackTopBar
import com.wac.worktrack.ui.DatePickerDialogFor
import com.wac.worktrack.ui.Hint
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.rememberUndo
import kotlinx.coroutines.launch

/** Someday list: park ideas here and give them a day when you're ready. */
@Composable
fun InboxScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val undo = rememberUndo()
    val today = LocalToday.current
    val db = context.db
    // null until the first query returns, so "nothing here" hints don't flash while loading.
    val itemsOrNull by remember { db.life().inbox() }.collectAsStateWithLifecycle(null)
    val items = itemsOrNull.orEmpty()
    var editing by remember { mutableStateOf<InboxItem?>(null) }
    var scheduling by remember { mutableStateOf<InboxItem?>(null) }

    Scaffold(
        topBar = { BackTopBar("Someday", { nav.popBackStack() }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = InboxItem(title = "", createdMillis = System.currentTimeMillis()) }) {
                Icon(Icons.Default.Add, "Add idea")
            }
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item { Hint("Ideas and \"one day\" tasks that don't belong to a date yet. Quick add: start with \"someday…\".") }
            if (itemsOrNull?.isEmpty() == true) item { Hint("Empty — nice and clear. ✨") }
            items(items, key = { it.id }) { i ->
                Card(onClick = { editing = i }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp)) {
                        Text(i.title, style = MaterialTheme.typography.bodyLarge)
                        if (i.note.isNotBlank()) Text(i.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row {
                            Text(
                                "Added ${Day.label(Day.ofMillis(i.createdMillis), today).lowercase()}",
                                Modifier.weight(1f).padding(top = 14.dp), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { schedule(context, scope, i, today) }) { Text("Today") }
                            TextButton(onClick = { scheduling = i }) { Text("Pick day") }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    editing?.let { i ->
        var title by remember(i) { mutableStateOf(i.title) }
        var note by remember(i) { mutableStateOf(i.note) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (i.id == 0L) "New idea" else "Edit idea") },
            text = {
                Column {
                    OutlinedTextField(title, { title = it }, label = { Text("Idea") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank(), onClick = {
                    scope.launch { db.life().upsertInbox(i.copy(title = title.trim(), note = note.trim())) }
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (i.id != 0L) {
                        TextButton(onClick = {
                            scope.launch {
                                db.life().deleteInbox(i)
                                undo.show("Deleted “${i.title}”") { db.life().upsertInbox(i) }
                            }
                            editing = null
                        }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            },
        )
    }
    scheduling?.let { i ->
        DatePickerDialogFor(today + 1, { scheduling = null }) { day ->
            schedule(context, scope, i, maxOf(day, today))
            scheduling = null
        }
    }
}

private fun schedule(context: android.content.Context, scope: kotlinx.coroutines.CoroutineScope, i: InboxItem, day: Long) {
    scope.launch {
        DayPlanner.saveTask(context, DailyTask(date = day, title = i.title, category = "Personal"))
        context.db.life().deleteInbox(i)
        Toast.makeText(context, "Moved to ${Day.label(day).lowercase()}", Toast.LENGTH_SHORT).show()
    }
}
