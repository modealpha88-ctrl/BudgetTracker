package com.example.mibbudget

import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/**
 * Conservative parser for MIB salary-credit SMS.
 * Stores only amount, timestamp, category, a non-sensitive description and a hash;
 * the masked account number and raw message are never persisted.
 */
object SalarySmsParser {
    private val pattern = Regex(
        """(?is)^Salary\\s+Transfer\\s+to\\s+your\\s+account\\s+[0-9*]+\\s+for\\s+MVR\\s+([\\d,]+\\.\\d{2})\\s+was\\s+processed\\s+on\\s+(\\d{2}/\\d{2}/\\d{4}\\s+\\d{2}:\\d{2}:\\d{2})\\.\\s+Ref\\.\\s*no\\.\\s*([\\w-]+)\\s*$"""
    )
    private val sensitive = Regex("""(?i)\\b(otp|one.time.password|verification code|login code|password|pin|authentication code)\\b""")

    fun parse(raw: String): BankSmsParser.Candidate? {
        val text = raw.trim()
        if (text.length !in 30..1000 || sensitive.containsMatchIn(text)) return null
        val match = pattern.matchEntire(text) ?: return null
        val amount = Money.parseLaari(match.groupValues[1]) ?: return null
        if (amount <= 0L) return null
        val timestamp = try {
            val formatter = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss", Locale.US)
                .withResolverStyle(ResolverStyle.STRICT)
            LocalDateTime.parse(match.groupValues[2], formatter)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        } catch (_: Exception) {
            return null
        }
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(text.lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return BankSmsParser.Candidate(
            amount,
            "Income",
            "Salary transfer (Ref: ${match.groupValues[3]})",
            fingerprint,
            timestamp
        )
    }
}
