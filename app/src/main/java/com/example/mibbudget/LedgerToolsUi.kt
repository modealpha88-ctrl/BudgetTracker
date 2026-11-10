package com.example.mibbudget

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.util.Locale

@Composable
internal fun ImportHealthCard(context: Context, onRecover: () -> Unit) {
    val tick by ImportHealth.revision.collectAsState()
    val prefs = remember(tick) { context.getSharedPreferences("import_health", Context.MODE_PRIVATE) }
    val inbox = AutomaticSmsImport.hasInboxAccess(context)
    val receive = context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Import health", style = MaterialTheme.typography.titleMedium)
        Text("Automatic import: ${if(AutomaticSmsImport.enabled(context)) "Enabled" else "Off"}")
        Text("Incoming SMS: ${if(receive) "Allowed" else "Not allowed"} · Inbox: ${if(inbox) "Allowed" else "Not allowed"}")
        Text("Last successful scan: ${prefs.getString("success", "Never")}")
        Text("Last scan: ${prefs.getInt("scanned", 0)} scanned · ${prefs.getInt("imported", 0)} imported · ${prefs.getInt("duplicates", 0)} duplicates · ${prefs.getInt("ignored", 0)} unsupported")
        val failure = prefs.getString("error", "").orEmpty()
        if(failure.isNotBlank()) Text(failure, color = MaterialTheme.colorScheme.error)
        Text("Background recovery is scheduled about every 15 minutes when enabled. Android may delay it. Recovery below rescans the last month with duplicate checks.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = onRecover) { Text("Recover missed messages") }
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) }) { Text("SMS permissions & battery settings") }
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("BML notification access") }
    } }
}

@Composable
internal fun SplitEditor(store: TransactionStore, tx: Tx, onChanged: () -> Unit, onNotice: (String) -> Unit) {
    var open by remember(tx.id) { mutableStateOf(false) }
    val saved = store.allocations()[tx.fingerprint].orEmpty()
    var categories by remember(tx.id, open) { mutableStateOf(if(saved.isEmpty()) listOf(tx.category, "Other") else saved.map { it.category }) }
    var amounts by remember(tx.id, open) { mutableStateOf(if(saved.isEmpty()) listOf("", "") else saved.map { java.math.BigDecimal.valueOf(it.amount, 2).toPlainString() }) }
    val parts = categories.mapIndexed { i, category -> SpendingAllocation(category.trim(), Money.parseLaari(amounts[i]) ?: 0L) }
    val valid = runCatching { SplitSpending.validate(tx, parts) }
    if(saved.isNotEmpty()) {
        Text("Split spending · original payment ${Money.format(tx.amountLaari)}")
        saved.forEach { Text("${it.category}: ${Money.format(it.amount)}", style = MaterialTheme.typography.bodySmall) }
        if(runCatching { SplitSpending.validate(tx, saved) }.isFailure) Text("Split needs review: payment changed. Reports use the original category until corrected.", color = MaterialTheme.colorScheme.error)
    }
    if(FinanceCalculations.isExpense(tx)) OutlinedButton(onClick = { open = true }) { Text(if(saved.isEmpty()) "Split across categories" else "Edit split") }
    if(open) AlertDialog(onDismissRequest = { open = false }, title = { Text("Split spending") }, text = {
        Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Original payment: ${Money.format(-tx.amountLaari)}. Amount, account and bank record stay intact.")
            categories.indices.forEach { i ->
                OutlinedTextField(categories[i], { value -> categories = categories.toMutableList().also { it[i] = value.take(40) } }, label = { Text("Category ${i + 1}") }, singleLine = true)
                OutlinedTextField(amounts[i], { value -> amounts = amounts.toMutableList().also { it[i] = value } }, label = { Text("Amount (MVR)") }, singleLine = true)
                if(categories.size > 2) TextButton(onClick = { categories = categories.filterIndexed { index, _ -> index != i }; amounts = amounts.filterIndexed { index, _ -> index != i } }) { Text("Remove allocation") }
            }
            if(categories.size < 20) TextButton(onClick = { categories = categories + ""; amounts = amounts + "" }) { Text("Add category") }
            Text(valid.exceptionOrNull()?.message ?: "Total matches the original payment", color = if(valid.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            if(saved.isNotEmpty()) TextButton(onClick = {
                store.saveAllocations(tx.id, emptyList()); open = false; onChanged(); onNotice("Split removed; Undo is available in Recovery history")
            }) { Text("Remove split") }
        }
    }, confirmButton = { Button(enabled = valid.isSuccess, onClick = {
        runCatching { store.saveAllocations(tx.id, parts) }.onSuccess { open = false; onChanged(); onNotice("Split saved; account balance unchanged") }.onFailure { onNotice(it.message ?: "Split failed") }
    }) { Text("Save split") } }, dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } })
}

