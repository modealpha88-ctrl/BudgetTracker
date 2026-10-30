package com.example.mibbudget

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.YearMonth

/** Small Compose destinations keep the JVM method size within its 64-KB bytecode limit. */

@Composable
internal fun MidnightHomeScreen(
    context: Context,
    transactions: List<Tx>,
    monthlyTransactions: List<Tx>,
    accountLabels: Map<String, String>,
    pendingBmlCount: Int,
    onAccounts: () -> Unit,
    onPlanning: () -> Unit,
    onInsights: () -> Unit,
    onReview: () -> Unit,
    onActivity: () -> Unit,
    onTransaction: (Long) -> Unit,
    onAdvanced: () -> Unit
) {
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    // A focused financial home. The legacy dashboard is preserved under More.
                    Text("BUDGET TRACKER",style=MaterialTheme.typography.labelLarge,
                        color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                    Text("Your money, in focus",style=MaterialTheme.typography.headlineMedium,
                        fontWeight=FontWeight.ExtraBold)
                    Text("Accounts, spending and activity at a glance",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    val balances=reconciledAccountBalances(context,transactions,accountLabels)
                    PremiumAccountsOverview(balances=balances,onAccounts=onAccounts)
                    val monthlyOut=monthlyTransactions.filter {
                        it.amountLaari<0 && it.category!="Internal Transfer"
                    }.sumOf { -it.amountLaari }
                    val monthlyIn=monthlyTransactions.filter {
                        it.amountLaari>0 && it.category!="Internal Transfer"
                    }.sumOf { it.amountLaari }
                    Text("THIS MONTH",style=MaterialTheme.typography.labelMedium,
                        color=MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=FontWeight.Bold)
                    PremiumMonthlyInsightStats(income=monthlyIn,expenses=monthlyOut)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick=onPlanning,modifier=Modifier.weight(1f)) { Text("Safe to spend") }
                        OutlinedButton(onClick=onInsights,modifier=Modifier.weight(1f)) { Text("Insights") }
                    }
                    if(pendingBmlCount>0) Card(
                        onClick=onReview,
                        modifier=Modifier.fillMaxWidth(),
                        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("${pendingBmlCount} BML credit(s) need review",
                                style=MaterialTheme.typography.titleMedium)
                            Text("Review incoming payments before including them in balances.",
                                style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                        verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Recent activity",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                        TextButton(onClick=onActivity) { Text("See all") }
                    }
                    val recent=transactions.sortedByDescending { it.timestamp }.take(6)
                    if(recent.isEmpty()) Text("Your transactions will appear here after importing a bank statement.")
                    recent.forEach { tx ->
                        Card(onClick={onTransaction(tx.id)},modifier=Modifier.fillMaxWidth(),
                            colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                            Row(Modifier.padding(14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),
                                verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                Text(MerchantIcons.icon(tx.description,tx.category),
                                    style=MaterialTheme.typography.headlineSmall)
                                Column(Modifier.weight(1f)) {
                                    Text(TransactionPresentation.label(tx.description),
                                        maxLines=1,style=MaterialTheme.typography.bodyLarge)
                                    Text("${tx.category} · ${tx.timestamp.take(10)}",
                                        style=MaterialTheme.typography.bodySmall,
                                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(Money.format(tx.amountLaari),
                                    style=MaterialTheme.typography.labelLarge,
                                    color=if(tx.amountLaari>0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                    OutlinedButton(onClick=onAdvanced,modifier=Modifier.fillMaxWidth()) {
                        Text("Advanced dashboard & reconciliation tools")
                    }

    }
}

@Composable
internal fun MidnightAccountsScreen(
    context: Context,
    transactions: List<Tx>,
    accountLabels: Map<String, String>,
    onOpenAccount: (String) -> Unit,
    onAccountTransactions: (String) -> Unit,
    onAdvanced: () -> Unit,
    onPlanning: () -> Unit
) {
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("Accounts",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                    Text("Reconciled balances, snapshots and account history",
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    val balances=reconciledAccountBalances(context,transactions,accountLabels)
                    listOf("MIB","BML","Cash").forEach { bank ->
                        val value=balances[bank]
                        val assignedCount=transactions.count { tx ->
                            FinanceCalculations.assignedAccount(tx,accountLabels)==bank
                        }
                        Card(onClick={onOpenAccount(bank)},modifier=Modifier.fillMaxWidth(),
                            colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerHigh)) {
                            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                    Text(bank,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                                    Text("View details ›",style=MaterialTheme.typography.labelMedium,
                                        color=MaterialTheme.colorScheme.primary)
                                }
                                Text(if(value==null || value==Long.MIN_VALUE) "Balance not set" else Money.format(value),
                                    style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                                Text("${assignedCount} transactions · Tap for history and balance reconciliation",
                                    style=MaterialTheme.typography.bodySmall,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedButton(onClick={onAccountTransactions(bank)},modifier=Modifier.fillMaxWidth()) { Text("View $bank transactions") }
                            }
                        }
                    }
                    OutlinedButton(onClick=onAdvanced,modifier=Modifier.fillMaxWidth()) {
                        Text("Advanced balance & internal transfer tools")
                    }
                    OutlinedButton(onClick=onPlanning,modifier=Modifier.fillMaxWidth()) {
                        Text("Cash wallet & financial planning")
                    }

    }
}

@Composable
internal fun MidnightInsightsScreen(
    monthlyTransactions: List<Tx>,
    selectedMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onCategory: (String) -> Unit,
    onMerchant: (String, String) -> Unit,
    onPlanning: () -> Unit
) {
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("Insights",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                    Text("Spending patterns and merchant analysis",
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                        verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Text(selectedMonth.toString(),style=MaterialTheme.typography.titleMedium)
                        Row {
                            TextButton(onClick={onMonthChange(selectedMonth.minusMonths(1))}) { Text("‹") }
                            TextButton(onClick={onMonthChange(selectedMonth.plusMonths(1))}) { Text("›") }
                        }
                    }
                    val expenses=monthlyTransactions.filter {
                        it.amountLaari<0 && it.category!="Internal Transfer"
                    }
                    val spent=expenses.sumOf { -it.amountLaari }
                    val incomeThisMonth=monthlyTransactions.filter {
                        it.amountLaari>0 && it.category!="Internal Transfer"
                    }.sumOf { it.amountLaari }
                    Card(Modifier.fillMaxWidth(),
                        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                            Text("Monthly cash flow",style=MaterialTheme.typography.titleMedium)
                            Text(Money.format(incomeThisMonth-spent),
                                style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                            Text("Income ${Money.format(incomeThisMonth)} · Spent ${Money.format(spent)}",
                                style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text("Spending by category",style=MaterialTheme.typography.titleMedium)
                            if(expenses.isEmpty()) Text("No expenses in this month.")
                            else SpendingBreakdown(
                                expenses=expenses,
                                initialStyle="Treemap",
                                onStyleChange={},
                                onCategory=onCategory,
                                onMerchant=onMerchant
                            )
                        }
                    }
                    Text("Top spending categories",style=MaterialTheme.typography.titleMedium)
                    expenses.groupBy { it.category }.mapValues { (_,items) ->
                        items.sumOf { -it.amountLaari }
                    }.toList().sortedByDescending { it.second }.take(5).forEach { (categoryName,total) ->
                        Card(onClick={onCategory(categoryName)},modifier=Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                    Text(categoryName)
                                    Text(Money.format(total))
                                }
                                LinearProgressIndicator(
                                    progress={if(spent>0) (total.toFloat()/spent).coerceIn(0f,1f) else 0f},
                                    modifier=Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    OutlinedButton(onClick=onPlanning,modifier=Modifier.fillMaxWidth()) {
                        Text("Forecasts, calendar & monthly reports")
                    }

    }
}

@Composable
internal fun MidnightMoreScreen(
    darkTheme: Boolean,
    setDarkTheme: (Boolean) -> Unit,
    lockEnabled: Boolean,
    onLockChange: (Boolean) -> Unit,
    screenshotPrivacy: Boolean,
    onScreenshotPrivacyChange: (Boolean) -> Unit,
    onNavigate: (Int) -> Unit
) {
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("More",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                    Text("Tools, planning and preferences",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf(
                        Triple(3,"Budgets","Monthly limits, rollover and categories"),
                        Triple(4,"Plan & reports","Safe-to-spend, savings, calendar and PDF reports"),
                        Triple(2,"Import & access","CSV, SMS, notifications and reconciliation review"),
                        Triple(8,"Advanced dashboard","Detailed balances, receipts and remaining tools")
                    ).forEach { (route,label,description) ->
                        Card(onClick={onNavigate(route)},modifier=Modifier.fillMaxWidth(),
                            colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                            Row(Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement=Arrangement.SpaceBetween,
                                verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                    Text(label,style=MaterialTheme.typography.titleMedium)
                                    Text(description,style=MaterialTheme.typography.bodySmall,
                                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("›",style=MaterialTheme.typography.headlineSmall,
                                    color=MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Text("Appearance & privacy",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Midnight Aurora")
                                    Text("Multi-accent AMOLED theme",style=MaterialTheme.typography.bodySmall,
                                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked=darkTheme,onCheckedChange=setDarkTheme)
                            }
                            HorizontalDivider()
                            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                Text("Biometric app lock",modifier=Modifier.weight(1f))
                                Switch(checked=lockEnabled,onCheckedChange=onLockChange)
                            }
                            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                Text("Hide sensitive screenshots",modifier=Modifier.weight(1f))
                                Switch(checked=screenshotPrivacy,onCheckedChange=onScreenshotPrivacyChange)
                            }
                            Text("Your transactions and account data remain stored locally.",
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

    }
}
