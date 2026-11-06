package com.example.mibbudget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Fully isolated, read-only preview for testers without Maldivian bank accounts.
 * Intentionally does not receive Context, TransactionStore or real user transactions.
 */
@Composable
internal fun RufiTrackReviewerPreview(onExit: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background)
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .verticalScroll(rememberScrollState())
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("RufiTrack · DEMO", color = colors.primary, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge)
                Text("Explore without bank data", color = colors.onBackground,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            }
            TextButton(onClick = onExit) { Text("Exit demo") }
        }
        Surface(color = colors.tertiaryContainer, shape = RoundedCornerShape(14.dp)) {
            Text("FICTIONAL EXAMPLE · Read-only preview. No SMS, CSV or permissions used. Demo data is never saved.",
                modifier = Modifier.padding(14.dp),
                color = colors.onTertiaryContainer,
                style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Overview", "Activity", "Accounts").forEachIndexed { i, label ->
                FilterChip(selected = page == i, onClick = { page = i }, label = { Text(label) })
            }
        }
        when (page) {
            0 -> {
                DemoCard("PROJECTED BALANCE", "MVR 4,267.18", "Illustrative snapshot + transactions")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { DemoCard("INCOME", "MVR 14,349.21", "Example only") }
                    Box(Modifier.weight(1f)) { DemoCard("SPENT", "MVR 10,755.59", "Example only") }
                }
                DemoCard("FINANCIAL PULSE", "74% spent", "Budget progress, spending trends and safe-to-spend insights")
            }
            1 -> {
                DemoCard("TRANSACTION HISTORY", "3 sample records", "These are fictional examples")
                DemoCard("Groceries · 5 Nov", "− MVR 145.00", "Food & essentials")
                DemoCard("Salary deposit · 5 Nov", "+ MVR 9,627.00", "Income")
                DemoCard("Transport · 4 Nov", "− MVR 60.00", "Fuel & travel")
            }
            else -> {
                DemoCard("MIB · EXAMPLE", "MVR 4,267.18", "Projected balance · not live bank data")
                DemoCard("BML · EXAMPLE", "MVR 1,250.00", "Illustrative account")
                DemoCard("CASH · EXAMPLE", "MVR 500.00", "Illustrative wallet")
            }
        }
        Text("To use your own financial records, exit the demo and complete the initial CSV setup.",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun DemoCard(title: String, amount: String, subtitle: String) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(22.dp), color = colors.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.background(Brush.linearGradient(listOf(
            colors.primaryContainer, colors.surfaceContainerHigh)))
            .padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, color = colors.primary)
            Text(amount, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold, color = colors.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant)
        }
    }
}