@Composable
internal fun RecoveryPanel(store: TransactionStore, revision: Int, onChanged: () -> Unit, onNotice: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<LedgerRecovery.Action?>(null) }
    val actions = remember(revision) { LedgerRecovery.recent(store.readableDatabase) }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { expanded = !expanded }) { Text("Recovery history · ${actions.size} actions ${if(expanded) "▴" else "▾"}") }
        if(expanded) {
            Text("Undo category batches, account assignments, CSV links and splits. A later conflicting change blocks undo.", style = MaterialTheme.typography.bodySmall)
            actions.forEach { action ->
                Text("${action.label} · ${action.date.take(16)}", style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !action.undone, onClick = { pending = action }) { Text(if(action.undone) "Undone" else "Undo action") }
            }
        }
    } }
    pending?.let { action -> AlertDialog(onDismissRequest = { pending = null }, title = { Text("Undo ${action.label}?") },
        text = { Text("Restore all records in this action together. This can change categories or account balances. Newer conflicting edits will block the entire undo.") },
        confirmButton = { TextButton(onClick = {
            val restored = runCatching { LedgerRecovery.undo(store.writableDatabase, action.key) }.getOrDefault(false)
            pending = null; onChanged(); onNotice(if(restored) "Action reversed" else "Undo blocked: records changed or conflict with another action")
        }) { Text("Undo") } }, dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }) }
}

