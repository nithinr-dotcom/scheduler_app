package com.wac.worktrack.finance

import com.wac.worktrack.core.Money
import com.wac.worktrack.data.TxnType

/**
 * Pulls a payment out of a bank SMS or payment-app notification, e.g.
 * "Rs.250.00 debited from A/c XX1234 to VPA swiggy@icici on 28-10-26. UPI Ref 429112345678".
 * Deliberately conservative: anything that looks like an OTP, request, offer or failure is ignored.
 */
object TxnParser {

    data class Parsed(
        val amountPaise: Long,
        val type: TxnType,
        val merchant: String?,
        val ref: String?,
        val method: String,
    )

    private val ignore = Regex(
        """\b(otp|one[- ]time|verification code|requested|request|will be (debited|charged|deducted)|due (on|date|by)|""" +
            """minimum (amount|due)|failed|declined|unsuccessful|reversed|statement|e-?mandate|autopay|""" +
            """offer|cashback|loan|pre-?approved|bill (generated|is due)|reminder)\b""" +
            // A credit-card bill payment "received" by the card is not income; the bank debit already counts it.
            """|towards (?:your )?(?:[a-z]+ )?credit card|credit card payment""",
        RegexOption.IGNORE_CASE,
    )
    private val amount = Regex("""(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    /** SBI-style "debited by 150.0" with no currency marker. */
    private val bareAmount = Regex("""\b(?:debited|credited)\s+(?:by|for|with)\s+([0-9][0-9,]*(?:\.[0-9]{1,2})?)\b""", RegexOption.IGNORE_CASE)
    /** "Rahul paid you ₹500" / "sent you" — money coming in, even though it says "paid". */
    private val creditToYou = Regex("""\b(?:paid|sent|transferred)\s+you\b""", RegexOption.IGNORE_CASE)
    private val debit = Regex("""\b(debited|spent|paid|sent|withdrawn|purchased?|used for|deducted)\b""", RegexOption.IGNORE_CASE)
    private val credit = Regex("""\b(credited|received|deposited|refunded|refund of)\b""", RegexOption.IGNORE_CASE)
    private val refKeyword = Regex(
        """(?:upi\s*ref(?:erence)?|ref(?:erence)?|rrn|utr|txn\s*id|transaction\s*id)[^0-9a-z]{0,12}(?:no\.?|number|id)?[^0-9]{0,4}([0-9]{9,16})""",
        RegexOption.IGNORE_CASE,
    )
    private val upiBare = Regex("""\bupi[:/\s-]+([0-9]{12})\b""", RegexOption.IGNORE_CASE)
    private val vpa = Regex("""\b([a-z0-9.\-_]{2,})@([a-z]{2,})\b""", RegexOption.IGNORE_CASE)
    private val name = Regex(
        """\b(?:to|at|towards|from|by)\s+(?!a/?c\b|ac\b|your\b|account\b|card\b|bank\b|vpa\b|upi\b)""" +
            """([a-z][a-z0-9&.'\- ]{1,40}?)(?=\s+(?:on|via|ref|refno|upi|for|dated|avl|avail|using|thru|through|with|is|has|was)\b|\s*[.,;:(]|$)""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String, defaultMethod: String = "Bank"): Parsed? {
        val body = text.replace('\n', ' ')
        if (ignore.containsMatchIn(body)) return null

        val paise = firstAmount(body) ?: bareAmount.find(body)?.let { Money.parse(it.groupValues[1]) } ?: return null
        val d = debit.find(body)?.range?.first
        val c = credit.find(body)?.range?.first
        val type = when {
            creditToYou.containsMatchIn(body) -> TxnType.INCOME
            d == null && c == null -> return null
            c == null -> TxnType.EXPENSE
            d == null -> TxnType.INCOME
            d < c -> TxnType.EXPENSE
            else -> TxnType.INCOME
        }

        val ref = refKeyword.find(body)?.groupValues?.get(1) ?: upiBare.find(body)?.groupValues?.get(1)
        val merchant = vpa.find(body)?.value?.lowercase()
            ?: name.find(body)?.groupValues?.get(1)?.trim()?.trimEnd('.', '-')?.takeIf { it.length >= 2 }
        val lower = body.lowercase()
        val method = when {
            "upi" in lower || vpa.containsMatchIn(body) -> "UPI"
            "card" in lower -> "Card"
            "atm" in lower || "withdrawn" in lower -> "Cash"
            listOf("neft", "imps", "rtgs").any { it in lower } -> "Bank"
            else -> defaultMethod
        }
        return Parsed(paise, type, merchant, ref, method)
    }

    /** First amount that isn't an "available balance" / "limit" figure. */
    private fun firstAmount(body: String): Long? {
        for (m in amount.findAll(body)) {
            val before = body.substring(maxOf(0, m.range.first - 15), m.range.first).lowercase()
            if ("bal" in before || "limit" in before) continue
            return Money.parse(m.groupValues[1]) ?: continue
        }
        return null
    }
}
