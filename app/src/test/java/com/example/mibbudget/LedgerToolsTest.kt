package com.example.mibbudget

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LedgerToolsTest {
    private lateinit var context: Context
    private val csv = "POSTED DATE,VALUE DATE,TRANSACTION TYPE,REFERENCE,DESCRIPTION,AMOUNT,RUNNING BALANCE\n2026-11-05,2026-11-05,Internet banking transfer,REF123456,Payment | Aishath | Bank,-100.00,900.00"
    @Before fun reset() {
        context=RuntimeEnvironment.getApplication()
        context.deleteDatabase("budget.db")
        listOf("appearance","import_health","transaction_recipients").forEach { context.getSharedPreferences(it,Context.MODE_PRIVATE).edit().clear().commit() }
    }
    private fun payment(store: TransactionStore, key: String="payment", amount: Long=-10000): Tx {
        assertTrue(store.add(amount,"Transfers","Favara transfer",key,"2026-11-05T12:00:00",sourceAccount="MIB"))
        return store.all().first { it.fingerprint==key }
    }
    @Test fun splitConservesLedgerBalanceAndUsesExactCategoryAmounts() {
        TransactionStore(context).use { store ->
            val tx=payment(store)
            store.saveAllocations(tx.id,listOf(SpendingAllocation("Food",6000),SpendingAllocation("Shopping",4000)))
            assertEquals(listOf(tx),store.all())
            assertEquals(10000L,FinanceCalculations.expenseTotal(store.spendingEntries()))
            assertEquals(listOf(-6000L,-4000L),store.spendingEntries().map { it.amountLaari })
            assertEquals(90000L,FinanceCalculations.projectedBalance("MIB",100000,"2026-11-05",store.all(),store.accounts(),anchorTime="11:00:00"))
            assertFalse(store.updateTransaction(tx.id,-11000,"Transfers","Changed"))
        }
    }
    @Test fun invalidSplitCannotChangeExistingAllocations() {
        TransactionStore(context).use { store ->
            val tx=payment(store)
            val parts=listOf(SpendingAllocation("Food",6000),SpendingAllocation("Shopping",4000))
            store.saveAllocations(tx.id,parts)
            assertTrue(runCatching { store.saveAllocations(tx.id,parts.map { it.copy(amount=100) }) }.isFailure)
            assertEquals(parts,store.allocations()[tx.fingerprint])
        }
    }
    @Test fun categoryBatchUndoIsAtomicAndRestoresSplits() {
        TransactionStore(context).use { store ->
            val a=payment(store,"a");val b=payment(store,"b")
            store.saveAllocations(a.id,listOf(SpendingAllocation("Food",6000),SpendingAllocation("Shopping",4000)))
            store.setCategories(listOf(a.id,b.id),"Transport")
            val action=LedgerRecovery.recent(store.readableDatabase).first()
            assertTrue(store.allocations().isEmpty())
            assertTrue(LedgerRecovery.undo(store.writableDatabase,action.key))
            assertEquals(setOf("Transfers"),store.all().map { it.category }.toSet())
            assertEquals(2,store.allocations()[a.fingerprint]!!.size)
            assertFalse(LedgerRecovery.undo(store.writableDatabase,action.key))
        }
    }
    @Test fun conflictingBatchUndoDoesNotPartiallyRevert() {
        TransactionStore(context).use { store ->
            val a=payment(store,"a");val b=payment(store,"b")
            store.setAccounts(listOf(a.fingerprint,b.fingerprint),"BML")
            val action=LedgerRecovery.recent(store.readableDatabase).first()
            store.setAccount(b.fingerprint,"Cash")
            assertFalse(LedgerRecovery.undo(store.writableDatabase,action.key))
            assertEquals("BML",store.accounts()[a.fingerprint]);assertEquals("Cash",store.accounts()[b.fingerprint])
        }
    }
    @Test fun csvLinkUnlinkUndoAndBackupRoundTrip() {
        val backup=TransactionStore(context).use { store ->
            val tx=payment(store)
            store.mergeCsvDetails(csv,2,tx.id)
            assertEquals("Aishath",store.csvDetails()[tx.fingerprint]!!.recipient)
            store.unlinkCsv(tx.fingerprint)
            assertTrue(store.csvDetails().isEmpty())
            assertTrue(LedgerRecovery.undo(store.writableDatabase,LedgerRecovery.recent(store.readableDatabase).first().key))
            store.saveAllocations(tx.id,listOf(SpendingAllocation("Food",6000),SpendingAllocation("Shopping",4000)))
            FullBackup.export(context,store)
        }
        context.deleteDatabase("budget.db")
        TransactionStore(context).use { store ->
            val preview=FullBackup.preview(context,store,backup)
            assertEquals(1,preview.database.tableCounts["csv_transaction_details"])
            store.restoreJson(backup)
            assertEquals("Aishath",store.csvDetails()["payment"]!!.recipient)
            assertEquals(2,store.allocations()["payment"]!!.size)
            assertEquals(10000L,FinanceCalculations.expenseTotal(store.spendingEntries()))
            assertEquals(1,store.all().size)
            store.restoreJson(backup)
            assertEquals(1,store.all().size)
        }
    }
    @Test fun oldBackupWithoutNewMetadataStillRestores() {
        val backup=TransactionStore(context).use { store -> payment(store);JSONObject(store.exportJson()).apply {
            remove("spending_allocations");remove("ledger_recovery");remove("csv_transaction_details")
        }.toString() }
        context.deleteDatabase("budget.db")
        TransactionStore(context).use { store -> store.restoreJson(backup);assertEquals(1,store.all().size) }
    }
    @Test fun preferencePreviewCountsConflictsAndAllowsKeepingSettings() {
        val prefs=context.getSharedPreferences("appearance",Context.MODE_PRIVATE)
        prefs.edit().putString("palette","Emerald").commit()
        TransactionStore(context).use { store ->
            val backup=FullBackup.export(context,store)
            prefs.edit().putString("palette","Sapphire").commit()
            assertEquals(1,FullBackup.preview(context,store,backup).overwrittenSettings)
            FullBackup.restore(context,store,backup,false)
            assertEquals("Sapphire",prefs.getString("palette",null))
        }
    }
    @Test fun sameMerchantRepeatedPaymentsAreNotSuppressed() {
        TransactionStore(context).use { store ->
            store.add(-10000,"Food","COFFEE BEAN","csv-one","2026-11-05T12:00:00",sourceAccount="MIB")
            assertTrue(store.add(-10000,"Food","COFFEE BEAN","sms-two","2026-11-05T12:02:00",sourceAccount="MIB"))
            assertEquals(2,store.all().size)
        }
    }
    @Test fun matchingCsvOnDifferentAccountCannotSuppressSms() {
        TransactionStore(context).use { store ->
            store.add(-10000,"Food","COFFEE BEAN","csv-one","2026-11-05T12:00:00")
            store.setAccount("csv-one","BML")
            assertTrue(store.add(-10000,"Food","COFFEE BEAN","sms-two","2026-11-05T12:00:00",sourceAccount="MIB"))
        }
    }
    @Test fun exactDuplicatesDetectedAndCrossAccountMergeBlocked() {
        TransactionStore(context).use { store ->
            val a=payment(store,"a");val b=payment(store,"b")
            assertEquals(1,store.exactDuplicateGroups().size)
            store.setAccount(b.fingerprint,"BML")
            assertTrue(runCatching { store.mergeExactDuplicates(listOf(a.id,b.id)) }.isFailure)
            assertEquals(2,store.all().size)
        }
    }
    @Test fun migrationFromNinePreservesExistingPayment() {
        TransactionStore(context).use { store -> payment(store) }
        SQLiteDatabase.openDatabase(context.getDatabasePath("budget.db").path,null,SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("DROP TABLE spending_allocations");db.execSQL("DROP TABLE ledger_recovery");db.version=9
        }
        TransactionStore(context).use { store ->
            assertEquals(1,store.all().size)
            assertTrue(store.allocations().isEmpty())
            assertEquals(10,store.readableDatabase.version)
        }
    }
}
