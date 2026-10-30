package com.example.mibbudget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Midnight Aurora surfaces for Home and Overview.
 *
 * Presentation only: takes calculated financial values from the existing ledger,
 * does not write preferences, transactions, anchors, or account balances.
 */
private val Mint = Color(0xFF60F0BA)
private val Ice = Color(0xFF72DEFA)
private val Lavender = Color(0xFFBDB0FF)
private val Gold = Color(0xFFFFCB86)
private val SoftWhite = Color(0xFFF5FCFB)
private val SoftMuted = Color(0xFFB1C7C4)

@Composable
private fun AuroraDecoration(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawCircle(
            color = Color.White.copy(alpha = 0.055f),
            radius = size.minDimension * 0.62f,
            center = Offset(size.width * 1.02f, size.height * 0.06f)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.055f),
            radius = size.minDimension * 0.40f,
            center = Offset(size.width * 1.02f, size.height * 0.06f)
        )
        drawCircle(
            color = Mint.copy(alpha = 0.14f),
            radius = size.minDimension * 0.22f,
            center = Offset(size.width * 0.94f, size.height * 0.08f)
        )
    }
}

@Composable
private fun AuroraTag(value: String, color: Color = Mint) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(value, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun PremiumSafeToSpendCard(
    safe: Long?,
    available: Long,
    reserved: Long,
    bills: Long,
    balances: Map<String, Long>,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A342F))
    ) {
        Box(Modifier.fillMaxWidth().background(
            Brush.linearGradient(listOf(Color(0xFF174E45), Color(0xFF0B3037), Color(0xFF122539)))
        )) {
            AuroraDecoration(Modifier.matchParentSize())
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("SAFE TO SPEND", style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold, color = SoftMuted)
                    AuroraTag("ESTIMATED")
                }
                Text(safe?.let(Money::format) ?: "Balance unavailable",
                    style = if (safe != null) MaterialTheme.typography.headlineLarge
                            else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold, color = SoftWhite)
                Text("After savings allocations and upcoming recurring bills",
                    style = MaterialTheme.typography.bodySmall, color = SoftMuted)
                HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("●  From verified snapshots", style = MaterialTheme.typography.labelSmall, color = Mint)
                    TextButton(onClick = onToggle, contentPadding = PaddingValues(0.dp)) {
                        Text(if (expanded) "Hide details ↑" else "Breakdown ↗",
                            color = SoftWhite, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (expanded) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                    listOf("MIB", "BML", "Cash").forEach { bank ->
                        AuroraBreakdownRow(bank, balances[bank]?.let(Money::format) ?: "No verified snapshot")
                    }
                    AuroraBreakdownRow("Available funds", Money.format(available))
                    AuroraBreakdownRow("Savings reserved", "−" + Money.format(reserved))
                    AuroraBreakdownRow("Unpaid recurring bills", "−" + Money.format(bills))
                    if (balances.values.any { it < 0L }) {
                        Text("Negative account balances are excluded from spendable funds.",
                            style = MaterialTheme.typography.bodySmall, color = Gold)
                    }
                    Text("Estimate only — not a live bank balance. Missing transactions may affect it.",
                        style = MaterialTheme.typography.bodySmall, color = SoftMuted)
                }
            }
        }
    }
}

@Composable
private fun AuroraBreakdownRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = SoftMuted,
            modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall,
            color = SoftWhite, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun PremiumForecastCard(predicted: Long) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF211D3C))) {
        Box(Modifier.fillMaxWidth().background(
            Brush.linearGradient(listOf(Color(0xFF30264E), Color(0xFF202640), Color(0xFF152436)))
        )) {
            AuroraDecoration(Modifier.matchParentSize())
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("MONTH-END FORECAST", style = MaterialTheme.typography.labelMedium,
                        color = Lavender, fontWeight = FontWeight.Bold)
                    AuroraTag("PROJECTION", Gold)
                }
                Text(Money.format(predicted), style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, color = SoftWhite)
                Text("Projected expenses at your recorded spending pace",
                    style = MaterialTheme.typography.bodySmall, color = SoftMuted)
            }
        }
    }
}

