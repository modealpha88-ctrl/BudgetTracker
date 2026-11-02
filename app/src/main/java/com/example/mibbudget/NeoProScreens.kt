package com.example.mibbudget

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Neo Banking Pro V2: data-driven screens, built as separate composables to avoid JVM method limits. */
private val neoViolet = Color(0xFFAD91F1)
private val neoAqua = Color(0xFF7BCBC3)
private val neoPeach = Color(0xFFF5BF87)
private val neoBlue = Color(0xFF8CBDF1)

@Composable
private fun NeoSectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun NeoHeader(eyebrow: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(eyebrow, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NeoShortcut(symbol: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center) {
                Text(symbol, color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun NeoSpendingBars(entries: List<Tx>) {
    val today = LocalDate.now()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val dayAmounts = entries.filter { it.amountLaari < 0 && it.category != "Internal Transfer" }
        .groupBy { it.timestamp.take(10) }
        .mapValues { (_, rows) -> rows.sumOf { -it.amountLaari } }
    val points = days.map { dayAmounts[it.toString()] ?: 0L }
    val maximum = (points.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.Bottom) {
        points.forEach { amount ->
            val height = if (amount == 0L) 4f else 7f + 44f * (amount.toFloat() / maximum)
            Box(Modifier.weight(1f).height(height.dp).clip(RoundedCornerShape(5.dp))
                .background(if (amount == 0L) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f + (amount.toFloat() / maximum) * 0.55f)))
        }
    }
}

@Composable
private fun NeoMoneyTile(label: String, amount: Long, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(19.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
        Text(Money.format(amount), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun NeoMixStrip(entries: List<Tx>, onInsights: () -> Unit) {
    val expenses = entries.filter { it.amountLaari < 0 && it.category != "Internal Transfer" }
    val totals = expenses.groupBy { it.category }.mapValues { (_, rows) -> rows.sumOf { -it.amountLaari } }
        .toList().sortedByDescending { it.second }
    val spent = totals.sumOf { it.second }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NeoSectionTitle("Where it went", "Details", onInsights)
        if (spent == 0L) {
            Text("No expenses for the selected month.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val palette = listOf(neoViolet, neoAqua, neoPeach, neoBlue)
            Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(8.dp)),
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                totals.forEachIndexed { index, pair ->
                    val weight = (pair.second.toFloat() / spent).coerceAtLeast(0.003f)
                    Box(Modifier.weight(weight).fillMaxHeight().background(palette[index % palette.size]))
                }
            }
            totals.take(3).forEachIndexed { index, pair ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(palette[index % palette.size], CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(pair.first, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text("${pair.second * 100 / spent}%", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
internal fun NeoProHomeScreen(
    context: Context,
    transactions: List<Tx>,
    monthlyTransactions: List<Tx>,
    accountLabels: Map<String, String>,
    savedRecipients: Map<String, String>,
    pendingBmlCount: Int,
    onAccounts: () -> Unit,
    onPlanning: () -> Unit,
    onInsights: () -> Unit,
    onReview: () -> Unit,
    onActivity: () -> Unit,
    onTransaction: (Long) -> Unit,
    onAdvanced: () -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit
) {
    val balances = reconciledAccountBalances(context, transactions, accountLabels)
    val primaryBank = listOf("MIB", "BML", "Cash").firstOrNull { balances[it] != null }
    val balance = primaryBank?.let { balances[it] }
    val expenses = monthlyTransactions.filter { it.amountLaari < 0 && it.category != "Internal Transfer" }
    val spent = expenses.sumOf { -it.amountLaari }
    val income = monthlyTransactions.filter { it.amountLaari > 0 && it.category != "Internal Transfer" }
        .sumOf { it.amountLaari }
    val ratio = if (income > 0L) spent.toDouble() / income else 0.0
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("BUDGET TRACKER", color = scheme.primary, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold)
                Text("Overview", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            }
            Surface(onClick = onAdvanced, shape = CircleShape,
                color = scheme.surfaceContainerHigh, modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("⚙", style = MaterialTheme.typography.titleLarge) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = scheme.primary) {
                Text("Overview", modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                    color = scheme.onPrimary, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onAccounts) { Text("Accounts") }
            TextButton(onClick = onInsights) { Text("Insights") }
        }
        Card(onClick = onAccounts, shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer)) {
            Box(Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(scheme.primaryContainer, scheme.surfaceContainerHigh)))) {
                Box(Modifier.align(Alignment.TopEnd).offset(x = 44.dp, y = (-48).dp)
                    .size(170.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.09f)))
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("${primaryBank ?: "ACCOUNTS"} • PROJECTED BALANCE",
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            color = scheme.onSurfaceVariant)
                        Text("↗", style = MaterialTheme.typography.titleLarge, color = scheme.primary)
                    }
                    Text(balance?.let(Money::format) ?: "Balance not set",
                        style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold,
                        color = scheme.onSurface)
                    Text("Snapshot + recorded transactions · Not live bank data",
                        color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.12f))
                    Text("RECORDED SPENDING • LAST 7 DAYS", style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant)
                    NeoSevenDaySpendTrend(transactions)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${balances.size} of 3 accounts have snapshots", style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant)
                        Text("View accounts  ↗", style = MaterialTheme.typography.labelSmall, color = scheme.primary)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeoShortcut("+", "Add", onAdd, Modifier.weight(1f))
            NeoShortcut("⇄", "Activity", onActivity, Modifier.weight(1f))
            NeoShortcut("↥", "Import", onImport, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeoMoneyTile("INCOME", income, scheme.primary, Modifier.weight(1f))
            NeoMoneyTile("SPENT", spent, neoPeach, Modifier.weight(1f))
        }
        Card(shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Monthly spending", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(if (income > 0L) "${(ratio * 100).toInt()}% of income" else "No income recorded",
                        color = if (ratio > 1.0) scheme.error else scheme.primary,
                        style = MaterialTheme.typography.labelMedium)
                }
                LinearProgressIndicator(progress = { ratio.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = if (ratio > 1.0) scheme.error else scheme.primary,
                    trackColor = scheme.surfaceContainerHigh)
                Text(if (income == 0L) "Spending compared with recorded income is unavailable."
                     else if (spent > income) "Spent ${Money.format(spent - income)} more than recorded income"
                     else "Recorded income less spending: ${Money.format(income - spent)}",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
        NeoMixStrip(monthlyTransactions, onInsights)
        if (pendingBmlCount > 0) Card(onClick = onReview,
            colors = CardDefaults.cardColors(containerColor = scheme.secondaryContainer)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("!  ${pendingBmlCount} BML credit(s) need review", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text("›", color = scheme.primary)
            }
        }
        NeoSectionTitle("Recent activity", "See all", onActivity)
        if (transactions.isEmpty()) Text("Imported transactions appear here.", color = scheme.onSurfaceVariant)
        transactions.sortedByDescending { it.timestamp }.take(4).forEach { tx ->
            NeoTransactionRow(tx, savedRecipients[tx.fingerprint]) { onTransaction(tx.id) }
        }
        TextButton(onClick = onPlanning, modifier = Modifier.fillMaxWidth()) {
            Text("Safe-to-spend, goals & financial planning  ↗")
        }
    }
}

/** Human-readable, stable timestamp without exposing raw ISO date-time strings. */
private fun neoActivityDate(timestamp: String): String {
    val day = runCatching { LocalDate.parse(timestamp.take(10)) }.getOrNull()
        ?: return timestamp.take(16).replace("T", " ")
    val dayLabel = when (day) {
        LocalDate.now() -> "Today"
        LocalDate.now().minusDays(1) -> "Yesterday"
        else -> "${day.dayOfMonth} ${day.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
    }
    val time = timestamp.drop(11).take(5).takeIf { it.matches(Regex("""\d{2}:\d{2}""")) }
    return if (time == null) dayLabel else "$dayLabel · $time"
}

@Composable
internal fun NeoTransactionRow(tx: Tx, savedRecipient: String?, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(43.dp).clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center) {
                Text(MerchantIcons.icon(tx.description, tx.category), style = MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(TransactionPresentation.displayLabel(tx.description, tx.category, savedRecipient),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("${tx.category} · ${neoActivityDate(tx.timestamp)}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(Money.format(tx.amountLaari), style = MaterialTheme.typography.labelMedium,
                color = if (tx.amountLaari > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun NeoAccountCard(
    bank: String,
    value: Long?,
    count: Int,
    onOpen: () -> Unit,
    onTransactions: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val tint = when (bank) { "MIB" -> scheme.primary; "BML" -> neoBlue; else -> neoPeach }
    Card(onClick = onOpen, modifier = Modifier.width(282.dp).height(221.dp),
        shape = RoundedCornerShape(27.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh)) {
        Box(Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(tint.copy(alpha = 0.21f), scheme.surfaceContainerHigh)))) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 38.dp, y = (-40).dp).size(140.dp)
                .clip(CircleShape).background(tint.copy(alpha = 0.14f)))
            Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(11.dp).clip(CircleShape).background(tint))
                    Spacer(Modifier.width(9.dp))
                    Text(if (bank == "Cash") "Cash wallet" else bank, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("↗", color = tint)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(value?.let(Money::format) ?: "Balance not set",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text(if (value == null) "Snapshot required" else "Snapshot-based estimate",
                        color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${count} transactions", style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f), color = scheme.onSurfaceVariant)
                    TextButton(onClick = onTransactions, contentPadding = PaddingValues(0.dp)) {
                        Text("Activity  ›", color = tint)
                    }
                }
            }
        }
    }
}

@Composable
private fun NeoCountTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp))
        .background(MaterialTheme.colorScheme.surfaceContainer).padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
internal fun NeoProAccountsScreen(
    context: Context,
    transactions: List<Tx>,
    accountLabels: Map<String, String>,
    onOpenAccount: (String) -> Unit,
    onAccountTransactions: (String) -> Unit,
    onAdvanced: () -> Unit,
    onPlanning: () -> Unit
) {
    val balances = reconciledAccountBalances(context, transactions, accountLabels)
    val banks = listOf("MIB", "BML", "Cash")
    val counts = banks.associateWith { bank ->
        transactions.count { FinanceCalculations.assignedAccount(it, accountLabels) == bank }
    }
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
        NeoHeader("ACCOUNTS / WALLETS", "Your accounts", "Manage balances, activity and reconciliation")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            NeoCountTile("BALANCES AVAILABLE", "${balances.size} / 3", scheme.primary, Modifier.weight(1f))
            NeoCountTile("TRANSACTIONS", "${transactions.size}", scheme.tertiary, Modifier.weight(1f))
        }
        Text("Swipe to explore accounts", style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            banks.forEach { bank ->
                NeoAccountCard(bank, balances[bank], counts[bank] ?: 0,
                    onOpen = { onOpenAccount(bank) },
                    onTransactions = { onAccountTransactions(bank) })
            }
        }
        NeoSectionTitle("Account overview", "Reconcile", onAdvanced)
        banks.forEach { bank ->
            val value = balances[bank]
            Card(onClick = { onOpenAccount(bank) }, shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer)) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val accent = when (bank) {"MIB" -> scheme.primary; "BML" -> neoBlue; else -> neoPeach }
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Text(if (bank == "Cash") "◈" else "▣", color = accent,
                            style = MaterialTheme.typography.titleMedium)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (bank == "Cash") "Cash wallet" else bank,
                            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(if (value == null) "Not reconciled" else "${counts[bank] ?: 0} linked transactions",
                            style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    }
                    Text(value?.let(Money::format) ?: "Set balance",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (value == null) scheme.primary else scheme.onSurface)
                    Text("›", color = scheme.primary)
                }
            }
        }
        OutlinedButton(onClick = onPlanning, modifier = Modifier.fillMaxWidth()) {
            Text("Cash wallet & financial planning")
        }
    }
}

