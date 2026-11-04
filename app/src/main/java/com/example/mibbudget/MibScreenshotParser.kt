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

/** Parses MIB Account Details OCR while tolerating labels before or after their figures. */
object MibScreenshotParser {
    private data class AmountToken(val start: Int, val end: Int, val minor: Long)

    // Supports MVR -858.02, -MVR 858.02, MVR 858.02 and comma grouping.
    private val amountPattern = Regex(
        """(?i)(-\s*)?(?:MVR|MRF|RF)?\s*(-\s*)?([0-9][0-9,]*\.[0-9]{2})"""
    )
    private val accountDetailsLabel = Regex("""(?i)account\s*details""")
    private val holdLabel = Regex("""(?i)amount\s*on\s*h[o0]ld""")
    private val currentLabel = Regex("""(?i)current\s*balance""")
    private val transactionsLabel = Regex("""(?i)transactions""")

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
            .replace(' ', ' ')
            .replace(Regex("""[\r\n\t]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (!accountDetailsLabel.containsMatchIn(text)) return null

        // Ignore transaction rows: only the account summary above this heading
        // is eligible to supply the three balance figures.
        val summaryEnd = transactionsLabel.find(text)?.range?.first ?: text.length
        val summary = text.substring(0, summaryEnd)
        val hold = holdLabel.find(summary) ?: return null
        val current = currentLabel.find(summary) ?: return null
        if (current.range.first <= hold.range.first) return null

        val tokens = amounts(summary)
        if (tokens.size < 3) return null

        // MIB OCR can emit either labels before their values or values before
        // their labels. In both cases the first three figures are available,
        // hold, and current balance, in that order.
        val availableToken = tokens[0]
        val holdToken = tokens[1]
        val currentToken = tokens[2]

        // Anchor the labels to the ordered figures and reject unrelated text.
        if (hold.range.first <= availableToken.end ||
            hold.range.first >= currentToken.end ||
            current.range.first <= hold.range.first ||
            current.range.first < holdToken.start
        ) return null

        val available = kotlin.math.abs(availableToken.minor)
        val holdAmount = kotlin.math.abs(holdToken.minor)
        val displayedCurrent = kotlin.math.abs(currentToken.minor)
        val reconstructed = runCatching { Math.addExact(available, holdAmount) }.getOrNull()
            ?: return null
        if (displayedCurrent != reconstructed) return null

        return MibScreenshotSnapshot(available, holdAmount, displayedCurrent)
    }
}
