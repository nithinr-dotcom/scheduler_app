package com.wac.worktrack.core

import com.wac.worktrack.data.Bill
import com.wac.worktrack.data.BillCycle
import java.time.LocalDate
import java.time.YearMonth

object Bills {
    /** The billing period that contains [day]: "yyyy-MM" or "yyyy". */
    fun periodOf(bill: Bill, day: Long): String {
        val d = Day.date(day)
        return if (bill.cycle == BillCycle.MONTHLY) YearMonth.from(d).toString() else d.year.toString()
    }

    /** Due date of [bill] within the period that contains [day]. */
    fun dueIn(bill: Bill, day: Long): Long {
        val d = Day.date(day)
        val month = if (bill.cycle == BillCycle.MONTHLY) YearMonth.from(d) else YearMonth.of(d.year, bill.dueMonth.coerceIn(1, 12))
        return month.atDay(minOf(bill.dueDay.coerceAtLeast(1), month.lengthOfMonth())).toEpochDay()
    }

    /**
     * The next unpaid due date: this period's if it isn't paid yet (even when overdue),
     * otherwise next period's.
     */
    fun nextDue(bill: Bill, today: Long): Long {
        val current = dueIn(bill, today)
        if (bill.lastPaidPeriod != periodOf(bill, today)) return current
        val next = if (bill.cycle == BillCycle.MONTHLY) Day.date(current).plusMonths(1) else Day.date(current).plusYears(1)
        return dueIn(bill, next.toEpochDay())
    }

    fun isOverdue(bill: Bill, today: Long) = bill.lastPaidPeriod != periodOf(bill, today) && dueIn(bill, today) < today

    /** Due within its reminder window (or overdue) and not paid yet. */
    fun needsAttention(bill: Bill, today: Long): Boolean {
        if (!bill.active) return false
        val due = nextDue(bill, today)
        return bill.lastPaidPeriod != periodOf(bill, due) && due - today <= bill.remindDaysBefore
    }

    /** Cost per month, so yearly subscriptions can be compared with monthly ones. */
    fun monthlyCost(bill: Bill): Long = if (bill.cycle == BillCycle.MONTHLY) bill.amountPaise else bill.amountPaise / 12

    fun cycleLabel(bill: Bill): String = when (bill.cycle) {
        BillCycle.MONTHLY -> "Day ${bill.dueDay} every month"
        BillCycle.YEARLY -> "${bill.dueDay} ${LocalDate.of(2000, bill.dueMonth.coerceIn(1, 12), 1).month.name.lowercase().replaceFirstChar { it.uppercase() }} every year"
    }
}
