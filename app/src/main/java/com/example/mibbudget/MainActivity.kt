package com.example.mibbudget

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.os.Bundle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
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
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight

private val categories = listOf("Food","Groceries","Transport","Bills","Shopping","Health","Transfers","Internal Transfer","Other","Income")

class MainActivity : ComponentActivity() {
    private lateinit var store: TransactionStore
    private var sharedText by mutableStateOf("")
    private var darkTheme by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TransactionStore(this)
        sharedText = intent?.takeIf { it.action == Intent.ACTION_SEND && it.type == "text/plain" }?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        setContent { MaterialTheme(colorScheme = (if(darkTheme) darkColorScheme(primary=Color(0xFF8FB5FF),secondary=Color(0xFF5EEAD4)) else lightColorScheme(primary = Color(0xFF2563EB), secondary = Color(0xFF0D9488), background = Color(0xFFF6F8FC), surface = Color.White, surfaceVariant = Color(0xFFEAF0FA))), shapes = Shapes(medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp))) { BudgetApp(store, sharedText, darkTheme, { darkTheme = it }) { sharedText = "" } } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedText = intent.takeIf { it.action == Intent.ACTION_SEND && it.type == "text/plain" }?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
    }
    override fun onDestroy() { store.close(); super.onDestroy() }
}

