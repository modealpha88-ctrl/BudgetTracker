package com.example.mibbudget

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Opt-in: only process one verified BML package and one confirmed credit template. */
class BmlNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName != "mv.com.bml.mib") return
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
        if (!title.equals("Favara Funds Received", ignoreCase = true)) return
        val amount = Regex("""(?i)^You have received MVR ([\d,]+\.\d{2}) to your [\d*Xx]+ from another bank\.$""")
            .matchEntire(body.trim())?.groupValues?.get(1)?.let(Money::parseLaari) ?: return
        if (amount <= 0) return
        val timestamp = Instant.ofEpochMilli(sbn.postTime).atZone(ZoneId.systemDefault())
            .toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val key = "BML|" + sbn.key + "|" + sbn.postTime + "|" + title + "|" + body
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        TransactionStore(this).use { it.queueBml(amount, timestamp, fingerprint) }
    }
}
