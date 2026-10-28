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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wac.worktrack.core.Module
import com.wac.worktrack.core.db
import com.wac.worktrack.notify.Alarms
import com.wac.worktrack.ui.BackTopBar
import com.wac.worktrack.ui.Hint
import com.wac.worktrack.ui.ModuleState
import kotlinx.coroutines.launch

/**
 * Pick what the app helps with. Hidden parts keep their data; they just drop out of the menus,
 * Today and notifications until switched back on. Shown on first run and from Settings.
 */
@Composable
fun SetupScreen(firstRun: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf(ModuleState.on) }

    fun finish() {
        ModuleState.set(context, chosen)
        // Dose alarms follow the Medicines switch.
        scope.launch {
            context.db.care().activeMedicinesNow().forEach { Alarms.scheduleMed(context, it) }
            onDone()
        }
    }

    Scaffold(
        topBar = {
            if (firstRun) TopAppBar(title = { Text("What do you want help with?") })
            else BackTopBar("Features", onBack = onDone)
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Hint(
                    (if (firstRun) "Start small — you can turn more on later in Settings → Features. " else "") +
                        "Tasks, Top 3 and reminders are always on. Turning something off hides it but keeps its data.",
                )
            }
            items(Module.entries, key = { it.key }) { m ->
                Row(
                    Modifier.fillMaxWidth().clickable { chosen = if (m in chosen) chosen - m else chosen + m }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(m in chosen, { chosen = if (it) chosen + m else chosen - m })
                    Column(Modifier.weight(1f)) {
                        Text(m.title, style = MaterialTheme.typography.bodyLarge)
                        Text(m.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(if (firstRun) "Start" else "Save")
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
