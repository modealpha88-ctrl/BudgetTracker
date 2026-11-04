package com.example.mibbudget

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
    private fun minor(text: String): Long? =
        Money.parseLaari(text.replace(",", "").replace(Regex("^[^0-9-]+"), ""))

    private fun around(lines: List<String>, labelIndex: Int): Long? {
        val candidates = (maxOf(0, labelIndex - 2)..minOf(lines.lastIndex, labelIndex + 2))
            .flatMap { i -> amount.findAll(lines[i]).map { it.groupValues[1] }.toList() }
        return candidates.firstNotNullOfOrNull(::minor)
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
            val candidates = (0 until holdLabel).flatMap { i ->
                amount.findAll(lines[i]).map { it.groupValues[1] }.toList()
            }
            candidates.firstNotNullOfOrNull(::minor)
        } ?: return null
        if (available < 0L) return null
        val reconstructed = runCatching { Math.addExact(available, hold) }.getOrNull() ?: return null
        if (displayedCurrent != null && displayedCurrent != reconstructed) return null
        return MibScreenshotSnapshot(available, hold, displayedCurrent)
    }
}
