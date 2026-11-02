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

    @Test fun monthSelectionUsesRealTransactionDate() {
        val rows = listOf(
            tx(1, -5000, "POS purchase: SHOP", date = "2026-10-31T23:00:00"),
            tx(2, -7000, "POS purchase: SHOP", date = "2026-11-01T00:00:00")
        )
        assertEquals(7000L, MerchantSpending.summarize(rows, YearMonth.of(2026, 11)).single().amountLaari)
        assertEquals(12000L, MerchantSpending.summarize(rows, null).single().amountLaari)
    }
}
