package com.wac.worktrack.finance

import android.content.Context
import com.wac.worktrack.core.Bills
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.db
import com.wac.worktrack.data.Bill
import com.wac.worktrack.data.DebtDirection
import com.wac.worktrack.data.NetWorthSnapshot
import com.wac.worktrack.data.Txn
import com.wac.worktrack.data.TxnSource
import com.wac.worktrack.data.TxnStatus
import com.wac.worktrack.data.TxnType
import com.wac.worktrack.notify.Notifs
import java.time.YearMonth

object MoneyOps {
    /**
     * When income is accepted, suggest the auto-share for each money goal. Suggestions go to
     * Pending like everything automatic — nothing is moved until accepted.
     */
    suspend fun onIncomeAccepted(context: Context, income: Txn) {
        if (income.type != TxnType.INCOME || income.status != TxnStatus.ACCEPTED || income.id == 0L) return
        val finance = context.db.finance()
        var added = 0
        for (g in context.db.goals().autoShareGoals()) {
            val share = income.amountPaise * g.autoSharePercent / 100
            if (share <= 0) continue
            val id = finance.insertIgnore(
                Txn(
                    amountPaise = share, type = TxnType.EXPENSE, category = "Savings", date = income.date,
                    timeMillis = income.timeMillis + 1, note = "${g.autoSharePercent}% of income → ${g.title}",
                    method = "Bank", status = TxnStatus.PENDING, source = TxnSource.AUTO_SAVE,
                    // One suggestion per income per goal, even if the income is edited and accepted again.
                    ref = "autosave:${income.id}:${g.id}", goalId = g.id,
                )
            )
            if (id != -1L) added++
        }
        if (added > 0) Notifs.pendingPayments(context, finance.pendingCountNow())
    }

    /** Records the bill's expense for the period of its next due date and marks that period paid. */
    suspend fun markPaid(context: Context, bill: Bill, amountPaise: Long = bill.amountPaise, today: Long = Day.today()) {
        val due = Bills.nextDue(bill, today)
        val period = Bills.periodOf(bill, due)
        context.db.finance().insertIgnore(
            Txn(
                amountPaise = amountPaise, type = TxnType.EXPENSE, category = bill.category, date = today,
                timeMillis = System.currentTimeMillis(), note = bill.title, method = "Bank",
                status = TxnStatus.ACCEPTED, source = TxnSource.BILL, ref = "bill:${bill.id}:$period",
            )
        )
        context.db.finance().upsertBill(bill.copy(lastPaidPeriod = period))
    }

    /** Daily check: one notification listing unpaid bills that are due soon or overdue. */
    suspend fun notifyDueBills(context: Context, today: Long = Day.today()) {
        val due = context.db.finance().activeBills().filter { Bills.needsAttention(it, today) }
        Notifs.billsDue(context, due.map { it to Bills.nextDue(it, today) }, today)
    }

    /**
     * Saves this month's net worth so the trend chart has a point per month. Money lent out
     * counts as owned, money borrowed as owed.
     */
    suspend fun snapshotNetWorth(context: Context) {
        val assets = context.db.finance().assetsNow()
        val debts = context.db.finance().debtsNow()
        context.db.finance().upsertSnapshot(
            NetWorthSnapshot(
                month = YearMonth.now().toString(),
                assetsPaise = assets.filter { !it.liability }.sumOf { it.valuePaise } +
                    debts.filter { it.direction == DebtDirection.LENT }.sumOf { it.outstanding },
                liabilitiesPaise = assets.filter { it.liability }.sumOf { it.valuePaise } +
                    debts.filter { it.direction == DebtDirection.BORROWED }.sumOf { it.outstanding },
            )
        )
    }

    /**
     * Everyday spending per month (savings left out): the average of the last 3 complete months
     * that had any, or this month so far if there's no history yet. 0 when nothing is recorded.
     */
    suspend fun monthlyLivingCost(context: Context, today: Long = Day.today()): Long {
        val finance = context.db.finance()
        val month = YearMonth.from(Day.date(today))
        val past = (1L..3L).map { month.minusMonths(it) }
            .map { finance.livingCostNow(it.atDay(1).toEpochDay(), it.atEndOfMonth().toEpochDay()) }
            .filter { it > 0 }
        if (past.isNotEmpty()) return past.sum() / past.size
        return finance.livingCostNow(month.atDay(1).toEpochDay(), today)
    }

    /**
     * Month-end spend if the pace so far continues, plus bills still to pay this month.
     * [dayOfMonth] is today's day (1-based).
     */
    fun forecast(spentSoFar: Long, dayOfMonth: Int, daysInMonth: Int, unpaidBills: Long): Long {
        if (dayOfMonth <= 0) return spentSoFar + unpaidBills
        return spentSoFar * daysInMonth / dayOfMonth + unpaidBills
    }
}