@Composable
internal fun SnapshotQuality(context: Context, entries: List<Tx>, labels: Map<String, String>) {
    val prefs = context.getSharedPreferences("balance_anchor", Context.MODE_PRIVATE)
    val today = LocalDate.now()
    listOf("MIB", "BML", "Cash").forEach { bank ->
        val key = bank.lowercase(Locale.ROOT)
        val day = runCatching { LocalDate.parse(prefs.getString("${key}_date", "")) }.getOrNull()
        val clock = prefs.getString("${key}_time", "").orEmpty()
        when {
            day == null -> Text("$bank: balance snapshot missing", style = MaterialTheme.typography.bodySmall)
            else -> {
                Text("$bank snapshot: $day ${clock.ifBlank { "· date only" }}", style = MaterialTheme.typography.bodySmall)
                if(day.isBefore(today.minusDays(7))) Text("$bank snapshot is over 7 days old; verify against your bank.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                val excluded = entries.count { FinanceCalculations.assignedAccount(it, labels) == bank && it.timestamp.take(10) == day.toString() && (clock.isBlank() || FinanceCalculations.transactionDateTime(it) == null) }
                if(excluded > 0) Text("$bank: $excluded same-day records excluded because timing is unknown.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    val unassigned = entries.count { FinanceCalculations.assignedAccount(it, labels) == "Unassigned" }
    if(unassigned > 0) Text("$unassigned unassigned transactions are excluded from account balances.", color = MaterialTheme.colorScheme.error)
}

@Composable
internal fun ReconciliationChecklist(context: Context, store: TransactionStore, entries: List<Tx>, labels: Map<String,String>, missing: Int?, possible: Int?, revision: Int, onImport: () -> Unit, onActivity: () -> Unit, onAccount: (String) -> Unit, onChanged: () -> Unit, onNotice: (String) -> Unit) {
    val duplicates = remember(revision) { store.exactDuplicateGroups() }
    val csvSmsDuplicates = remember(revision) { store.csvSmsDuplicateGroups() }
    var merge by remember { mutableStateOf<List<Tx>?>(null) }
    var mergeCsvSms by remember { mutableStateOf<List<Tx>?>(null) }
    var showDuplicates by remember { mutableStateOf(false) }
    var showCsvSmsDuplicates by remember { mutableStateOf(false) }
    Text("Reconciliation checklist", style = MaterialTheme.typography.headlineSmall)
    Text("Resolve uncertain records before treating an estimated balance as reconciled.")
    SnapshotQuality(context, entries, labels)
    TextButton(onClick = onActivity) { Text("Review unassigned accounts and transactions") }
    Text("Statement: ${missing?.let { "$it missing entries · ${possible ?: 0} possible matches" } ?: "Upload a CSV to check for missing entries"}")
    TextButton(onClick = onImport) { Text("Open statement cross-check") }
    TextButton(onClick = { showDuplicates = !showDuplicates }) { Text("${duplicates.size} exact-time duplicate groups · review") }
    if(showDuplicates) duplicates.take(40).forEach { group ->
        DuplicateComparison(context, store, group, labels)
        OutlinedButton(onClick = { merge = group }) { Text("Review merge") }
    }
    if(duplicates.size > 40 && showDuplicates) Text("First 40 groups shown. Resolve these to see the rest.")
    TextButton(onClick = { showCsvSmsDuplicates = !showCsvSmsDuplicates }) {
        Text("${csvSmsDuplicates.size} confirmed CSV + SMS duplicate groups · amend existing")
    }
    if(showCsvSmsDuplicates) csvSmsDuplicates.take(40).forEach { group ->
        DuplicateComparison(context, store, group, labels)
        OutlinedButton(onClick = { mergeCsvSms = group }) { Text("Review CSV + SMS merge") }
    }
    if(csvSmsDuplicates.size > 40 && showCsvSmsDuplicates)
        Text("First 40 groups shown. Resolve these to see the rest.")
    BankReconciliation.history(context).distinctBy { it.account }.forEach { report ->
        Text("${report.account} last comparison (${report.statementDate}): ${Money.format(report.differenceMinor)} difference")
    }
    listOf("MIB", "BML", "Cash").forEach { bank -> TextButton(onClick = { onAccount(bank) }) { Text("Verify & reconcile $bank") } }
    RecoveryPanel(store, revision, onChanged, onNotice)
    merge?.let { rows -> AlertDialog(onDismissRequest = { merge = null }, title = { Text("Merge duplicate records?") }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            Text("Keep the first record and archive the others. Confirm they represent one bank payment. Balances will change; restore is available in Transaction edit history.")
            DuplicateComparison(context, store, rows, labels)
        }
    }, confirmButton = { TextButton(onClick = {
        runCatching { store.mergeExactDuplicates(rows.map { it.id }) }.onSuccess { onChanged(); onNotice("Archived $it records. Transaction edit history can restore them.") }.onFailure { onNotice("Merge blocked: ${it.message}") }; merge = null
    }) { Text("Merge and archive") } }, dismissButton = { TextButton(onClick = { merge = null }) { Text("Cancel") } }) }
    mergeCsvSms?.let { rows -> AlertDialog(onDismissRequest = { mergeCsvSms = null },
        title = { Text("Amend duplicated CSV and SMS records?") },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            Text("Keep the SMS transaction time and merchant, attach the verified CSV details, and remove the extra CSV ledger row. The full change is available in Recovery history.")
            DuplicateComparison(context, store, rows, labels)
        } },
        confirmButton = { TextButton(onClick = {
            runCatching { store.mergeCsvSmsDuplicate(rows.map { it.id }) }
                .onSuccess { onChanged(); onNotice("Merged CSV and SMS duplicate. Balance and reports recalculated; Recovery history can undo it.") }
                .onFailure { onNotice("Merge blocked: ${it.message}") }
            mergeCsvSms = null
        }) { Text("Amend and merge") } },
        dismissButton = { TextButton(onClick = { mergeCsvSms = null }) { Text("Cancel") } })
    }
}

@Composable
internal fun DuplicateComparison(context: Context, store: TransactionStore, rows: List<Tx>, labels: Map<String, String>) {
    val linked = store.csvDetails()
    val names = context.getSharedPreferences("transaction_recipients", Context.MODE_PRIVATE)
    // Two columns remain readable on a phone; each source has the same fields in the same order.
    rows.chunked(2).forEach { pair -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        pair.forEach { tx -> Card(Modifier.weight(1f)) { Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Record ${tx.id}", style = MaterialTheme.typography.titleSmall)
            Text(FinanceCalculations.assignedAccount(tx, labels))
            Text(Money.format(tx.amountLaari))
            Text(tx.timestamp)
            Text(TransactionPresentation.displayLabel(tx.description, tx.category, TransactionPresentation.preferredRecipient(names.getString(tx.fingerprint, null), linked[tx.fingerprint]?.recipient)))
            Text("Reference: ${linked[tx.fingerprint]?.reference?.ifBlank { null } ?: "Not recorded"}", style = MaterialTheme.typography.bodySmall)
            Text(tx.description, style = MaterialTheme.typography.bodySmall)
        } } }
    } }
}

