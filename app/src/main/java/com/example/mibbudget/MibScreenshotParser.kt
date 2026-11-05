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

/** Parses MIB Account Details OCR using label-to-amount proximity, not line ordering. */
object MibScreenshotParser {
    private data class AmountToken(val start: Int, val end: Int, val minor: Long)

    // Accepts MVR -858.02, -MVR 858.02, MVR 858.02 and comma grouping.
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

    private fun distance(token: AmountToken, label: MatchResult): Int = when {
        token.end <= label.range.first -> label.range.first - token.end
        token.start >= label.range.last + 1 -> token.start - label.range.last - 1
        else -> 0
    }

    fun parse(ocrText: String): MibScreenshotSnapshot? {
        val text = ocrText
            .replace('\u00A0', ' ')
            .replace(Regex("""[\r\n\t]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        // The screen heading is useful confirmation, but OCR sometimes misses it.
        if (!accountDetailsLabel.containsMatchIn(text) &&
            !currentLabel.containsMatchIn(text)
        ) return null

        // Do not let transaction amounts below the summary contaminate the balance.
        val summaryEnd = transactionsLabel.find(text)?.range?.first ?: text.length
        val summary = text.substring(0, summaryEnd)
        val current = currentLabel.find(summary) ?: return null
        val hold = holdLabel.find(summary)
        val tokens = amounts(summary)
        if (tokens.isEmpty()) return null

        // OCR engines do not guarantee that a label and its value are emitted
        // in the same order as the screen. Associate each label with its nearest
        // amount, whether the value appears before or after the label.
        // The bank commonly renders the hold as a negative amount. Prefer that
        // explicit sign so a preceding available-balance figure cannot win a
        // distance tie when OCR places the hold label before its value.
        val negativeHoldTokens = tokens.filter { it.minor < 0L }
        val holdToken = hold?.let { holdLabelMatch ->
            val holdCandidates = if (negativeHoldTokens.isNotEmpty()) negativeHoldTokens else tokens
            holdCandidates.minWithOrNull(
                compareBy<AmountToken> { distance(it, holdLabelMatch) }
                    .thenBy { if (it.start >= holdLabelMatch.range.last + 1) 0 else 1 }
            )
        }
        val currentToken = tokens.minWithOrNull(
            compareBy<AmountToken> { distance(it, current) }
                .thenBy { if (it.start >= current.range.last + 1) 0 else 1 }
        ) ?: return null
        if (holdToken == currentToken) return null

        // Other screenshot figures are retained for review only; the displayed current balance is authoritative.
        val availableToken = tokens
            .filter { it != holdToken && it != currentToken }
            .minByOrNull { it.start }
        val available = availableToken?.minor?.let { kotlin.math.abs(it) } ?: 0L
        val holdAmount = holdToken?.minor?.let { kotlin.math.abs(it) } ?: 0L
        val displayedCurrent = kotlin.math.abs(currentToken.minor)

        return MibScreenshotSnapshot(available, holdAmount, displayedCurrent)
    }
}
