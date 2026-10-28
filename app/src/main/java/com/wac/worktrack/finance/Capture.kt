package com.wac.worktrack.finance

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wac.worktrack.core.Categories
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.Prefs
import com.wac.worktrack.core.db
import com.wac.worktrack.data.MerchantRule
import com.wac.worktrack.data.Txn
import com.wac.worktrack.data.TxnSource
import com.wac.worktrack.data.TxnStatus
import com.wac.worktrack.data.TxnType
import com.wac.worktrack.notify.Notifs
import com.wac.worktrack.notify.launchAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Auto-captured payments only ever land in the Pending inbox. Nothing counts toward totals or
 * budgets until the user accepts it. Raw message text is never stored — only the parsed fields.
 */
object Capture {
    /** Same amount + direction within this window from another source is treated as a duplicate. */
    private const val DUPLICATE_WINDOW_MILLIS = 15 * 60_000L

    /** Hand-entered or bill entries this many days either side are checked for a match. */
    const val RECORDED_WINDOW_DAYS = 3

    /** Rules are learned separately for money in and out, so a refund from a shop isn't filed as spending. */
    fun merchantKey(merchant: String, type: TxnType) =
        (if (type == TxnType.INCOME) "in:" else "") + merchant.lowercase().trim()

    /** Returns true if a new pending entry was created. */
    suspend fun add(context: Context, p: TxnParser.Parsed, source: TxnSource, at: Long): Boolean {
        val finance = context.db.finance()
        if (finance.similarCaptured(p.amountPaise, p.type, source, p.ref, at, DUPLICATE_WINDOW_MILLIS) > 0) return false
        val category = p.merchant?.let { finance.ruleFor(merchantKey(it, p.type)) } ?: Categories.UNCATEGORIZED
        val id = finance.insertIgnore(
            Txn(
                amountPaise = p.amountPaise, type = p.type, category = category, date = Day.ofMillis(at),
                timeMillis = at, method = p.method, status = TxnStatus.PENDING, source = source,
                ref = p.ref, merchant = p.merchant,
            )
        )
        if (id == -1L) return false // same bank reference already captured (or rejected)
        Notifs.pendingPayments(context, finance.pendingCountNow())
        return true
    }

    suspend fun accept(context: Context, txn: Txn) {
        val accepted = txn.copy(status = TxnStatus.ACCEPTED)
        context.db.finance().upsert(accepted)
        MoneyOps.onIncomeAccepted(context, accepted)
        // Learn the category so the next payment to this merchant is pre-filled.
        if (txn.merchant != null && txn.category != Categories.UNCATEGORIZED) {
            context.db.finance().upsertRule(MerchantRule(merchantKey(txn.merchant, txn.type), txn.category))
        }
        Notifs.pendingPayments(context, context.db.finance().pendingCountNow())
    }

    suspend fun reject(context: Context, txn: Txn) {
        // Kept as REJECTED so the same message is never captured again.
        context.db.finance().upsert(txn.copy(status = TxnStatus.REJECTED))
        Notifs.pendingPayments(context, context.db.finance().pendingCountNow())
    }

    /**
     * "Already recorded?" hint for a captured payment that matches one entered by hand, from a
     * bill or a recurring item a few days either side. It stays in Pending (it may be a second,
     * real payment) so the user decides.
     */
    suspend fun recordedTwin(context: Context, txn: Txn): String? {
        if (txn.source != TxnSource.SMS && txn.source != TxnSource.NOTIFICATION) return null
        val twin = context.db.finance().recordedLike(
            txn.amountPaise, txn.type, txn.date - RECORDED_WINDOW_DAYS, txn.date + RECORDED_WINDOW_DAYS,
        ) ?: return null
        return "Already recorded? Matches “${twin.note.ifBlank { twin.category }}” on ${Day.short(twin.date)}."
    }

    /** Undo a reject: back to Pending for another look. */
    suspend fun restore(context: Context, txn: Txn) {
        context.db.finance().upsert(txn.copy(status = TxnStatus.PENDING))
        Notifs.pendingPayments(context, context.db.finance().pendingCountNow())
    }

    /** Bank alerts come from alphanumeric sender ids like "VM-HDFCBK", never from phone numbers. */
    fun looksLikeBank(sender: String?, body: String): Boolean {
        if (sender == null || sender.none { it.isLetter() }) return false
        val lower = body.lowercase()
        return listOf("a/c", "acct", "account", "card", "upi", "bank", "wallet", "vpa").any { it in lower }
    }

    fun hasSmsPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    /** Reads the last [days] of bank SMS from the inbox into the review list. Returns entries added. */
    suspend fun scanInbox(context: Context, days: Int = 30): Int {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return 0
        val since = System.currentTimeMillis() - days * 86_400_000L
        val found = mutableListOf<Triple<String, String, Long>>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?", arrayOf(since.toString()), "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            while (c.moveToNext()) found += Triple(c.getString(0) ?: "", c.getString(1) ?: "", c.getLong(2))
        }
        var added = 0
        for ((sender, body, at) in found) {
            if (!looksLikeBank(sender, body)) continue
            val parsed = TxnParser.parse(body) ?: continue
            if (add(context, parsed, TxnSource.SMS, at)) added++
        }
        return added
    }

    fun notificationAccessGranted(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(context, PaymentNotificationListener::class.java).flattenToString()
        return enabled.split(':').any { it == me }
    }
}

/** New bank SMS → parsed → Pending inbox. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION || !Prefs(context).smsCapture) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // A long SMS arrives in several parts; join them per sender.
        val bySender = parts.groupBy { it.originatingAddress }
        launchAsync {
            for ((sender, msgs) in bySender) {
                val body = msgs.joinToString("") { it.messageBody ?: "" }
                if (!Capture.looksLikeBank(sender, body)) continue
                val parsed = TxnParser.parse(body) ?: continue
                Capture.add(context, parsed, TxnSource.SMS, msgs.first().timestampMillis)
            }
        }
    }
}

/** Backup capture from GPay / PhonePe / Paytm notifications, for when the bank SMS is late or missing. */
class PaymentNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in PAYMENT_APPS || !Prefs(applicationContext).notificationCapture) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString().orEmpty()
        val parsed = TxnParser.parse("$title. $text", defaultMethod = "UPI") ?: return
        scope.launch { Capture.add(applicationContext, parsed, TxnSource.NOTIFICATION, sbn.postTime) }
    }

    companion object {
        val PAYMENT_APPS = setOf(
            "com.google.android.apps.nbu.paisa.user", // Google Pay (India)
            "com.phonepe.app",
            "net.one97.paytm",
        )
    }
}
