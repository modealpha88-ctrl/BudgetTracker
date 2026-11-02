package com.example.mibbudget

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
        Column(Modifier.fillMaxWidth().padding(vertical = if(BankingAppearance.density=="Compact") 9.dp else if(BankingAppearance.density=="Comfortable") 18.dp else 13.dp), horizontalAlignment = Alignment.CenterHorizontally,
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
        .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(BankingAppearance.cardPadding),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
        Text(Money.format(amount), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Monthly pulse uses recorded income/spending, never claims to be the live bank balance. */
@Composable
private fun NeoMonthlyPulse(income: Long, spent: Long, onPlanning: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val ratio = if (income > 0L) spent.toDouble() / income else 0.0
    val percent = (ratio * 100.0).toInt()
    Card(
        shape = RoundedCornerShape(23.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(17.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { ratio.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 7.dp,
                    color = if (ratio > 1.0) scheme.error else scheme.primary,
                    trackColor = scheme.surfaceContainerHighest
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (income == 0L) "—" else "$percent%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold)
                    Text("spent", style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Financial pulse", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text(
                    if (income == 0L) "No recorded income this month"
                    else if (spent > income) "Spending exceeds income by ${Money.format(spent - income)}"
                    else "Net this month: +${Money.format(income - spent)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onPlanning,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Safe-to-spend & planning  ↗", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Figures are computed from the local ledger, never from demonstration mockup amounts. */
@Composable
private fun NeoRecordedComparisons(entries: List<Tx>) {
    val scheme = MaterialTheme.colorScheme
    val month = YearMonth.now()
    val forecast = FinanceCalculations.spendingForecast(entries, month, LocalDate.now())
    val previous = FinanceCalculations.monthExpenses(entries, month.minusMonths(1))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        listOf(
            Triple("MONTH-END FORECAST", forecast, "Projected expenses"),
            Triple("PREVIOUS MONTH", previous, "Recorded expenses")
        ).forEach { (heading, value, detail) ->
            Card(Modifier.weight(1f), shape = RoundedCornerShape(17.dp),
                colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer)) {
                Column(Modifier.padding(BankingAppearance.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(heading, color = scheme.primary, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    Text(Money.format(value), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text(detail, style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant)
                }
            }
        }
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
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(BankingAppearance.screenGap)) {
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
        Card(onClick = onAccounts, shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer)) {
            Box(Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(scheme.primaryContainer, scheme.surfaceContainerHigh)))) {
                Box(Modifier.align(Alignment.TopEnd).offset(x = 44.dp, y = (-48).dp)
                    .size(170.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.09f)))
                Column(Modifier.padding(BankingAppearance.cardPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("${primaryBank ?: "ACCOUNTS"} • PROJECTED BALANCE",
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f), color = scheme.onSurfaceVariant)
                        Surface(shape = RoundedCornerShape(12.dp),
                            color = scheme.primary.copy(alpha = 0.16f)) {
                            Text("ESTIMATE", Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = scheme.onPrimaryContainer,
                                style = MaterialTheme.typography.labelSmall)
                        }
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
                        Text("${balances.size} of 3 accounts have balance estimates", style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant)
                        Text("View accounts  ↗", style = MaterialTheme.typography.labelSmall, color = scheme.primary)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeoShortcut("+", "Add", onAdd, Modifier.weight(1f))
            NeoShortcut("⇄", "Activity", onActivity, Modifier.weight(1f))
            NeoShortcut("↥", "Import", onImport, Modifier.weight(1f))
            NeoShortcut("◷", "Plan", onPlanning, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeoMoneyTile("INCOME", income, scheme.primary, Modifier.weight(1f))
            NeoMoneyTile("SPENT", spent, neoPeach, Modifier.weight(1f))
        }
        NeoRecordedComparisons(transactions)
        NeoMonthlyPulse(income = income, spent = spent, onPlanning = onPlanning)
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
            NeoTransactionIcon(tx)
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
    onTransactions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val tint = when (bank) { "MIB" -> scheme.primary; "BML" -> neoBlue; else -> neoPeach }
    Card(onClick = onOpen, modifier = modifier.height(when(BankingAppearance.density) {
        "Compact" -> 196.dp
        "Comfortable" -> 252.dp
        else -> 221.dp
    }),
        shape = RoundedCornerShape(27.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh)) {
        Box(Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(tint.copy(alpha = 0.21f), scheme.surfaceContainerHigh)))) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 38.dp, y = (-40).dp).size(140.dp)
                .clip(CircleShape).background(tint.copy(alpha = 0.14f)))
            Column(Modifier.fillMaxSize().padding(BankingAppearance.cardPadding), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(11.dp).clip(CircleShape).background(tint))
                    Text(if (bank == "Cash") "Cash wallet" else bank,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Surface(shape = RoundedCornerShape(20.dp),
                        color = tint.copy(alpha = 0.15f)) {
                        Text(if (value == null) "SET UP" else "ESTIMATED",
                            Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(value?.let(Money::format) ?: "Balance not set",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text(if (value == null) "Add a balance snapshot to start" else "Based on snapshots and recorded activity",
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

/** Complete account overview is visible even when the swipeable carousel is on one bank. */
@Composable
private fun NeoAccountSummaryRow(
    bank: String,
    amount: Long?,
    count: Int,
    snapshotDate: String?,
    onOpen: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val tint = when(bank) { "MIB" -> scheme.primary; "BML" -> neoBlue; else -> neoPeach }
    Card(onClick = onOpen, shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                .background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Text(if(bank == "Cash") "◇" else "▣",
                    style = MaterialTheme.typography.titleMedium, color = tint)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if(bank == "Cash") "Cash wallet" else bank,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(if(amount == null) "Snapshot not set" else
                    "${count} transactions · Snapshot ${snapshotDate ?: "on file"}",
                    maxLines = 1, style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant)
            }
            Text(amount?.let(Money::format) ?: "Set balance",
                style = MaterialTheme.typography.labelMedium,
                color = if(amount != null) scheme.onSurface else scheme.primary,
                maxLines = 1)
            Text("›", style = MaterialTheme.typography.titleMedium, color = scheme.primary)
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
    Column(verticalArrangement = Arrangement.spacedBy(BankingAppearance.screenGap)) {
        NeoHeader("ACCOUNTS / WALLETS", "Your accounts", "Estimated balances, account activity and reconciliation")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            NeoCountTile("BALANCE ESTIMATES", "${balances.size} / 3", scheme.primary, Modifier.weight(1f))
            NeoCountTile("TRANSACTIONS", "${transactions.size}", scheme.tertiary, Modifier.weight(1f))
        }

        val carousel = rememberLazyListState()
        val carouselScope = rememberCoroutineScope()
        val selectedAccountIndex = carousel.firstVisibleItemIndex.coerceIn(0, banks.lastIndex)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("SWIPE BETWEEN ACCOUNTS", style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Text("${selectedAccountIndex + 1} / ${banks.size}",
                style = MaterialTheme.typography.labelSmall, color = scheme.primary)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = maxWidth
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                state = carousel,
                flingBehavior = rememberSnapFlingBehavior(lazyListState = carousel),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(banks.size) { index ->
                    val bank = banks[index]
                    NeoAccountCard(bank, balances[bank], counts[bank] ?: 0,
                        onOpen = { onOpenAccount(bank) },
                        onTransactions = { onAccountTransactions(bank) },
                        modifier = Modifier.width(cardWidth))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            banks.forEachIndexed { index, _ ->
                val active = index == selectedAccountIndex
                Box(Modifier.size(36.dp)
                    .clickable { carouselScope.launch { carousel.animateScrollToItem(index) } },
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.size(if (active) 11.dp else 8.dp).clip(CircleShape)
                        .background(if (active) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.35f)))
                }
            }
        }
        NeoSectionTitle("Account overview")
        val anchors = context.getSharedPreferences("balance_anchor", Context.MODE_PRIVATE)
        banks.forEach { bank ->
            val date = anchors.getString("${bank.lowercase(Locale.ROOT)}_date", null)
            NeoAccountSummaryRow(
                bank = bank,
                amount = balances[bank],
                count = counts[bank] ?: 0,
                snapshotDate = date,
                onOpen = { onOpenAccount(bank) }
            )
        }
        NeoSectionTitle("Account tools")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedButton(onClick = onAdvanced, modifier = Modifier.weight(1f)) {
                Text("Reconcile", maxLines = 1)
            }
            OutlinedButton(onClick = { onAccountTransactions(banks[selectedAccountIndex]) },
                modifier = Modifier.weight(1f)) {
                Text("Transactions", maxLines = 1)
            }
        }
        Card(onClick = onPlanning, shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer)) {
            Row(Modifier.fillMaxWidth().padding(15.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Cash wallet & financial planning", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium)
                Text("›", color = scheme.primary)
            }
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
                    onStyleChange = {}, onCategory = onCategory, onMerchant = onMerchant, showTitle = false)
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
