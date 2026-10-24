package com.example.mibbudget

import java.time.LocalDate
import java.time.YearMonth

/** Read-only calculations: never modify statements, balances, or transaction records. */
object FinanceCalculations {
    fun isExpense(tx: Tx): Boolean =
        tx.amountLaari < 0 && !tx.category.equals("Internal Transfer", ignoreCase = true)

    fun expenseTotal(entries: List<Tx>): Long =
        entries.filter(::isExpense).sumOf { -it.amountLaari }

    fun monthExpenses(entries: List<Tx>, month: YearMonth): Long =
        expenseTotal(entries.filter { it.timestamp.startsWith(month.toString()) })

    fun monthIncome(entries: List<Tx>, month: YearMonth): Long =
        entries.filter { it.amountLaari > 0 && it.category != "Internal Transfer" &&
            it.timestamp.startsWith(month.toString()) }.sumOf { it.amountLaari }

    fun periodEntries(entries: List<Tx>, start: LocalDate, endExclusive: LocalDate): List<Tx> =
        entries.filter { it.timestamp.take(10) >= start.toString() &&
            it.timestamp.take(10) < endExclusive.toString() }

    /** Salary cycles always use a valid day in every month (1–28). */
    fun cycleStart(today: LocalDate, payday: Int): LocalDate {
        val anchor = today.withDayOfMonth(payday.coerceIn(1, 28))
        return if (today.isBefore(anchor)) anchor.minusMonths(1) else anchor
    }

    /** Past months are actuals; present month is extrapolated from elapsed calendar days. */
    fun spendingForecast(entries: List<Tx>, month: YearMonth, today: LocalDate): Long {
        val spent = monthExpenses(entries, month)
        val current = YearMonth.from(today)
        if (month < current) return spent
        if (month > current) return 0L
        return (spent.toDouble() * month.lengthOfMonth() / today.dayOfMonth)
            .coerceAtMost(Long.MAX_VALUE.toDouble()).toLong()
    }

    /** One-month, non-negative rollover; previous overspending never produces negative allowances. */
    fun rollover(category: String, month: YearMonth, limit: Long, entries: List<Tx>): Long {
        if (limit <= 0L) return 0L
        val previousSpend = entries.filter {
            it.timestamp.startsWith(month.minusMonths(1).toString()) &&
                it.category == category && isExpense(it)
        }.sumOf { -it.amountLaari }
        return (limit - previousSpend).coerceAtLeast(0L)
    }

    fun assignedAccount(tx: Tx, labels: Map<String, String>): String =
        labels[tx.fingerprint]?.takeIf { it.isNotBlank() }
            ?: if (tx.fingerprint.startsWith("mib-", ignoreCase = true)) "MIB" else "Unassigned"

    /** An estimated snapshot + subsequent labeled movements, NOT a live bank balance. */
    fun projectedBalance(
        account: String, anchorMinor: Long, anchorDate: String,
        entries: List<Tx>, labels: Map<String, String>
    ): Long? {
        if (anchorMinor == Long.MIN_VALUE || runCatching { LocalDate.parse(anchorDate) }.isFailure)
            return null
        val movements = entries.filter { tx ->
            assignedAccount(tx, labels).equals(account, ignoreCase = true) &&
                tx.timestamp.take(10) > anchorDate && tx.category != "Internal Transfer"
        }.sumOf { it.amountLaari }
        return anchorMinor + movements
    }
}
