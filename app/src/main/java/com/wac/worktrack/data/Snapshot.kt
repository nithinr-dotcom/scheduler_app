package com.wac.worktrack.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.wac.worktrack.core.db
import com.wac.worktrack.notify.Alarms
import com.wac.worktrack.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The whole database as JSON rows per table. Used by the cloud backup and by the local
 * backup file (Settings → Export / Import), which works without Firebase.
 */
object Snapshot {
    val TABLES = listOf(
        "task_templates", "daily_tasks", "reminders", "habits", "habit_logs", "goals",
        "transactions", "budgets", "recurring_txns", "merchant_rules", "weekly_reviews",
        "period_reviews", "bills", "focus_sessions", "journal", "inbox", "places", "learning",
        "debts", "assets", "net_worth", "medicines", "med_logs", "important_dates", "rest_days",
    )

    /** Bumped when the database version changes; older backups are still accepted. */
    const val FORMAT = 4

    fun dump(context: Context): Map<String, List<JSONObject>> {
        val sql = context.db.openHelper.readableDatabase
        // One transaction, so a write landing mid-dump can't leave the backup half old, half new.
        return context.db.runInTransaction<Map<String, List<JSONObject>>> {
            TABLES.associateWith { table -> sql.query("SELECT * FROM $table").use { it.toJsonRows() } }
        }
    }

    /**
     * Columns added after the first version, with the value an older backup's rows should get.
     * A fresh install's tables have no SQL defaults, so these have to be filled in by hand.
     */
    private val ADDED_COLUMNS: Map<String, Map<String, Any>> = mapOf(
        "task_templates" to mapOf("energy" to 0L, "checklist" to ""),
        "daily_tasks" to mapOf("actualMinutes" to 0L, "energy" to 0L, "checklist" to ""),
        "habits" to mapOf("target" to 1L, "unit" to ""),
        "habit_logs" to mapOf("count" to 1L),
        "goals" to mapOf("autoSharePercent" to 0L, "isEmergency" to 0L),
    )

    /**
     * Replaces all local data with [data]. Tables missing from [data] (an older backup) are
     * emptied; columns missing from a row get the values in [ADDED_COLUMNS].
     */
    suspend fun load(context: Context, data: Map<String, List<JSONObject>>): Int = withContext(Dispatchers.IO) {
        val sql = context.db.openHelper.writableDatabase
        var rows = 0
        sql.beginTransaction()
        try {
            for (table in TABLES) {
                sql.delete(table, null, null)
                val added = ADDED_COLUMNS[table].orEmpty()
                for (row in data[table].orEmpty()) {
                    val values = row.toContentValues()
                    for ((column, value) in added) {
                        if (values.containsKey(column)) continue
                        when (value) {
                            is Long -> values.put(column, value)
                            else -> values.put(column, value.toString())
                        }
                    }
                    sql.insert(table, SQLiteDatabase.CONFLICT_REPLACE, values)
                    rows++
                }
            }
            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
        // Raw writes bypass Room's DAOs; tell observers (open screens) to re-query.
        context.db.invalidationTracker.refreshAsync()
        Alarms.rescheduleAll(context)
        Widgets.refresh(context)
        rows
    }

    suspend fun writeFile(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val data = dump(context)
        val root = JSONObject()
            .put("app", "WorkTrack")
            .put("format", FORMAT)
            .put("exportedAt", System.currentTimeMillis())
            .put("tables", JSONObject().also { t -> data.forEach { (k, v) -> t.put(k, JSONArray(v)) } })
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(root.toString()) }
            ?: error("Couldn't open the file")
        data.values.sumOf { it.size }
    }

    suspend fun readFile(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Couldn't open the file")
        val root = JSONObject(text)
        require(root.optString("app") == "WorkTrack") { "This isn't a WorkTrack backup file" }
        val tables = root.getJSONObject("tables")
        val data = TABLES.associateWith { table ->
            val arr = tables.optJSONArray(table) ?: JSONArray()
            List(arr.length()) { arr.getJSONObject(it) }
        }
        load(context, data)
    }

    private fun Cursor.toJsonRows(): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        while (moveToNext()) {
            val o = JSONObject()
            for (i in 0 until columnCount) {
                o.put(
                    getColumnName(i),
                    when (getType(i)) {
                        Cursor.FIELD_TYPE_INTEGER -> getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> getDouble(i)
                        Cursor.FIELD_TYPE_STRING -> getString(i)
                        else -> JSONObject.NULL
                    },
                )
            }
            out += o
        }
        return out
    }

    private fun JSONObject.toContentValues() = ContentValues().also { cv ->
        keys().forEach { key ->
            when (val v = get(key)) {
                JSONObject.NULL -> cv.putNull(key)
                is Int -> cv.put(key, v.toLong())
                is Long -> cv.put(key, v)
                is Double -> cv.put(key, v)
                is Boolean -> cv.put(key, if (v) 1L else 0L)
                else -> cv.put(key, v.toString())
            }
        }
    }
}
