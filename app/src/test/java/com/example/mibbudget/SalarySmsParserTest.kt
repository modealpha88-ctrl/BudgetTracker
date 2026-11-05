package com.example.mibbudget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SalarySmsParserTest {
    private val sample = "Salary Transfer to your account 90503***91000 for MVR 9627.00 was processed on 05/11/2026 09:08:01. Ref. no. 10784051-99554143"

    @Test fun recognizesNovemberSalaryCreditAsMibIncome() {
        val candidate = SalarySmsParser.parse(sample)
        assertNotNull(candidate)
        requireNotNull(candidate)
        assertEquals(962700L, candidate.amountLaari)
        assertEquals("Income", candidate.category)
        assertEquals("2026-11-05T09:08:01", candidate.transactionTime)
    }

    @Test fun reimportingSameMessageKeepsDeduplicationFingerprint() {
        val first = SalarySmsParser.parse(sample)
        val second = SalarySmsParser.parse(sample)
        requireNotNull(first)
        requireNotNull(second)
        assertEquals(first.fingerprint, second.fingerprint)
    }

    @Test fun rejectsInvalidAndSensitiveMessages() {
        assertNull(SalarySmsParser.parse("Salary Transfer to your account 90503***91000 for MVR 9627.00 was processed on 31/02/2026 09:08:01. Ref. no. 10784051-99554143"))
        assertNull(SalarySmsParser.parse("Your OTP verification code is 123456"))
    }
}
