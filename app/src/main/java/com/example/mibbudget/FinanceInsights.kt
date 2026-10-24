package com.example.mibbudget

import kotlin.math.abs

/** Read-only calculations; never changes imported transactions or balances. */
object FinanceInsights {
    data class TransferPair(val outgoing: Tx, val incoming: Tx)
    fun suggestedInternalPairs(transactions: List<Tx>, accountLabels: Map<String,String> = emptyMap()): List<TransferPair> {
        val outgoing=transactions.filter { it.amountLaari<0 && it.category!="Internal Transfer" &&
            VendorProfiles.isTransfer(it.description,it.category) }.sortedBy { it.id }
        val incoming=transactions.filter { it.amountLaari>0 && it.category!="Internal Transfer" &&
            VendorProfiles.isTransfer(it.description,it.category) }.sortedBy { it.id }
        val used=mutableSetOf<Long>()
        return outgoing.mapNotNull { sent ->
            val match=incoming.firstOrNull { received ->
                received.id !in used && received.amountLaari == -sent.amountLaari &&
                    dayNumber(received.timestamp)!=null && dayNumber(sent.timestamp)!=null &&
                    kotlin.math.abs(dayNumber(received.timestamp)!!-dayNumber(sent.timestamp)!!)<=3 &&
                    (accountLabels[sent.fingerprint].isNullOrBlank() ||
                        accountLabels[received.fingerprint].isNullOrBlank() ||
                        accountLabels[sent.fingerprint]!=accountLabels[received.fingerprint])
            }
            if(match!=null) { used.add(match.id); TransferPair(sent,match) } else null
        }
    }
    private fun dayNumber(date: String): Long? = try {
        java.time.LocalDate.parse(date.take(10)).toEpochDay()
    } catch (_: Exception) { null }

    data class MonthlyInsight(val income: Long,val spending: Long,val internalMoved: Long,
        val biggestExpenseCategory: String?,val biggestExpense: Long)
    fun monthly(transactions: List<Tx>): MonthlyInsight {
        val external=transactions.filter { it.category!="Internal Transfer" }
        val expense=external.filter { it.amountLaari<0 }
        val byCategory=expense.groupBy { it.category }.mapValues { (_,rows)->rows.sumOf { -it.amountLaari } }
        val biggest=byCategory.maxByOrNull { it.value }
        return MonthlyInsight(
            income=external.filter { it.amountLaari>0 }.sumOf { it.amountLaari },
            spending=expense.sumOf { -it.amountLaari },
            internalMoved=transactions.filter { it.category=="Internal Transfer" }.sumOf { abs(it.amountLaari) },
            biggestExpenseCategory=biggest?.key,biggestExpense=biggest?.value ?: 0L)
    }
}
