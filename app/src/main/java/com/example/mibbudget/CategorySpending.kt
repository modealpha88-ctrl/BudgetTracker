package com.example.mibbudget

/** One definition of expense category allocation for charts, budgets and drill-downs. */
object CategorySpending {
    fun allocations(amountLaari: Long, category: String, split: String?): Map<String, Long> {
        if(amountLaari>=0 || amountLaari==Long.MIN_VALUE || category=="Internal Transfer") return emptyMap()
        val amount=-amountLaari
        val parts=split?.split("|")
        val target=parts?.getOrNull(0)?.takeIf { it.isNotBlank() }
        val value=parts?.getOrNull(1)?.toLongOrNull()
        return if(target!=null && target!="Internal Transfer" && target!=category && value!=null && value>0 && value<amount) {
            mapOf(category to amount-value,target to value)
        } else mapOf(category to amount)
    }
}
