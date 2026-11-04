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
 * Parses the three figures on MIB's Account Details screen.
 *
 * OCR may split a label and its amount into separate lines or place the amount
 * immediately before the label. Search only a small neighbourhood around each
 * label and never reuse a number already assigned to another field.
 */
object MibScreenshotParser {
    private val amount = Regex("""(?i)(?:MVR|MRF|RF)?\s*[:.]?\s*(-?[0-9][0-9,]*\.[0-9]{2})""")
    private val holdLabel = Regex("""(?i)amount\s*on\s*h[o0]ld""")
    private val currentLabel = Regex("""(?i)current\s*balance""")
    private val anyLabel = Regex("""(?i)(amount\s*on\s*h[o0]ld|current\s*balance|available\s*balance|transactions)""")

    private fun minor(text: String): Long? = runCatching {
        BigDecimal(text.replace(",", "")).movePointRight(2)
            .setScale(0, RoundingMode.UNNECESSARY).longValueExact()
    }.getOrNull()

    private fun amountOn(line: String): Long? =
        amount.findAll(line).mapNotNull { minor(it.groupValues[1]) }.firstOrNull()

    private fun associatedAmount(lines: List<String>, index: Int, used: Set<Int>): Pair<Long, Int>? {
        // Same line first, then the closest OCR line on either side.
        val candidates = listOf(index, index - 1, index + 1, index - 2, index + 2)
            .filter { it in lines.indices && it !in used }
        for (i in candidates) {
            val value = amountOn(lines[i]) ?: continue
            // Do not cross another semantic field label while searching outward.
            if (i != index && (minOf(i, index) + 1 until maxOf(i, index))
                    .any { anyLabel.containsMatchIn(lines[it]) }) continue
            return value to i
        }
        return null
    }

    fun parse(ocrText: String): MibScreenshotSnapshot? {
        val lines = ocrText.lineSequence().map {
            it.trim().replace(Regex("""\s+"""), " ")
                .replace("H0ld", "Hold", ignoreCase = true)
        }.filter { it.isNotBlank() }.toList()
        val holdIndex = lines.indexOfFirst { holdLabel.containsMatchIn(it) }
        val currentIndex = lines.indexOfFirst { currentLabel.containsMatchIn(it) }
        if (holdIndex < 0 || currentIndex < 0) return null

        val holdPair = associatedAmount(lines, holdIndex, emptySet()) ?: return null
        val currentPair = associatedAmount(lines, currentIndex, setOf(holdPair.second))
        val used = setOf(holdPair.second) + listOfNotNull(currentPair?.second)

        // The MIB account card shows the available amount before the hold row.
        // Prefer an explicitly labelled available balance when present.
        val availableIndex = lines.indexOfFirst {
            Regex("""(?i)available\s*balance""").containsMatchIn(it)
        }
        val availablePair = if (availableIndex >= 0) {
            associatedAmount(lines, availableIndex, used)
        } else {
            (0 until holdIndex).asSequence()
                .filter { it !in used && !anyLabel.containsMatchIn(lines[it]) }
                .mapNotNull { i -> amountOn(lines[i])?.let { it to i } }
                .firstOrNull()
        } ?: return null

        val available = availablePair.first
        val hold = kotlin.math.abs(holdPair.first)
        if (available < 0L || hold < 0L) return null
        val reconstructed = runCatching { Math.addExact(available, hold) }.getOrNull() ?: return null
        val displayed = currentPair?.first?.let { kotlin.math.abs(it) }
        if (displayed != null && displayed != reconstructed) return null
        return MibScreenshotSnapshot(available, hold, displayed)
    }
}
