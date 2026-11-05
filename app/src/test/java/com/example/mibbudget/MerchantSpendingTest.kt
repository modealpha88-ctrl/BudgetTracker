package com.example.mibbudget

import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class MerchantSpendingTest {
    private fun tx(id: Long, amount: Long, description: String, category: String = "Other",
                   date: String = "2026-11-02T10:00:00") =
        Tx(id, amount, category, description, date, "fingerprint-$id")

    @Test fun aggregatesIdenticalMerchantsAndOrdersBySpend() {
        val rows = listOf(
            tx(1, -2000, "POS purchase: AVAS POINT"),
            tx(2, -8000, "POS purchase: LINCHPIN"),
            tx(3, -5000, "POS purchase: avas   point"),
            tx(4, -1000, "E-commerce purchase: LINCHPIN")
        )
        val ranked = MerchantSpending.summarize(rows, YearMonth.of(2026, 11))
        assertEquals(listOf("LINCHPIN", "AVAS POINT"), ranked.map { it.name })
        assertEquals(listOf(9000L, 7000L), ranked.map { it.amountLaari })
        assertEquals(2, ranked[0].count)
    }

    @Test fun incomingPaymentsAndTransfersDoNotCountAsMerchantSpend() {
        val rows = listOf(
            tx(1, -10000, "POS purchase: ADK PHARMACY", "Health"),
            tx(2, 50000, "POS purchase: ADK PHARMACY", "Income"),
            tx(3, -20000, "Favara incoming transfer", "Transfers"),
            tx(4, -30000, "POS purchase: TEST SHOP", "Internal Transfer"),
            tx(5, -10000, "ATM withdrawal", "Other")
        )
        val ranked = MerchantSpending.summarize(rows, null)
        assertEquals(1, ranked.size)
        assertEquals("ADK PHARMACY", ranked.first().name)
        assertEquals(10000L, ranked.first().amountLaari)
    }

    @Test fun verifiedTransfersAppearBesideMerchantsAndGroupByRecipient() {
        val rows = listOf(
            tx(1, -50600, "Favara transfer", "Transfers"),
            tx(2, -50000, "Favara transfer", "Transfers"),
            tx(3, -350000, "Favara transfer", "Transfers"),
            tx(4, -98650, "E-commerce purchase: OOREDOO", "Bills"),
            tx(5, -9000, "Favara transfer", "Transfers"),
            tx(6, 150000, "Favara incoming transfer", "Transfers"),
            tx(7, -55000, "Favara transfer", "Internal Transfer")
        )
        val recipients = mapOf(
            "fingerprint-1" to "HAYYAAN HASSAN",
            "fingerprint-2" to "HAYYAAN HASSAN",
            "fingerprint-3" to "SIYAMA HASSAN",
            "fingerprint-6" to "OTHER",
            "fingerprint-7" to "HAYYAAN HASSAN"
        )
        val ranked = MerchantSpending.summarize(rows, YearMonth.of(2026,11), recipients)
        assertEquals(listOf("SIYAMA HASSAN", "HAYYAAN HASSAN", "OOREDOO"),
            ranked.map { it.name })
        assertEquals(listOf(350000L, 100600L, 98650L), ranked.map { it.amountLaari })
        assertEquals(listOf(1,2,1), ranked.map { it.count })
        assertEquals(setOf(1L,2L), ranked[1].transactions.map { it.id }.toSet())
    }

    @Test fun genericTransfersAndInternalTransfersStayExcludedEvenWithNames() {
        val rows = listOf(
            tx(1, -10000, "Favara transfer", "Transfers"),
            tx(2, -15000, "Favara transfer", "Transfers"),
            tx(3, -20000, "Favara transfer", "Internal Transfer"),
            tx(4, -30000, "Favara transfer", "Transfers")
        )
        val names = mapOf(
            "fingerprint-2" to "Favara transfer",
            "fingerprint-3" to "KNOWN PERSON",
            "fingerprint-4" to " MANUALLY CONFIRMED "
        )
        val ranked = MerchantSpending.summarize(rows, null, names)
        assertEquals(1, ranked.size)
        assertEquals("MANUALLY CONFIRMED", ranked.single().name)
        assertEquals(30000L, ranked.single().amountLaari)
    }

    @Test fun monthSelectionUsesRealTransactionDate() {
        val rows = listOf(
            tx(1, -5000, "POS purchase: SHOP", date = "2026-10-31T23:00:00"),
            tx(2, -7000, "POS purchase: SHOP", date = "2026-11-01T00:00:00")
        )
        assertEquals(7000L, MerchantSpending.summarize(rows, YearMonth.of(2026, 11)).single().amountLaari)
        assertEquals(12000L, MerchantSpending.summarize(rows, null).single().amountLaari)
    }
}
