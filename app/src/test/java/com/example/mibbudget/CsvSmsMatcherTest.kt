package com.example.mibbudget

import org.junit.Assert.*
import org.junit.Test

class CsvSmsMatcherTest {
    @Test fun sameAmountDifferentMerchantMustNotBeDropped() {
        assertFalse(CsvSmsMatcher.isConfirmedDuplicate(-6500,"POS purchase: COFFEE BEAN",
            "2026-10-23T15:56:00",-6500,"POS Payment: OTHER STORE",
            "2026-10-23T14:00:00"))
    }
    @Test fun matchingMerchantAndDateIsConfirmed() {
        assertTrue(CsvSmsMatcher.isConfirmedDuplicate(-6500,"POS purchase COFFEE BEAN",
            "2026-10-23T15:56:00",-6500,"POS Payment COFFEE BEAN",
            "2026-10-23T15:58:00"))
    }
    @Test fun sameAmountDifferentDayIsNotConfirmed() {
        assertFalse(CsvSmsMatcher.isConfirmedDuplicate(-6500,"COFFEE BEAN",
            "2026-10-23T15:56:00",-6500,"COFFEE BEAN",
            "2026-10-20T15:56:00"))
    }
}
