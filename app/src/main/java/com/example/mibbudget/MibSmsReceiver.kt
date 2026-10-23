package com.example.mibbudget

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony

/** Opt-in RECEIVE_SMS only; never reads inbox history, stores raw SMS, or sends SMS. */
class MibSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!context.getSharedPreferences("sms_settings", Context.MODE_PRIVATE).getBoolean("enabled", false)) return
        if (context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return
        // Multipart SMS: group parts by originating address, preserving received order.
        val grouped = messages.groupBy { it.originatingAddress.orEmpty() }
        TransactionStore(context).use { store ->
            // A statement baseline must be imported before SMS can add gap transactions.
            if (store.all().none { it.fingerprint.startsWith("csv-") }) return
            for ((_, parts) in grouped) {
                val body = parts.joinToString("") { it.messageBody.orEmpty() }
                val declined = BankSmsParser.parseDeclined(body)
                if (declined != null) {
                    store.addDeclined(declined)
                    continue
                }
                val bmlCard = BankSmsParser.parseBmlCard(body)
                if (bmlCard != null) { store.addForeign(bmlCard); continue }
                val candidate = BankSmsParser.parse(body) ?: continue
                // Sender names/numbers can be spoofed: the parser is not bank authentication.
                store.add(candidate.amountLaari, candidate.category, candidate.description,
                    candidate.fingerprint, candidate.transactionTime)
            }
        }
    }
}
