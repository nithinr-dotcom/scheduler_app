package com.wac.worktrack.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.wac.worktrack.MainActivity
import com.wac.worktrack.R
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.DayPlanner
import com.wac.worktrack.core.HabitOps
import com.wac.worktrack.core.Habits
import com.wac.worktrack.core.db
import com.wac.worktrack.data.TaskStatus
import com.wac.worktrack.notify.launchAsync

/** Keeps every home-screen widget in step with the data. Call after any change they show. */
object Widgets {
    suspend fun refresh(context: Context) {
        TodayWidget.update(context)
        HabitsWidget.update(context)
        Top3Widget.update(context)
    }

    fun openApp(context: Context, route: String, code: Int): PendingIntent =
        PendingIntent.getActivity(
            context, code,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun action(context: Context, action: String, id: Long, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, WidgetActionReceiver::class.java).setAction(action).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun ids(context: Context, cls: Class<*>): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, cls))
}

/** Up to 4 habits; tap to check in (count habits go up by one). */
class HabitsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        launchAsync { update(context) }
    }

    companion object {
        private val SLOTS = listOf(R.id.habit_1, R.id.habit_2, R.id.habit_3, R.id.habit_4)

        suspend fun update(context: Context) {
            val ids = Widgets.ids(context, HabitsWidget::class.java)
            if (ids.isEmpty()) return
            val today = Day.today()
            val habits = context.db.habits().activeNow().take(SLOTS.size)
            val logs = context.db.habits().logsNow(today, today)
            val views = RemoteViews(context.packageName, R.layout.widget_habits)
            views.setTextViewText(R.id.habits_title, if (habits.isEmpty()) "No habits yet — tap to add" else "Habits today")
            views.setOnClickPendingIntent(R.id.habits_title, Widgets.openApp(context, "habits", 20))
            SLOTS.forEachIndexed { i, slot ->
                val h = habits.getOrNull(i)
                if (h == null) {
                    views.setViewVisibility(slot, View.GONE)
                } else {
                    val count = Habits.countOn(h, logs, today)
                    val done = count >= h.target
                    views.setViewVisibility(slot, View.VISIBLE)
                    views.setTextViewText(slot, (if (done) "✓ " else "○ ") + Habits.label(h, count))
                    views.setInt(slot, "setBackgroundResource", if (done) R.drawable.widget_chip_on else R.drawable.widget_chip_off)
                    views.setOnClickPendingIntent(slot, Widgets.action(context, WidgetActionReceiver.HABIT, h.id, 1000 + i))
                }
            }
            AppWidgetManager.getInstance(context).updateAppWidget(ids, views)
        }
    }
}

/** Today's Top 3 priorities with tap-to-complete. */
class Top3Widget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        launchAsync { update(context) }
    }

    companion object {
        private val SLOTS = listOf(R.id.top_1, R.id.top_2, R.id.top_3)

        suspend fun update(context: Context) {
            val ids = Widgets.ids(context, Top3Widget::class.java)
            if (ids.isEmpty()) return
            val top = context.db.tasks().tasksOn(Day.today()).filter { it.isTop3 }.take(SLOTS.size)
            val views = RemoteViews(context.packageName, R.layout.widget_top3)
            views.setTextViewText(R.id.top_title, if (top.isEmpty()) "No Top 3 yet — tap to plan" else "Top 3 today")
            views.setOnClickPendingIntent(R.id.top_title, Widgets.openApp(context, "plan", 21))
            SLOTS.forEachIndexed { i, slot ->
                val t = top.getOrNull(i)
                if (t == null) {
                    views.setViewVisibility(slot, View.GONE)
                } else {
                    val done = t.status == TaskStatus.DONE
                    views.setViewVisibility(slot, View.VISIBLE)
                    views.setTextViewText(slot, (if (done) "✓ " else "○ ") + t.title)
                    views.setInt(slot, "setBackgroundResource", if (done) R.drawable.widget_chip_on else R.drawable.widget_chip_off)
                    views.setOnClickPendingIntent(slot, Widgets.action(context, WidgetActionReceiver.TASK, t.id, 2000 + i))
                }
            }
            AppWidgetManager.getInstance(context).updateAppWidget(ids, views)
        }
    }
}

class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", 0)
        launchAsync {
            when (intent.action) {
                HABIT -> context.db.habits().get(id)?.let { HabitOps.tap(context, it, Day.today()) }
                TASK -> context.db.tasks().task(id)?.let {
                    DayPlanner.setStatus(context, it, if (it.status == TaskStatus.DONE) TaskStatus.PENDING else TaskStatus.DONE)
                }
            }
            Widgets.refresh(context)
        }
    }

    companion object {
        const val HABIT = "com.wac.worktrack.widget.HABIT"
        const val TASK = "com.wac.worktrack.widget.TASK"
    }
}
