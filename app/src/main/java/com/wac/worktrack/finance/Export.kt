package com.wac.worktrack.finance

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.wac.worktrack.core.Categories
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.Money
import com.wac.worktrack.core.db
import com.wac.worktrack.data.TxnType
import java.time.format.DateTimeFormatter

/** A month's accepted transactions as CSV (for spreadsheets) or a one-look PDF report. */
object Export {
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE

    private fun csvCell(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    suspend fun csv(context: Context, month: LongRange, uri: Uri): Int {
        val txns = context.db.finance().acceptedNow(month.first, month.last)
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w ->
            w.write("Date,Type,Category,Amount,Method,Note,Merchant\n")
            for (t in txns) {
                val amount = (if (t.type == TxnType.EXPENSE) "-" else "") + Money.input(t.amountPaise)
                w.write(
                    listOf(Day.date(t.date).format(iso), t.type.name.lowercase(), t.category, amount, t.method, t.note, t.merchant ?: "")
                        .joinToString(",") { csvCell(it) } + "\n"
                )
            }
        } ?: error("Couldn't open the file")
        return txns.size
    }

    suspend fun pdf(context: Context, month: LongRange, title: String, uri: Uri): Int {
        val txns = context.db.finance().acceptedNow(month.first, month.last)
        val income = txns.filter { it.type == TxnType.INCOME }.sumOf { it.amountPaise }
        val spending = txns.filter { it.type == TxnType.EXPENSE && it.category != Categories.SAVINGS }
        val spent = spending.sumOf { it.amountPaise }
        val byCategory = spending.groupBy { it.category }
            .mapValues { (_, l) -> l.sumOf { it.amountPaise } }.toList().sortedByDescending { it.second }

        val doc = PdfDocument()
        val width = 595 // A4 at 72 dpi
        val height = 842
        val margin = 40f
        val text = Paint().apply { textSize = 10f; isAntiAlias = true }
        val bold = Paint(text).apply { typeface = Typeface.DEFAULT_BOLD }
        val heading = Paint(bold).apply { textSize = 18f }
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun newPage() {
            page?.let { doc.finishPage(it) }
            page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, ++pageNo).create())
            y = margin
        }
        fun line(left: String, right: String = "", paint: Paint = text, gap: Float = 15f) {
            if (y + gap > height - margin) newPage()
            y += gap
            page!!.canvas.drawText(left, margin, y, paint)
            if (right.isNotEmpty()) page!!.canvas.drawText(right, width - margin - paint.measureText(right), y, paint)
        }

        newPage()
        line("WorkTrack · $title", paint = heading, gap = 22f)
        line("")
        line("Income", Money.format(income), bold)
        line("Spent", Money.format(spent), bold)
        line(if (income >= spent) "Saved" else "Over", Money.format(kotlin.math.abs(income - spent)), bold)
        line("")
        line("Spending by category", paint = bold, gap = 20f)
        byCategory.forEach { (c, v) -> line(c, Money.format(v)) }
        line("")
        line("All entries", paint = bold, gap = 20f)
        for (t in txns) {
            val sign = if (t.type == TxnType.INCOME) "+" else "−"
            val what = listOf(t.note.ifBlank { t.merchant ?: "" }, t.category).filter { it.isNotBlank() }.joinToString(" · ")
            line("${Day.short(t.date)}   ${what.take(70)}", sign + Money.format(t.amountPaise))
        }
        page?.let { doc.finishPage(it) }
        try {
            context.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) } ?: error("Couldn't open the file")
        } finally {
            doc.close()
        }
        return txns.size
    }
}