@Composable
fun BudgetApp(store: TransactionStore, shared: String, darkTheme: Boolean, setDarkTheme: (Boolean)->Unit, clearShared: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sms_settings", android.content.Context.MODE_PRIVATE) }
    val openAppSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
    var monitoring by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        monitoring = granted
        prefs.edit().putBoolean("enabled", granted).apply()
    }
    var notice by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    var searchText by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var editingAmount by remember { mutableStateOf("") }
    var editingDescription by remember { mutableStateOf("") }
    var editingCategory by remember { mutableStateOf("Other") }
    var deletingId by remember { mutableStateOf<Long?>(null) }
    var accountFilter by remember { mutableStateOf("All accounts") }
    var accountExpanded by remember { mutableStateOf(false) }
    var goalName by remember { mutableStateOf("") }
    var goalTarget by remember { mutableStateOf("") }
    var goalSaved by remember { mutableStateOf("") }
    var recurringName by remember { mutableStateOf("") }
    var recurringAmount by remember { mutableStateOf("") }
    var transactionFilter by remember { mutableStateOf("All") }
    var showTransactionForm by remember { mutableStateOf(false) }
    var filterExpanded by remember { mutableStateOf(false) }
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    var importingHistory by remember { mutableStateOf(false) }
    var showImportSettings by remember { mutableStateOf(false) }
    var historyRequested by remember { mutableStateOf(false) }
    var historyMonths by remember { mutableStateOf(1L) }
    val historyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) historyRequested = true else notice = "SMS inbox permission denied. Manual import still works."
    }
    var revision by remember { mutableIntStateOf(0) }
    val transactions = remember(revision) { store.all() }
    val accountLabels = remember(revision) { store.accounts() }
    val declinedTransactions = remember(revision) { store.declined() }
    val foreignTransactions = remember(revision) { store.foreign() }
    val pendingBml = remember(revision) { store.pendingBml() }
    val monthlyTransactions = remember(revision, selectedMonth) { store.forMonth(selectedMonth) }
    val budgets = remember(revision) { store.budgets() }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null) try { context.contentResolver.openOutputStream(uri)?.use { it.write(store.exportJson().toByteArray(Charsets.UTF_8)) }; notice="Backup exported. Keep the file private." } catch(e:Exception) { notice="Export failed: ${e.javaClass.simpleName}" }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) try { val data=context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Cannot read file"); val count=store.restoreJson(data);revision++;notice="Restored $count new records. Existing records kept." } catch(e:Exception) { notice="Restore failed: ${e.message?.take(90)}" }
    }
    LaunchedEffect(historyRequested) {
        if (historyRequested) {
            historyRequested = false
            importingHistory = true
            try {
                val result = withContext(Dispatchers.IO) { SmsHistoryImporter.importPrevious(context, store, historyMonths) }
                notice = "Scanned ${result.scanned} inbox messages; imported ${result.imported}, duplicates ${result.duplicates}, ignored ${result.ignored}."
                revision++
            } catch (e: CancellationException) { throw e
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
        Column(Modifier.fillMaxSize().padding(padding).background(if(tab==1) Color(0xFF191B38) else MaterialTheme.colorScheme.background).then(if (tab == 0 || tab == 1 || tab == 2) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("MIB Budget Tracker",style=MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text("Your money, clearly organized  •  MVR",style=MaterialTheme.typography.bodyMedium,color=if(tab==1) Color(0xFFB8BDD4) else MaterialTheme.colorScheme.onSurfaceVariant)
            if(notice.isNotBlank()) Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer), modifier=Modifier.fillMaxWidth()) { Text(notice, Modifier.padding(14.dp), color=MaterialTheme.colorScheme.onPrimaryContainer) }
            if (tab == 0) {
                OutlinedButton(onClick={showImportSettings = !showImportSettings}, modifier=Modifier.fillMaxWidth()) { Text(if(showImportSettings) "Hide bank import settings  ↑" else "Bank SMS & notification settings  ↓") }
                if (showImportSettings) {
                Text("Permissions & Android restricted settings",style=MaterialTheme.typography.titleMedium)
                Text("If Android says Restricted setting, open App info, tap the three-dot menu, and choose Allow restricted settings if available. This is controlled by Android, not the app. Only enable access if you trust this APK.",style=MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick=openAppSettings) { Text("Open app info & permissions") }
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
                    Text("Monthly spending overview",style=MaterialTheme.typography.titleMedium)
                    val totalSpent=grouped.values.flatten().sumOf { -it.amountLaari }
                    grouped.forEach { (cat,txs) ->
                        val spent=txs.sumOf { -it.amountLaari }
                        Text("$cat  ${if(totalSpent>0) (spent*100/totalSpent) else 0}%")
                        LinearProgressIndicator(progress={if(totalSpent>0) spent.toFloat()/totalSpent else 0f},modifier=Modifier.fillMaxWidth())
                    }
                    budgets.forEach { (cat,limit) ->
                        val spent=grouped[cat]?.sumOf { -it.amountLaari } ?: 0L
                        if(spent>=limit*8/10) Text("Budget alert: $cat is at ${if(limit>0) spent*100/limit else 0}% of limit",color=MaterialTheme.colorScheme.error)
                    }
                    HorizontalDivider()
                    Text("Savings goals",style=MaterialTheme.typography.titleMedium)
                    store.goals().forEach { (name,target,saved) ->
                        Text("$name: ${Money.format(saved)} / ${Money.format(target)}")
                        LinearProgressIndicator(progress={(saved.toFloat()/target).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
                    }
                    OutlinedTextField(value=goalName,onValueChange={goalName=it},label={Text("Goal name")})
                    OutlinedTextField(value=goalTarget,onValueChange={goalTarget=it},label={Text("Target MVR")})
                    OutlinedTextField(value=goalSaved,onValueChange={goalSaved=it},label={Text("Saved MVR")})
                    OutlinedButton(onClick={
                        val target=Money.parseLaari(goalTarget);val saved=Money.parseLaari(goalSaved)
                        if(goalName.isNotBlank() && target!=null && target>0 && saved!=null) {store.setGoal(goalName,target,saved);revision++;goalName="";goalTarget="";goalSaved=""} else notice="Invalid goal"
                    }) { Text("Save goal") }
                    HorizontalDivider()
                    Text("Recurring expense templates",style=MaterialTheme.typography.titleMedium)
                    Text("Templates only; no automatic charges or duplicate entries.")
                    store.recurring().forEach { (name,value,cat) ->
                        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                            Text("$name: ${Money.format(value)}",modifier=Modifier.weight(1f))
                            TextButton(onClick={store.add(-value,cat,name);revision++}) { Text("Record now") }
                        }
                    }
                    OutlinedTextField(value=recurringName,onValueChange={recurringName=it},label={Text("Recurring name")})
                    OutlinedTextField(value=recurringAmount,onValueChange={recurringAmount=it},label={Text("Monthly amount MVR")})
                    OutlinedButton(onClick={
                        val v=Money.parseLaari(recurringAmount)
                        if(recurringName.isNotBlank() && v!=null && v>0) {store.setRecurring(recurringName,v,"Bills");revision++;recurringName="";recurringAmount=""} else notice="Invalid recurring amount"
                    }) { Text("Save recurring template") }
                    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Dark theme",modifier=Modifier.weight(1f))
                        Switch(checked=darkTheme,onCheckedChange=setDarkTheme)
                    }
                }
                1 -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Transactions",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,color=Color.White)
                        Box {
                            OutlinedButton(onClick={filterExpanded=true}) { Text("Filter: $transactionFilter") }
                            DropdownMenu(expanded=filterExpanded,onDismissRequest={filterExpanded=false}) {
                                listOf("All","Expenses","Income","Transfers").forEach { option ->
                                    DropdownMenuItem(text={Text(option)},onClick={transactionFilter=option;filterExpanded=false})
                                }
                            }
                        }
                    }
                    Text("${transactions.size} saved MVR transactions",color=Color(0xFFB8BDD4),style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value=searchText,onValueChange={searchText=it},label={Text("Search transactions")},modifier=Modifier.fillMaxWidth(),singleLine=true,colors=OutlinedTextFieldDefaults.colors(focusedTextColor=Color.White,unfocusedTextColor=Color.White,focusedLabelColor=Color.White,unfocusedLabelColor=Color.LightGray))
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Box {
                            OutlinedButton(onClick={accountExpanded=true}) { Text(accountFilter) }
                            DropdownMenu(expanded=accountExpanded,onDismissRequest={accountExpanded=false}) {
                                (listOf("All accounts","Unassigned","MIB","BML","Cash","Other")).forEach { label ->
                                    DropdownMenuItem(text={Text(label)},onClick={accountFilter=label;accountExpanded=false})
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={backupLauncher.launch("mib-budget-backup.json")}) { Text("Export backup") }
                        OutlinedButton(onClick={restoreLauncher.launch(arrayOf("application/json","text/plain"))}) { Text("Restore") }
                    }
                    val filteredTransactions = transactions.filter { t ->
                        (searchText.isBlank() || t.description.contains(searchText,true) || t.category.contains(searchText,true) || t.timestamp.contains(searchText) || Money.format(t.amountLaari).contains(searchText,true)) &&
                        (accountFilter=="All accounts" || (accountLabels[t.fingerprint] ?: "Unassigned")==accountFilter) &&
                        when(transactionFilter) {
                            "Expenses" -> t.amountLaari < 0
                            "Income" -> t.amountLaari > 0
                            "Transfers" -> t.category.contains("Transfer",ignoreCase=true)
                            else -> true
                        }
                    }
                    if(filteredTransactions.isEmpty()) Text("No transactions match this filter.",color=Color.White)
                    filteredTransactions.forEach { t ->
                        Card(Modifier.fillMaxWidth().padding(vertical=4.dp),shape=RoundedCornerShape(18.dp),
                            colors=CardDefaults.cardColors(containerColor=Color(0xFF2A2D4C))) {
                            Row(Modifier.fillMaxWidth().padding(18.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),
                                verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                Text("▤",color=Color(0xFF4EA1FF),style=MaterialTheme.typography.headlineMedium)
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                    Text((if(t.amountLaari>0) "+" else "")+Money.format(t.amountLaari),
                                        color=if(t.amountLaari>0) Color(0xFF4ADEA5) else Color(0xFFFF6B77),
                                        style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                                    Text(t.description,color=Color.White,style=MaterialTheme.typography.bodyMedium)
                                    Text("${t.category}  •  ${t.timestamp.take(10)}",color=Color(0xFFADB2CC),style=MaterialTheme.typography.bodySmall)
                                    Row {
                                        TextButton(onClick={store.setAccount(t.fingerprint,if((accountLabels[t.fingerprint]?:"Unassigned")=="MIB") "BML" else "MIB");revision++}) { Text(accountLabels[t.fingerprint] ?: "Set MIB/BML") }
                                        TextButton(onClick={editingId=t.id;editingAmount=Money.format(kotlin.math.abs(t.amountLaari)).removePrefix("MVR ").replace(",","");editingDescription=t.description;editingCategory=t.category}) { Text("Edit") }
                                        TextButton(onClick={deletingId=t.id}) { Text("Delete",color=Color(0xFFFF6B77)) }
                                    }
                                    if(t.category=="Transfers" || t.category=="Incoming Transfer")
                                        TextButton(onClick={store.markInternal(t.id);revision++}) { Text("Mark internal transfer") }
                                }
                            }
                        }
                    }
                    if(editingId!=null) {
                        AlertDialog(onDismissRequest={editingId=null},title={Text("Edit transaction")},text={
                            Column {
                                OutlinedTextField(value=editingAmount,onValueChange={editingAmount=it},label={Text("Amount MVR")})
                                OutlinedTextField(value=editingDescription,onValueChange={editingDescription=it},label={Text("Description")})
                                Text("Category")
                                categories.forEach { cat -> if(cat==editingCategory) Text(cat) }
                                OutlinedButton(onClick={editingCategory=if(editingCategory=="Other") "Food" else "Other"}) { Text("Toggle Food / Other") }
                            }
                        },confirmButton={TextButton(onClick={
                            val original=transactions.firstOrNull { it.id==editingId }
                            val v=Money.parseLaari(editingAmount)
                            if(original!=null && v!=null && v>0 && store.updateTransaction(original.id,if(original.amountLaari>0) v else -v,editingCategory,editingDescription)) { revision++;notice="Transaction updated";editingId=null }
                            else notice="Enter a valid amount and description"
                        }) { Text("Save") }},dismissButton={TextButton(onClick={editingId=null}) { Text("Cancel") }})
                    }
                    if(deletingId!=null) {
                        AlertDialog(onDismissRequest={deletingId=null},title={Text("Delete transaction?")},text={Text("This permanently deletes the selected record. Export a backup first if needed.")},confirmButton={TextButton(onClick={deletingId?.let { store.deleteTransaction(it) };deletingId=null;revision++}) { Text("Delete") }},dismissButton={TextButton(onClick={deletingId=null}) { Text("Cancel") }})
                    }
                    OutlinedButton(onClick={showTransactionForm=!showTransactionForm},modifier=Modifier.fillMaxWidth()) {
                        Text(if(showTransactionForm) "Hide add transaction" else "+ Add transaction")
                    }
                    if(showTransactionForm) {
                        Card(colors=CardDefaults.cardColors(containerColor=Color.White),modifier=Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("Add a transaction",style=MaterialTheme.typography.titleMedium)
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
                            }
                        }
                    }
                    Card(colors=CardDefaults.cardColors(containerColor=Color.White),modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                        Text("Other transaction records",style=MaterialTheme.typography.titleMedium)
                        Text("Foreign currency purchases (excluded from MVR totals)", style=MaterialTheme.typography.titleMedium)
                        foreignTransactions.forEach { t ->
                            ListItem(headlineContent={Text("BML: ${t.merchant}")}, supportingContent={Text(t.timestamp.take(16))}, trailingContent={Text("${t.currency} ${Money.format(t.amountMinor).removePrefix("MVR ")}")})
                        }
                        Text("Declined card attempts (not counted as spending)",style=MaterialTheme.typography.titleMedium)
                        declinedTransactions.forEach { d ->
                            ListItem(headlineContent={Text(d.merchant)},supportingContent={Text("${d.timestamp.take(16)} • ${d.reason} • Declined")},trailingContent={Text("${d.currency} ${Money.format(d.amountLaari).removePrefix("MVR ")}")})
                        }
                        }
                    }
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
