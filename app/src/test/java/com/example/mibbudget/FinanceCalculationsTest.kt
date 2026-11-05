package com.example.mibbudget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FinanceCalculationsTest {
    private fun tx(amount: Long, category: String, date: String, id: Long = 1L,
                   fingerprint: String = "manual-$id"): Tx =
        Tx(id, amount, category, "Test", "$date 12:00:00", fingerprint)

    @Test fun forecastsOnlyRecordedExpensesAndIgnoresTransfers() {
        val entries = listOf(
            tx(-10000L,"Food","2026-10-10"),
            tx(-9000L,"Internal Transfer","2026-10-10",2),
            tx(20000L,"Income","2026-10-10",3)
        )
        assertEquals(10000L, FinanceCalculations.monthExpenses(entries,YearMonth.of(2026,10)))
        assertEquals(31000L, FinanceCalculations.spendingForecast(
            entries,YearMonth.of(2026,10),LocalDate.of(2026,10,10)))
    }

    @Test fun salaryCyclesAnchorAtTheLastPayday() {
        assertEquals(LocalDate.of(2026,9,20),
            FinanceCalculations.cycleStart(LocalDate.of(2026,10,5),20))
        assertEquals(LocalDate.of(2026,10,20),
            FinanceCalculations.cycleStart(LocalDate.of(2026,10,25),20))
    }

    @Test fun lastDayPaydaysDoNotDriftAcrossFebruary() {
        val start=FinanceCalculations.cycleStart(LocalDate.of(2027,3,1),31)
        assertEquals(LocalDate.of(2027,2,28),start)
        assertEquals(LocalDate.of(2027,3,31),FinanceCalculations.cycleEnd(start,31))
        assertEquals(LocalDate.of(2027,3,31),
            FinanceCalculations.cycleStart(LocalDate.of(2027,3,31),31))
    }

    @Test fun unusedBudgetCarriesOnlyOneMonth() {
        val entries = listOf(tx(-3000L,"Food","2026-09-12"))
        assertEquals(7000L, FinanceCalculations.rollover(
            "Food",YearMonth.of(2026,10),10000L,entries))
        assertEquals(17000L, FinanceCalculations.effectiveLimit(
            "Food",YearMonth.of(2026,10),10000L,entries,true))
        assertEquals(10000L, FinanceCalculations.effectiveLimit(
            "Food",YearMonth.of(2026,10),10000L,entries,false))
        assertEquals(0L, FinanceCalculations.rollover(
            "Shopping",YearMonth.of(2026,10),10000L,
            listOf(tx(-15000L,"Shopping","2026-09-12"))))
    }

    @Test fun accountProjectionsExcludeReferenceDayAndOtherAccounts() {
        val entries = listOf(
            tx(-2000L,"Food","2026-10-01",1,"mib-one"),
            tx(-1000L,"Food","2026-10-02",2,"mib-two"),
            tx(-9000L,"Food","2026-10-02",3,"bml-one")
        )
        val labels=mapOf("bml-one" to "BML")
        assertEquals(99000L, FinanceCalculations.projectedBalance(
            "MIB",100000L,"2026-10-01",entries,labels))
        assertNull(FinanceCalculations.projectedBalance(
            "MIB",Long.MIN_VALUE,"2026-10-01",entries,labels))
    }
    @Test fun internalTransfersChangeOnlyTheAssignedAccountBalance() {
        val entries = listOf(
            tx(-2500L,"Internal Transfer","2026-10-02",1,"source"),
            tx(2500L,"Internal Transfer","2026-10-02",2,"destination"),
            tx(-1000L,"Food","2026-10-03",3,"source")
        )
        val labels=mapOf("source" to "MIB","destination" to "BML")
        assertEquals(96500L,FinanceCalculations.projectedBalance(
            "MIB",100000L,"2026-10-01",entries,labels))
        assertEquals(52500L,FinanceCalculations.projectedBalance(
            "BML",50000L,"2026-10-01",entries,labels))
        assertEquals(1000L,FinanceCalculations.expenseTotal(entries))
        assertEquals(97500L,FinanceCalculations.projectedBalance(
            "MIB",100000L,"2026-10-01",entries,labels,LocalDate.of(2026,10,2)))
    }

    @Test fun balanceComparisonRejectsDatesBeforeSnapshotAndOverflow() {
        val entries=listOf(tx(1000L,"Income","2026-10-02",1,"mib-one"))
        assertNull(FinanceCalculations.projectedBalance(
            "MIB",100000L,"2026-10-03",entries,emptyMap(),LocalDate.of(2026,10,2)))
        assertNull(FinanceCalculations.projectedBalance(
            "MIB",Long.MAX_VALUE,"2026-10-01",entries,emptyMap()))
    }



    @Test fun verifiedSnapshotClockIncludesOnlyLaterSameDayTransactions() {
        val rows=listOf(
            Tx(1,-1000,"Food","Before","2026-11-05T12:00:00","mib-a"),
            Tx(2,-2500,"Food","After","2026-11-05T16:50:00","mib-b"),
            Tx(3,9000,"Income","Next","2026-11-06T09:00:00","mib-c"),
            Tx(4,-5000,"Food","Other account","2026-11-05T17:00:00","bml-d"),
            Tx(5,-2000,"Food","At snapshot","2026-11-05T15:30:00","mib-e")
        )
        val labels=mapOf("bml-d" to "BML")
        assertEquals(106500L,FinanceCalculations.projectedBalance(
            "MIB",100000L,"2026-11-05",rows,labels,LocalDate.of(2026,11,6),"15:30:00"))
        assertEquals(109000L,FinanceCalculations.projectedBalance(
            "MIB",100000L,"2026-11-05",rows,labels,LocalDate.of(2026,11,6)))
        assertEquals(97500L,FinanceCalculations.projectedBalance(
            "MIB",100000L,"2026-11-05",rows,labels,LocalDate.of(2026,11,5),"15:30:00"))
    }

    @Test fun october29ProjectionExcludesAllSnapshotDayMovements() {
        val entries=listOf(
            Tx(1,-1300,"Food","POS purchase","2026-11-04T15:00:00","mib-pos"),
            Tx(2,-700,"Food","POS purchase","2026-11-04T09:00:00","mib-before"),
            Tx(3,-500,"Food","POS purchase","2026-11-05T08:00:00","mib-next-day")
        )
        assertEquals(101181L,FinanceCalculations.projectedBalance(
            "MIB",102981L,"2026-11-04",entries,emptyMap(),LocalDate.of(2026,11,5),"14:00:00"))
        assertEquals(102481L,FinanceCalculations.projectedBalance(
            "MIB",102981L,"2026-11-04",entries,emptyMap(),LocalDate.of(2026,11,5),"16:00:00"))
    }

}