@Composable
internal fun NeoProInsightsScreen(
    monthlyTransactions: List<Tx>,
    selectedMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onCategory: (String) -> Unit,
    onMerchant: (String, String) -> Unit,
    onPlanning: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val expenses = monthlyTransactions.filter { it.amountLaari < 0 && it.category != "Internal Transfer" }
    val spent = expenses.sumOf { -it.amountLaari }
    val income = monthlyTransactions.filter { it.amountLaari > 0 && it.category != "Internal Transfer" }
        .sumOf { it.amountLaari }
    val categoryTotals = expenses.groupBy { it.category }.mapValues { (_, rows) -> rows.sumOf { -it.amountLaari } }
        .toList().sortedByDescending { it.second }
    Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
        NeoHeader("INSIGHTS / ANALYTICS", "Your insights", "Your spending patterns, made clear")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onMonthChange(selectedMonth.minusMonths(1)) }) { Text("‹") }
            Text("${selectedMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${selectedMonth.year}",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = { onMonthChange(selectedMonth.plusMonths(1)) },
                enabled = selectedMonth < YearMonth.now()) { Text("›") }
        }
        Card(shape = RoundedCornerShape(27.dp),
            colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer)) {
            Column(Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(scheme.primaryContainer, scheme.surfaceContainerHigh)))
                .padding(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("NET CASH FLOW", color = scheme.primary, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold)
                Text(Money.format(income - spent), style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold, color = scheme.onSurface)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("↑  ${Money.format(income)} incoming", style = MaterialTheme.typography.labelMedium)
                    Text("↓  ${Money.format(spent)} out", style = MaterialTheme.typography.labelMedium)
                }
                HorizontalDivider(color = scheme.outlineVariant)
                PremiumWeeklySpendBars(expenses, selectedMonth)
            }
        }
        Card(shape = RoundedCornerShape(25.dp),
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer)) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Spending breakdown", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (expenses.isEmpty()) Text("No expenses recorded in this month.")
                else SpendingBreakdown(expenses = expenses, initialStyle = "Treemap",
                    onStyleChange = {}, onCategory = onCategory, onMerchant = onMerchant)
            }
        }
        NeoSectionTitle("Top categories", "Planning", onPlanning)
        categoryTotals.take(4).forEachIndexed { index, pair ->
            val tint = listOf(neoViolet, neoAqua, neoPeach, neoBlue)[index % 4]
            Card(onClick = { onCategory(pair.first) }, shape = RoundedCornerShape(17.dp),
                colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(7.dp).background(tint, CircleShape))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(pair.first, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text(Money.format(pair.second), style = MaterialTheme.typography.labelMedium)
                        }
                        LinearProgressIndicator(progress = { if(spent > 0) pair.second.toFloat() / spent else 0f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = tint, trackColor = scheme.surfaceContainerHigh)
                    }
                    Text("›", color = scheme.primary)
                }
            }
        }
    }
}
