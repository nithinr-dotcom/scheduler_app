package com.wac.worktrack.core

/**
 * A task's sub-steps, stored as plain text so it copies, moves and backs up with the task:
 * one item per line, "[x] " for done and "[ ] " for open.
 */
object Checklist {
    data class Item(val text: String, val done: Boolean = false)

    fun parse(text: String): List<Item> = text.lines().filter { it.isNotBlank() }.map {
        when {
            it.startsWith("[x] ") -> Item(it.drop(4), true)
            it.startsWith("[ ] ") -> Item(it.drop(4), false)
            else -> Item(it.trim(), false)
        }
    }

    fun format(items: List<Item>): String =
        items.filter { it.text.isNotBlank() }
            .joinToString("\n") { (if (it.done) "[x] " else "[ ] ") + it.text.replace('\n', ' ').trim() }

    /** (done, total) */
    fun progress(text: String): Pair<Int, Int> = parse(text).let { l -> l.count { it.done } to l.size }

    /** Same items, all unticked — what a repeating task starts each day with. */
    fun reset(text: String): String = format(parse(text).map { it.copy(done = false) })
}
