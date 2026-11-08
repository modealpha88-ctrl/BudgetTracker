package com.example.mibbudget

import android.app.Application
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WithdrawalImportTest {
    private lateinit var context: Application
    private val sms = "Your CASH WITHDRAWAL from ***1234 for 100.00 MVR at EXAMPLE ATM, MDV on 05.11.26 17:31 was processed successfully. Approval Code: 123987"
    private val header = "POSTED DATE,VALUE DATE,TRANSACTION TYPE,REFERENCE,DESCRIPTION,AMOUNT,RUNNING BALANCE"
    private val row = "2026-11-06 00:00:00,2026-11-05,ATM Withdrawal,1-123456789,05-11-2026 17-31-45 | EXAMPLE ATM | Main Road,-100,900"
    private val csv = "$header\n$row"

    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("budget.db")
        context.getSharedPreferences("review_queue", 0).edit().clear().commit()
    }

    private fun assertTransfer(store: TransactionStore) {
        val rows = store.all()
        assertEquals(2, rows.size)
        val labels = store.accounts()
        assertEquals(mapOf("MIB" to -10000L, "Cash" to 10000L),
            rows.associate { labels[it.fingerprint] to it.amountLaari })
        assertTrue(rows.all { it.category == "Internal Transfer" && it.timestamp.startsWith("2026-11-05T17:31") })
        assertEquals(0L, FinanceCalculations.expenseTotal(rows))
        assertEquals(0L, FinanceCalculations.monthIncome(rows, YearMonth.of(2026, 11)))
        assertEquals(90000L, FinanceCalculations.projectedBalance("MIB", 100000, "2026-11-04", rows, labels))
        assertEquals(10000L, FinanceCalculations.projectedBalance("Cash", 0, "2026-11-04", rows, labels))
    }

    @Test fun completedMvrOnly() {
        val candidate = requireNotNull(BankSmsParser.parse(sms))
        assertEquals("EXAMPLE ATM", candidate.atm)
        assertEquals("123987", CsvSmsMatcher.approvalCode(candidate.description))
        assertFalse(candidate.description.contains("1234"))
        listOf(sms.replace("successfully", "unsuccessfully"), sms.replace("MVR", "USD"),
            sms.replace("100.00", "0.00"), sms.replace("05.11.26", "31.02.26"),
            sms.replace("Approval Code", "OTP")).forEach { assertNull(BankSmsParser.parse(it)) }
    }

    @Test fun smsFirstSkipsMatchingStatementAndReplays() = TransactionStore(context).use { store ->
        assertEquals(BankSmsImporter.Outcome.IMPORTED, BankSmsImporter.importBody(store, sms))
        assertEquals(BankSmsImporter.Outcome.DUPLICATE, BankSmsImporter.importBody(store, sms.lowercase()))
        assertEquals("MATCHED", store.reviewCsv(csv).matches.single().status)
        assertEquals(0, store.importMissingCsv(csv).first)
        assertEquals(0, store.importCsv(csv).first)
        assertEquals(1, store.csvDetails().size)
        val otherReference = csv.replace("1-123456789", "1-987654321")
        assertEquals("POSSIBLE", store.reviewCsv(otherReference).matches.single().status)
        assertTransfer(store)
    }

    @Test fun csvFirstReusesDebitAndPreservesStatementDetails() = TransactionStore(context).use { store ->
        assertEquals(1, store.importCsv(csv).first)
        val original = store.all().single()
        assertEquals(BankSmsImporter.Outcome.IMPORTED, BankSmsImporter.importBody(store, sms))
        assertTrue(store.all().any { it.id == original.id && it.fingerprint == original.fingerprint })
        assertEquals(original.description, store.csvDetails()[original.fingerprint]?.bankDescription)
        assertEquals(BankSmsImporter.Outcome.DUPLICATE, BankSmsImporter.importBody(store, sms))
        assertEquals(0, store.importMissingCsv(csv).first)
        assertTransfer(store)
    }

    @Test fun differentApprovalCodesAreSeparateWithdrawals() = TransactionStore(context).use { store ->
        store.importMissingCsv(csv)
        BankSmsImporter.importBody(store, sms)
        BankSmsImporter.importBody(store, sms.replace("123987", "123988"))
        assertEquals(4, store.all().size)
        assertEquals(-20000L, store.all().filter { store.accounts()[it.fingerprint] == "MIB" }.sumOf { it.amountLaari })
    }

    @Test fun ambiguousSameMinuteRequiresSelection() = TransactionStore(context).use { store ->
        store.importMissingCsv(csv + "\n" + row.replace("1-123456789", "1-987654321"))
        assertEquals(BankSmsImporter.Outcome.IGNORED, BankSmsImporter.importBody(store, sms))
        assertEquals(2, store.all().size)
        val entry = ReviewQueue(context).all().single()
        val candidate = requireNotNull(BankSmsParser.parse(sms))
        val chosen = store.withdrawalCandidates(candidate).first()
        assertEquals(BankSmsImporter.Outcome.IMPORTED, store.importWithdrawal(candidate, chosen.id))
        assertEquals(3, store.all().size)
        assertEquals("RESOLVED", ReviewQueue(context).all().single { it.id == entry.id }.status)
    }

    @Test fun missingOrConflictingEvidenceDoesNotConfirm() {
        val tx = requireNotNull(BankSmsParser.parse(sms))
        fun matches(desc: String) = CsvSmsMatcher.isConfirmedDuplicate(tx.amountLaari, tx.description,
            tx.transactionTime!!, tx.amountLaari, desc, "2026-11-06")
        val atm = "ATM Withdrawal: 05-11-2026 17-31-45 | EXAMPLE ATM"
        assertTrue(matches(atm))
        assertTrue(matches("ATM Withdrawal: 123456-123987@EXAMPLE ATM"))
        for (desc in listOf(atm.replace("17-31", "17-32"), atm.replace("EXAMPLE", "SECOND"),
            "ATM Withdrawal: EXAMPLE ATM", "$atm (Approval Code: 223987)", atm.replace("ATM Withdrawal", "POS Payment")))
            assertFalse(matches(desc))
    }

    @Test fun failedCashInsertRollsBackExistingDebitChanges() = TransactionStore(context).use { store ->
        store.importMissingCsv(csv)
        val original = store.all().single()
        store.writableDatabase.execSQL("""CREATE TRIGGER reject_cash BEFORE INSERT ON transactions
            WHEN NEW.fingerprint LIKE 'cash-mib-atm-%' BEGIN SELECT RAISE(ABORT, 'test failure'); END""")
        assertThrows(android.database.SQLException::class.java) { BankSmsImporter.importBody(store, sms) }
        assertEquals(original, store.all().single())
        assertTrue(store.csvDetails().isEmpty())
    }
}
