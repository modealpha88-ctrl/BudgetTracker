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

/**
 * Parses MIB Account Details OCR as a single normalized text stream.
 * ML Kit may split a label over multiple lines, and MIB formats a debit hold
 * as either "MVR -858.02" or "-MVR 858.02". Both are handled here.
 */
object MibScreenshotParser {
    private data class AmountToken(val start: Int, val end: Int, val minor: Long)

    private val amountPattern = Regex(
        """(?i)(-\\s*)?(?:MVR|MRF|RF)?\\s*(-?\\s*[0-9][0-9,]*\\.[0-9]{2})"""
    )
    private val holdLabel = Regex("""(?i)amount\\s*on\\s*h[o0]ld""")
    private val currentLabel = Regex("""(?i)current\\s*balance""")
    private val availableLabel = Regex("""(?i)available\\s*balance""")
    private val semanticLabel = Regex(
        """(?i)(amount\\s*on\\s*h[o0]ld|current\\s*balance|available\\s*balance|transactions)"""
    )

    private fun toMinor(raw: String, prefixNegative: Boolean): Long? = runCatching {
        val cleaned = raw.replace(",", "").replace(Regex("""\\s+"""), "")
        val parsed = BigDecimal(cleaned).movePointRight(2)
            .setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        if (prefixNegative) Math.negateExact(kotlin.math.abs(parsed)) else parsed
    }.getOrNull()

    private fun amounts(text: String): List<AmountToken> =
        amountPattern.findAll(text).mapNotNull { match ->
            val prefixNegative = !match.groupValues[1].isNullOrBlank()
            toMinor(match.groupValues[2], prefixNegative)?.let {
                AmountToken(match.range.first, match.range.last + 1, it)
            }
        }.toList()

    private fun nearestAmount(
        text: String,
        label: MatchResult,
        tokens: List<AmountToken>,
        used: Set<Int>,
        maxDistance: Int = 100
    ): Pair<AmountToken, Int>? {
        return tokens.withIndex()
            .filter { (index, token) -> index !in used &&
                minOf(kotlin.math.abs(token.end - label.range.first),
                    kotlin.math.abs(token.start - (label.range.last + 1))) <= maxDistance }
            .filter { (_, token) ->
                val from = minOf(token.end, label.range.last + 1)
                val to = maxOf(token.start, label.range.first)
                from >= to || !semanticLabel.containsMatchIn(text.substring(from, to))
            }
            .minByOrNull { (_, token) ->
                minOf(kotlin.math.abs(token.end - label.range.first),
                    kotlin.math.abs(token.start - (label.range.last + 1)))
            }?.let { it.value to it.index }
    }

    fun parse(ocrText: String): MibScreenshotSnapshot? {
        val text = ocrText
            .replace('\u00A0', ' ')
            .replace(Regex("""[\\r\\n\\t]+"""), " ")
            .replace(Regex("""\\s+"""), " ")
            .trim()
        if (text.isBlank() || !text.contains(Regex("""(?i)account\\s*details"""))) return null

        val tokens = amounts(text)
        val holdMatch = holdLabel.find(text) ?: return null
        val currentMatch = currentLabel.find(text) ?: return null

        val holdPair = nearestAmount(text, holdMatch, tokens, emptySet()) ?: return null
        val currentPair = nearestAmount(text, currentMatch, tokens, setOf(holdPair.second))
            ?: return null
        val availableMatch = availableLabel.find(text)
        val availablePair = if (availableMatch != null) {
            nearestAmount(text, availableMatch, tokens, setOf(holdPair.second, currentPair.second))
        } else {
            // On the MIB Account Details screen the account-card amount is shown
            // above "Amount on Hold"; it is the available amount to reconstruct.
            tokens.withIndex()
                .filter { (index, token) -> index != holdPair.second &&
                    index != currentPair.second && token.end <= holdMatch.range.first }
                .maxByOrNull { it.value.end }?.let { it.value to it.index }
        } ?: return null

        val available = kotlin.math.abs(availablePair.first.minor)
        val hold = kotlin.math.abs(holdPair.first.minor)
        val displayedCurrent = kotlin.math.abs(currentPair.first.minor)
        if (available < 0L || hold < 0L) return null
        val reconstructed = runCatching { Math.addExact(available, hold) }.getOrNull() ?: return null
        if (displayedCurrent != reconstructed) return null

        return MibScreenshotSnapshot(available, hold, displayedCurrent)
    }
}
