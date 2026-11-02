package com.example.mibbudget

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Presentation-only settings. Never write to TransactionStore from here. */
internal data class BankingPalette(
    val label: String, val accent: Color, val accentSoft: Color,
    val background: Color, val surface: Color, val raised: Color,
    val container: Color, val secondary: Color
)

internal val bankingPalettes = listOf(
    BankingPalette("Emerald Finance", Color(0xFF50E6A4), Color(0xFF9AF5CD), Color(0xFF041A17), Color(0xFF0B2521), Color(0xFF12352E), Color(0xFF123F33), Color(0xFF5FD9C5)),
    BankingPalette("Midnight Aurora", Color(0xFF4EE4C7), Color(0xFF99FAE4), Color(0xFF070B13), Color(0xFF101A27), Color(0xFF1B2839), Color(0xFF183F3A), Color(0xFFBDB0FF)),
    BankingPalette("Royal Sapphire", Color(0xFF339CFF), Color(0xFF9AD1FF), Color(0xFF070E25), Color(0xFF0D1E43), Color(0xFF172F60), Color(0xFF103775), Color(0xFF83B9FF)),
    BankingPalette("Sunset Blaze", Color(0xFFFFA05F), Color(0xFFFFD0A7), Color(0xFF1D1020), Color(0xFF31172D), Color(0xFF46233F), Color(0xFF542343), Color(0xFFFF6D9A)),
    BankingPalette("Ocean Deep", Color(0xFF63DDF5), Color(0xFFB5F4FF), Color(0xFF071727), Color(0xFF0D293B), Color(0xFF14394E), Color(0xFF12435A), Color(0xFF4FC5E0)),
    BankingPalette("Amethyst Night", Color(0xFFC48AFF), Color(0xFFE7C5FF), Color(0xFF160C27), Color(0xFF26163D), Color(0xFF382352), Color(0xFF48216D), Color(0xFFAA8EFF))
)

internal object BankingAppearance {
    var selected by mutableStateOf("Emerald Finance")
        private set
    private var loaded = false

    fun initialize(context: Context) {
        if (!loaded) {
            val stored = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
                .getString("banking_palette", "Emerald Finance")
            selected = bankingPalettes.firstOrNull { it.label == stored }?.label ?: "Emerald Finance"
            loaded = true
        }
    }

    fun select(context: Context, name: String) {
        if (bankingPalettes.none { it.label == name }) return
        selected = name
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
            .putString("banking_palette", name).apply()
    }
}

@Composable
fun MidnightEmeraldTheme(dark: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    BankingAppearance.initialize(context)
    val palette = bankingPalettes.first { it.label == BankingAppearance.selected }
    val colors = if (dark) darkColorScheme(
        primary = palette.accent,
        onPrimary = Color(0xFF041B17),
        primaryContainer = palette.container,
        onPrimaryContainer = palette.accentSoft,
        secondary = palette.secondary,
        onSecondary = Color(0xFF07121B),
        secondaryContainer = palette.raised,
        onSecondaryContainer = Color(0xFFE7F7F0),
        tertiary = palette.accentSoft,
        background = palette.background,
        onBackground = Color(0xFFF3FBF7),
        surface = palette.surface,
        onSurface = Color(0xFFF3FBF7),
        surfaceVariant = palette.raised,
        onSurfaceVariant = Color(0xFFADC5BD),
        surfaceContainerLowest = palette.background,
        surfaceContainerLow = palette.surface,
        surfaceContainer = palette.surface,
        surfaceContainerHigh = palette.raised,
        surfaceContainerHighest = palette.container,
        outline = Color(0xFF577568),
        outlineVariant = Color(0xFF315249),
        error = Color(0xFFFF8995)
    ) else {
        // A separate, hue-matched light scheme for every colorway.
        // Light mode must never silently fall back to Emerald Finance.
        val light = when (palette.label) {
            "Royal Sapphire" -> listOf(0xFF1556B8, 0xFFD9E8FF, 0xFFF4F8FF, 0xFFE8F0FF, 0xFFD6E5FF, 0xFF102B56)
            "Midnight Aurora" -> listOf(0xFF007E78, 0xFFCCF3ED, 0xFFF1FAF9, 0xFFE3F3F1, 0xFFD1EAE6, 0xFF123E3D)
            "Sunset Blaze" -> listOf(0xFFA23B56, 0xFFFFDBDE, 0xFFFFF6F5, 0xFFFFEBE9, 0xFFF8D8DE, 0xFF56243B)
            "Ocean Deep" -> listOf(0xFF007C9C, 0xFFC8F0FA, 0xFFF0FAFC, 0xFFE0F3F9, 0xFFC9E7F2, 0xFF153C51)
            "Amethyst Night" -> listOf(0xFF793BAE, 0xFFEEDCFF, 0xFFFBF6FF, 0xFFF2E9FA, 0xFFE5D6F5, 0xFF392051)
            else -> listOf(0xFF087C55, 0xFFD1F5E2, 0xFFF4F8F5, 0xFFE8F0E9, 0xFFDCE9DF, 0xFF132F23)
        }.map { Color(it) }
        lightColorScheme(
            primary = light[0],
            onPrimary = Color.White,
            primaryContainer = light[1],
            onPrimaryContainer = light[5],
            secondary = light[0],
            onSecondary = Color.White,
            secondaryContainer = light[3],
            onSecondaryContainer = light[5],
            tertiary = light[0],
            onTertiary = Color.White,
            background = light[2],
            onBackground = light[5],
            surface = Color.White,
            onSurface = light[5],
            surfaceVariant = light[3],
            onSurfaceVariant = light[5].copy(alpha = 0.78f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = light[2],
            surfaceContainer = light[3],
            surfaceContainerHigh = light[4],
            surfaceContainerHighest = light[4],
            outline = light[0].copy(alpha = 0.66f),
            outlineVariant = light[0].copy(alpha = 0.24f),
            error = Color(0xFFB3261E)
        )
    }
    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp)
        ),
        content = content
    )
}
