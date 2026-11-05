package com.example.mibbudget

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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
    var currentBalanceInput by remember(account) { mutableStateOf("") }
    var enteredDate by remember(account) { mutableStateOf(LocalDate.now().toString()) }
    var selectedDate by remember(account) { mutableStateOf(LocalDate.now().toString()) }
    var message by remember { mutableStateOf("") }
    val accountKey = account.lowercase(java.util.Locale.ROOT)
    var screenshotText by remember { mutableStateOf("") }
    var screenshotReady by remember { mutableStateOf(false) }
    val screenshotPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) try {
            val captured = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = androidx.exifinterface.media.ExifInterface(stream)
                    val raw = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_DIGITIZED)
                    raw?.let { java.time.LocalDateTime.parse(it, java.time.format.DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")) }
                }
            }.getOrNull()
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(InputImage.fromFilePath(context, uri)).addOnSuccessListener { result ->
                screenshotText = result.text.replace(Regex("""\b\d{8,}\b"""), "••••••••").take(1200)
                val parsed = MibScreenshotParser.parse(result.text)
                if (parsed != null) {
                    currentBalanceInput = "%.2f".format(java.util.Locale.US, parsed.availableMinor / 100.0)
                    enteredDate = captured?.toLocalDate()?.toString() ?: LocalDate.now().toString()
                    captured?.let { prefs.edit().putString("${accountKey}_pending_time", it.toLocalTime().toString()).apply() }
                    screenshotReady = true
                    message = "MIB account-card balance detected. Review and save the available balance."
                } else {
                    screenshotReady = false
                    message = "Could not identify the MIB account-card balance. Please enter it manually."
                }
            }.addOnFailureListener { message = "Screenshot OCR failed: ${it.message?.take(80)}" }
                .addOnCompleteListener { recognizer.close() }
        } catch (e: Exception) { message = "Could not read screenshot: ${e.message?.take(80)}" }
    }
    val anchorMinor = remember(accountKey, refresh) { prefs.getLong("${accountKey}_minor", Long.MIN_VALUE) }
    val anchorDate = remember(accountKey, refresh) { prefs.getString("${accountKey}_date", "").orEmpty() }
    val anchorDay = runCatching { LocalDate.parse(anchorDate) }.getOrNull()
    val anchorTime = remember(accountKey, refresh) { prefs.getString("${accountKey}_time", null)?.takeIf { it.isNotBlank() } }
    val today = LocalDate.now()
    val accountTransactions = transactions.filter {
        FinanceCalculations.assignedAccount(it, labels).equals(account, ignoreCase = true)
    }.sortedByDescending { it.timestamp }
    // October 29 method: include only transactions on later calendar dates.
    val isAfterSnapshot: (Tx) -> Boolean = { tx ->
        val date = runCatching { LocalDate.parse(tx.timestamp.take(10)) }.getOrNull()
        date != null && anchorDay != null && date.isAfter(anchorDay) && !date.isAfter(today)
    }
    val currentBalance = FinanceCalculations.projectedBalance(account, anchorMinor, anchorDate, transactions, labels, today, anchorTime)
    val afterAnchor = if (anchorDay != null) accountTransactions.filter(isAfterSnapshot) else emptyList()
    val unassigned = if (anchorDay != null) transactions.count {
        FinanceCalculations.assignedAccount(it, labels) == "Unassigned" && isAfterSnapshot(it)
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
                        Text("$account · Current balance")
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
                if (account == "MIB") OutlinedButton(onClick={screenshotPicker.launch("image/*")},modifier=Modifier.fillMaxWidth()) { Text("Read MIB bank screenshot") }
                if (screenshotText.isNotBlank()) {
                    Text(
                        if (screenshotReady) "Screenshot figures detected — review before saving."
                        else "Could not match the screenshot safely. OCR text below (long account numbers masked):",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!screenshotReady) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                screenshotText,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                OutlinedTextField(value=enteredDate,onValueChange={enteredDate=it},
                    label={Text("Statement date (YYYY-MM-DD)")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(value=currentBalanceInput,onValueChange={currentBalanceInput=it},
                    label={Text(if(account == "MIB") "Available balance from MIB screenshot (MVR)" else "Verified balance (MVR)")},modifier=Modifier.fillMaxWidth())
                val snapshotAmount=Money.parseLaari(currentBalanceInput)
                Text("Snapshot balance: ${snapshotAmount?.let(Money::format) ?: "Enter the verified balance"}",
                    style=MaterialTheme.typography.titleMedium)
                Button(onClick={
                    val date=runCatching { LocalDate.parse(enteredDate) }.getOrNull()
                    val amount=snapshotAmount
                    if(date==null || date.isAfter(today) || amount==null || amount < 0L)
                        message="Enter a valid date and non-negative account balance."
                    else {
                        prefs.edit().putString("${accountKey}_date",date.toString())
                            .putLong("${accountKey}_minor",amount)
                            .putString("${accountKey}_time",prefs.getString("${accountKey}_pending_time","").orEmpty())
                            .remove("${accountKey}_pending_time")
                            .apply()
                        refresh++
                        screenshotReady=false
                        message="Verified account balance saved. No hold calculation is used."
                    }
                },modifier=Modifier.fillMaxWidth()) { Text("Save bank balance snapshot") }
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
