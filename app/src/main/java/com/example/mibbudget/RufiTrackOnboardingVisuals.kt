package com.example.mibbudget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Presentation-only first-run onboarding visuals. The flow state, file import, SMS permissions,
 * account snapshots and persistence remain owned by BudgetApp.
 */
private val setupSteps = listOf(
    Triple("Statement", "Build your starting ledger", Icons.Outlined.Description),
    Triple("Connect", "Link bank messages", Icons.Outlined.Sms),
    Triple("Review", "Make transactions accurate", Icons.Outlined.FactCheck),
    Triple("Balances", "Start from a verified balance", Icons.Outlined.AccountBalanceWallet)
)

@Composable
internal fun RufiTrackSetupHeader(stage: Int) {
    val index = stage.coerceIn(0, 3)
    val palette = MaterialTheme.colorScheme
    val accent = palette.primary
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(18.dp),
                color = accent.copy(alpha = 0.13f),
                modifier = Modifier.size(58.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Image(painter = painterResource(R.drawable.rufiyaa_mark),
                        contentDescription = "Original rufiyaa symbol",
                        colorFilter = ColorFilter.tint(accent),
                        modifier = Modifier.size(width = 23.dp, height = 39.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("RufiTrack", style = MaterialTheme.typography.headlineSmall,
                    color = palette.onBackground, fontWeight = FontWeight.ExtraBold)
                Text("YOUR MONEY, IN FOCUS",
                    style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp,
                    color = accent)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Let's get you set up", style = MaterialTheme.typography.headlineMedium,
                    color = palette.onBackground, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text("Four quick steps to a clearer financial picture",
                    color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Surface(shape = RoundedCornerShape(14.dp),
                color = accent.copy(alpha = 0.15f)) {
                Text("${index + 1} / 4", color = accent, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.fillMaxWidth()) {
            setupSteps.forEachIndexed { step, item ->
                Column(modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.fillMaxWidth().height(5.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (step <= index) accent else palette.onSurface.copy(alpha = 0.12f)))
                    Text(item.first, style = MaterialTheme.typography.labelSmall,
                        color = if (step == index) accent else palette.onSurfaceVariant,
                        maxLines = 1)
                }
            }
        }
        RufiTrackSetupHero(index)
    }
}

@Composable
private fun RufiTrackSetupHero(index: Int) {
    val colors = MaterialTheme.colorScheme
    val current = setupSteps[index]
    Surface(shape = RoundedCornerShape(28.dp), color = colors.primaryContainer,
        modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.background(Brush.linearGradient(listOf(
            colors.primaryContainer, colors.surfaceContainerHigh, colors.primaryContainer
        )))) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 30.dp, y = (-35).dp)
                .size(145.dp).clip(CircleShape)
                .background(colors.primary.copy(alpha = 0.075f)))
            Column(Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(14.dp),
                        color = colors.primary.copy(alpha = 0.17f)) {
                        Icon(current.third, contentDescription = null, tint = colors.primary,
                            modifier = Modifier.padding(11.dp).size(28.dp))
                    }
                    Column {
                        Text("STEP ${index + 1} · ${current.first.uppercase()}",
                            color = colors.primary, style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold)
                        Text(current.second, color = colors.onSurface,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                    }
                }
                when (index) {
                    0 -> SetupLedgerIllustration()
                    1 -> SetupConnectionIllustration()
                    2 -> SetupReviewIllustration()
                    else -> SetupBalanceIllustration()
                }
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Outlined.Lock, contentDescription = null,
                        tint = colors.primary, modifier = Modifier.size(15.dp))
                    Text("Private by design · Stored on your device",
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun SetupLedgerIllustration() {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(18.dp),
        color = colors.surface.copy(alpha = 0.78f)) {
        Column(Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("STATEMENT PREVIEW", color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold)
                Text("CSV", color = colors.primary, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium)
            }
            SetupLedgerRow("01", "Transactions", "••••  ••••", colors.primary)
            SetupLedgerRow("02", "Categories", "••••  •••", colors.tertiary)
            SetupLedgerRow("03", "Starting balance", "MVR  —", colors.primary)
        }
    }
}

@Composable
private fun SetupLedgerRow(number: String, label: String, value: String, tint: Color) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(number, color = tint, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall)
        Text(label, color = colors.onSurface,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f))
        Text(value, color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SetupConnectionIllustration() {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SetupIllustrationTile("MIB", "SMS", Icons.Outlined.Sms, Modifier.weight(1f))
        SetupIllustrationTile("BML", "SMS + alerts", Icons.Outlined.Sms, Modifier.weight(1f))
    }
}

@Composable
private fun SetupIllustrationTile(title: String, subtitle: String, icon: ImageVector,
                                  modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp),
        color = colors.surface.copy(alpha = 0.78f)) {
        Column(Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = colors.primary,
                modifier = Modifier.size(27.dp))
            Text(title, color = colors.onSurface, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SetupReviewIllustration() {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(18.dp), color = colors.surface.copy(alpha = 0.78f)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SetupReviewStatus("Imported transactions", "Check matches")
            HorizontalDivider(color = colors.outlineVariant)
            SetupReviewStatus("Possible duplicates", "Review safely")
            HorizontalDivider(color = colors.outlineVariant)
            SetupReviewStatus("Recipient details", "Keep accurate")
        }
    }
}

@Composable
private fun SetupReviewStatus(label: String, state: String) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null,
            tint = colors.primary, modifier = Modifier.size(18.dp))
        Text(label, modifier = Modifier.weight(1f), color = colors.onSurface,
            style = MaterialTheme.typography.bodySmall)
        Text(state, color = colors.primary,
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SetupBalanceIllustration() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        listOf("MIB", "BML", "Cash").forEach { name ->
            SetupIllustrationTile(name, "Verify", Icons.Outlined.AccountBalanceWallet,
                Modifier.weight(1f))
        }
    }
}

@Composable
internal fun RufiTrackSetupTip(title: String, detail: String) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(18.dp), color = colors.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null,
                tint = colors.primary, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = colors.onSurface, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium)
                Text(detail, color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
