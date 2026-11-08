package com.example.mibbudget

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.Telephony
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AutomaticSmsImportTest {
    private lateinit var context: Application
    private lateinit var inbox: TestInbox
    private val sms = "Your payment to TEST UTILITY for MVR 50.00 was made on 04/11/2026 12:34:56 with Order ID ORD12345, and reference number 654321."

    @Before fun prepare() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("budget.db")
        listOf("sms_settings", "sms_inbox_sync", "initial_csv_setup").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        context.getSharedPreferences("sms_settings", Context.MODE_PRIVATE).edit().putBoolean("enabled", true).commit()
        context.getSharedPreferences("initial_csv_setup", Context.MODE_PRIVATE).edit().putBoolean("completed", true).commit()
        shadowOf(context).grantPermissions(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
        inbox = TestInbox()
        ShadowContentResolver.registerProviderInternal("sms", inbox)
    }

    @Test fun completedSetupImportsMissedSmsWithoutRemainingCsvRows() {
        inbox.rows += Row(1, sms)
        assertFalse(AutomaticSmsImport.catchUp(context))
        TransactionStore(context).use { store ->
            assertEquals(1, store.all().size)
            assertEquals(-5000L, store.all().single().amountLaari)
            assertEquals("MIB", store.accounts()[store.all().single().fingerprint])
        }
    }

    @Test fun liveThenCatchUpThenManualDoesNotDuplicate() {
        val before = BankSmsImporter.revision.value
        TransactionStore(context).use { store ->
            assertEquals(BankSmsImporter.Outcome.IMPORTED, BankSmsImporter.importBody(store, sms))
            inbox.rows += Row(1, sms)
            AutomaticSmsImport.catchUp(context)
            val history = SmsHistoryImporter.importPrevious(context, store, 1)
            assertEquals(0, history.imported)
            assertEquals(1, history.duplicates)
            assertEquals(1, store.all().size)
            assertEquals(before + 1, BankSmsImporter.revision.value)
        }
    }

    @Test fun disabledOrMissingPermissionNeverScansOrImports() {
        inbox.rows += Row(1, sms)
        context.getSharedPreferences("sms_settings", Context.MODE_PRIVATE).edit().putBoolean("enabled", false).commit()
        AutomaticSmsImport.catchUp(context)
        assertEquals(0, inbox.queries)
        context.getSharedPreferences("sms_settings", Context.MODE_PRIVATE).edit().putBoolean("enabled", true).commit()
        shadowOf(context).denyPermissions(Manifest.permission.READ_SMS)
        AutomaticSmsImport.catchUp(context)
        assertEquals(0, inbox.queries)
    }

    @Test fun incompleteSetupWaitsWithoutAdvancingCheckpoint() {
        context.getSharedPreferences("initial_csv_setup", Context.MODE_PRIVATE).edit().clear().commit()
        inbox.rows += Row(1, sms)
        AutomaticSmsImport.catchUp(context)
        assertEquals(0, inbox.queries)
        assertEquals(-1L, checkpoint())
    }

    @Test fun legacyCsvBaselineMigratesOnceAndStaysComplete() {
        context.getSharedPreferences("initial_csv_setup", Context.MODE_PRIVATE).edit().clear().commit()
        TransactionStore(context).use { store ->
            store.add(-100L, "Other", "Statement row", "csv-test")
            assertTrue(AutomaticSmsImport.setupComplete(context, store))
            store.deleteTransaction(store.all().single().id)
            assertTrue(AutomaticSmsImport.setupComplete(context, store))
        }
    }

    @Test fun sameTimestampAndDelayedDeliveryAreNotLostBetweenScans() {
        val time = System.currentTimeMillis()
        inbox.rows += Row(1, "Unrelated message", time)
        AutomaticSmsImport.catchUp(context)
        inbox.rows += Row(2, sms, time)
        inbox.rows += Row(3, sms.replace("654321", "654322").replace("50.00", "60.00"), time - 1000)
        AutomaticSmsImport.catchUp(context)
        assertEquals(3L, checkpoint())
        TransactionStore(context).use { assertEquals(2, it.all().size) }
    }

    @Test fun fullBatchContinuesWithoutSkippingOrReplayingRows() {
        (1L..1001L).forEach { inbox.rows += Row(it, "Unrelated message") }
        assertTrue(AutomaticSmsImport.catchUp(context))
        assertEquals(1000L, checkpoint())
        assertFalse(AutomaticSmsImport.catchUp(context))
        assertEquals(1001L, checkpoint())
        assertFalse(AutomaticSmsImport.catchUp(context))
    }

    @Test fun unavailableProviderDoesNotAdvanceCheckpoint() {
        inbox.unavailable = true
        assertThrows(IllegalStateException::class.java) { AutomaticSmsImport.catchUp(context) }
        assertEquals(-1L, checkpoint())
        inbox.unavailable = false
        inbox.rows += Row(1, sms)
        AutomaticSmsImport.catchUp(context)
        TransactionStore(context).use { assertEquals(1, it.all().size) }
    }

    @Test fun initialRecoveryIsLimitedToLastMonth() {
        inbox.rows += Row(1, sms, 0)
        inbox.rows += Row(2, "Unrelated current message")
        AutomaticSmsImport.catchUp(context)
        assertEquals(2L, checkpoint())
        TransactionStore(context).use { assertTrue(it.all().isEmpty()) }
    }

    @Test fun upgradeRecoversPreviouslySkippedWithdrawalsOnlyOnce() {
        val withdrawal = "Your CASH WITHDRAWAL from ***1234 for 50.00 MVR at EXAMPLE ATM, MDV on 05.11.26 14:13 was processed successfully. Approval Code: 123987"
        context.getSharedPreferences("sms_inbox_sync", Context.MODE_PRIVATE).edit()
            .putLong("last_scanned_inbox_id", 4L).commit()
        inbox.rows += Row(1, withdrawal, 0)
        inbox.rows += Row(2, sms)
        inbox.rows += Row(3, withdrawal.replace("50.00", "25.00"))
        AutomaticSmsImport.catchUp(context)
        AutomaticSmsImport.catchUp(context)
        TransactionStore(context).use { store ->
            assertEquals(listOf(-2500L, 2500L), store.all().map { it.amountLaari }.sorted())
        }
        assertEquals(3, inbox.queries)
        assertEquals(4L, checkpoint())
    }

    private fun checkpoint() = context.getSharedPreferences("sms_inbox_sync", Context.MODE_PRIVATE)
        .getLong("last_scanned_inbox_id", -1L)

    data class Row(val id: Long, val body: String, val date: Long = System.currentTimeMillis())
    class TestInbox : ContentProvider() {
        val rows = mutableListOf<Row>()
        var queries = 0
        var unavailable = false
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                           selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
            queries++
            if (unavailable) return null
            val bound = requireNotNull(selectionArgs).first().toLong()
            val filtered = rows.filter {
                when {
                    selection?.contains(" LIKE ?") == true -> it.date >= bound &&
                        it.id <= requireNotNull(selectionArgs)[1].toLong() && it.body.contains("cash withdrawal", true)
                    selection?.contains("${Telephony.Sms._ID} >") == true -> it.id > bound
                    else -> it.date >= bound
                }
            }.let { if (sortOrder?.endsWith("DESC") == true) it.sortedByDescending { row -> row.date } else it.sortedBy { row -> row.id } }
            val columns = requireNotNull(projection)
            return MatrixCursor(columns).apply {
                filtered.forEach { row ->
                    addRow(columns.map { column -> when (column) {
                        Telephony.Sms._ID -> row.id
                        Telephony.Sms.BODY -> row.body
                        Telephony.Sms.DATE -> row.date
                        else -> error("Unexpected column")
                    } }.toTypedArray())
                }
            }
        }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Read only")
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("Read only")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = error("Read only")
    }
}
