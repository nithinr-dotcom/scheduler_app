@file:OptIn(ExperimentalMaterial3Api::class)

package com.wac.worktrack.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.Prefs
import com.wac.worktrack.core.db
import com.wac.worktrack.core.millisTimeLabel
import com.wac.worktrack.core.timeLabel
import com.wac.worktrack.data.Place
import com.wac.worktrack.data.Reminder
import com.wac.worktrack.notify.Alarms
import com.wac.worktrack.notify.Geofences
import com.wac.worktrack.ui.Choice
import com.wac.worktrack.ui.ConfirmDialog
import com.wac.worktrack.ui.DatePickerDialogFor
import com.wac.worktrack.ui.Hint
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.SectionTitle
import com.wac.worktrack.ui.TimePickerDialog
import com.wac.worktrack.ui.rememberGoogleItems
import com.wac.worktrack.ui.rememberUndo
import kotlinx.coroutines.launch

@Composable
fun RemindersScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val undo = rememberUndo()
    val today = LocalToday.current
    val reminders by remember { context.db.reminders().all() }.collectAsStateWithLifecycle(emptyList())
    val google = rememberGoogleItems(today, today + 6)
    val prefs = remember { Prefs(context) }
    val places by remember { context.db.life().places() }.collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<Reminder?>(null) }
    var namingPlace by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var askBackground by remember { mutableStateOf(false) }
    var deletingPlace by remember { mutableStateOf<Place?>(null) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    fun save(r: Reminder) = scope.launch {
        val id = context.db.reminders().upsert(r)
        Alarms.scheduleReminder(context, if (r.id == 0L) r.copy(id = id) else r)
        Geofences.sync(context)
        if (r.placeId != null && !Geofences.hasBackground(context)) askBackground = true
    }

    val backgroundPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch { Geofences.sync(context) }
        else toast("Location reminders need location access \"All the time\" to work in the background.")
    }

    fun captureLocation() = scope.launch {
        val loc = runCatching { Geofences.currentLocation(context) }.getOrNull()
        if (loc == null) toast("Couldn't get your location. Turn on location and try again.")
        else namingPlace = loc.latitude to loc.longitude
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) captureLocation()
        else toast("Location permission is needed to save a place.")
    }

    fun addPlace() {
        if (Geofences.hasForeground(context)) captureLocation()
        else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reminders") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = Reminder(title = "", timeMillis = Day.millisAt(today, Day.minutesOfMillis(System.currentTimeMillis()) + 60))
            }) { Icon(Icons.Default.Add, "Add reminder") }
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item { SectionTitle("My reminders (app only)") }
            if (reminders.isEmpty()) item { Hint("No reminders. Tap + to add one.") }
            items(reminders, key = { it.id }) { r ->
                Row(
                    Modifier.fillMaxWidth().clickable { editing = r }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(r.title, style = MaterialTheme.typography.bodyLarge)
                        val place = places.firstOrNull { it.id == r.placeId }
                        val whenText = when {
                            r.placeId != null -> (if (r.repeatDaily) "Every time I arrive at " else "When I arrive at ") + (place?.name ?: "a deleted place")
                            r.repeatDaily -> "Every day at ${millisTimeLabel(r.timeMillis)}"
                            else -> "${Day.label(Day.ofMillis(r.timeMillis), today)} at ${millisTimeLabel(r.timeMillis)}"
                        }
                        Text(
                            listOf(whenText, r.note).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(r.enabled, { on ->
                        // Re-enabling a past one-off reminder needs a new time first.
                        if (on && r.placeId == null && Alarms.nextFire(r.copy(enabled = true)) == null) editing = r
                        else save(r.copy(enabled = on))
                    })
                }
            }

            if (places.isNotEmpty()) {
                item { SectionTitle("Saved places") }
                items(places, key = { "p" + it.id }) { p ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("📍 ${p.name}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { deletingPlace = p }) { Text("Remove") }
                    }
                }
            }

            item { SectionTitle("From Google Calendar · next 7 days (read-only)") }
            if (!prefs.calendarEnabled && !prefs.googleTasksEnabled) {
                item {
                    Hint("Turn on Google Calendar in Settings to see your events and Google Tasks here.")
                    TextButton(onClick = { nav.navigate("settings") }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Open Settings") }
                }
            } else if (google.isEmpty()) {
                item { Hint("Nothing on your Google Calendar this week.") }
            }
            google.groupBy { it.day }.forEach { (day, list) ->
                item(key = "d$day") {
                    Text(
                        Day.label(day, today), style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 20.dp, top = 8.dp),
                    )
                }
                items(list, key = { "g" + it.key }) { GoogleItemRow(it) }
            }
            item {
                Hint("Google Calendar's own app sends notifications for these. Nothing here is ever changed in Google.")
                Spacer(Modifier.height(88.dp))
            }
        }
    }

    namingPlace?.let { (lat, lng) ->
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { namingPlace = null },
            title = { Text("Save this place") },
            text = {
                Column {
                    Text("Your current location will be saved. Reminders fire when you arrive within about 150 m.")
                    OutlinedTextField(name, { name = it }, label = { Text("Name (Home, Office, Gym…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch { context.db.life().upsertPlace(Place(name = name.trim(), lat = lat, lng = lng)) }
                    namingPlace = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { namingPlace = null }) { Text("Cancel") } },
        )
    }
    deletingPlace?.let { p ->
        ConfirmDialog("Remove ${p.name}?", "Reminders for this place will stop working.", "Remove", { deletingPlace = null }) {
            scope.launch {
                context.db.life().deletePlace(p)
                Geofences.sync(context)
            }
        }
    }
    if (askBackground) {
        ConfirmDialog(
            "Allow location all the time",
            "So the reminder can fire when you arrive, even with the app closed, choose \"Allow all the time\" on the next screen.",
            "Continue",
            { askBackground = false },
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) backgroundPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }
    editing?.let { r ->
        ReminderDialog(
            initial = r, today = today, places = places, onAddPlace = { addPlace() },
            onDismiss = { editing = null },
            onSave = { save(it); editing = null },
            onDelete = if (r.id == 0L) null else ({
                scope.launch {
                    Alarms.cancelReminder(context, r.id)
                    context.db.reminders().delete(r)
                    Geofences.sync(context)
                    undo.show("Deleted “${r.title}”") {
                        context.db.reminders().upsert(r)
                        Alarms.scheduleReminder(context, r)
                        Geofences.sync(context)
                    }
                }
                editing = null
            }),
        )
    }
}

@Composable
private fun ReminderDialog(
    initial: Reminder,
    today: Long,
    places: List<Place>,
    onAddPlace: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (Reminder) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var atPlace by remember { mutableStateOf(initial.placeId != null) }
    var placeId by remember { mutableStateOf(initial.placeId) }
    var title by remember { mutableStateOf(initial.title) }
    var note by remember { mutableStateOf(initial.note) }
    var day by remember { mutableLongStateOf(maxOf(Day.ofMillis(initial.timeMillis), today)) }
    var minutes by remember { mutableIntStateOf(Day.minutesOfMillis(initial.timeMillis)) }
    var daily by remember { mutableStateOf(initial.repeatDaily) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    val at = Day.millisAt(day, minutes)
    val chosenPlace = places.firstOrNull { it.id == placeId } ?: places.firstOrNull()
    val valid = title.isNotBlank() && if (atPlace) chosenPlace != null else (daily || at > System.currentTimeMillis())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New reminder" else "Edit reminder") },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text("Remind me to…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.padding(top = 8.dp)) {
                    FilterChip(!atPlace, { atPlace = false }, { Text("At a time") }, Modifier.padding(end = 6.dp))
                    FilterChip(atPlace, { atPlace = true }, { Text("At a place") })
                }
                if (atPlace) {
                    if (places.isEmpty()) {
                        Text("No saved places yet.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Choice("Place", places, chosenPlace!!, { placeId = it.id }, Modifier.fillMaxWidth(), display = { it.name })
                    }
                    TextButton(onClick = onAddPlace) { Text("📍 Save where I am now as a place") }
                } else {
                    Row {
                        if (!daily) {
                            OutlinedButton(onClick = { pickingDate = true }) { Text(Day.label(day, today)) }
                            Spacer(Modifier.padding(4.dp))
                        }
                        OutlinedButton(onClick = { pickingTime = true }) { Text(timeLabel(minutes)) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text(if (atPlace) "Every time I arrive" else "Repeat every day", Modifier.weight(1f))
                    Switch(daily, { daily = it })
                }
                if (!valid && title.isNotBlank() && !atPlace) {
                    Text("Pick a time in the future.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(
                    initial.copy(
                        title = title.trim(), note = note.trim(), timeMillis = at, repeatDaily = daily, enabled = true,
                        placeId = if (atPlace) chosenPlace?.id else null,
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
    if (pickingDate) DatePickerDialogFor(day, { pickingDate = false }) { day = maxOf(it, today); pickingDate = false }
    if (pickingTime) TimePickerDialog(minutes, { pickingTime = false }) { minutes = it; pickingTime = false }
}
