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
    val reconcilesWithBank: Boolean get() = runCatching { reconstructedCurrentMinor == displayedCurrentMinor }.getOrDefault(false)
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

        // MIB's OCR reading order can put "Current Balance" AFTER the
        // Transactions heading. Its label must be searched across the whole
        // screenshot; do not truncate the text at "Transactions".
        val current = currentLabel.find(text) ?: return null
        val hold = holdLabel.find(text) ?: return null
        val tokens = amounts(text)
        if (tokens.size < 3) return null

        // Find the figure attached to Amount on Hold. Prefer a signed hold
        // close to that label, not a later negative transaction below it.
        val holdCandidates = tokens.filter { distance(it, hold) <= 160 }
        val holdToken = holdCandidates.filter { it.minor < 0L }
            .minByOrNull { distance(it, hold) }
            ?: holdCandidates.minByOrNull { distance(it, hold) }
            ?: return null

        // Current Balance may be above or below the label, even amongst the
        // OCR text for individual transactions. Prefer the nearest positive
        // figure, excluding the hold itself.
        val currentToken = tokens.filter { it != holdToken && it.minor >= 0L }
            .minByOrNull { distance(it, current) } ?: return null

        // The top MIB account card is the AVAILABLE balance the user can spend.
        // Its amount is the first positive money figure before "Amount on Hold".
        // Never substitute the larger bank ledger/current figure for this one.
        val availableToken = tokens.firstOrNull {
            it != holdToken && it != currentToken &&
                it.minor >= 0L && it.start < hold.range.first
        } ?: return null

        val available = availableToken.minor
        val holdAmount = if (holdToken.minor == Long.MIN_VALUE) return null
            else kotlin.math.abs(holdToken.minor)
        val displayedCurrent = currentToken.minor
        if (runCatching { Math.addExact(available, holdAmount) }.isFailure) return null

        return MibScreenshotSnapshot(available, holdAmount, displayedCurrent)
    }
}
