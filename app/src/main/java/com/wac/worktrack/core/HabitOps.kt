package com.wac.worktrack.core

import android.content.Context
import com.wac.worktrack.data.Habit
import com.wac.worktrack.data.HabitLog
import com.wac.worktrack.widget.Widgets

object HabitOps {
    /** Yes/no habits toggle; count habits go up by one. */
    suspend fun tap(context: Context, habit: Habit, day: Long) {
        val existing = context.db.habits().logOn(habit.id, day)
        if (habit.target <= 1) {
            if (existing != null && existing.count >= 1) context.db.habits().unlog(existing)
            else context.db.habits().upsertLog(HabitLog(habit.id, day, 1))
        } else {
            context.db.habits().upsertLog(HabitLog(habit.id, day, (existing?.count ?: 0) + 1))
        }
        Widgets.refresh(context)
    }

    suspend fun setCount(context: Context, habit: Habit, day: Long, count: Int) {
        if (count <= 0) context.db.habits().unlog(HabitLog(habit.id, day))
        else context.db.habits().upsertLog(HabitLog(habit.id, day, count))
        Widgets.refresh(context)
    }
}
