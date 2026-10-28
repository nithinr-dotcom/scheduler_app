package com.wac.worktrack.core

/** Parts of the app that can be hidden, so it only shows what you actually use. Tasks are always on. */
enum class Module(val key: String, val title: String, val description: String) {
    HABITS("habits", "Habits", "Daily habits, streaks and Health Connect auto-ticks"),
    GOALS("goals", "Goals", "Long-term and savings goals"),
    MONEY("money", "Money", "Income, spending, budgets, bills and payment capture"),
    JOURNAL("journal", "Journal & mood", "A few lines and a mood each day"),
    FOCUS("focus", "Focus timer", "Pomodoro sessions logged against tasks"),
    LEARNING("learning", "Learning", "Books and courses"),
    MEDICINE("medicine", "Medicines", "Dose reminders, \"taken?\" tracking and refill alerts"),
    DATES("dates", "Important dates", "Birthdays and anniversaries with a heads-up"),
}
