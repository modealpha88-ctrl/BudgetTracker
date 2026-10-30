package com.example.mibbudget

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Midnight Emerald: an AMOLED-first palette that keeps the light preference usable.
 * Appearance changes only; no account, transaction, or database values are modified.
 */
private val MidnightEmeraldDark = darkColorScheme(
    primary = Color(0xFF35D49A),
    onPrimary = Color(0xFF042015),
    primaryContainer = Color(0xFF123D2D),
    onPrimaryContainer = Color(0xFFB8F4D8),
    secondary = Color(0xFF9BE8C5),
    onSecondary = Color(0xFF0D2C1E),
    secondaryContainer = Color(0xFF233A2E),
    onSecondaryContainer = Color(0xFFD6F7E6),
    tertiary = Color(0xFFB6D6FE),
    background = Color(0xFF050B09),
    onBackground = Color(0xFFF0F8F3),
    surface = Color(0xFF101B17),
    onSurface = Color(0xFFF0F8F3),
    surfaceVariant = Color(0xFF243A30),
    onSurfaceVariant = Color(0xFFA7BDB1),
    surfaceContainerLowest = Color(0xFF050B09),
    surfaceContainerLow = Color(0xFF0A1510),
    surfaceContainer = Color(0xFF101B17),
    surfaceContainerHigh = Color(0xFF182720),
    surfaceContainerHighest = Color(0xFF203329),
    outline = Color(0xFF486356),
    outlineVariant = Color(0xFF294137),
    error = Color(0xFFFF8089)
)

private val MidnightEmeraldLight = lightColorScheme(
    primary = Color(0xFF087C55),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1F5E2),
    onPrimaryContainer = Color(0xFF06452E),
    secondary = Color(0xFF386B52),
    background = Color(0xFFF4F8F5),
    onBackground = Color(0xFF13251A),
    surface = Color.White,
    onSurface = Color(0xFF13251A),
    surfaceVariant = Color(0xFFE3EDE7),
    onSurfaceVariant = Color(0xFF536C5E),
    surfaceContainerLow = Color(0xFFEDF4EE),
    surfaceContainer = Color(0xFFE8F0E9),
    surfaceContainerHigh = Color(0xFFDCE9DF),
    outline = Color(0xFF83998B)
)

@Composable
fun MidnightEmeraldTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) MidnightEmeraldDark else MidnightEmeraldLight,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp)
        ),
        content = content
    )
}
