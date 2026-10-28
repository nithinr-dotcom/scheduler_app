package com.wac.worktrack.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.Prefs
import java.time.Instant
import java.time.ZoneOffset

/** Something from Google — shown in the app, never edited, never part of the score. */
data class CalendarItem(
    val key: String,
    val title: String,
    val day: Long,
    /** Start minute of the day, or null for all-day events and Google Tasks (date only). */
    val minutes: Int?,
    val endMinutes: Int?,
    val kind: Kind,
    val source: String,
) {
    enum class Kind { EVENT, GOOGLE_TASK }
}

/** Read-only access to Google Calendar events (via the phone's synced calendar) and Google Tasks. */
object Calendars {

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Everything from [from] to [to] (inclusive epoch days). Call off the main thread. */
    suspend fun items(context: Context, from: Long, to: Long): List<CalendarItem> {
        val prefs = Prefs(context)
        val events = if (prefs.calendarEnabled && hasPermission(context)) events(context, from, to) else emptyList()
        val tasks = if (prefs.googleTasksEnabled) GoogleTasks.due(context, from, to) else emptyList()
        return (tasks + events).sortedWith(compareBy({ it.day }, { it.minutes ?: -1 }))
    }

    private fun events(context: Context, from: Long, to: Long): List<CalendarItem> {
        val begin = Day.startMillis(from)
        val end = Day.startMillis(to + 1)
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin - 86_400_000L) // all-day events are stored in UTC
            ContentUris.appendId(it, end + 86_400_000L)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances._ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        )
        val out = mutableListOf<CalendarItem>()
        context.contentResolver.query(
            uri, projection, "${CalendarContract.Instances.VISIBLE} = 1", null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(No title)"
                val start = c.getLong(2)
                val stop = c.getLong(3)
                val allDay = c.getInt(4) == 1
                val calName = c.getString(5) ?: "Calendar"
                if (allDay) {
                    // All-day instances span UTC midnights; map them to calendar dates directly.
                    val first = Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    val last = Instant.ofEpochMilli(stop - 1).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    for (d in maxOf(first, from)..minOf(last, to)) {
                        out += CalendarItem("e$id-$d", title, d, null, null, CalendarItem.Kind.EVENT, calName)
                    }
                } else {
                    if (stop <= begin || start >= end) continue
                    val d = maxOf(Day.ofMillis(start), from)
                    val startMin = if (Day.ofMillis(start) < d) 0 else Day.minutesOfMillis(start)
                    val endMin = if (Day.ofMillis(stop) > d) null else Day.minutesOfMillis(stop)
                    out += CalendarItem("e$id-$d", title, d, startMin, endMin, CalendarItem.Kind.EVENT, calName)
                }
            }
        }
        return out
    }
}
