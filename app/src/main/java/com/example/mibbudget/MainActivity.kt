package com.example.mibbudget

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.os.Bundle
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight

private val categories = listOf("Food","Groceries","Transport","Bills","Shopping","Health","Transfers","Internal Transfer","Other","Income")

class MainActivity : ComponentActivity() {
    private lateinit var store: TransactionStore
    private var sharedText by mutableStateOf("")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TransactionStore(this)
        sharedText = intent?.takeIf { it.action == Intent.ACTION_SEND && it.type == "text/plain" }?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF2563EB), secondary = Color(0xFF0D9488), background = Color(0xFFF6F8FC), surface = Color.White, surfaceVariant = Color(0xFFEAF0FA)), shapes = Shapes(medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp))) { BudgetApp(store, sharedText) { sharedText = "" } } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedText = intent.takeIf { it.action == Intent.ACTION_SEND && it.type == "text/plain" }?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
    }
    override fun onDestroy() { store.close(); super.onDestroy() }
}

@Composable
fun BudgetApp(store: TransactionStore, shared: String, clearShared: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sms_settings", android.content.Context.MODE_PRIVATE) }
    var monitoring by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        monitoring = granted
        prefs.edit().putBoolean("enabled", granted).apply()
    }
    var notice by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    var importingHistory by remember { mutableStateOf(false) }
    var historyRequested by remember { mutableStateOf(false) }
    var historyMonths by remember { mutableStateOf(1L) }
    val historyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) historyRequested = true else notice = "SMS inbox permission denied. Manual import still works."
    }
    var revision by remember { mutableIntStateOf(0) }
    val transactions = remember(revision) { store.all() }
    val declinedTransactions = remember(revision) { store.declined() }
    val foreignTransactions = remember(revision) { store.foreign() }
    val pendingBml = remember(revision) { store.pendingBml() }
    val monthlyTransactions = remember(revision, selectedMonth) { store.forMonth(selectedMonth) }
    val budgets = remember(revision) { store.budgets() }
    LaunchedEffect(historyRequested) {
        if (historyRequested) {
            historyRequested = false
            importingHistory = true
            try {
                val result = withContext(Dispatchers.IO) { SmsHistoryImporter.importPrevious(context, store, historyMonths) }
                notice = "Scanned ${result.scanned} inbox messages; imported ${result.imported}, duplicates ${result.duplicates}, ignored ${result.ignored}."
                revision++
            } catch (e: Exception) { notice = "History import failed: ${e.javaClass.simpleName}" }
            finally { importingHistory = false }
        }
    }
    var input by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Food") }
    var income by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var importCategory by remember { mutableStateOf("Other") }
    var importExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(shared) { if(shared.isNotBlank()) { input = shared; tab = 2 } }
    // Refresh saved transactions when app returns to foreground after SMS broadcasts.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) revision++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Scaffold(bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            listOf("Dashboard","Transactions","Import SMS","Budgets").forEachIndexed { i, label ->
                NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Text(listOf("⌂","⇄","✉","◉")[i])},label={Text(label)})
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).then(if (tab == 0 || tab == 2) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("MIB Budget Tracker",style=MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text("Your money, clearly organized  •  MVR",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(notice.isNotBlank()) Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer), modifier=Modifier.fillMaxWidth()) { Text(notice, Modifier.padding(14.dp), color=MaterialTheme.colorScheme.onPrimaryContainer) }
            if (tab == 0) {
                Text("Automatic bank SMS import", style=MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-import recognized bank SMS")
                        Text("Requires RECEIVE_SMS permission. Supported MIB and BML transaction formats import automatically after enabling.", style=MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked=monitoring, onCheckedChange={ enabled ->
                        if (!enabled) {
                            monitoring=false
                            prefs.edit().putBoolean("enabled", false).apply()
                        } else if (context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) {
                            monitoring=true
                            prefs.edit().putBoolean("enabled", true).apply()
                        } else permissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                    })
                }
                Text("Automatic SMS imports save recognized MIB transfers and purchases. Review entries; bank sender names can be spoofed.", style=MaterialTheme.typography.bodySmall)

                Text("Import previous bank SMS", style=MaterialTheme.typography.titleMedium)
                Text("Scan only SMS stored on this phone. No message bodies are saved or uploaded. Review imported transactions for accuracy.", style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(1L, 3L, 12L).forEach { months ->
                        FilterChip(selected=historyMonths==months,onClick={historyMonths=months},label={Text("${months} month${if(months>1) "s" else ""}")})
                    }
                }
                Button(enabled=!importingHistory,onClick={
                    if (context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) historyRequested=true
                    else historyLauncher.launch(Manifest.permission.READ_SMS)
                }) { Text(if(importingHistory) "Importing…" else "Import previous SMS") }
                Text("BML notification imports (optional)", style=MaterialTheme.typography.titleMedium)
                Text("Notification access is broad. This app filters only BML Favara received messages, storing amount and time for your review.", style=MaterialTheme.typography.bodySmall)
                Button(onClick={ context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }) { Text("Manage notification access") }
                Button(onClick={
                    val imported = BmlNotificationListener.importVisible()
                    notice = if (imported == null) "Enable notification access first, then reopen the app."
                             else "Scanned visible notifications; queued $imported new BML credits."
                    revision++
                }) { Text("Import existing BML notifications") }
                Text("Only notifications still active in Android can be scanned. Cleared notification history is not accessible.", style=MaterialTheme.typography.bodySmall)
                Text("BML credits awaiting review: ${pendingBml.size}")
                pendingBml.forEach { tx ->
                    Card(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("BML received: +${Money.format(tx.amountLaari)}")
                            Text(tx.timestamp.take(16), style=MaterialTheme.typography.bodySmall)
                            Text("For your own MIB-to-BML transfer, choose Own transfer to avoid double-counting.", style=MaterialTheme.typography.bodySmall)
                            Row {
                                TextButton(onClick={store.resolveBml(tx,true);revision++}) { Text("Add credit") }
                                TextButton(onClick={store.resolveBml(tx,true,true);revision++}) { Text("Own transfer") }
                                TextButton(onClick={store.resolveBml(tx,false);revision++}) { Text("Discard") }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            when(tab) {
                0 -> {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        TextButton(onClick={selectedMonth=selectedMonth.minusMonths(1)}) { Text("‹ Previous") }
                        Text(selectedMonth.toString(),modifier=Modifier.padding(top=12.dp))
                        TextButton(onClick={selectedMonth=selectedMonth.plusMonths(1)},enabled=selectedMonth<YearMonth.now()) { Text("Next ›") }
                    }
                    val cashflowTransactions = monthlyTransactions.filter { it.category != "Internal Transfer" }
                    val incoming = cashflowTransactions.filter { it.amountLaari>0 }.sumOf { it.amountLaari }
                    val outgoing = cashflowTransactions.filter { it.amountLaari<0 }.sumOf { -it.amountLaari }
                    Card(Modifier.fillMaxWidth(), shape=RoundedCornerShape(24.dp), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primary)) { Column(Modifier.padding(22.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("MONTHLY CASH FLOW", style=MaterialTheme.typography.labelLarge, color=Color.White.copy(alpha=0.8f))
                        Text(Money.format(incoming-outgoing),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold,color=Color.White)
                        Text("↑  Income   ${Money.format(incoming)}",color=Color.White)
                        Text("↓  Expenses   ${Money.format(outgoing)}",color=Color.White)
                        Text("Cash flow, not your bank balance",style=MaterialTheme.typography.labelSmall,color=Color.White.copy(alpha=0.7f))
                    } }
                    Spacer(Modifier.height(20.dp))
                    Text("Spending by category",style=MaterialTheme.typography.titleMedium)
                    val grouped = monthlyTransactions.filter { it.amountLaari<0 && it.category != "Internal Transfer" }.groupBy { it.category }
                    grouped.forEach { (cat,txs) ->
                        val spent=txs.sumOf{-it.amountLaari}
                        Text("$cat    •    ${Money.format(spent)}",Modifier.padding(top=12.dp),fontWeight=FontWeight.Medium)
                        val limit=budgets[cat]
                        if(limit!=null) { LinearProgressIndicator(progress={ (spent.toFloat()/limit).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth().height(8.dp),strokeCap=androidx.compose.ui.graphics.StrokeCap.Round); if(spent>=limit) Text("Budget reached",color=MaterialTheme.colorScheme.error) }
                    }
                    if(grouped.isEmpty()) Text("No transactions yet. Add one or import a bank SMS.")
                }
                1 -> {
                    OutlinedTextField(value=amount,onValueChange={amount=it},label={Text("Amount (MVR)")},modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(value=input,onValueChange={input=it},label={Text("Description")},modifier=Modifier.fillMaxWidth())
                    Row { Text("Income"); Switch(checked=income,onCheckedChange={income=it}) }
                    Box { TextButton(onClick={expanded=true}) { Text("Category: $category ▾") }
                        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                            categories.forEach { c -> DropdownMenuItem(text={Text(c)},onClick={category=c;expanded=false}) }
                        }
                    }
                    Button(onClick={
                        val v=Money.parseLaari(amount)
                        if(v==null || v<=0 || input.isBlank()) notice="Enter a valid amount and description"
                        else { store.add(if(income) v else -v,if(income) "Income" else if(category=="Income") "Other" else category,input.trim()); amount="";input="";notice="Transaction saved";revision++ }
                    }) { Text("Add transaction") }
                    HorizontalDivider(Modifier.padding(vertical=12.dp))
                    Text("Transaction history (all dates)",style=MaterialTheme.typography.titleMedium)
                    Text("Foreign currency purchases (excluded from MVR totals)", style=MaterialTheme.typography.titleMedium)
                    foreignTransactions.forEach { t ->
                        ListItem(headlineContent={Text("BML: ${t.merchant}")}, supportingContent={Text(t.timestamp.take(16))}, trailingContent={Text("${t.currency} ${Money.format(t.amountMinor).removePrefix("MVR ")}")})
                    }
                    Text("Declined card attempts (not counted as spending)",style=MaterialTheme.typography.titleMedium)
                    declinedTransactions.forEach { d ->
                        ListItem(headlineContent={Text(d.merchant)},supportingContent={Text("${d.timestamp.take(16)} • ${d.reason} • Declined")},trailingContent={Text("${d.currency} ${Money.format(d.amountLaari).removePrefix("MVR ")}")})
                    }
                    LazyColumn { items(transactions,key={it.id}) { t ->
                        ListItem(headlineContent={Text(t.description)},supportingContent={Text("${t.category} • ${t.timestamp.take(10)}")},trailingContent={Text((if(t.amountLaari>0) "+" else "")+Money.format(t.amountLaari),fontWeight=FontWeight.SemiBold,color=if(t.amountLaari>0) Color(0xFF0D9488) else MaterialTheme.colorScheme.onSurface)})
                        if (t.category == "Transfers" || t.category == "Incoming Transfer") TextButton(onClick={store.markInternal(t.id);revision++}) { Text("Mark internal transfer") }
                        HorizontalDivider()
                    } }
                }
                2 -> {
                    Text("Paste or share a transaction SMS. Verify details before saving. Never paste OTPs or passwords.")
                    OutlinedTextField(value=input,onValueChange={input=it},label={Text("Bank SMS text")},modifier=Modifier.fillMaxWidth().height(160.dp))
                    val candidate = remember(input) { BankSmsParser.parse(input) }
                    val declinedCandidate = remember(input) { BankSmsParser.parseDeclined(input) }
                    val bmlCard = remember(input) { BankSmsParser.parseBmlCard(input) }
                    LaunchedEffect(candidate?.fingerprint) { importCategory = candidate?.category ?: "Other" }
                    if (declinedCandidate != null) {
                        Text("Declined card attempt — NOT a completed expense", color=MaterialTheme.colorScheme.error)
                        Text("${declinedCandidate.currency} ${Money.format(declinedCandidate.amountLaari).removePrefix("MVR ")} at ${declinedCandidate.merchant}")
                        Text("Date: ${declinedCandidate.transactionTime} • Reason: ${declinedCandidate.reason}")
                        Button(onClick={
                            val inserted = store.addDeclined(declinedCandidate)
                            notice = if(inserted) "Declined attempt saved (not counted as spending)" else "Duplicate declined attempt ignored"
                            if(inserted) revision++
                            input="";clearShared()
                        }) { Text("Save declined attempt") }
                    } else if (bmlCard != null) {
                        Text("BML card purchase: ${bmlCard.currency} ${Money.format(bmlCard.amountMinor).removePrefix("MVR ")}")
                        Text("${bmlCard.merchant} • ${bmlCard.transactionTime}")
                        Text("Excluded from MVR budget totals until converted with a verified rate.")
                        Button(onClick={
                            val inserted = store.addForeign(bmlCard)
                            notice = if(inserted) "BML purchase saved" else "Duplicate ignored"
                            if(inserted) revision++
                            input="";clearShared()
                        }) { Text("Save BML purchase") }
                    } else if(candidate==null) Text("Unrecognized SMS. Only supported transaction formats can be imported.")
                    else {
                        Text("Detected: ${if(candidate.amountLaari>0) "Credit" else "Debit"} ${Money.format(candidate.amountLaari)}")
                        Text("Transaction date: ${candidate.transactionTime ?: "Unknown"}")
                        Text("Description: ${candidate.description}")
                        Box { TextButton(onClick={importExpanded=true}) { Text("Category: $importCategory ▾") }
                            DropdownMenu(expanded=importExpanded,onDismissRequest={importExpanded=false}) {
                                categories.filter { it != "Income" }.forEach { c -> DropdownMenuItem(text={Text(c)},onClick={importCategory=c;importExpanded=false}) }
                            }
                        }
                        Button(onClick={
                            val inserted=store.add(candidate.amountLaari,importCategory,candidate.description,candidate.fingerprint,candidate.transactionTime)
                            notice=if(inserted) "Transaction imported" else "Duplicate transaction ignored"
                            if(inserted) revision++
                            input="";clearShared()
                        }) { Text("Confirm and save") }
                    }
                }
                3 -> {
                    Text("Set monthly category limits (MVR)",style=MaterialTheme.typography.titleMedium)
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        categories.filter { it!="Income" }.forEach { cat ->
                            var limitText by remember(cat,revision) { mutableStateOf(budgets[cat]?.let { "${it/100}.${(it%100).toString().padStart(2,'0')}" } ?: "") }
                            Text(cat,Modifier.padding(top=12.dp))
                            Row { OutlinedTextField(value=limitText,onValueChange={limitText=it},label={Text("Limit MVR")},modifier=Modifier.weight(1f),singleLine=true)
                                Spacer(Modifier.width(8.dp))
                                Button(onClick={val v=Money.parseLaari(limitText);if(v!=null&&v>0){store.setBudget(cat,v);revision++;notice="$cat budget saved"}else notice="Invalid limit"}) { Text("Save") }
                            }
                        }
                    }
                }
            }
        }
    }
}
