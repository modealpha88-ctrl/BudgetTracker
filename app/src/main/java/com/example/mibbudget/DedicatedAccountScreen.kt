package com.example.mibbudget

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.util.Locale

/** Unified account overview, dated balance verification and transaction history. */
@Composable
fun DedicatedAccountScreen(
    context: Context,
    initialAccount: String,
    transactions: List<Tx>,
    labels: Map<String, String>,
    onDismiss: () -> Unit,
    onTransactions: (String) -> Unit
) {
    val prefs = remember(context) { context.getSharedPreferences("balance_anchor", Context.MODE_PRIVATE) }
    var account by remember(initialAccount) { mutableStateOf(initialAccount) }
    var refresh by remember { mutableIntStateOf(0) }
    var selectedDate by remember(account) { mutableStateOf(LocalDate.now().toString()) }
    var showMore by remember(account) { mutableStateOf(false) }
    val key = account.lowercase(Locale.ROOT)
    val anchorMinor = remember(key, refresh) { prefs.getLong("${key}_minor", Long.MIN_VALUE) }
    val anchorDate = remember(key, refresh) { prefs.getString("${key}_date", "").orEmpty() }
    val anchorTime = remember(key, refresh) { prefs.getString("${key}_time", "").orEmpty() }
    val anchorDay = runCatching { LocalDate.parse(anchorDate) }.getOrNull()
    val today = LocalDate.now()
    val accountTransactions = transactions.filter {
        FinanceCalculations.assignedAccount(it, labels).equals(account, ignoreCase = true)
    }.sortedByDescending { it.timestamp }
    // Same-day activity is included only when its precise time is after the snapshot clock.
    val afterAnchor = if (anchorDay == null) emptyList() else accountTransactions.filter { tx ->
        val date = runCatching { LocalDate.parse(tx.timestamp.take(10)) }.getOrNull()
        date != null && FinanceCalculations.afterSnapshot(tx, anchorDay, anchorTime) && !date.isAfter(today)
    }
    val unassigned = if (anchorDay == null) 0 else transactions.count { tx ->
        val date = runCatching { LocalDate.parse(tx.timestamp.take(10)) }.getOrNull()
        date != null && FinanceCalculations.afterSnapshot(tx, anchorDay, anchorTime) && !date.isAfter(today) &&
            FinanceCalculations.assignedAccount(tx, labels) == "Unassigned"
    }
    val netActivity = afterAnchor.sumOf { it.amountLaari }
    val estimated = FinanceCalculations.projectedBalance(
        account, anchorMinor, anchorDate, transactions, labels, today, anchorTime
    )
    val historyDay = runCatching { LocalDate.parse(selectedDate) }.getOrNull()
    val historical = if (historyDay != null && anchorDay != null) {
        if (!historyDay.isBefore(anchorDay)) {
            FinanceCalculations.projectedBalance(account, anchorMinor, anchorDate, transactions, labels, historyDay, anchorTime)
        } else {
            val reverse = accountTransactions.filter { tx ->
                val date = runCatching { LocalDate.parse(tx.timestamp.take(10)) }.getOrNull()
                date != null && date.isAfter(historyDay) && !date.isAfter(anchorDay)
            }.sumOf { it.amountLaari }
            runCatching { Math.subtractExact(anchorMinor, reverse) }.getOrNull()
        }
    } else null

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Your accounts", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold)
                    TextButton(onClick = onDismiss) { Text("Close ✕") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("MIB", "BML", "Cash").forEach { name ->
                        FilterChip(selected = account == name, onClick = { account = name },
                            label = { Text(name) })
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("$account · Estimated balance", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(estimated?.let(Money::format) ?: "Balance not set",
                            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            if (anchorDay == null) "Verify an opening balance to start tracking."
                            else "Based on ${Money.format(anchorMinor)} verified on $anchorDate${if(anchorTime.isNotBlank()) " at $anchorTime" else " (date only)"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Net movement", style = MaterialTheme.typography.labelSmall)
                                Text(if(anchorDay != null) Money.format(netActivity) else "—",
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Transactions after snapshot", style = MaterialTheme.typography.labelSmall)
                                Text(if(anchorDay != null) afterAnchor.size.toString() else "—",
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (unassigned > 0) Text("$unassigned unassigned transactions are excluded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
                Text("Estimates replay transactions after the verified snapshot time when available; otherwise only later dates. This is not a live bank feed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                VerifiedBalanceEditor(context = context, account = account, onSaved = { refresh++ })

                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Balance history", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = selectedDate, onValueChange = { selectedDate = it.take(10) },
                            label = { Text("View balance on (YYYY-MM-DD)") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        Text(historical?.let(Money::format) ?: "No estimate for this date",
                            style = MaterialTheme.typography.headlineSmall)
                        Text("Historical estimates require complete, correctly assigned transactions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Recent transactions", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text("${accountTransactions.size}", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (accountTransactions.isEmpty()) Text("No assigned transactions yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        accountTransactions.take(if (showMore) 30 else 5).forEach { tx ->
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(tx.description.take(44), style = MaterialTheme.typography.bodyMedium)
                                    Text("${tx.timestamp.take(16)} · ${tx.category}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                Text(Money.format(tx.amountLaari), style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (accountTransactions.size > 5) TextButton(
                            onClick = { showMore = !showMore }, modifier = Modifier.fillMaxWidth()
                        ) { Text(if (showMore) "Show fewer" else "Show more transactions") }
                        OutlinedButton(onClick = { onTransactions(account) }, modifier = Modifier.fillMaxWidth()) {
                            Text("View all $account transactions")
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}
