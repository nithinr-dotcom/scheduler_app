package com.wac.worktrack.finance

import com.wac.worktrack.data.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TxnParserTest {

    private fun parse(s: String, method: String = "Bank") = TxnParser.parse(s, method)

    @Test
    fun upiDebitWithVpaAndRef() {
        val p = parse("Rs.250.00 debited from A/c XX1234 to VPA swiggy@icici on 28-10-26. UPI Ref 429112345678. Not you? Call 1800")!!
        assertEquals(25000L, p.amountPaise)
        assertEquals(TxnType.EXPENSE, p.type)
        assertEquals("swiggy@icici", p.merchant)
        assertEquals("429112345678", p.ref)
        assertEquals("UPI", p.method)
    }

    @Test
    fun sentStyleWithUpiColonRef() {
        val p = parse("Sent Rs.1,499.00 From HDFC Bank A/C x1234 To AMAZON PAY On 27/10/26 Ref 430012345678 Not You? Call 18002586161")!!
        assertEquals(149900L, p.amountPaise)
        assertEquals(TxnType.EXPENSE, p.type)
        assertEquals("430012345678", p.ref)
    }

    @Test
    fun creditIgnoresAvailableBalance() {
        val p = parse("Your A/c XX5678 is credited with INR 45,000.00 on 01-11-26 by NEFT from ACME PVT LTD. Avl Bal INR 1,20,500.50")!!
        assertEquals(4500000L, p.amountPaise)
        assertEquals(TxnType.INCOME, p.type)
        assertEquals("Bank", p.method)
    }

    @Test
    fun balanceFirstStillFindsTransactionAmount() {
        val p = parse("Avl Bal Rs 10,000.00. Rs 300 debited from a/c XX12 to rahul@okaxis on 12-10-26 UPI Ref No 512345678901")!!
        assertEquals(30000L, p.amountPaise)
        assertEquals("rahul@okaxis", p.merchant)
    }

    @Test
    fun cardSpendNamedMerchant() {
        val p = parse("INR 820.00 spent on ICICI Bank Card XX9876 on 15-Oct-26 at DOMINOS PIZZA. Avl Limit: INR 45,000.00")!!
        assertEquals(82000L, p.amountPaise)
        assertEquals(TxnType.EXPENSE, p.type)
        assertEquals("DOMINOS PIZZA", p.merchant)
        assertEquals("Card", p.method)
    }

    @Test
    fun earliestKeywordDecidesDirection() {
        // "debited" comes before "credited" → it's money going out.
        val p = parse("Rs 500 debited from A/c XX1 and credited to friend@ybl. UPI Ref 600012345678")!!
        assertEquals(TxnType.EXPENSE, p.type)
    }

    @Test
    fun gpayNotification() {
        val p = parse("₹120 paid to Chai Point. Paid via UPI", "UPI")!!
        assertEquals(12000L, p.amountPaise)
        assertEquals(TxnType.EXPENSE, p.type)
        assertEquals("Chai Point", p.merchant)
    }

    @Test
    fun receivedNotification() {
        val p = parse("You received ₹2,000. Money received from Priya", "UPI")!!
        assertEquals(TxnType.INCOME, p.type)
        assertEquals(200000L, p.amountPaise)
    }

    @Test
    fun ignoresOtpRequestsAndFailures() {
        assertNull(parse("123456 is your OTP for txn of Rs 2,000 at AMAZON. Do not share."))
        assertNull(parse("Rahul has requested Rs 500 from you via UPI. Pay now on GPay"))
        assertNull(parse("Txn of Rs 750 at FLIPKART failed. Amount will be refunded"))
        assertNull(parse("Your credit card bill of Rs 5,400 is due on 05-11-26. Minimum amount due Rs 270"))
        assertNull(parse("Rs 999 will be debited on 01-11 for NETFLIX via e-mandate"))
    }

    @Test
    fun ignoresMessagesWithoutAmountOrDirection() {
        assertNull(parse("Dear customer, your KYC is updated."))
        assertNull(parse("Rs 500 balance in your account"))
    }

    @Test
    fun looksLikeBankRejectsPhoneNumbers() {
        assertNotNull(TxnParser.parse("Rs 50 debited from a/c XX1"))
        assert(Capture.looksLikeBank("VM-HDFCBK", "Rs 50 debited from a/c XX1"))
        assert(!Capture.looksLikeBank("+919876543210", "Rs 50 debited from a/c XX1"))
        assert(!Capture.looksLikeBank("VM-OFFERS", "Big sale today"))
    }

    @Test
    fun paidYouIsIncome() {
        val p = parse("Rahul paid you ₹500. Tap to view", "UPI")!!
        assertEquals(TxnType.INCOME, p.type)
        assertEquals(50000L, p.amountPaise)
        assertEquals(TxnType.INCOME, parse("Priya sent you ₹1,200", "UPI")!!.type)
    }

    @Test
    fun sbiDebitWithoutCurrency() {
        val p = parse("Dear UPI user A/C X1234 debited by 150.0 on date 28Oct26 trf to ZOMATO Refno 429112345678. If not u? call 1800111109. -SBI")!!
        assertEquals(15000L, p.amountPaise)
        assertEquals(TxnType.EXPENSE, p.type)
        assertEquals("ZOMATO", p.merchant)
        assertEquals("429112345678", p.ref)
    }

    @Test
    fun creditCardBillPaymentIsNotIncome() {
        assertNull(parse("Payment of Rs 10,000 received towards your Credit Card XX4321. Thank you."))
        assertNull(parse("We have received payment of INR 5,000.00 towards your SBI credit card ending 1234"))
    }
}
