package com.example.mibbudget

import java.math.BigDecimal
import java.math.RoundingMode

/** Parsed MIB account-details screenshot values, expressed in laari. */
data class MibScreenshotSnapshot(
    val availableMinor: Long,
    val holdMinor: Long,
    val displayedCurrentMinor: Long?
) {
    val reconstructedCurrentMinor: Long get() = Math.addExact(availableMinor, holdMinor)
}

/** Conservative OCR parser for MIB's Account Details screen. */
object MibScreenshotParser {
    private val amount = Regex("""(?i)(?:MVR|MRF|RF)?\s*[:.]?\s*(-?[0-9][0-9,]*\.[0-9]{2})""")
    private fun minor(text: String): Long? = runCatching {
        BigDecimal(text.replace(",", "")).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
    }.getOrNull()

    private fun around(lines: List<String>, labelIndex: Int): Long? {
        val indexes = listOf(labelIndex, labelIndex - 1, labelIndex + 1, labelIndex - 2, labelIndex + 2)
            .filter { it in lines.indices }
        for (i in indexes) {
            val value = amount.findAll(lines[i]).mapNotNull { minor(it.groupValues[1]) }.firstOrNull()
            if (value != null) return value
        }
        return null
    }

    fun parse(ocrText: String): MibScreenshotSnapshot? {
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.none { it.contains("Account Details", true) } ||
            lines.none { it.contains("Amount on Hold", true) } ||
            lines.none { it.contains("Current Balance", true) }) return null
        val holdLabel = lines.indexOfFirst { it.contains("Amount on Hold", true) }
        val currentLabel = lines.indexOfFirst { it.contains("Current Balance", true) }
        val hold = kotlin.math.abs(around(lines, holdLabel) ?: return null)
        val displayedCurrent = around(lines, currentLabel)
        val availableLabel = lines.indexOfFirst {
            it.contains("Available Balance", true) && !it.contains("Current", true)
        }
        val available = if (availableLabel >= 0) around(lines, availableLabel) else {
            (0 until holdLabel).firstNotNullOfOrNull { i ->
                amount.findAll(lines[i]).mapNotNull { minor(it.groupValues[1]) }.firstOrNull()
            }
        } ?: return null
        if (available < 0L) return null
        val reconstructed = runCatching { Math.addExact(available, hold) }.getOrNull() ?: return null
        if (displayedCurrent != null && displayedCurrent != reconstructed) return null
        return MibScreenshotSnapshot(available, hold, displayedCurrent)
    }
}
