package com.example.mibbudget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MibScreenshotParserTest {
    @Test fun readsAvailableBalanceWithoutAddingHeldMoney() {
        val ocr = """
            Account Details
            MVR 171.79
            9050301081291000
            Mohd.ibrahim Saving Account
            MVR -858.02
            Amount on Hold
            MVR 1,029.81
            Current Balance
            Transactions
        """.trimIndent()
        assertEquals(17179L, MibScreenshotParser.parse(ocr)?.availableMinor)
    }

    @Test fun readsAccountCardWhenTransactionsAppearBeforeLedgerBalance() {
        val ocr = """
            T
            MVR 6,298.79
            Account Details
            Mohd.ibrahim Saving Account
            MVR -845.02
            Amount on Hold
            Transactions
            -MVR 3,500.00
            MVR 9,627.00
            SALARY DEPOSIT November 5, 2026
            -MVR 13.00
            MVR 7,143.81
            Current Balance
        """.trimIndent()
        assertEquals(629879L, MibScreenshotParser.parse(ocr)?.availableMinor)
    }

    @Test fun readsMibHomeCardAsBalanceWithoutHoldSections() {
        val ocr = """
            Mohamed Ibrahim
            Personal Profile
            MVR 4,730.18
            9050301081291000
            Mohd.ibrahim
            Saving
            Info History Transfer
        """.trimIndent()
        assertEquals(473018L, MibScreenshotParser.parse(ocr)?.availableMinor)
    }

    @Test fun rejectsUnidentifiedOrTransactionOnlyScreenshots() {
        assertNull(MibScreenshotParser.parse("MVR 49.00\nTransactions"))
        assertNull(MibScreenshotParser.parse("MIB Transactions\nMVR 71.00"))
        assertNull(MibScreenshotParser.parse("Account Details\nMVR -71.00\nSaving Account"))
    }
}
