package com.wac.worktrack.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.QuickAdd
import com.wac.worktrack.core.QuickParse
import kotlinx.coroutines.launch

/**
 * One line for anything: "call mom tomorrow 6pm", "remind me to pay rent on the 5th at 9am",
 * "someday learn guitar". Shows what it understood before saving. Voice uses the phone's
 * own speech recogniser.
 */
@Composable
fun QuickAddDialog(onDismiss: () -> Unit, startWithVoice: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    val today = Day.today()
    val parsed = remember(text) { QuickParse.parse(text, today, Day.minutesOfMillis(System.currentTimeMillis())) }
    val focus = remember { FocusRequester() }
    // Guards against a double tap (or Done + Add) saving the same thing twice.
    var saving by remember { mutableStateOf(false) }
    var voiceStarted by rememberSaveable { mutableStateOf(false) }

    fun save() {
        if (parsed.title.isBlank() || saving) return
        saving = true
        scope.launch {
            val msg = QuickAdd.save(context, parsed)
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && heard != null) text = heard
    }

    fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a task, reminder or idea")
        runCatching { voice.launch(intent) }
            .onFailure { Toast.makeText(context, "Voice input isn't available on this phone", Toast.LENGTH_SHORT).show() }
    }

    LaunchedEffect(Unit) {
        if (startWithVoice && !voiceStarted) {
            voiceStarted = true
            listen()
        } else {
            runCatching { focus.requestFocus() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick add") },
        text = {
            Column {
                OutlinedTextField(
                    text, { text = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    placeholder = { Text("e.g. gym tomorrow 7am") },
                    trailingIcon = { IconButton(onClick = { listen() }) { Icon(Icons.Default.Mic, "Speak") } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                )
                Spacer(Modifier.height(8.dp))
                if (parsed.title.isNotBlank()) {
                    Text("“${parsed.title}”", style = MaterialTheme.typography.bodyLarge)
                    Text(QuickAdd.preview(parsed, today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text(
                        "Start with “remind me to…” for a reminder or “someday…” for your someday list. Add “!” for high priority.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(enabled = parsed.title.isNotBlank() && !saving, onClick = { save() }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
