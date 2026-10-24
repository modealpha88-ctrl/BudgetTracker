package com.example.mibbudget

import org.junit.Assert.*
import org.junit.Test

class CategorySpendingTest {
    @Test fun shoppingAndFoodRemainSeparate() {
        assertEquals(mapOf("Shopping" to 2575182L),CategorySpending.allocations(-2575182L,"Shopping",null))
        assertEquals(mapOf("Food" to 6500L),CategorySpending.allocations(-6500L,"Food",null))
    }
    @Test fun splitAllocationsConserveBankAmount() {
        val result=CategorySpending.allocations(-10000L,"Shopping","Food|2500")
        assertEquals(7500L,result["Shopping"])
        assertEquals(2500L,result["Food"])
        assertEquals(10000L,result.values.sum())
    }
    @Test fun invalidSplitsCannotHideOrInflateSpending() {
        listOf("Food|-1","Food|0","Food|10000","Food|10001","Food|oops","|100","Shopping|100","Internal Transfer|100").forEach {
            assertEquals(mapOf("Shopping" to 10000L),CategorySpending.allocations(-10000L,"Shopping",it))
        }
    }
    @Test fun incomeAndInternalTransfersAreNotSpending() {
        assertTrue(CategorySpending.allocations(5000,"Income",null).isEmpty())
        assertTrue(CategorySpending.allocations(-5000,"Internal Transfer","Food|100").isEmpty())
        assertTrue(CategorySpending.allocations(Long.MIN_VALUE,"Other",null).isEmpty())
    }
}
