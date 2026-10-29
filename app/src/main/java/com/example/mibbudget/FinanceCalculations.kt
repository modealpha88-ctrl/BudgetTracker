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

    /** Clamp paydays 29–31 to the month's final date without drifting across months. */
    private fun paydayInMonth(month: YearMonth, payday: Int): LocalDate =
        month.atDay(payday.coerceIn(1, 31).coerceAtMost(month.lengthOfMonth()))

    fun cycleStart(today: LocalDate, payday: Int): LocalDate {
        val month = YearMonth.from(today)
        val anchor = paydayInMonth(month, payday)
        return if (today.isBefore(anchor)) paydayInMonth(month.minusMonths(1), payday) else anchor
    }

    fun cycleEnd(start: LocalDate, payday: Int): LocalDate =
        paydayInMonth(YearMonth.from(start).plusMonths(1), payday)


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

    fun effectiveLimit(category: String, month: YearMonth, limit: Long,
                       entries: List<Tx>, rolloverEnabled: Boolean): Long {
        if (!rolloverEnabled || limit <= 0L) return limit
        val carry = rollover(category, month, limit, entries)
        return if (carry > Long.MAX_VALUE - limit) Long.MAX_VALUE else limit + carry
    }

    fun assignedAccount(tx: Tx, labels: Map<String, String>): String =
        labels[tx.fingerprint]?.takeIf { it.isNotBlank() }
            ?: if (tx.fingerprint.startsWith("mib-", ignoreCase = true)) "MIB" else "Unassigned"

    /**
     * A saved bank snapshot plus subsequent movements assigned to that account.
     * Internal transfers ARE account movements even though they are not spending.
     * The date bound is inclusive; no transaction on the snapshot's reference day is replayed.
     */
    fun projectedBalance(
        account: String, anchorMinor: Long, anchorDate: String,
        entries: List<Tx>, labels: Map<String, String>,
        throughDate: LocalDate? = null,
        anchorTime: String? = null
    ): Long? {
        val anchor = runCatching { LocalDate.parse(anchorDate) }.getOrNull()
        if (anchorMinor == Long.MIN_VALUE || anchor == null ||
            (throughDate != null && throughDate.isBefore(anchor))) return null
        val movements = entries.filter { tx ->
            val date = runCatching { LocalDate.parse(tx.timestamp.take(10)) }.getOrNull()
            date != null && (date.isAfter(anchor) || (date==anchor && anchorTime!=null && runCatching { java.time.LocalTime.parse(tx.timestamp.substring(11).take(8)) }.getOrNull()?.isAfter(runCatching { java.time.LocalTime.parse(anchorTime) }.getOrNull())==true)) &&
                (throughDate == null || !date.isAfter(throughDate)) &&
                assignedAccount(tx, labels).equals(account, ignoreCase = true)
        }.sumOf { it.amountLaari }
        return runCatching { Math.addExact(anchorMinor, movements) }.getOrNull()
    }
}
