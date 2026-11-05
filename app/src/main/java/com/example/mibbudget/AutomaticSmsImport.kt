package com.example.mibbudget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object AutomaticSmsImport {
    private const val PERIODIC_WORK = "bank-sms-catch-up"
    private const val IMMEDIATE_WORK = "bank-sms-catch-up-now"

    fun enabled(context: Context) = context.getSharedPreferences("sms_settings", Context.MODE_PRIVATE)
        .getBoolean("enabled", false)

    /** Completion is durable, even if the original CSV rows were later merged or removed. */
    fun setupComplete(context: Context, store: TransactionStore): Boolean {
        val prefs = context.getSharedPreferences("initial_csv_setup", Context.MODE_PRIVATE)
        if (prefs.getBoolean("completed", false)) return true
        if (store.all().none { it.fingerprint.startsWith("csv-") }) return false
        prefs.edit().putBoolean("completed", true).apply()
        return true
    }

    fun hasInboxAccess(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** Called on enable, permission changes and every foreground resume. */
    fun reconcile(context: Context) {
        val manager = WorkManager.getInstance(context)
        if (!enabled(context) || !hasInboxAccess(context)) {
            manager.cancelUniqueWork(PERIODIC_WORK)
            manager.cancelUniqueWork(IMMEDIATE_WORK)
            return
        }
        manager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SmsCatchUpWorker>(15, TimeUnit.MINUTES).build())
        manager.enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SmsCatchUpWorker>().build())
    }

    /** Returns true when another bounded batch is needed. Never persists SMS bodies. */
    @Synchronized
    fun catchUp(context: Context): Boolean {
        if (!enabled(context) || !hasInboxAccess(context)) return false
        TransactionStore(context).use { store ->
            if (!setupComplete(context, store)) return false
            // Device-local inbox IDs must never travel in a financial backup.
            val prefs = context.getSharedPreferences("sms_inbox_sync", Context.MODE_PRIVATE)
            val lastId = prefs.getLong("last_scanned_inbox_id", -1L)
            // First opt-in catches up the last month. Later scans use inbox IDs, so delayed
            // deliveries and messages sharing a timestamp cannot fall through a time watermark.
            val cutoff = LocalDate.now().minusMonths(1).atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()
            val selection = if (lastId < 0) "${Telephony.Sms.DATE} >= ?" else "${Telephony.Sms._ID} > ?"
            val args = arrayOf(if (lastId < 0) cutoff.toString() else lastId.toString())
            val cursor = context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY), selection, args,
                "${Telephony.Sms._ID} ASC") ?: error("SMS inbox unavailable")
            cursor.use {
                val idColumn = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val bodyColumn = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                var scanned = 0
                var completedId = lastId
                try {
                    while (scanned < 1000 && it.moveToNext()) {
                        // Recheck opt-out/revoked access while a batch is in progress.
                        if (!enabled(context) || !hasInboxAccess(context)) return false
                        BankSmsImporter.importBody(store, it.getString(bodyColumn).orEmpty())
                        completedId = it.getLong(idColumn)
                        scanned++
                    }
                } finally {
                    // Failed rows are retried; successful rows are safe to replay after a crash
                    // because all three import paths use the same deduplication fingerprints.
                    if (completedId != lastId) prefs.edit()
                        .putLong("last_scanned_inbox_id", completedId).apply()
                }
                return scanned == 1000 && !it.isLast
            }
        }
    }
}

class SmsCatchUpWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = try {
        if (AutomaticSmsImport.catchUp(applicationContext)) Result.retry() else Result.success()
    } catch (_: SecurityException) {
        // Permissions may be revoked between the check and provider query.
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}