@Composable
internal fun PremiumAccountPill(bank: String, selected: Boolean, onClick: () -> Unit) {
    val accent = when (bank) {
        "MIB" -> Mint
        "BML" -> Ice
        else -> Gold
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor =
            if (selected) accent.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(accent))
            Text(bank, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun PremiumAccountSnapshotCard(
    bank: String,
    amount: Long?,
    anchorDate: String,
    onClick: () -> Unit
) {
    val accent = when (bank) {
        "MIB" -> Mint
        "BML" -> Ice
        else -> Gold
    }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF132B34))) {
        Box(Modifier.fillMaxWidth().background(
            Brush.linearGradient(listOf(Color(0xFF153C38), Color(0xFF142C3C), Color(0xFF172837)))
        )) {
            AuroraDecoration(Modifier.matchParentSize())
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("$bank ACCOUNT", color = accent, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge)
                    AuroraTag(if (amount == null) "SET UP" else "SNAPSHOT", accent)
                }
                Text(amount?.let(Money::format) ?: "Balance not set",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold, color = SoftWhite)
                Text(if (amount == null) "Set a verified balance to start tracking this account"
                     else "Estimated today · Based on $anchorDate snapshot + recorded movements",
                    style = MaterialTheme.typography.bodySmall, color = SoftMuted)
                Spacer(Modifier.height(1.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Manage balance & history", style = MaterialTheme.typography.labelLarge,
                        color = accent, fontWeight = FontWeight.SemiBold)
                    Text("↗", color = accent, fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
internal fun PremiumMonthlyInsightStats(income: Long, expenses: Long) {
    val ratio = if (income > 0L) expenses.toDouble() / income.toDouble() else 0.0
    val percent = (ratio * 100.0).toLong()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PremiumStatTile("INCOME", Money.format(income), Mint, Modifier.weight(1f))
        PremiumStatTile("SPENT", Money.format(expenses), Gold, Modifier.weight(1f))
    }
    Spacer(Modifier.height(9.dp))
    LinearProgressIndicator(
        progress = { ratio.coerceIn(0.0, 1.0).toFloat() },
        modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)),
        color = if (ratio > 1.0) Color(0xFFFF898E) else Mint,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    )
    Text(if (income > 0L) "$percent% of recorded income spent"
         else "No recorded income this month",
        style = MaterialTheme.typography.bodySmall,
        color = if (ratio > 1.0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PremiumStatTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp))
        .background(accent.copy(alpha = 0.09f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = accent,
            fontWeight = FontWeight.Bold)
        Text(value, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2)
    }
}

@Composable
internal fun PremiumAccountsOverview(balances: Map<String, Long>, onAccounts: () -> Unit) {
    Card(onClick = onAccounts, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF132B34))) {
        Box(Modifier.fillMaxWidth().background(
            Brush.linearGradient(listOf(Color(0xFF173C43), Color(0xFF142E3E), Color(0xFF1B2240)))
        )) {
            AuroraDecoration(Modifier.matchParentSize())
            Column(Modifier.padding(21.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("ACCOUNTS AT A GLANCE", style = MaterialTheme.typography.labelMedium,
                            color = Ice, fontWeight = FontWeight.Bold)
                        Text("Your money", style = MaterialTheme.typography.headlineSmall,
                            color = SoftWhite, fontWeight = FontWeight.ExtraBold)
                    }
                    AuroraTag("VIEW ALL ↗", Ice)
                }
                listOf("MIB", "BML", "Cash").forEach { bank ->
                    val accent = when (bank) { "MIB" -> Mint; "BML" -> Ice; else -> Gold }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
                            Text(bank, color = SoftWhite, fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium)
                        }
                        Text(balances[bank]?.let(Money::format) ?: "Not reconciled",
                            color = if (balances.containsKey(bank)) SoftWhite else SoftMuted,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                Text("Snapshot-based estimates · Tap to manage balances",
                    color = SoftMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
