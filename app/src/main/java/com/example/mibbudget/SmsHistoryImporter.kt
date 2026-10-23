package com.example.mibbudget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** User-triggered, bounded inbox scan. Never retains SMS bodies. */
object SmsHistoryImporter {
    data class Result(val scanned: Int, val imported: Int, val duplicates: Int, val ignored: Int)
    fun importPrevious(context: Context, store: TransactionStore, months: Long): Result {
        check(context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED)
        val cutoff = LocalDate.now().minusMonths(months).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        var scanned=0; var imported=0; var duplicates=0; var ignored=0
        val uri = Telephony.Sms.Inbox.CONTENT_URI
        context.contentResolver.query(uri, arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?", arrayOf(cutoff.toString()), "${Telephony.Sms.DATE} DESC")?.use { cursor ->
            val bodyIndex=cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            while(cursor.moveToNext() && scanned < 10000) {
                scanned++
                val body=cursor.getString(bodyIndex) ?: continue
                val declined = BankSmsParser.parseDeclined(body)
                if (declined != null) {
                    if (store.addDeclined(declined)) imported++ else duplicates++
                    continue
                }
                val foreign = BankSmsParser.parseBmlCard(body)
                if (foreign != null) {
                    if (store.addForeign(foreign)) imported++ else duplicates++
                    continue
                }
                val candidate=BankSmsParser.parse(body)
                if(candidate==null) { ignored++; continue }
                if(store.add(candidate.amountLaari,candidate.category,candidate.description,candidate.fingerprint,candidate.transactionTime)) imported++ else duplicates++
            }
        }
        return Result(scanned,imported,duplicates,ignored)
    }
}