@Composable
internal fun BulkAccountEditor(store: TransactionStore, rows: List<Tx>, onChanged: () -> Unit, onNotice: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var selected by remember(rows) { mutableStateOf(emptySet<Long>()) }
    var bank by remember { mutableStateOf("MIB") }
    var confirm by remember { mutableStateOf(false) }
    TextButton(onClick={open=!open}) { Text(if(open) "Hide bulk account assignment" else "Assign accounts in bulk") }
    if(open) {
        Text("Select from the current filtered results. Changing accounts moves the recorded payment between account estimates.")
        TextButton(onClick={selected=rows.map { it.id }.toSet()}) { Text("Select all ${rows.size} matching") }
        TextButton(onClick={selected=emptySet()}) { Text("Clear selection") }
        rows.take(40).forEach { tx -> Row {
            Checkbox(checked=tx.id in selected,onCheckedChange={checked -> selected=if(checked) selected+tx.id else selected-tx.id})
            Text("${tx.timestamp.take(16)} · ${Money.format(tx.amountLaari)} · ${TransactionPresentation.label(tx.description)}",style=MaterialTheme.typography.bodySmall)
        } }
        if(rows.size>40) Text("First 40 shown. Narrow filters to inspect more; Select all includes all matching results.")
        Row { listOf("MIB","BML","Cash","Unassigned").forEach { name -> TextButton(onClick={bank=name}) { Text(name) } } }
        Button(enabled=selected.isNotEmpty(),onClick={confirm=true}) { Text("Assign ${selected.size} to $bank") }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Change account assignments?")},
        text={Text("Assign ${selected.size} selected payments to $bank. Amounts stay the same; account balances recalculate. Undo is available in Recovery history.")},
        confirmButton={TextButton(onClick={
            runCatching { store.setAccounts(rows.filter { it.id in selected }.map { it.fingerprint },bank) }.onSuccess { onChanged();onNotice("Account assignments saved") }.onFailure { onNotice(it.message ?: "Assignment failed") }
            selected=emptySet();confirm=false;open=false
        }) { Text("Assign") }},dismissButton={TextButton(onClick={confirm=false}) { Text("Cancel") }})
}
