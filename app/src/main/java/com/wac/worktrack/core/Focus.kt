package com.wac.worktrack.core

import android.content.Context
import com.wac.worktrack.data.FocusSession
import com.wac.worktrack.notify.Alarms
import com.wac.worktrack.notify.Notifs

/**
 * Pomodoro-style focus timer. The running session lives in [Prefs] with an exact alarm for its
 * end, so it keeps going when the app is closed. Finished focus time is logged and added to the
 * task's actual minutes (for "estimate vs actual").
 */
object Focus {
    data class State(val running: Boolean, val isBreak: Boolean, val endAt: Long, val startAt: Long, val taskId: Long?, val title: String)

    fun state(context: Context): State {
        val p = Prefs(context)
        return State(p.focusEndAt > 0, p.focusIsBreak, p.focusEndAt, p.focusStartAt, p.focusTaskId.takeIf { it != 0L }, p.focusTitle)
    }

    fun start(context: Context, taskId: Long?, title: String, minutes: Int = Prefs(context).focusMinutes) =
        begin(context, taskId, title, minutes, isBreak = false)

    fun startBreak(context: Context) {
        val p = Prefs(context)
        begin(context, p.focusTaskId.takeIf { it != 0L }, p.focusTitle, p.breakMinutes, isBreak = true)
    }

    private fun begin(context: Context, taskId: Long?, title: String, minutes: Int, isBreak: Boolean) {
        val p = Prefs(context)
        val now = System.currentTimeMillis()
        p.focusStartAt = now
        p.focusEndAt = now + minutes * 60_000L
        p.focusIsBreak = isBreak
        p.focusTaskId = taskId ?: 0
        p.focusTitle = title
        Alarms.scheduleFocusEnd(context, p.focusEndAt)
        // The "session done" card's buttons would otherwise still be there to restart over this one.
        Notifs.cancelFocusDone(context)
        Notifs.focusRunning(context, state(context))
    }

    /** Stops early; focus time so far (if at least a minute) is still logged. */
    suspend fun stop(context: Context) {
        val s = take(context) ?: return
        if (!s.isBreak) log(context, s, ((System.currentTimeMillis() - s.startAt) / 60_000L).toInt())
    }

    /** Called by the end-of-session alarm. */
    suspend fun finished(context: Context) {
        val s = take(context) ?: return
        if (!s.isBreak) log(context, s, ((s.endAt - s.startAt) / 60_000L).toInt())
        Notifs.focusFinished(context, s.isBreak, s.title)
    }

    private suspend fun log(context: Context, s: State, minutes: Int) {
        if (minutes < 1) return
        context.db.life().insertFocus(
            FocusSession(taskId = s.taskId, title = s.title.ifBlank { "Focus" }, date = Day.ofMillis(s.startAt), startMillis = s.startAt, minutes = minutes)
        )
        s.taskId?.let { context.db.tasks().addActualMinutes(it, minutes) }
    }

    /**
     * Ends the running session and returns it, or null if none is running. Atomic, so the
     * screen's own timer and the end alarm can't both log the same session.
     */
    @Synchronized
    private fun take(context: Context): State? {
        val s = state(context)
        if (!s.running) return null
        Prefs(context).focusEndAt = 0
        Alarms.cancelFocusEnd(context)
        Notifs.cancelFocus(context)
        return s
    }
}
