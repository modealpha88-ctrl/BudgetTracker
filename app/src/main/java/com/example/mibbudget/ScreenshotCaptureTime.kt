package com.example.mibbudget

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Parse original screenshot filename timestamps only, never modification time. */
object ScreenshotCaptureTime {
    private val screenshotPattern=Regex(
        """(?i)(?:Screenshot|Screen[_ -]?shot)[_ -](\d{4})[-_](\d{2})[-_](\d{2})[-_](\d{2})[-_](\d{2})(?:[-_](\d{2}))?"""
    )
    fun fromFilename(filename: String?): LocalDateTime? {
        val match = filename?.let { screenshotPattern.find(it) } ?: return null
        val parts = match.groupValues
        return runCatching {
            LocalDateTime.of(parts[1].toInt(),parts[2].toInt(),parts[3].toInt(),
                parts[4].toInt(),parts[5].toInt(),parts[6].toIntOrNull() ?: 0)
        }.getOrNull()
    }
}
