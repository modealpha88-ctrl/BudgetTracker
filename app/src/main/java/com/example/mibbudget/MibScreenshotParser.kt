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

/** Parses MIB Account Details OCR without relying on line boundaries or label proximity. */
object MibScreenshotParser {
    private data class AmountToken(val start: Int, val end: Int, val minor: Long)

    // Supports MVR -858.02, -MVR 858.02, MVR 858.02 and comma grouping.
    private val amountPattern = Regex(
        """(?i)(-\s*)?(?:MVR|MRF|RF)?\s*(-\s*)?([0-9][0-9,]*\.[0-9]{2})"""
    )
    private val accountDetailsLabel = Regex("""(?i)account\s*details""")
    private val holdLabel = Regex("""(?i)amount\s*on\s*h[o0]ld""")
    private val currentLabel = Regex("""(?i)current\s*balance""")

    private fun amounts(text: String): List<AmountToken> =
        amountPattern.findAll(text).mapNotNull { match ->
            val negative = match.groupValues[1].isNotBlank() || match.groupValues[2].isNotBlank()
            runCatching {
                val value = BigDecimal(match.groupValues[3].replace(",", ""))
                    .movePointRight(2)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact()
                val signed = if (negative) Math.negateExact(value) else value
                AmountToken(match.range.first, match.range.last + 1, signed)
            }.getOrNull()
        }.toList()

    fun parse(ocrText: String): MibScreenshotSnapshot? {
        val text = ocrText
            .replace('\u00A0', ' ')
            .replace(Regex("""[\r\n\t]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (!accountDetailsLabel.containsMatchIn(text)) return null

        val hold = holdLabel.find(text) ?: return null
        val current = currentLabel.find(text) ?: return null
        if (current.range.first <= hold.range.last) return null

        val tokens = amounts(text)
        // On MIB Account Details, the card amount is displayed before the
        // hold label; the hold amount follows its label; current balance
        // follows its label. This ordered structure is more reliable than
        // guessing which nearby amount belongs to a label.
        val availableToken = tokens.lastOrNull { it.end <= hold.range.first } ?: return null
        val holdToken = tokens.firstOrNull {
            it.start > hold.range.last && it.end <= current.range.first
        } ?: return null
        val currentToken = tokens.firstOrNull { it.start > current.range.last } ?: return null

        val available = kotlin.math.abs(availableToken.minor)
        val holdAmount = kotlin.math.abs(holdToken.minor)
        val displayedCurrent = kotlin.math.abs(currentToken.minor)
        val reconstructed = runCatching { Math.addExact(available, holdAmount) }.getOrNull()
            ?: return null
        if (displayedCurrent != reconstructed) return null

        return MibScreenshotSnapshot(available, holdAmount, displayedCurrent)
    }
}
