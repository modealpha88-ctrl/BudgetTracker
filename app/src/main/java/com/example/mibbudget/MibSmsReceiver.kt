package com.example.mibbudget

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import java.util.concurrent.Executors

/** Live SMS delivery; background inbox recovery is separately gated by READ_SMS. */
class MibSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!AutomaticSmsImport.enabled(context)) return
        if (context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return
        val grouped = messages.groupBy { it.originatingAddress.orEmpty() }
        val pending = goAsync()
        val appContext = context.applicationContext
        executor.execute {
            try {
                if (!AutomaticSmsImport.enabled(appContext)) return@execute
                TransactionStore(appContext).use { store ->
                    if (!AutomaticSmsImport.setupComplete(appContext, store)) return@use
                    for (parts in grouped.values) {
                        if (!AutomaticSmsImport.enabled(appContext)) break
                        BankSmsImporter.importBody(store, parts.joinToString("") { it.messageBody.orEmpty() })
                    }
                }
            } catch (_: Exception) {
                // The next automatic inbox pass can recover a failed live write.
                runCatching { AutomaticSmsImport.reconcile(appContext) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val executor = Executors.newSingleThreadExecutor()
    }
}
