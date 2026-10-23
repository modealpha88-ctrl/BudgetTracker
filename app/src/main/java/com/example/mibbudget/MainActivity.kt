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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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

private val categories = listOf("Food","Fast Food","Groceries","Transport","Bills","Shopping","Health","Transfers","Internal Transfer","Other","Income")

class MainActivity : ComponentActivity() {
    private lateinit var store: TransactionStore
    private var sharedText by mutableStateOf("")
    private var darkTheme by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TransactionStore(this)
        darkTheme = getSharedPreferences("appearance", MODE_PRIVATE).getBoolean("dark",false)
        sharedText = intent?.takeIf { it.action == Intent.ACTION_SEND && it.type == "text/plain" }?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        setContent { MaterialTheme(colorScheme = (if(darkTheme) darkColorScheme(primary=Color(0xFF8FB5FF),secondary=Color(0xFF5EEAD4)) else lightColorScheme(primary = Color(0xFF2563EB), secondary = Color(0xFF0D9488), background = Color(0xFFF6F8FC), surface = Color.White, surfaceVariant = Color(0xFFEAF0FA))), shapes = Shapes(medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp))) { BudgetApp(store, sharedText, darkTheme, { darkTheme = it; getSharedPreferences("appearance", MODE_PRIVATE).edit().putBoolean("dark",it).apply() }) { sharedText = "" } } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedText = intent.takeIf { it.action == Intent.ACTION_SEND && it.type == "text/plain" }?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
    }
    override fun onDestroy() { store.close(); super.onDestroy() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetApp(store: TransactionStore, shared: String, darkTheme: Boolean, setDarkTheme: (Boolean)->Unit, clearShared: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sms_settings", android.content.Context.MODE_PRIVATE) }
    val securityPrefs=remember { context.getSharedPreferences("app_security",android.content.Context.MODE_PRIVATE) }
    var lockEnabled by remember { mutableStateOf(securityPrefs.getBoolean("biometric_lock",false)) }
    var unlocked by remember { mutableStateOf(!lockEnabled) }
    var lockError by remember { mutableStateOf("") }
    val authenticate = {
        if(android.os.Build.VERSION.SDK_INT>=29) {
            val manager=context.getSystemService(android.content.Context.BIOMETRIC_SERVICE) as android.hardware.biometrics.BiometricManager
            if(manager.canAuthenticate()==android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS) {
                android.hardware.biometrics.BiometricPrompt.Builder(context)
                    .setTitle("Unlock MIB Budget Tracker")
                    .setSubtitle("Confirm your fingerprint or face")
                    .setNegativeButton("Cancel",context.mainExecutor,{ _,_ -> lockError="Authentication cancelled" })
                    .build()
                    .authenticate(android.os.CancellationSignal(),context.mainExecutor,object:android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result:android.hardware.biometrics.BiometricPrompt.AuthenticationResult) {
                            unlocked=true
                            lockError=""
                        }
                        override fun onAuthenticationError(errorCode:Int,errString:CharSequence) { lockError=errString.toString() }
                    })
            } else lockError="Set up fingerprint or face unlock in Android Settings first."
        } else lockError="Biometric lock requires Android 10 or later."
    }

    val openAppSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
    var monitoring by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }
    var permissionRefresh by remember { mutableIntStateOf(0) }
    var showAccessHelp by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        monitoring = granted
        prefs.edit().putBoolean("enabled", granted).apply()
    }
    var notice by remember { mutableStateOf("") }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionRefresh++
        notice=if(granted) "App notifications allowed." else "App notifications not allowed. Bank notification reading is a separate permission."
    }
    var tab by remember { mutableStateOf(0) }
    var csvReview by remember { mutableStateOf<TransactionStore.CsvReview?>(null) }
    var csvText by remember { mutableStateOf<String?>(null) }
    var confirmMissingImport by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var editingAmount by remember { mutableStateOf("") }
    var editingDescription by remember { mutableStateOf("") }
    var editingCategory by remember { mutableStateOf("Other") }
    var editingCategoryExpanded by remember { mutableStateOf(false) }
    var deletingId by remember { mutableStateOf<Long?>(null) }
    var selectedTransactionId by remember { mutableStateOf<Long?>(null) }
    var showTransactionTools by remember { mutableStateOf(false) }
    var accountFilter by remember { mutableStateOf("All accounts") }
    var accountExpanded by remember { mutableStateOf(false) }
    var goalName by remember { mutableStateOf("") }
    var goalTarget by remember { mutableStateOf("") }
    var goalSaved by remember { mutableStateOf("") }
    var recurringName by remember { mutableStateOf("") }
    var recurringAmount by remember { mutableStateOf("") }
    var transactionFilter by remember { mutableStateOf("All") }
    var counterpartyFilter by remember { mutableStateOf("All types") }
    var categoryFilter by remember { mutableStateOf("All categories") }
    var counterpartyExpanded by remember { mutableStateOf(false) }
    var categoryFilterExpanded by remember { mutableStateOf(false) }
    val counterpartyPrefs=remember { context.getSharedPreferences("transaction_counterparty",android.content.Context.MODE_PRIVATE) }
    var counterpartyRevision by remember { mutableIntStateOf(0) }
    var counterpartyEditExpanded by remember { mutableStateOf(false) }
    var showTransactionForm by remember { mutableStateOf(false) }
    var filterExpanded by remember { mutableStateOf(false) }
    var showAdvancedFilters by remember { mutableStateOf(false) }
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    val balancePrefs=remember { context.getSharedPreferences("balance_anchor",android.content.Context.MODE_PRIVATE) }
    var dashboardView by remember { mutableStateOf("Cash flow") }
    val dashboardPrefs=remember { context.getSharedPreferences("dashboard_layout",android.content.Context.MODE_PRIVATE) }
    var showAccountCards by remember { mutableStateOf(dashboardPrefs.getBoolean("accounts",true)) }
    var showSpendingInsights by remember { mutableStateOf(dashboardPrefs.getBoolean("insights",true)) }
    var showDashboardOptions by remember { mutableStateOf(false) }
    var balanceDate by remember { mutableStateOf(balancePrefs.getString("mib_date","") ?: "") }
    var balanceAmount by remember { mutableStateOf(balancePrefs.getString("mib_amount","") ?: "") }
    var savedBalanceDate by remember { mutableStateOf(balancePrefs.getString("mib_date","") ?: "") }
    var savedBalanceMinor by remember { mutableStateOf(balancePrefs.getLong("mib_minor",Long.MIN_VALUE)) }
    var balanceSource by remember { mutableStateOf(balancePrefs.getString("mib_source","Manual") ?: "Manual") }
    var accountChoice by remember { mutableStateOf("MIB") }
    var otherBalanceAmount by remember { mutableStateOf("") }
    var otherBalanceDate by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var screenshotPending by remember { mutableStateOf(false) }
    var screenshotExtract by remember { mutableStateOf("") }
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
    var input by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var income by remember { mutableStateOf(false) }
    var receiptPreview by remember { mutableStateOf("") }
    var receiptPending by remember { mutableStateOf(false) }
    val receiptLauncher=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri!=null) try {
            val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(InputImage.fromFilePath(context,uri)).addOnSuccessListener { result ->
                receiptPreview=result.text.take(1200)
                val lines=result.text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                val totals=lines.filter { it.contains(Regex("(?i)total|amount due|grand total")) }
                val pattern=Regex("""(?:MVR|RF)?\s*([0-9][0-9,]*\.[0-9]{2})""")
                val detected=totals.firstNotNullOfOrNull { pattern.find(it)?.groupValues?.get(1) }
                amount=detected?.replace(",","") ?: ""
                input=lines.firstOrNull { !it.contains(Regex("(?i)receipt|invoice|total")) }?.take(80) ?: ""
                income=false
                receiptPending=true
                showTransactionForm=true
                tab=1
                notice="Receipt scanned. Check amount, merchant and category before adding; nothing imported automatically."
            }.addOnFailureListener { notice="Receipt OCR failed: ${it.message?.take(80)}" }
                .addOnCompleteListener { recognizer.close() }
        } catch(e:Exception) { notice="Cannot open receipt: ${e.message?.take(70)}" }
    }
    val pdfLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if(uri!=null) try {
            context.contentResolver.openOutputStream(uri)?.use { BudgetPdfReport.write(it,selectedMonth,transactions,budgets) }
                ?: error("Could not open PDF destination")
            notice="Monthly PDF report saved"
        } catch(e:Exception) { notice="PDF export failed: ${e.message?.take(90)}" }
    }
    val screenshotLauncher=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri!=null) {
            try {
                val image=InputImage.fromFilePath(context,uri)
                val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image).addOnSuccessListener { result ->
                    screenshotExtract=result.text.take(2000)
                    // Read the account-card region only; ML Kit often splits "MVR" and
                    // the numeric amount into separate text lines or blocks.
                    val lines=result.text.lines().map { it.trim() }.filter { it.isNotBlank() }
                    val transactionsAt=lines.indexOfFirst {
                        it.equals("Transactions",true) || it.equals("Transaction History",true)
                    }
                    val header=if(transactionsAt>=0) lines.take(transactionsAt) else lines.take(18)
                    val accountAt=header.indexOfFirst { it.contains("Account Details",true) }
                    val card=if(accountAt>=0) header.take(accountAt) else header
                    val currencyAmount=Regex("""(?i)\b(?:MVR|MRF|RF)\s*[:.]?\s*([0-9][0-9,]*\.[0-9]{2})\b""")
                    val standaloneAmount=Regex("""^\s*([0-9][0-9,]*\.[0-9]{2})\s*$""")
                    val currencyOnly=Regex("""(?i)^(?:MVR|MRF|RF)\s*[:.]?$""")
                    var recognized:String?=null
                    for(i in card.indices) {
                        val line=card[i]
                        val inline=currencyAmount.find(line)?.groupValues?.get(1)
                        if(inline!=null) { recognized=inline; break }
                        if(currencyOnly.matches(line)) {
                            val next=card.drop(i+1).take(3).firstNotNullOfOrNull {
                                standaloneAmount.matchEntire(it)?.groupValues?.get(1)
                            }
                            if(next!=null) { recognized=next; break }
                        }
                    }
                    // Some screenshots show only a numeric balance inside the account card.
                    if(recognized==null && accountAt>=0) {
                        recognized=card.take(7).firstNotNullOfOrNull {
                            standaloneAmount.matchEntire(it)?.groupValues?.get(1)
                        }
                    }
                    val amountCandidate=recognized?.replace(",","")
                    val valid=amountCandidate?.let { Money.parseLaari(it) }
                    screenshotPending=valid!=null
                    if(valid!=null) {
                        balanceAmount=amountCandidate!!
                        balanceDate=java.time.LocalDate.now().toString()
                        notice="Detected MVR $balanceAmount. Review the amount and screenshot date, then tap Save verified balance."
                    } else {
                        balanceAmount=""
                        notice="No safe balance match. Type the account balance below and save it manually. OCR preview is shown below."
                    }
                }.addOnFailureListener { notice="Could not read screenshot: ${it.message?.take(70)}" }
                    .addOnCompleteListener { recognizer.close() }
            } catch(e:Exception) { notice="Screenshot failed: ${e.message?.take(80)}" }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) try {
            val data=context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Cannot read CSV")
            csvReview=store.reviewCsv(data)
            csvText=data
            store.statementBalance(data)?.let { closing ->
                val currentDate=savedBalanceDate
                if(currentDate.isBlank() || closing.date>currentDate || (closing.date==currentDate && balanceSource=="MIB CSV statement")) {
                    savedBalanceDate=closing.date
                    savedBalanceMinor=closing.amountLaari
                    balanceDate=closing.date
                    balanceAmount=String.format(java.util.Locale.US,"%.2f",closing.amountLaari/100.0)
                    balanceSource="MIB CSV statement"
                    balancePrefs.edit().putString("mib_date",closing.date)
                        .putString("mib_amount",balanceAmount).putLong("mib_minor",closing.amountLaari)
                        .putString("mib_source",balanceSource).apply()
                }
            }
            notice="Statement checked. No transactions were imported. Review the results below."
        } catch(e:Exception) { csvReview=null;csvText=null;notice="CSV comparison failed: ${e.message?.take(100)}" }
    }
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
    var category by remember { mutableStateOf("Food") }
    var expanded by remember { mutableStateOf(false) }
    var importCategory by remember { mutableStateOf("Other") }
    var importExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(shared) { if(shared.isNotBlank()) { input = shared; tab = 2 } }
    // Refresh saved transactions when app returns to foreground after SMS broadcasts.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { revision++; permissionRefresh++ }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if(lockEnabled && !unlocked) {
        Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center) {
            Text("MIB Budget Tracker is locked",style=MaterialTheme.typography.headlineSmall)
            Button(onClick={authenticate}) { Text("Unlock with biometrics") }
            if(lockError.isNotBlank()) Text(lockError)
        }
        return
    }
    Scaffold(floatingActionButton = {
        if(tab==1) FloatingActionButton(onClick={showTransactionForm=true}) { Text("+",style=MaterialTheme.typography.headlineMedium) }
    }, bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            listOf("Dashboard","Transactions","Import & Access","Budgets").forEachIndexed { i, label ->
                NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Text(listOf("⌂","⇄","⚙","◉")[i])},label={Text(label)})
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background).then(if (tab == 0 || tab == 1 || tab == 2) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if(tab==0) {
            Text("MIB Budget Tracker",style=MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text("Your money, clearly organized  •  MVR",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/modealpha88-ctrl/MIBBudgetTracker/releases/latest")))},modifier=Modifier.fillMaxWidth()) { Text("Update App") }
            }
            if(notice.isNotBlank()) Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer), modifier=Modifier.fillMaxWidth()) { Text(notice, Modifier.padding(14.dp), color=MaterialTheme.colorScheme.onPrimaryContainer) }
            if (tab == 2) {
                OutlinedButton(onClick={showImportSettings = !showImportSettings}, modifier=Modifier.fillMaxWidth()) { Text(if(showImportSettings) "Hide bank import settings  ↑" else "Bank SMS & notification settings  ↓") }
                if (showImportSettings) {
                Text("Permissions & Android restricted settings",style=MaterialTheme.typography.titleMedium)
                Text("If Android says Restricted setting, open App info, tap the three-dot menu, and choose Allow restricted settings if available. This is controlled by Android, not the app. Only enable access if you trust this APK.",style=MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick=openAppSettings) { Text("Open app info & permissions") }
                val smsReceiveGranted = remember(permissionRefresh) { context.checkSelfPermission(Manifest.permission.RECEIVE_SMS)==PackageManager.PERMISSION_GRANTED }
                val smsHistoryGranted = remember(permissionRefresh) { context.checkSelfPermission(Manifest.permission.READ_SMS)==PackageManager.PERMISSION_GRANTED }
                val listenerGranted = remember(permissionRefresh) {
                    val enabled=Settings.Secure.getString(context.contentResolver,"enabled_notification_listeners").orEmpty()
                    enabled.split(":").any { it.substringBefore("/")==context.packageName }
                }
                Card(modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text("Import access status",style=MaterialTheme.typography.titleMedium)
                        Text("Incoming SMS: "+if(smsReceiveGranted) "Allowed" else "Not allowed")
                        Text("SMS history: "+if(smsHistoryGranted) "Allowed" else "Not allowed")
                        Text("Notification listener: "+if(listenerGranted) "Enabled" else "Not enabled")
                        Text("Android may restrict SMS and notification access for sideloaded apps. Permissions must be approved in Android Settings.",style=MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick={permissionRefresh++}) { Text("Refresh access status") }
                    }
                }
                val canPostNotifications = android.os.Build.VERSION.SDK_INT<33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
                OutlinedButton(onClick={showAccessHelp=!showAccessHelp},modifier=Modifier.fillMaxWidth()) {
                    Text(if(showAccessHelp) "Hide access setup guide" else "Set up SMS & notification access")
                }
                if(showAccessHelp) {
                    Card(modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text("OnePlus / Android access setup",style=MaterialTheme.typography.titleMedium)
                            Text("1. Open App info. If Android marks this APK as restricted, use the three-dot menu → Allow restricted settings, when available.")
                            OutlinedButton(onClick=openAppSettings) { Text("Open app info") }
                            Text("2. Allow SMS permission for automatic imports. Historical inbox imports also need Read SMS.")
                            OutlinedButton(onClick={permissionLauncher.launch(Manifest.permission.RECEIVE_SMS)}) { Text("Request incoming SMS access") }
                            OutlinedButton(onClick={historyLauncher.launch(Manifest.permission.READ_SMS)}) { Text("Request SMS history access") }
                            Text("3. Enable Notification access to read supported BML alerts. This is separate from the ordinary notification toggle.")
                            OutlinedButton(onClick={context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))}) { Text("Open notification access") }
                            Text("4. Optional: enable app notifications for future reminders. This is not needed to read BML notifications.")
                            if(android.os.Build.VERSION.SDK_INT>=33 && !canPostNotifications) {
                                OutlinedButton(onClick={notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)}) { Text("Request app notification permission") }
                            } else Text(if(canPostNotifications) "App notification permission available." else "Notification permission unavailable.")
                            Text("Android controls restricted settings. The app cannot override them. Never uninstall to fix permissions without first exporting a backup.",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
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
                Button(onClick={ context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Manage notification access") }
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
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Overview",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                        TextButton(onClick={showDashboardOptions=!showDashboardOptions}) { Text("Customize") }
                    }
                    if(showDashboardOptions) {
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                Text("Account cards",Modifier.weight(1f))
                                Switch(checked=showAccountCards,onCheckedChange={showAccountCards=it;dashboardPrefs.edit().putBoolean("accounts",it).apply()})
                            }
                            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                Text("Spending insights",Modifier.weight(1f))
                                Switch(checked=showSpendingInsights,onCheckedChange={showSpendingInsights=it;dashboardPrefs.edit().putBoolean("insights",it).apply()})
                            }
                        } }
                    }
                    if(showAccountCards) {
                        Text("Account snapshots",style=MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            listOf("MIB","BML","Cash").forEach { bank ->
                                val key=bank.lowercase(java.util.Locale.ROOT)
                                val minor=balancePrefs.getLong("${key}_minor",Long.MIN_VALUE)
                                Card(onClick={dashboardView="Account balance";accountChoice=bank},modifier=Modifier.weight(1f)) {
                                    Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                        Text(bank,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                                        Text(if(minor==Long.MIN_VALUE) "Not set" else Money.format(minor),style=MaterialTheme.typography.bodySmall,maxLines=2)
                                        Text("Snapshot",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    if(showSpendingInsights) {
                        val monthExpenses=monthlyTransactions.filter { it.amountLaari<0 && it.category!="Internal Transfer" }
                        val monthIncome=monthlyTransactions.filter { it.amountLaari>0 && it.category!="Internal Transfer" }.sumOf { it.amountLaari }
                        val monthOut=monthExpenses.sumOf { -it.amountLaari }
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("Monthly insights",style=MaterialTheme.typography.titleMedium)
                            Text("Income: ${Money.format(monthIncome)}")
                            Text("Expenses: ${Money.format(monthOut)}")
                            LinearProgressIndicator(progress={if(monthIncome>0) (monthOut.toFloat()/monthIncome).coerceIn(0f,1f) else 0f},modifier=Modifier.fillMaxWidth())
                            Text(if(monthIncome>0) "${monthOut*100/monthIncome}% of recorded income spent" else "No recorded income this month",style=MaterialTheme.typography.bodySmall)
                        } }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected=dashboardView=="Cash flow",onClick={dashboardView="Cash flow"},label={Text("Cash flow")})
                        FilterChip(selected=dashboardView=="Account balance",onClick={dashboardView="Account balance"},label={Text("Account balance")})
                    }
                    if(dashboardView=="Cash flow") {
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
                    } else {
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            listOf("MIB","BML","Cash").forEach { bank ->
                                FilterChip(selected=accountChoice==bank,onClick={
                                    accountChoice=bank
                                    otherBalanceAmount=""
                                    otherBalanceDate=java.time.LocalDate.now().toString()
                                },label={Text(bank)})
                            }
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                    Text("${accountChoice} recent activity",style=MaterialTheme.typography.titleMedium)
                                    TextButton(onClick={revision++}) { Text("Refresh") }
                                }
                                Text("Recorded app transactions, not a live bank feed. Only assigned ${accountChoice} transactions are included.",style=MaterialTheme.typography.bodySmall)
                                val accountActivity=transactions.filter { accountLabels[it.fingerprint]==accountChoice }.sortedByDescending { it.timestamp }
                                if(accountActivity.isEmpty()) Text("No assigned transactions yet. Assign accounts in Transactions.")
                                accountActivity.take(15).forEach { tx ->
                                    ListItem(headlineContent={Text(tx.description.take(50),maxLines=1)},
                                        supportingContent={Text("${tx.timestamp.take(16)} • ${tx.category}")},
                                        trailingContent={Text(Money.format(tx.amountLaari),color=if(tx.amountLaari>=0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)})
                                }
                                if(accountActivity.size>15) Text("Showing 15 of ${accountActivity.size}. Open Transactions for the complete history.",style=MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick={tab=1;accountFilter=accountChoice},modifier=Modifier.fillMaxWidth()) { Text("View all ${accountChoice} transactions") }
                            }
                        }
                        if(accountChoice=="MIB") {
                        Text("MIB account balance",style=MaterialTheme.typography.titleMedium)
                        Text("Upload an MIB CSV in Transactions to automatically use its closing running balance. You can also enter a manual balance below.",style=MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick={screenshotLauncher.launch("image/*")}) { Text("Use MIB bank screenshot") }
                        if(screenshotExtract.isNotBlank()) {
                            Text(if(screenshotPending) "Screenshot balance found; confirm before saving." else "Screenshot scanned, but balance not identified. Enter the amount manually.",style=MaterialTheme.typography.bodySmall)
                            Text("Recognized text: ${screenshotExtract.take(300)}",style=MaterialTheme.typography.bodySmall)
                        }
                        OutlinedTextField(value=balanceDate,onValueChange={balanceDate=it},label={Text("Balance date (YYYY-MM-DD)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                        OutlinedTextField(value=balanceAmount,onValueChange={balanceAmount=it},label={Text("Verified balance (MVR)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                        Button(onClick={
                            val date=runCatching { java.time.LocalDate.parse(balanceDate) }.getOrNull()
                            val amount=Money.parseLaari(balanceAmount)
                            if(date==null || date>java.time.LocalDate.now() || amount==null) notice="Enter a valid date and non-negative MVR balance"
                            else {
                                savedBalanceDate=date.toString()
                                savedBalanceMinor=amount
                                balanceSource=if(screenshotPending) "MIB screenshot (user confirmed)" else "Manual"
                                screenshotPending=false
                                balancePrefs.edit().putString("mib_date",savedBalanceDate).putString("mib_amount",balanceAmount).putLong("mib_minor",amount).putString("mib_source",balanceSource).apply()
                                notice="MIB balance reference saved"
                            }
                        }) { Text("Save verified balance") }
                        if(savedBalanceMinor!=Long.MIN_VALUE && savedBalanceDate.isNotBlank()) {
                            val later=transactions.filter { it.timestamp.take(10)>savedBalanceDate && accountLabels[it.fingerprint]=="MIB" && it.category!="Internal Transfer" }
                            val unlabeled=transactions.count { it.timestamp.take(10)>savedBalanceDate && accountLabels[it.fingerprint].isNullOrBlank() }
                            val estimated=savedBalanceMinor+later.sumOf { it.amountLaari }
                            Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primary)) {
                                Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Text("MIB ESTIMATED BALANCE",color=Color.White)
                                    Text(Money.format(if(balanceSource.startsWith("MIB screenshot")) savedBalanceMinor else estimated),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold,color=Color.White)
                                    Text("${balanceSource} at ${savedBalanceDate}: ${Money.format(savedBalanceMinor)}",color=Color.White)
                                    Text("MIB transactions after reference: ${later.size}",color=Color.White)
                                    if(balanceSource.startsWith("MIB screenshot") && later.isNotEmpty()) Text("Estimated after recorded transactions: ${Money.format(estimated)}",color=Color.White)
                                    Text(if(balanceSource.startsWith("MIB screenshot")) "Latest user-confirmed screenshot balance (snapshot, not live). Later transactions are shown separately." else "Estimated from reference balance and later labeled MIB transactions; not a live bank balance.",color=Color.White)
                                }
                            }
                            Text("Transactions affecting estimated balance",style=MaterialTheme.typography.titleMedium)
                            if(later.isEmpty()) Text("No recorded MIB transactions after the balance reference.")
                            later.sortedByDescending { it.timestamp }.take(50).forEach { tx ->
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(tx.description.take(75))
                                        Text(tx.timestamp.take(16),style=MaterialTheme.typography.bodySmall)
                                    }
                                    Text(Money.format(tx.amountLaari))
                                }
                                HorizontalDivider()
                            }
                            if(later.size>50) Text("Showing latest 50 of ${later.size} transactions")
                            if(unlabeled>0) Text("Warning: ${unlabeled} unassigned transactions after the reference date were excluded. Label accounts to improve accuracy.")
                        } else Text("Balance unavailable until you enter a verified statement balance.")
                        } else {
                            val key=accountChoice.lowercase(java.util.Locale.ROOT)
                            val storedMinor=balancePrefs.getLong("${key}_minor",Long.MIN_VALUE)
                            val storedDate=balancePrefs.getString("${key}_date","").orEmpty()
                            Text("${accountChoice} balance",style=MaterialTheme.typography.titleMedium)
                            Text("Manual verified balance for this account. This is a snapshot, not live banking data.")
                            OutlinedTextField(value=otherBalanceDate,onValueChange={otherBalanceDate=it},label={Text("Balance date (YYYY-MM-DD)")},modifier=Modifier.fillMaxWidth())
                            OutlinedTextField(value=otherBalanceAmount,onValueChange={otherBalanceAmount=it},label={Text("Balance (MVR)")},modifier=Modifier.fillMaxWidth())
                            Button(onClick={
                                val date=runCatching { java.time.LocalDate.parse(otherBalanceDate) }.getOrNull()
                                val value=Money.parseLaari(otherBalanceAmount)
                                if(date==null || date>java.time.LocalDate.now() || value==null) notice="Enter a valid date and amount"
                                else {
                                    balancePrefs.edit().putLong("${key}_minor",value).putString("${key}_date",date.toString()).apply()
                                    notice="${accountChoice} verified balance saved"
                                }
                            }) { Text("Save ${accountChoice} balance") }
                            if(storedMinor!=Long.MIN_VALUE) {
                                Text("Verified ${accountChoice} balance: ${Money.format(storedMinor)}",style=MaterialTheme.typography.headlineSmall)
                                Text("Reference date: $storedDate")
                                val related=transactions.filter { accountLabels[it.fingerprint]==accountChoice && it.timestamp.take(10)>storedDate }
                                Text("Recorded transactions after reference: ${related.size}")
                                Text("Shown balance is the verified snapshot; later transactions are not silently added.")
                            }
                        }
                    }
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
                    OutlinedButton(onClick={pdfLauncher.launch("MIB-Report-${selectedMonth}.pdf")},modifier=Modifier.fillMaxWidth()) { Text("Export monthly PDF report") }
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
                        Text("Biometric app lock",modifier=Modifier.weight(1f))
                        Switch(checked=lockEnabled,onCheckedChange={ enabled ->
                            if(!enabled || android.os.Build.VERSION.SDK_INT>=28) {
                                lockEnabled=enabled
                                securityPrefs.edit().putBoolean("biometric_lock",enabled).apply()
                                unlocked=true
                            } else notice="Biometric lock requires Android 9 or later"
                        })
                    }
                    Text("Uses Android biometric enrollment. Keep a device backup before enabling.",style=MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Dark theme",modifier=Modifier.weight(1f))
                        Switch(checked=darkTheme,onCheckedChange=setDarkTheme)
                    }
                }
                1 -> {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Column {
                            Text("Transactions",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                            Text("${transactions.size} transactions",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick={tab=2}) { Text("Import") }
                    }
                    OutlinedTextField(value=searchText,onValueChange={searchText=it},
                        placeholder={Text("Search merchant, amount or date")},
                        leadingIcon={Text("⌕")},modifier=Modifier.fillMaxWidth(),singleLine=true,
                        shape=RoundedCornerShape(20.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        listOf("All","Expenses","Income","Transfers").forEach { option ->
                            FilterChip(selected=transactionFilter==option,onClick={transactionFilter=option},
                                label={Text(option,style=MaterialTheme.typography.labelSmall)})
                        }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Activity",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                        TextButton(onClick={showAdvancedFilters=!showAdvancedFilters}) {
                            Text(if(showAdvancedFilters) "Hide filters ↑" else "More filters ↓")
                        }
                    }
                    if(showAdvancedFilters) {
                        Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)) {
                            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Box {
                                        OutlinedButton(onClick={accountExpanded=true}) { Text(accountFilter) }
                                        DropdownMenu(expanded=accountExpanded,onDismissRequest={accountExpanded=false}) {
                                            listOf("All accounts","Unassigned","MIB","BML","Cash","Other").forEach { label ->
                                                DropdownMenuItem(text={Text(label)},onClick={accountFilter=label;accountExpanded=false})
                                            }
                                        }
                                    }
                                    Box {
                                        OutlinedButton(onClick={counterpartyExpanded=true}) { Text(counterpartyFilter) }
                                        DropdownMenu(expanded=counterpartyExpanded,onDismissRequest={counterpartyExpanded=false}) {
                                            listOf("All types","Business","Individual","Unclassified").forEach { option ->
                                                DropdownMenuItem(text={Text(option)},onClick={counterpartyFilter=option;counterpartyExpanded=false})
                                            }
                                        }
                                    }
                                }
                                Box {
                                    OutlinedButton(onClick={categoryFilterExpanded=true}) { Text(categoryFilter) }
                                    DropdownMenu(expanded=categoryFilterExpanded,onDismissRequest={categoryFilterExpanded=false}) {
                                        (listOf("All categories")+categories).forEach { option ->
                                            DropdownMenuItem(text={Text(option)},onClick={categoryFilter=option;categoryFilterExpanded=false})
                                        }
                                    }
                                }
                                Text("Classify a business or individual from transaction details.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick={accountFilter="All accounts";counterpartyFilter="All types";categoryFilter="All categories";transactionFilter="All";searchText=""}) { Text("Clear all filters") }
                            }
                        }
                    }
                    OutlinedButton(onClick={showTransactionTools=!showTransactionTools},modifier=Modifier.fillMaxWidth()) {
                        Text(if(showTransactionTools) "Hide import & backup tools" else "Import & backup tools")
                    }
                    if(showTransactionTools) {
                        Card(modifier=Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("Data tools",style=MaterialTheme.typography.titleMedium)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick={backupLauncher.launch("mib-budget-backup.json")}) { Text("Export backup") }
                                    OutlinedButton(onClick={restoreLauncher.launch(arrayOf("application/json","text/plain"))}) { Text("Restore") }
                                }
                                OutlinedButton(onClick={csvLauncher.launch(arrayOf("text/*","application/csv","application/octet-stream"))}) { Text("Cross-check CSV vs SMS") }
                                OutlinedButton(onClick={receiptLauncher.launch("image/*")}) { Text("Scan receipt") }
                            }
                        }
                    }
                    if(confirmMissingImport) {
                        AlertDialog(
                            onDismissRequest={confirmMissingImport=false},
                            title={Text("Import missing transactions?")},
                            text={Text("Only missing records will be added. Possible matches are excluded. Make a backup first.")},
                            confirmButton={TextButton(onClick={
                                confirmMissingImport=false
                                try {
                                    val data=csvText ?: error("CSV not available")
                                    val result=store.importMissingCsv(data)
                                    revision++
                                    csvReview=store.reviewCsv(data)
                                    notice="Added ${result.first} missing transactions; excluded ${result.second} matched or uncertain records."
                                } catch(e:Exception) { notice="Import failed: ${e.message?.take(100)}" }
                            }) { Text("Import missing") }},
                            dismissButton={TextButton(onClick={confirmMissingImport=false}) { Text("Cancel") }}
                        )
                    }
                    csvReview?.let { review ->
                        Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("Statement cross-check",color=MaterialTheme.colorScheme.onSurface,style=MaterialTheme.typography.titleMedium)
                                Text("Matched: ${review.confirmed}  •  Possible: ${review.possible}  •  Missing: ${review.missing}  •  Ignored: ${review.ignored}",color=Color.White)
                                Text("Read-only preview. No CSV transactions have been added. Possible matches need manual review.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                                review.matches.filter { it.status!="MATCHED" }.take(100).forEach { item ->
                                    HorizontalDivider()
                                    Text("${item.status}  •  ${item.date}  •  ${Money.format(item.amountLaari)}",color=if(item.status=="POSSIBLE") Color(0xFFFFD166) else Color(0xFF8FE4C0))
                                    Text(item.description,color=Color.White)
                                    Text(item.reason,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
                                }
                                if(review.matches.count { it.status!="MATCHED" }>100) Text("Showing first 100 unmatched/possible records",color=Color.White)
                                if(review.missing>0 && csvText!=null) {
                                    Button(onClick={confirmMissingImport=true}) { Text("Add all ${review.missing} missing transactions") }
                                }
                                OutlinedButton(onClick={csvReview=null;csvText=null}) { Text("Close comparison") }
                            }
                        }
                    }
                    val filteredTransactions = transactions.filter { t ->
                        (searchText.isBlank() || t.description.contains(searchText,true) || t.category.contains(searchText,true) || t.timestamp.contains(searchText) || Money.format(t.amountLaari).contains(searchText,true)) &&
                        (accountFilter=="All accounts" || (accountLabels[t.fingerprint] ?: "Unassigned")==accountFilter) &&
                        (categoryFilter=="All categories" || t.category==categoryFilter || (categoryFilter=="Fast Food" && t.description.contains("fast food",true))) &&
                        (counterpartyFilter=="All types" || (counterpartyPrefs.getString(t.fingerprint,"Unclassified") ?: "Unclassified")==counterpartyFilter) &&
                        when(transactionFilter) {
                            "Expenses" -> t.amountLaari < 0
                            "Income" -> t.amountLaari > 0
                            "Transfers" -> t.category.contains("Transfer",ignoreCase=true)
                            else -> true
                        }
                    }
                    if(filteredTransactions.isEmpty()) Text("No transactions match this filter.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    var lastTransactionDay = ""
                    filteredTransactions.take(150).forEach { t ->
                        val day=t.timestamp.take(10)
                        if(day!=lastTransactionDay) {
                            Text(day,style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(top=12.dp,bottom=4.dp))
                            lastTransactionDay=day
                        }
                        val transferDescription=t.description
                        val transferRecipient=if(transferDescription.startsWith("Internet banking transfer:",ignoreCase=true)) {
                            val fields=transferDescription.split(" | ")
                            if(fields.size>=2) fields.last().trim().takeIf { it.length>=3 && it.any(Char::isLetter) } else null
                        } else null
                        val merchant=if(transferDescription.startsWith("Internet banking transfer:",ignoreCase=true)) {
                            "Transfer" + (transferRecipient?.let { " · " + it.take(35) } ?: "")
                        } else t.description.substringAfter("@",t.description).substringBefore(" |").substringBefore(" VISA").substringBefore(" MASTERCARD").trim().ifBlank { t.description }.take(34)
                        Card(onClick={selectedTransactionId=t.id},modifier=Modifier.fillMaxWidth().padding(vertical=2.dp),shape=RoundedCornerShape(16.dp),
                            colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.secondaryContainer,RoundedCornerShape(14.dp)),contentAlignment=androidx.compose.ui.Alignment.Center) {
                                    Text(when(t.category) { "Food","Fast Food" -> "☕"; "Groceries" -> "▦"; "Transport" -> "↗"; "Shopping" -> "▤"; "Bills" -> "▥"; else -> if(t.amountLaari>=0) "↓" else "↑" },color=MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                    Text(merchant,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold,maxLines=1)
                                    val storedTime=t.timestamp.substringAfter("T",t.timestamp.substringAfter(" ","")).take(5)
                                    val sourceTime=Regex("""\\b\\d{2}-\\d{2}-\\d{4}\\s+(\\d{2})-(\\d{2})-\\d{2}\\b""").find(t.description)
                                    val bankTime=sourceTime?.let { match ->
                                        val hour=match.groupValues[1].toIntOrNull()
                                        val minute=match.groupValues[2].toIntOrNull()
                                        if(hour!=null && hour in 0..23 && minute!=null && minute in 0..59) "%02d:%02d".format(hour,minute) else null
                                    }
                                    val displayTime=bankTime ?: storedTime.takeIf { it.matches(Regex("""\\d{2}:\\d{2}""")) && it!="00:00" }
                                    Text(displayTime ?: "Time not recorded",
                                        style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
                                }
                                Text((if(t.amountLaari>0) "+" else "")+Money.format(t.amountLaari),
                                    style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold,
                                    color=if(t.amountLaari>=0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                    if(filteredTransactions.size>150) Text("Showing first 150 of ${filteredTransactions.size} results. Narrow your search to see more.",style=MaterialTheme.typography.bodySmall)
                    val selectedTransaction=transactions.firstOrNull { it.id==selectedTransactionId }
                    if(selectedTransaction!=null) {
                        ModalBottomSheet(onDismissRequest={selectedTransactionId=null}) {
                            Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                Text("Transaction details",style=MaterialTheme.typography.headlineSmall)
                                Text(selectedTransaction.description)
                                Text(Money.format(selectedTransaction.amountLaari),style=MaterialTheme.typography.headlineMedium)
                                Text("${selectedTransaction.category} • ${selectedTransaction.timestamp}")
                                Text("Account: ${accountLabels[selectedTransaction.fingerprint] ?: "Unassigned"}")
                                Text("Counterparty: ${counterpartyPrefs.getString(selectedTransaction.fingerprint,"Unclassified")}",style=MaterialTheme.typography.bodyMedium)
                                Box {
                                    OutlinedButton(onClick={counterpartyEditExpanded=true}) { Text("Classify business / individual") }
                                    DropdownMenu(expanded=counterpartyEditExpanded,onDismissRequest={counterpartyEditExpanded=false}) {
                                        listOf("Business","Individual","Unclassified").forEach { kind ->
                                            DropdownMenuItem(text={Text(kind)},onClick={
                                                counterpartyPrefs.edit().putString(selectedTransaction.fingerprint,kind).apply()
                                                counterpartyRevision++
                                                counterpartyEditExpanded=false
                                            })
                                        }
                                    }
                                }

                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick={
                                        store.setAccount(selectedTransaction.fingerprint,if((accountLabels[selectedTransaction.fingerprint]?:"Unassigned")=="MIB") "BML" else "MIB")
                                        revision++
                                    }) { Text("Change account") }
                                    OutlinedButton(onClick={
                                        editingId=selectedTransaction.id
                                        editingAmount=Money.format(kotlin.math.abs(selectedTransaction.amountLaari)).removePrefix("MVR ").replace(",","")
                                        editingDescription=selectedTransaction.description
                                        editingCategory=selectedTransaction.category
                                        selectedTransactionId=null
                                    }) { Text("Edit") }
                                }
                                if(selectedTransaction.category.contains("Transfer",true))
                                    OutlinedButton(onClick={store.markInternal(selectedTransaction.id);revision++;selectedTransactionId=null}) { Text("Mark internal transfer") }
                                TextButton(onClick={deletingId=selectedTransaction.id;selectedTransactionId=null}) { Text("Delete transaction",color=MaterialTheme.colorScheme.error) }
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
                                Box {
                                    OutlinedButton(onClick={editingCategoryExpanded=true}) { Text("Category: ${editingCategory}") }
                                    DropdownMenu(expanded=editingCategoryExpanded,onDismissRequest={editingCategoryExpanded=false}) {
                                        categories.forEach { cat ->
                                            DropdownMenuItem(text={Text(cat)},onClick={editingCategory=cat;editingCategoryExpanded=false})
                                        }
                                    }
                                }
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

                    if(receiptPending) Text("Receipt OCR preview: ${receiptPreview.take(400)}",color=Color.White)
                    OutlinedButton(onClick={showTransactionForm=!showTransactionForm},modifier=Modifier.fillMaxWidth()) {
                        Text(if(showTransactionForm) "Hide add transaction" else "+ Add transaction")
                    }
                    if(showTransactionForm) {
                        Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer),modifier=Modifier.fillMaxWidth()) {
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
                            else { store.add(if(income) v else -v,if(income) "Income" else if(category=="Income") "Other" else category,input.trim()); amount="";input="";receiptPending=false;notice="Transaction saved";revision++ }
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
                    Text("Import & Access",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
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
                    Column {
                        categories.filter { it!="Income" }.forEach { cat ->
                            var limitText by remember(cat,revision) { mutableStateOf(budgets[cat]?.let { "${it/100}.${(it%100).toString().padStart(2,'0')}" } ?: "") }
                            val spent=monthlyTransactions.filter { it.category==cat && it.amountLaari<0 }.sumOf { -it.amountLaari }
                            val limit=budgets[cat] ?: 0L
                            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                Box(contentAlignment=androidx.compose.ui.Alignment.Center,modifier=Modifier.size(56.dp)) {
                                    CircularProgressIndicator(progress={if(limit>0) (spent.toFloat()/limit).coerceIn(0f,1f) else 0f},modifier=Modifier.fillMaxSize(),strokeWidth=6.dp)
                                    Text(if(limit>0) "${spent*100/limit}%" else "—",style=MaterialTheme.typography.labelSmall)
                                }
                                Column {
                                    Text(cat,style=MaterialTheme.typography.titleMedium)
                                    Text("${Money.format(spent)} spent"+if(limit>0) " / ${Money.format(limit)}" else " • No limit",style=MaterialTheme.typography.bodySmall)
                                }
                            }
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
