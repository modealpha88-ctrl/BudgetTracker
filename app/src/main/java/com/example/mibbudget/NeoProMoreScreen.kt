package com.example.mibbudget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Compact destination rows match the same navigation rhythm as the other Neo Pro screens. */
@Composable
private fun NeoToolsCard(
    symbol: String, label: String, description: String, onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(scheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(symbol, style = MaterialTheme.typography.titleMedium, color = scheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant, maxLines = 2)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = scheme.primary)
        }
    }
}

@Composable
private fun NeoColorwayTile(palette: BankingPalette) {
    val context = LocalContext.current
    val active = BankingAppearance.selected == palette.label
    Card(onClick = { BankingAppearance.select(context, palette.label) },
        shape = RoundedCornerShape(19.dp),
        border = if (active) androidx.compose.foundation.BorderStroke(2.dp, palette.accent) else null,
        colors = CardDefaults.cardColors(containerColor = palette.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(palette.accent, palette.secondary, palette.raised).forEach { c ->
                    Box(Modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).background(c))
                }
            }
            Text(palette.label, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, color = Color.White)
            Text(if (active) "✓ Selected" else "Tap to apply",
                style = MaterialTheme.typography.labelSmall,
                color = if (active) palette.accent else Color(0xFFCCD6E0))
        }
    }
}

@Composable
internal fun NeoProMoreScreen(
    darkTheme: Boolean,
    setDarkTheme: (Boolean) -> Unit,
    lockEnabled: Boolean,
    onLockChange: (Boolean) -> Unit,
    screenshotPrivacy: Boolean,
    onScreenshotPrivacyChange: (Boolean) -> Unit,
    transactionCount: Int,
    onNavigate: (Int) -> Unit
) {
    var appearanceOpen by remember { mutableStateOf(false) }
    var privacyOpen by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (privacyOpen) {
            TextButton(onClick = { privacyOpen = false }, contentPadding = PaddingValues(0.dp)) {
                Text("‹  More")
            }
            PrivacyFeedbackScreen(LocalContext.current, transactionCount)
        } else if (!appearanceOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("YOUR FINANCIAL TOOLKIT", color = colors.primary,
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text("Explore", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text("Planning, controls and personal settings",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Surface(shape = RoundedCornerShape(16.dp), color = colors.primaryContainer) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(colors.primary))
                    Text("Your money tools · ${BankingAppearance.selected}",
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                        color = colors.onPrimaryContainer)
                    Text("●", color = colors.primary, style = MaterialTheme.typography.labelMedium)
                }
            }
            Text("FINANCIAL TOOLS", color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            NeoToolsCard("◎", "Budgets", "Category limits, rollover and progress", { onNavigate(3) })
            NeoToolsCard("◷", "Plan & reports", "Safe-to-spend, savings, calendar and forecasts", { onNavigate(4) })
            NeoToolsCard("⇣", "Import & access", "CSV, bank messages and reconciliation", { onNavigate(2) })
            NeoToolsCard("▤", "Advanced dashboard", "Balances, transfers and detailed tools", { onNavigate(8) })
            Text("SECURITY & PREFERENCES", style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Card(onClick = { appearanceOpen = true }, shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp))
                        .background(colors.primary.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                        Text("◈", style = MaterialTheme.typography.titleLarge, color = colors.primary)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Appearance & privacy", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium)
                        Text("${BankingAppearance.selected} · ${if (darkTheme) "AMOLED dark" else "Light"}",
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    Text("›", color = colors.primary, style = MaterialTheme.typography.titleLarge)
                }
            }
            Card(onClick = { privacyOpen = true }, shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer)) {
                Row(Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp))
                        .background(colors.primary.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center) {
                        Text("◇", style = MaterialTheme.typography.titleLarge, color = colors.primary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Privacy & diagnostics", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium)
                        Text("Consent, feedback and local data controls",
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    Text("›", color = colors.primary, style = MaterialTheme.typography.titleLarge)
                }
            }
        } else {
            TextButton(onClick = { appearanceOpen = false }, contentPadding = PaddingValues(0.dp)) {
                Text("‹  More")
            }
            Text("Appearance & privacy", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold)
            Text("Your selected colorway applies across the app.",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Card(shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Text("Neo Banking Pro colorways", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    bankingPalettes.chunked(2).forEach { group ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            group.forEach { palette ->
                                Box(Modifier.weight(1f)) { NeoColorwayTile(palette) }
                            }
                        }
                    }
                }
            }
            Card(shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("AMOLED dark mode", style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold)
                            Text("High contrast with your chosen accent",
                                color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
                        Switch(checked = darkTheme, onCheckedChange = setDarkTheme)
                    }
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Biometric app lock", modifier = Modifier.weight(1f))
                        Switch(checked = lockEnabled, onCheckedChange = onLockChange)
                    }
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Hide sensitive screenshots", modifier = Modifier.weight(1f))
                        Switch(checked = screenshotPrivacy, onCheckedChange = onScreenshotPrivacyChange)
                    }
                    Text("Transactions and account data remain stored locally.",
                        style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}
