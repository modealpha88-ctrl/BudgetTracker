package com.example.mibbudget

import java.math.BigDecimal
import java.math.RoundingMode

/** The spendable MVR balance shown on the MIB account card, in laari. */
data class MibScreenshotSnapshot(val availableMinor: Long)

/**
 * Reads the MVR balance on the account card. No pending hold or ledger-balance
 * arithmetic is performed; neither salary nor transaction rows supply the
 * snapshot amount.
 */
object MibScreenshotParser {
    private val cardAmount = Regex("""(?i)\bMVR\s*([0-9][0-9,]*\.[0-9]{2})\b""")
    private val bankScreen = Regex("""(?i)\b(account\s*details|saving\s*account|saving|MIB)\b""")
    private val transactionsHeading = Regex("""(?i)\btransactions\b""")

    fun parse(ocrText: String): MibScreenshotSnapshot? {
        val text = ocrText.replace('\u00A0', ' ')
            .replace(Regex("""[\r\n\t]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (text.length !in 15..10000 || !bankScreen.containsMatchIn(text)) return null

        // Only read the account summary; values listed under Transactions are
        // not account balances. The main MIB dashboard has no such heading.
        val end = transactionsHeading.find(text)?.range?.first ?: text.length
        val cardText = text.substring(0, end)
        val match = cardAmount.find(cardText) ?: return null
        val minor = runCatching {
            BigDecimal(match.groupValues[1].replace(",", ""))
                .movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact()
        }.getOrNull()?.takeIf { it >= 0L } ?: return null
        return MibScreenshotSnapshot(minor)
    }
}
