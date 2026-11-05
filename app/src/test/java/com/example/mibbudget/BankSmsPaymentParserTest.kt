package com.example.mibbudget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BankSmsPaymentParserTest {
    // Synthetic order/reference IDs: never commit real customer payment identifiers.
    private val sample =
        "Your payment to MWSC for MVR 111.58 was made on 05/11/2026 16:44:19 with Order ID UMP1234567890, and reference number 1234567."

    @Test fun importsMwscPaymentAsMibBillsExpense() {
        val candidate = BankSmsParser.parse(sample)
        assertNotNull(candidate)
        requireNotNull(candidate)
        assertEquals(-11158L, candidate.amountLaari)
        assertEquals("Bills", candidate.category)
        assertEquals("Bill payment: MWSC", candidate.description)
        assertEquals("2026-11-05T16:44:19", candidate.transactionTime)
    }

    @Test fun repeatedPaymentMessageKeepsSameDeduplicationFingerprint() {
        val first = BankSmsParser.parse(sample)
        val second = BankSmsParser.parse(sample)
        requireNotNull(first)
        requireNotNull(second)
        assertEquals(first.fingerprint, second.fingerprint)
    }

    @Test fun acceptsAnotherMerchantUsingSamePaymentFormat() {
        val sample = "Your payment to TEST UTILITY for MVR 50.00 was made on 04/11/2026 12:34:56 with Order ID ORD12345, and reference number 654321."
        val candidate = BankSmsParser.parse(sample)
        requireNotNull(candidate)
        assertEquals(-5000L, candidate.amountLaari)
        assertEquals("Bills", candidate.category)
        assertEquals("Bill payment: TEST UTILITY", candidate.description)
    }

    @Test fun rejectsInvalidDatesAndSecurityCodeTexts() {
        assertNull(BankSmsParser.parse(
            "Your payment to MWSC for MVR 111.58 was made on 31/02/2026 16:44:19 with Order ID UMP1234567890, and reference number 1234567."
        ))
        assertNull(BankSmsParser.parse("Your OTP verification code is 123456"))
    }
}
