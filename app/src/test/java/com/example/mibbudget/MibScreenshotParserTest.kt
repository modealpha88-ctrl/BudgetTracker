package com.example.mibbudget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MibScreenshotParserTest {
    @Test fun parsesAccountDetailsWithLabelsAndAmountsOnSeparateLines() {
        val ocr = """
            Account Details
            MVR 171.79
            9050301081291000
            Mohd.ibrahim Saving Account
            Amount on Hold
            MVR -858.02
            Current Balance
            MVR 1,029.81
        """.trimIndent()

        val result = MibScreenshotParser.parse(ocr)
        requireNotNull(result)
        assertEquals(17179L, result.availableMinor)
        assertEquals(85802L, result.holdMinor)
        assertEquals(102981L, result.reconstructedCurrentMinor)
    }

    @Test fun parsesLabelsSplitAcrossOcrLinesAndCurrencySignBeforeCode() {
        val ocr = """
            Account Details
            MVR 171.79
            Amount on
            Hold
            -MVR 858.02
            Current
            Balance
            MVR 1,029.81
        """.trimIndent()

        val result = MibScreenshotParser.parse(ocr)
        requireNotNull(result)
        assertEquals(17179L, result.availableMinor)
        assertEquals(85802L, result.holdMinor)
        assertEquals(102981L, result.reconstructedCurrentMinor)
    }

    @Test fun rejectsScreenshotWhenFiguresDoNotReconcile() {
        val ocr = """
            Account Details
            MVR 171.79
            Amount on Hold
            MVR -858.02
            Current Balance
            MVR 999.00
        """.trimIndent()

        assertNull(MibScreenshotParser.parse(ocr))
    }
}
