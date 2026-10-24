package com.example.mibbudget

import org.junit.Assert.*
import org.junit.Test

class FinanceInsightsTest {
    private fun tx(id:Long,amount:Long,category:String,date:String,account:String="f$id") =
        Tx(id,amount,category,"Transfer to own account",date,account)

    @Test fun internalTransfersAreExcludedFromIncomeAndSpending() {
        val rows=listOf(
            tx(1,-10000,"Food","2026-10-10"),
            tx(2,25000,"Income","2026-10-11"),
            tx(3,-50000,"Internal Transfer","2026-10-12"),
            tx(4,50000,"Internal Transfer","2026-10-12")
        )
        val insight=FinanceInsights.monthly(rows)
        assertEquals(10000L,insight.spending)
        assertEquals(25000L,insight.income)
        assertEquals(100000L,insight.internalMoved)
    }

    @Test fun transferSuggestionsRequireDifferentKnownAccounts() {
        val rows=listOf(tx(1,-10000,"Transfers","2026-10-10","one"),
            tx(2,10000,"Transfers","2026-10-11","two"))
        assertTrue(FinanceInsights.suggestedInternalPairs(rows,mapOf("one" to "MIB","two" to "MIB")).isEmpty())
    }

    @Test fun invalidDatesCannotMatchTransfers() {
        val rows=listOf(tx(1,-10000,"Transfers","unknown"),
            tx(2,10000,"Transfers","unknown"))
        assertTrue(FinanceInsights.suggestedInternalPairs(rows).isEmpty())
    }
}
