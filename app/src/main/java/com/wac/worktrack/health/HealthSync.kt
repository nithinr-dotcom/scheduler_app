package com.wac.worktrack.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.Prefs
import com.wac.worktrack.core.db
import com.wac.worktrack.data.HabitLog
import com.wac.worktrack.data.HealthSource
import java.time.Duration
import java.time.Instant

/**
 * Reads steps, sleep and exercise from Health Connect (read-only) and ticks habits linked to
 * them. The habit's count for the day is set to the measured value, so "8,000 steps" is done
 * once Health Connect reports 8,000.
 */
object HealthSync {
    val PERMISSIONS = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )

    data class DayHealth(val steps: Long, val sleepMinutes: Long, val exerciseMinutes: Long) {
        fun value(source: HealthSource): Long = when (source) {
            HealthSource.STEPS -> steps
            HealthSource.SLEEP_MINUTES -> sleepMinutes
            HealthSource.EXERCISE_MINUTES -> exerciseMinutes
        }
    }

    fun available(context: Context) =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private fun client(context: Context) = HealthConnectClient.getOrCreate(context)

    suspend fun hasPermissions(context: Context): Boolean =
        available(context) && client(context).permissionController.getGrantedPermissions().containsAll(PERMISSIONS)

    /** Totals for [day]. Sleep counts toward the day it ends on (last night's sleep shows today). */
    suspend fun read(context: Context, day: Long): DayHealth {
        val c = client(context)
        val start = Instant.ofEpochMilli(Day.startMillis(day))
        val end = Instant.ofEpochMilli(Day.startMillis(day + 1))
        val steps = c.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(start, end)))[StepsRecord.COUNT_TOTAL] ?: 0L
        val sleep = c.readRecords(
            ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(start.minus(Duration.ofHours(12)), end))
        ).records.filter { !it.endTime.isBefore(start) && it.endTime.isBefore(end) }
            .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
        val exercise = c.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.between(start, end)))
            .records.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
        return DayHealth(steps, sleep, exercise)
    }

    /** Updates today's (and yesterday's, which may have synced late) counts for health-linked habits. */
    suspend fun sync(context: Context) {
        if (!Prefs(context).healthConnect || !hasPermissions(context)) return
        val habits = context.db.habits().activeNow().filter { it.autoSource != null }
        if (habits.isEmpty()) return
        val today = Day.today()
        for (day in listOf(today - 1, today)) {
            val health = runCatching { read(context, day) }.getOrNull() ?: continue
            for (h in habits) {
                if (day < h.createdDate) continue
                val value = health.value(h.autoSource!!).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                val existing = context.db.habits().logOn(h.id, day)
                if (value > 0 && (existing == null || existing.count < value)) context.db.habits().upsertLog(HabitLog(h.id, day, value))
            }
        }
    }
}
