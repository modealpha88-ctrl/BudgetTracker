package com.example.mibbudget

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate

/** Full-screen account workspace. Reads existing anchors without changing transaction storage. */
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
    var enteredAmount by remember(account) { mutableStateOf("") }
    var enteredDate by remember(account) { mutableStateOf(LocalDate.now().toString()) }
    var selectedDate by remember(account) { mutableStateOf(LocalDate.now().toString()) }
    var message by remember { mutableStateOf("") }
    val key = account.lowercase(java.util.Locale.ROOT)
    val anchorMinor = prefs.getLong("${key}_minor", Long.MIN_VALUE)
    val anchorDate = prefs.getString("${key}_date", "").orEmpty()
    val anchorDay = runCatching { LocalDate.parse(anchorDate) }.getOrNull()
    val today = LocalDate.now()
    val accountTransactions = transactions.filter {
        FinanceCalculations.assignedAccount(it, labels).equals(account, ignoreCase = true)
    }.sortedByDescending { it.timestamp }
    val currentBalance = FinanceCalculations.projectedBalance(account, anchorMinor, anchorDate, transactions, labels, today)
    val afterAnchor = if (anchorDay != null) accountTransactions.filter {
        val date = runCatching { LocalDate.parse(it.timestamp.take(10)) }.getOrNull()
        date != null && date.isAfter(anchorDay) && !date.isAfter(today)
    } else emptyList()
    val unassigned = if (anchorDay != null) transactions.count {
        val date = runCatching { LocalDate.parse(it.timestamp.take(10)) }.getOrNull()
        FinanceCalculations.assignedAccount(it, labels) == "Unassigned" &&
            date != null && date.isAfter(anchorDay) && !date.isAfter(today)
    } else 0
    val historyDay = runCatching { LocalDate.parse(selectedDate) }.getOrNull()
    val historical = if (historyDay != null && anchorDay != null) {
        if (!historyDay.isBefore(anchorDay))
            FinanceCalculations.projectedBalance(account, anchorMinor, anchorDate, transactions, labels, historyDay)
        else {
            val reverse = accountTransactions.filter {
                val date = runCatching { LocalDate.parse(it.timestamp.take(10)) }.getOrNull()
                date != null && date.isAfter(historyDay) && !date.isAfter(anchorDay)
            }.sumOf { it.amountLaari }
            runCatching { Math.subtractExact(anchorMinor, reverse) }.getOrNull()
        }
    } else null
    Dialog(onDismissRequest=onDismiss, properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(modifier=Modifier.fillMaxSize(), color=MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("Accounts", style=MaterialTheme.typography.headlineMedium)
                    TextButton(onClick=onDismiss) { Text("Close ✕") }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("MIB","BML","Cash").forEach { name ->
                        FilterChip(selected=account==name, onClick={account=name;message=""},label={Text(name)})
                    }
                }
                Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer),
                    modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("$account · Estimated current balance")
                        Text(currentBalance?.let(Money::format) ?: "Balance not set",
                            style=MaterialTheme.typography.headlineLarge)
                        Text(if(anchorDay==null) "Save a verified balance to start tracking."
                             else "Verified ${Money.format(anchorMinor)} on $anchorDate + ${afterAnchor.size} recorded movements")
                        if(unassigned>0) Text("$unassigned unassigned transactions excluded; estimate may be incomplete.",
                            color=MaterialTheme.colorScheme.error)
                    }
                }
                Text("Balance reconciliation",style=MaterialTheme.typography.titleLarge)
                if(anchorDay!=null) {
                    Text("Verified snapshot: ${Money.format(anchorMinor)} · $anchorDate")
                    Text("Recorded movements: ${Money.format(afterAnchor.sumOf { it.amountLaari })}")
                    Text("Estimated today: ${currentBalance?.let(Money::format) ?: "Unavailable"}")
                    Text("This is not a live bank feed. Compare it with your bank statement before treating it as confirmed.",
                        style=MaterialTheme.typography.bodySmall)
                } else Text("No verified snapshot saved.")
                HorizontalDivider()
                Text("Set verified balance",style=MaterialTheme.typography.titleLarge)
                OutlinedTextField(value=enteredDate,onValueChange={enteredDate=it},
                    label={Text("Statement date (YYYY-MM-DD)")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(value=enteredAmount,onValueChange={enteredAmount=it},
                    label={Text("Verified balance (MVR)")},modifier=Modifier.fillMaxWidth())
                Button(onClick={
                    val date=runCatching { LocalDate.parse(enteredDate) }.getOrNull()
                    val amount=Money.parseLaari(enteredAmount)
                    if(date==null || date.isAfter(today) || amount==null || amount<0L)
                        message="Enter a valid past or present date and non-negative balance."
                    else {
                        prefs.edit().putString("${key}_date",date.toString())
                            .putLong("${key}_minor",amount)
                            .apply()
                        refresh++
                        message="Verified snapshot saved for $account."
                    }
                },modifier=Modifier.fillMaxWidth()) { Text("Save verified snapshot") }
                if(message.isNotBlank()) Text(message)
                HorizontalDivider()
                Text("Balance history",style=MaterialTheme.typography.titleLarge)
                OutlinedTextField(value=selectedDate,onValueChange={selectedDate=it},
                    label={Text("View balance on (YYYY-MM-DD)")},modifier=Modifier.fillMaxWidth())
                Text(historical?.let(Money::format) ?: "No estimate for this date",
                    style=MaterialTheme.typography.headlineSmall)
                Text("Historical estimates depend on complete, correctly assigned transactions.",
                    style=MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("Recorded account transactions",style=MaterialTheme.typography.titleLarge)
                accountTransactions.take(30).forEach { tx ->
                    ListItem(headlineContent={Text(tx.description.take(55))},
                        supportingContent={Text("${tx.timestamp.take(16)} · ${tx.category}")},
                        trailingContent={Text(Money.format(tx.amountLaari))})
                    HorizontalDivider()
                }
                if(accountTransactions.isEmpty()) Text("No assigned transactions yet.")
                OutlinedButton(onClick={onTransactions(account)},modifier=Modifier.fillMaxWidth()) {
                    Text("View all $account transactions")
                }
            }
        }
    }
}
