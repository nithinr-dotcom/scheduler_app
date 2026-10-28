package com.wac.worktrack.core

import android.content.Context
import com.wac.worktrack.data.MedLog
import com.wac.worktrack.data.Medicine
import com.wac.worktrack.notify.Alarms
import com.wac.worktrack.notify.Notifs

/** Dose times, the next dose to remind about, and taking a dose (which also counts down the stock). */
object Meds {
    fun parseTimes(times: String): List<Int> =
        times.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 0 until 24 * 60 }.distinct().sorted()

    fun formatTimes(times: List<Int>): String = times.distinct().sorted().joinToString(",")

    /** Next dose strictly after [nowMinutes] on [today], as (day, minutes); null if no times are set. */
    fun nextDose(med: Medicine, today: Long, nowMinutes: Int): Pair<Long, Int>? {
        val times = parseTimes(med.times)
        if (times.isEmpty()) return null
        return times.firstOrNull { it > nowMinutes }?.let { today to it } ?: ((today + 1) to times.first())
    }

    fun taken(logs: List<MedLog>, medId: Long, day: Long, minutes: Int) =
        logs.any { it.medId == medId && it.date == day && it.minutes == minutes }

    /** Share of scheduled doses taken over [days], or null when nothing was due. */
    fun adherence(meds: List<Medicine>, logs: List<MedLog>, days: LongRange): Int? {
        val due = meds.sumOf { parseTimes(it.times).size } * days.count()
        if (due == 0) return null
        val taken = logs.count { l -> l.date in days && meds.any { it.id == l.medId } }
        return minOf(100, taken * 100 / due)
    }

    fun lowStock(med: Medicine) = med.stock != null && med.stock <= med.refillAt

    suspend fun take(context: Context, med: Medicine, day: Long, minutes: Int) {
        val care = context.db.care()
        if (care.logDose(MedLog(med.id, day, minutes, System.currentTimeMillis())) == -1L) return
        care.addStock(med.id, -1)
        Notifs.cancelMed(context, med.id)
        val updated = care.medicine(med.id) ?: return
        if (updated.stock != null && updated.stock == updated.refillAt) Notifs.refill(context, updated)
    }

    suspend fun untake(context: Context, med: Medicine, day: Long, minutes: Int) {
        if (context.db.care().unlogDose(med.id, day, minutes) > 0) context.db.care().addStock(med.id, 1)
    }

    suspend fun save(context: Context, med: Medicine): Long {
        val id = context.db.care().upsertMedicine(med)
        val saved = if (med.id == 0L) med.copy(id = id) else med
        Alarms.scheduleMed(context, saved)
        return saved.id
    }

    suspend fun delete(context: Context, med: Medicine) {
        context.db.care().deleteMedicine(med)
        Alarms.cancelMed(context, med.id)
        Notifs.cancelMed(context, med.id)
    }
}
