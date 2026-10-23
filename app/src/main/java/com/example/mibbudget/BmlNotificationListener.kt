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
    companion object {
        @Volatile private var connected: BmlNotificationListener? = null
        fun importVisible(): Int? = connected?.scanActive()
    }
    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = this
    }
    override fun onListenerDisconnected() {
        connected = null
        super.onListenerDisconnected()
    }
    private fun scanActive(): Int {
        var count = 0
        val current = try { activeNotifications ?: emptyArray() } catch (_: Exception) { emptyArray() }
        for (item in current) if (queueIfSupported(item)) count++
        return count
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn != null) queueIfSupported(sbn)
    }
    private fun queueIfSupported(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName != "mv.com.bml.mib") return false
        val extras = sbn.notification.extras ?: return false
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return false
        if (!title.equals("Favara Funds Received", ignoreCase = true)) return false
        val amount = Regex("""(?i)^You have received MVR ([\d,]+\.\d{2}) to your [\d*Xx]+ from another bank\.$""")
            .matchEntire(body.trim())?.groupValues?.get(1)?.let(Money::parseLaari) ?: return false
        if (amount <= 0) return false
        val timestamp = Instant.ofEpochMilli(sbn.postTime).atZone(ZoneId.systemDefault())
            .toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val key = "BML|" + sbn.key + "|" + sbn.postTime + "|" + title + "|" + body
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return TransactionStore(this).use { it.queueBml(amount, timestamp, fingerprint) }
    }
}
