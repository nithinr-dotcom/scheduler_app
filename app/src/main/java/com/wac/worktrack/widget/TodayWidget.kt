package com.wac.worktrack.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.wac.worktrack.MainActivity
import com.wac.worktrack.R
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.Scores
import com.wac.worktrack.core.db
import com.wac.worktrack.core.timeLabel
import com.wac.worktrack.data.TaskStatus
import com.wac.worktrack.notify.launchAsync

/** Home-screen widget: today's score, streak and the next open task. */
class TodayWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        launchAsync { update(context) }
    }

    companion object {
        suspend fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isEmpty()) return

            val today = Day.today()
            val tasks = context.db.tasks().tasksOn(today)
            val aggs = context.db.tasks().aggsNow(today - 400, today)
            val score = aggs.lastOrNull { it.date == today }?.score
            val rest = context.db.care().restDatesNow(today - 400, today).toSet()
            val streak = Scores.streaks(aggs, today, rest).current
            val done = tasks.count { it.status == TaskStatus.DONE }
            val next = tasks.filter { it.status == TaskStatus.PENDING }
                .sortedWith(compareBy({ it.timeMinutes == null }, { it.timeMinutes }, { !it.isTop3 }))
                .firstOrNull()

            val views = RemoteViews(context.packageName, R.layout.widget_today).apply {
                setTextViewText(R.id.widget_score, score?.let { "$it%" } ?: "–")
                setTextViewText(R.id.widget_progress, "$done / ${tasks.size} done")
                setTextViewText(R.id.widget_streak, "🔥 $streak day streak")
                setTextViewText(
                    R.id.widget_next,
                    when {
                        next == null && tasks.isNotEmpty() -> "All done for today 🎉"
                        next == null -> "Nothing planned — tap to plan"
                        else -> "Next: " + (next.timeMinutes?.let { timeLabel(it) + " · " } ?: "") + next.title
                    },
                )
                val open = PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                setOnClickPendingIntent(R.id.widget_root, open)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
