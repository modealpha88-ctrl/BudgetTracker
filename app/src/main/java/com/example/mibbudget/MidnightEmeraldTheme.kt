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
 * Midnight Aurora: an AMOLED-first palette that keeps the light preference usable.
 * Appearance changes only; no account, transaction, or database values are modified.
 */
private val MidnightEmeraldDark = darkColorScheme(
    primary = Color(0xFF60F0BA),
    onPrimary = Color(0xFF05251D),
    primaryContainer = Color(0xFF183F3A),
    onPrimaryContainer = Color(0xFFB9FBE5),
    secondary = Color(0xFFBDB0FF),
    onSecondary = Color(0xFF20183C),
    secondaryContainer = Color(0xFF2B2647),
    onSecondaryContainer = Color(0xFFF0EAFE),
    tertiary = Color(0xFF72DEFA),
    background = Color(0xFF070B13),
    onBackground = Color(0xFFF5F8FF),
    surface = Color(0xFF101A27),
    onSurface = Color(0xFFF5F8FF),
    surfaceVariant = Color(0xFF273747),
    onSurfaceVariant = Color(0xFFAABCC9),
    surfaceContainerLowest = Color(0xFF070B13),
    surfaceContainerLow = Color(0xFF0B1420),
    surfaceContainer = Color(0xFF101A27),
    surfaceContainerHigh = Color(0xFF1B2839),
    surfaceContainerHighest = Color(0xFF243449),
    outline = Color(0xFF526778),
    outlineVariant = Color(0xFF304354),
    error = Color(0xFFFF8995)
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
