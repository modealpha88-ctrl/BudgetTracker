package com.example.mibbudget

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
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
    savedRecipients: Map<String, String>,
    pendingBmlCount: Int,
    onAccounts: () -> Unit,
    onPlanning: () -> Unit,
    onInsights: () -> Unit,
    onReview: () -> Unit,
    onActivity: () -> Unit,
    onTransaction: (Long) -> Unit,
    onAdvanced: () -> Unit
) {
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
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
                    Card(
                        modifier=Modifier.fillMaxWidth(),
                        shape=RoundedCornerShape(24.dp),
                        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                Text("Financial health",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                                Text("This month",style=MaterialTheme.typography.labelMedium,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (monthlyIn > 0L) {
                                val percentage = monthlyOut.toDouble() / monthlyIn.toDouble()
                                Text("${(percentage*100).toInt()}% of recorded income spent",
                                    style=MaterialTheme.typography.titleLarge,
                                    color=if(percentage>1.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                LinearProgressIndicator(
                                    progress={percentage.toFloat().coerceIn(0f,1f)},
                                    modifier=Modifier.fillMaxWidth().height(9.dp),
                                    color=if(percentage>1.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    trackColor=MaterialTheme.colorScheme.surfaceContainerLowest
                                )
                                Text(
                                    if (percentage>1.0) "Spending exceeds recorded income by ${Money.format(monthlyOut-monthlyIn)}"
                                    else "Unspent portion of recorded income: ${Money.format(monthlyIn-monthlyOut)}",
                                    style=MaterialTheme.typography.bodySmall,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text("No income recorded this month. Spending: ${Money.format(monthlyOut)}",
                                    style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf(
                            Triple("Activity",onActivity,"↔"),
                            Triple("Accounts",onAccounts,"▣"),
                            Triple("Planning",onPlanning,"◷")
                        ).forEach { (label,action,symbol) ->
                            OutlinedButton(onClick=action,modifier=Modifier.weight(1f),
                                contentPadding=PaddingValues(horizontal=4.dp,vertical=10.dp)) {
                                Text("$symbol $label",style=MaterialTheme.typography.labelMedium,maxLines=1)
                            }
                        }
                    }
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
                                    Text(TransactionPresentation.displayLabel(tx.description,tx.category,savedRecipients[tx.fingerprint]),
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
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text("ACCOUNTS • OVERVIEW", style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                    Text("Your accounts",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.ExtraBold)
                    Text("Your balances and account activity in one place",
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    val balances=reconciledAccountBalances(context,transactions,accountLabels)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf("3 accounts","Balance history","Reconciliation").forEach { label ->
                            Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Text(label,Modifier.padding(horizontal=9.dp,vertical=8.dp),style=MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    listOf("MIB","BML","Cash").forEach { bank ->
                        val value=balances[bank]
                        val assignedCount=transactions.count { tx ->
                            FinanceCalculations.assignedAccount(tx,accountLabels)==bank
                        }
                        Card(onClick={onOpenAccount(bank)},modifier=Modifier.fillMaxWidth(),
                            shape=RoundedCornerShape(28.dp),
                            colors=CardDefaults.cardColors(containerColor=when(bank) {
                                "MIB" -> MaterialTheme.colorScheme.primaryContainer
                                "BML" -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.surfaceContainerHigh
                            })) {
                            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                    Text(when(bank) { "MIB" -> "●  MIB"; "BML" -> "●  BML"; else -> "●  Cash wallet" },
                                        style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,
                                        color=when(bank) { "MIB" -> MaterialTheme.colorScheme.primary; "BML" -> MaterialTheme.colorScheme.tertiary; else -> Color(0xFFF5C17C) })
                                    Text("View details ›",style=MaterialTheme.typography.labelMedium,
                                        color=MaterialTheme.colorScheme.primary)
                                }
                                Text(if(value==null || value==Long.MIN_VALUE) "Balance not set" else Money.format(value),
                                    style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                                Text("${assignedCount} transactions · History & reconciliation",
                                    style=MaterialTheme.typography.bodySmall,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()) {
                                    Button(onClick={onOpenAccount(bank)},modifier=Modifier.weight(1f)) { Text("Account details") }
                                    OutlinedButton(onClick={onAccountTransactions(bank)},modifier=Modifier.weight(1f)) { Text("Transactions") }
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick=onAdvanced,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp)) {
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
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text("INSIGHTS • ANALYTICS",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                    Text("Your insights",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.ExtraBold)
                    Text("Understand where your money goes",
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
                        shape=RoundedCornerShape(28.dp),
                        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                            Text("Monthly cash flow",style=MaterialTheme.typography.titleMedium)
                            Text(Money.format(incomeThisMonth-spent),
                                style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                            Text("Income ${Money.format(incomeThisMonth)} · Spent ${Money.format(spent)}",
                                style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    Card(Modifier.fillMaxWidth(), shape=RoundedCornerShape(26.dp),
                        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
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
                    PremiumWeeklySpendBars(monthlyTransactions, selectedMonth)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("Top spending categories",style=MaterialTheme.typography.titleMedium)
                        Text("${expenses.size} expenses",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                    }
                    expenses.groupBy { it.category }.mapValues { (_,items) ->
                        items.sumOf { -it.amountLaari }
                    }.toList().sortedByDescending { it.second }.take(5).forEach { (categoryName,total) ->
                        Card(onClick={onCategory(categoryName)},modifier=Modifier.fillMaxWidth(),
                            shape=RoundedCornerShape(22.dp),
                            colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
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
    var appearanceOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if (!appearanceOpen) {
                    Text("YOUR FINANCIAL TOOLKIT",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                    Text("More to explore",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.ExtraBold)
                    Text("Your tools, preferences and money management",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf(
                        Triple(3,"Budgets","Monthly limits, rollover and categories"),
                        Triple(4,"Plan & reports","Safe-to-spend, savings, calendar and PDF reports"),
                        Triple(2,"Import & access","CSV, SMS, notifications and reconciliation review"),
                        Triple(8,"Advanced dashboard","Detailed balances, receipts and remaining tools")
                    ).forEach { (route,label,description) ->
                        Card(onClick={onNavigate(route)},modifier=Modifier.fillMaxWidth(),
                            shape=RoundedCornerShape(24.dp),
                            colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                            Row(Modifier.fillMaxWidth().padding(20.dp),
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
                    Card(onClick={appearanceOpen=true},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text("Appearance & privacy",style=MaterialTheme.typography.titleMedium)
                            Text("Customize  ›",color=MaterialTheme.colorScheme.primary)
                        }
                    }
        } else {
                    TextButton(onClick={appearanceOpen=false}) { Text("‹  Back to More") }
                    Text("Appearance & privacy",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                    val appearanceContext = LocalContext.current
                    Card(
                        modifier=Modifier.fillMaxWidth(),
                        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer),
                        shape=RoundedCornerShape(24.dp)
                    ) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Text("Neo Banking Pro • Colorway",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                            Text("Select a theme. Your transactions and account data will not change.",
                                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            bankingPalettes.chunked(2).forEach { group ->
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                    group.forEach { palette ->
                                        val active = BankingAppearance.selected == palette.label
                                        Card(
                                            modifier=Modifier.weight(1f).clickable { BankingAppearance.select(appearanceContext,palette.label) },
                                            shape=RoundedCornerShape(18.dp),
                                            colors=CardDefaults.cardColors(containerColor=palette.surface),
                                            border=if(active) androidx.compose.foundation.BorderStroke(2.dp,palette.accent) else null
                                        ) {
                                            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                                Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                                                    Box(Modifier.size(16.dp).background(palette.accent,RoundedCornerShape(6.dp)))
                                                    Box(Modifier.size(16.dp).background(palette.secondary,RoundedCornerShape(6.dp)))
                                                    Box(Modifier.size(16.dp).background(palette.raised,RoundedCornerShape(6.dp)))
                                                }
                                                Text(palette.label,color=Color.White,style=MaterialTheme.typography.labelMedium)
                                                if(active) Text("✓ Selected",color=palette.accent,style=MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(26.dp),
                        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("AMOLED dark mode",fontWeight=FontWeight.SemiBold)
                                    Text("Deep contrast, vivid selected colorway",style=MaterialTheme.typography.bodySmall,
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
}
