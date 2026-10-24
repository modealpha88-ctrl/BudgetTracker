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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Event
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
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

class MainActivity : FragmentActivity() {
    private lateinit var store: TransactionStore
    private var sharedText by mutableStateOf("")
    private var darkTheme by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TransactionStore(this)
        darkTheme = getSharedPreferences("appearance", MODE_PRIVATE).getBoolean("dark",true)
        sharedText = intent?.takeIf { it.action == Intent.ACTION_SEND && it.type == "text/plain" }?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        setContent { MaterialTheme(colorScheme = (if(darkTheme) darkColorScheme(primary=Color(0xFFB5A0FF),secondary=Color(0xFF7DD3FC),background=Color(0xFF050507),surface=Color(0xFF101014),surfaceVariant=Color(0xFF202027),surfaceContainer=Color(0xFF14141A),surfaceContainerLow=Color(0xFF0D0D12),surfaceContainerHigh=Color(0xFF23232C),primaryContainer=Color(0xFF32234E),onPrimaryContainer=Color(0xFFEDE4FF)) else lightColorScheme(primary = Color(0xFF2563EB), secondary = Color(0xFF0D9488), background = Color(0xFFF6F8FC), surface = Color.White, surfaceVariant = Color(0xFFEAF0FA))), shapes = Shapes(medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp))) { BudgetApp(store, sharedText, darkTheme, { darkTheme = it; getSharedPreferences("appearance", MODE_PRIVATE).edit().putBoolean("dark",it).apply() }) { sharedText = "" } } }
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
    // Startup remains unlocked; do not silently overwrite the user's saved lock preference.
    var screenshotPrivacy by remember { mutableStateOf(securityPrefs.getBoolean("screenshot_privacy",false)) }
    DisposableEffect(screenshotPrivacy) {
        val activity=context as? android.app.Activity
        if(screenshotPrivacy) activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { }
    }

    var unlocked by remember { mutableStateOf(!securityPrefs.getBoolean("biometric_lock",false)) }
    var lockError by remember { mutableStateOf("") }
    // Unified biometric and device credential authentication.
    val activity = context as FragmentActivity
    val prompt = remember(activity) {
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked = true
                    lockError = ""
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_CANCELED) {
                        lockError = "Authentication unavailable: $errString"
                    }
                }
                override fun onAuthenticationFailed() {
                    lockError = "Not recognized. Try again or use your screen lock."
                }
            })
    }
    val credentialAllowed = BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val biometricAllowed = BiometricManager.Authenticators.BIOMETRIC_STRONG
    // Use separate supported authentication flows: biometric-only prompt and Android's
    // native keyguard confirmation for PIN/pattern/password. Mixed authenticator prompts
    // can fail on some Android versions and vendor devices.
    val credentialLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            unlocked = true
            lockError = ""
        } else {
            lockError = "Screen lock confirmation was cancelled or unsuccessful."
        }
    }
    val unlockWithDeviceCredential: () -> Unit = {
        lockError = ""
        try {
            val keyguard = context.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            if (keyguard.isDeviceSecure) {
                lockError = "Opening device screen lock…"
                val intent = keyguard.createConfirmDeviceCredentialIntent(
                    "Unlock MIB Budget Tracker", "Confirm your device screen lock")
                if (intent != null) credentialLauncher.launch(intent)
                else lockError = "Screen lock confirmation is unavailable on this device."
            } else lockError = "Set up a device PIN, pattern or password in Android Settings."
        } catch (error: Exception) {
            lockError = "Unable to open screen lock: ${error.localizedMessage ?: "unknown error"}"
        }
    }
    val authenticate: () -> Unit = {
        lockError = ""
        try {
            val biometricStatus = BiometricManager.from(context).canAuthenticate(biometricAllowed)
            if (biometricStatus == BiometricManager.BIOMETRIC_SUCCESS) {
                lockError = "Opening biometric prompt…"
                prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock MIB Budget Tracker")
                    .setSubtitle("Confirm your fingerprint")
                    .setAllowedAuthenticators(biometricAllowed)
                    .setNegativeButtonText("Cancel")
                    .build())
            } else {
                // Fingerprint unavailable: keep the PIN/pattern/password path usable.
                lockError = "Biometrics unavailable (Android status $biometricStatus). Try screen lock."
            }
        } catch (error: Exception) {
            lockError = "Biometric launch error: ${error.localizedMessage ?: "unknown error"}"
        }
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
    // Each new notice resets the timer; persistent status banners must not obscure the dashboard.
    LaunchedEffect(notice) {
        if(notice.isNotBlank()) {
            kotlinx.coroutines.delay(4500)
            notice=""
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionRefresh++
        notice=if(granted) "App notifications allowed." else "App notifications not allowed. Bank notification reading is a separate permission."
    }
    var tab by remember { mutableStateOf(1) }
    var csvReview by remember { mutableStateOf<TransactionStore.CsvReview?>(null) }
    var csvText by remember { mutableStateOf<String?>(null) }
    var confirmMissingImport by remember { mutableStateOf(false) }
    var csvReviewFilter by remember { mutableStateOf("MISSING") }
    var selectedMissingCsvLines by remember { mutableStateOf(setOf<Int>()) }
    var selectedPossibleCsvLines by remember { mutableStateOf(setOf<Int>()) }
    var confirmNamingReset by remember { mutableStateOf(false) }
    val reviewQueue=remember { ReviewQueue(context) }
    var reviewQueueRevision by remember { mutableIntStateOf(0) }
    var reviewStatusFilter by remember { mutableStateOf("PENDING") }
    val csvImportPrefs=remember { context.getSharedPreferences("csv_import_history",android.content.Context.MODE_PRIVATE) }
    var encryptedBackupPassword by remember { mutableStateOf("") }
    var useEncryptedBackup by remember { mutableStateOf(true) }
    var restoreBackupPassword by remember { mutableStateOf("") }
    var showDataReset by remember { mutableStateOf(false) }
    var resetDate by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var resetPhrase by remember { mutableStateOf("") }
    var resetBackupAcknowledged by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var rankedRecipientFilter by remember { mutableStateOf<String?>(null) }
    var rankedRecipientMonth by remember { mutableStateOf<String?>(null) }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var editingAmount by remember { mutableStateOf("") }
    var editingDescription by remember { mutableStateOf("") }
    var editingCategory by remember { mutableStateOf("Other") }
    var editingCategoryExpanded by remember { mutableStateOf(false) }
    var deletingId by remember { mutableStateOf<Long?>(null) }
    var selectedTransactionId by remember { mutableStateOf<Long?>(null) }
    var internalSelectionMode by remember { mutableStateOf(false) }
    var owedSelectionMode by remember { mutableStateOf(false) }
    var owedSelectedIds by remember { mutableStateOf(setOf<Long>()) }
    var debtPersonName by remember { mutableStateOf("") }
    var editingDebtKey by remember { mutableStateOf<String?>(null) }
    var debtRecordsRevision by remember { mutableIntStateOf(0) }
    val debtPrefs=remember { context.getSharedPreferences("debt_tracker",android.content.Context.MODE_PRIVATE) }
    var debtDirection by remember { mutableStateOf("Owed to me") }
    var transactionMinAmount by remember { mutableStateOf("") }
    var transactionMaxAmount by remember { mutableStateOf("") }


    var selectedInternalIds by remember { mutableStateOf(setOf<Long>()) }
    var categorySelectionMode by remember { mutableStateOf(false) }
    var categorySelectedIds by remember { mutableStateOf(setOf<Long>()) }
    var confirmInternalSelection by remember { mutableStateOf(false) }
    val originalInternalCategories=remember { context.getSharedPreferences("internal_transfer_original_categories",android.content.Context.MODE_PRIVATE) }

    val recipientPrefs=remember { context.getSharedPreferences("transaction_recipients",android.content.Context.MODE_PRIVATE) }
    val vendorAvatarPrefs=remember { context.getSharedPreferences("vendor_avatar_files",android.content.Context.MODE_PRIVATE) }
    var avatarRevision by remember { mutableIntStateOf(0) }
    var socialUrl by remember { mutableStateOf("") }
    var socialEvidence by remember { mutableStateOf("") }
    var showSocialMatch by remember { mutableStateOf(false) }
    var socialRevision by remember { mutableIntStateOf(0) }
    val avatarPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val chosen=store.all().firstOrNull { it.id==selectedTransactionId }
        if(uri!=null && chosen!=null) try {
            val key=VendorProfiles.key(chosen.description)
            val file=java.io.File(context.filesDir,"vendor_avatar_$key.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bitmap=BitmapFactory.decodeStream(input) ?: error("Invalid image")
                val resized=android.graphics.Bitmap.createScaledBitmap(bitmap,128,128,true)
                file.outputStream().use { output -> resized.compress(android.graphics.Bitmap.CompressFormat.JPEG,85,output) }
                if(resized!==bitmap) resized.recycle()
                bitmap.recycle()
            } ?: error("Cannot read image")
            vendorAvatarPrefs.edit().putString(key,file.absolutePath).apply()
            avatarRevision++
            notice="Vendor icon saved on this device"
        } catch(e:Exception) { notice="Icon could not be saved: ${e.message?.take(80)}" }
    }

    var recipientEdit by remember { mutableStateOf(false) }
    var recipientText by remember { mutableStateOf("") }
    var recipientRevision by remember { mutableIntStateOf(0) }
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
    var categoryDrilldownMonth by remember { mutableStateOf<YearMonth?>(null) }
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
    var insightsExpanded by remember { mutableStateOf(dashboardPrefs.getBoolean("insights_expanded",true)) }
    var selectedAccountCard by remember { mutableStateOf(dashboardPrefs.getString("account_card","MIB") ?: "MIB") }
    var showSpendingHeatmap by remember { mutableStateOf(dashboardPrefs.getBoolean("heatmap",true)) }
    var planningLayoutRevision by remember { mutableIntStateOf(0) }
    var balanceDate by remember { mutableStateOf(balancePrefs.getString("mib_date","") ?: "") }
    var balanceAmount by remember { mutableStateOf(balancePrefs.getString("mib_amount","") ?: "") }
    var savedBalanceDate by remember { mutableStateOf(balancePrefs.getString("mib_date","") ?: "") }
    var savedBalanceMinor by remember { mutableStateOf(balancePrefs.getLong("mib_minor",Long.MIN_VALUE)) }
    var balanceSource by remember { mutableStateOf(balancePrefs.getString("mib_source","Manual") ?: "Manual") }
    var accountChoice by remember { mutableStateOf("MIB") }
    var otherBalanceAmount by remember { mutableStateOf("") }
    var otherBalanceDate by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var historicalBalanceDate by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var largeMovementThreshold by remember { mutableStateOf("1000") }
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
    val transactions = store.all()
    val csvBaselineReady = remember(revision) { transactions.any { it.fingerprint.startsWith("csv-") } }
    val accountLabels = store.accounts()
    val declinedTransactions = remember(revision) { store.declined() }
    val foreignTransactions = remember(revision) { store.foreign() }
    val pendingBml = remember(revision) { store.pendingBml() }
    val monthlyTransactions = remember(revision, selectedMonth) { store.forMonth(selectedMonth) }
    val budgets = remember(revision) { store.budgets() }
    val rolloverEnabled = context.getSharedPreferences("financial_planning",android.content.Context.MODE_PRIVATE)
        .getBoolean("rollover",false)
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
    var lastPdfUri by remember { mutableStateOf<Uri?>(null) }
    val pdfLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if(uri!=null) try {
            val reportBudgets=budgets.mapValues { (category,limit) ->
                FinanceCalculations.effectiveLimit(category,selectedMonth,limit,transactions,rolloverEnabled)
            }
            context.contentResolver.openOutputStream(uri)?.use { BudgetPdfReport.write(it,selectedMonth,transactions,reportBudgets) }
                ?: error("Could not open PDF destination")
            lastPdfUri = uri
            notice="Monthly PDF report saved. Use Plan → Share last exported PDF to send it."
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
            reviewQueue.addCsv(data,csvReview!!)
            reviewQueueRevision++
            selectedMissingCsvLines=emptySet()
            selectedPossibleCsvLines=emptySet()
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
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if(uri!=null) try {
            val data=if(useEncryptedBackup) SecureBackup.encrypt(FullBackup.export(context,store),encryptedBackupPassword) else FullBackup.export(context,store)
            val backupJson = if(useEncryptedBackup) SecureBackup.decrypt(data,encryptedBackupPassword) else data
            val backupRoot = org.json.JSONObject(backupJson)
            require(backupRoot.optString("format")=="mib-budget-backup" &&
                backupRoot.optJSONArray("transactions")!=null) { "Backup verification failed" }
            context.contentResolver.openOutputStream(uri)?.use { it.write(data.toByteArray(Charsets.UTF_8)) } ?: error("Cannot write backup")
            notice=if(useEncryptedBackup) "Encrypted backup saved. Keep your password safe: it cannot be recovered." else "Unencrypted backup saved. Keep it private."
        } catch(e:Exception) { notice="Backup failed: ${e.message?.take(90)}" }
    }
    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }
    var pendingRestorePreview by remember { mutableStateOf<FullBackup.Preview?>(null) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) try {
            val data=context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Cannot read file")
            val json=if(data.startsWith("MIB-ENC-V1:")) SecureBackup.decrypt(data,restoreBackupPassword) else data
            val preview=FullBackup.preview(context,store,json)
            pendingRestoreJson=json
            pendingRestorePreview=preview
            notice="Backup validated. Review the preview before confirming."
        } catch(e:Exception) {
            pendingRestoreJson=null
            pendingRestorePreview=null
            notice="Restore preview failed. Check password and file: ${e.message?.take(75)}"
        }
    }
    pendingRestorePreview?.let { preview ->
        AlertDialog(
            onDismissRequest={pendingRestorePreview=null;pendingRestoreJson=null},
            title={Text("Review backup before restoring")},
            text={
                Column(Modifier.heightIn(max=410.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("This is a preview only. Your existing transactions have not changed.",
                        style=MaterialTheme.typography.bodyMedium)
                    Text("New transactions: ${preview.database.newTransactions}",
                        fontWeight=FontWeight.Bold)
                    Text("Already-present transactions: ${preview.database.existingTransactions}")
                    preview.database.tableCounts.entries.filter { it.value>0 }.forEach { (table,count) ->
                        Text("${table.replace('_',' ')}: $count records",
                            style=MaterialTheme.typography.bodySmall)
                    }
                    Text("Preference values available: ${preview.settingsToRestore}")
                    Text("Confirming will merge missing records and may replace non-security preferences. Existing biometric settings will stay on this device.",
                        color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton={
                TextButton(onClick={
                    val text=pendingRestoreJson
                    pendingRestorePreview=null
                    pendingRestoreJson=null
                    if(text!=null) try {
                        val count=FullBackup.restore(context,store,text)
                        revision++
                        notice="Restored $count additional records. Existing transaction records kept."
                    } catch(e:Exception) {
                        notice="Restore failed: ${e.message?.take(80)}"
                    }
                }) { Text("Confirm restore") }
            },
            dismissButton={TextButton(onClick={
                pendingRestorePreview=null
                pendingRestoreJson=null
            }) { Text("Cancel") }}
        )
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
        val landingAccent = Color(0xFFB5A0FF)
        Column(
            modifier = Modifier.fillMaxSize()
                .background(Color(0xFF08090F))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(32.dp))
                Box(
                    modifier = Modifier.size(92.dp)
                        .background(Color(0xFF211B36), RoundedCornerShape(28.dp)),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text("M", style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold, color = landingAccent)
                }
                Spacer(Modifier.height(24.dp))
                Text("MIB", style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold, color = Color.White)
                Text("BUDGET TRACKER", style = MaterialTheme.typography.labelLarge,
                    color = landingAccent)
                Spacer(Modifier.height(28.dp))
                Text("Your money. In focus.",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(Modifier.height(10.dp))
                Text("Track your spending, manage your accounts and stay on top of your budget.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB7B8C6),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(vertical = 28.dp)
                    .background(Color(0xFF151621), RoundedCornerShape(24.dp))
                    .padding(22.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text("Welcome back", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text("Unlock to view your private financial dashboard",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFB7B8C6),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        lockError = ""
                        authenticate()
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = landingAccent,
                        contentColor = Color(0xFF111018)),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Unlock with biometrics", fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        lockError = ""
                        unlockWithDeviceCredential()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Use device PIN or screen lock", color = Color.White) }
                if(lockError.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(lockError, style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFC4C4),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            Text("Protected by your device's authentication",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF9395A8),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        return
    }
    val mainScrollState = rememberScrollState()
    Scaffold(floatingActionButton = {
        if(tab==1) FloatingActionButton(onClick={showTransactionForm=true}) { Text("+",style=MaterialTheme.typography.headlineMedium) }
    }, bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
            listOf("Home","Transactions","Import","Budgets","Plan").forEachIndexed { i, label ->
                NavigationBarItem(selected=tab==i,onClick={tab=i},icon={ Icon(listOf(Icons.Default.Home,Icons.Default.SwapHoriz,Icons.Default.AddCircleOutline,Icons.Default.PieChart,Icons.Default.Event)[i],contentDescription=label) },label={Text(label)})
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background).then(Modifier.verticalScroll(mainScrollState)).padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if(tab==0) {
            Text("Budget Tracker",style=MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text("Your money, clearly organized  •  MVR",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/modealpha88-ctrl/BudgetTracker/releases/latest")))},modifier=Modifier.fillMaxWidth()) { Text("Update App") }
            }
            if(notice.isNotBlank()) Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer), modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(start=14.dp,end=6.dp,top=6.dp,bottom=6.dp),
                    verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    Text(notice,Modifier.weight(1f),color=MaterialTheme.colorScheme.onPrimaryContainer)
                    TextButton(onClick={notice=""}) { Text("Dismiss") }
                }
            }
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
                Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("Recommended setup: CSV first, SMS second",style=MaterialTheme.typography.titleMedium)
                        Text("1. Upload your bank CSV and review the matches. 2. Import missing statement rows. 3. Enable SMS or import previous SMS to fill gaps. Same-amount transactions within one day of a CSV entry are skipped to avoid duplicates.",style=MaterialTheme.typography.bodySmall)
                        Text(if(csvBaselineReady) "CSV baseline: ready" else "CSV baseline: not yet imported",color=if(csvBaselineReady) Color(0xFF15966C) else MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick={tab=2;csvLauncher.launch(arrayOf("text/*","application/csv","application/octet-stream"))}) { Text("Step 1 · Upload CSV statement") }
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
                        } else if (!csvBaselineReady) {
                            tab=2
                            notice="Upload and import your CSV statement first. Then enable SMS to fill missing transactions."
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
                    if (!csvBaselineReady) { tab=2; notice="Import CSV statement records first; SMS will then fill gaps." }
                    else if (context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) historyRequested=true
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
                                TextButton(onClick={
                                    val accepted=store.resolveBml(tx,true)
                                    revision++
                                    notice=if(accepted) "BML credit approved and added to account activity" else "Approval not applied; review pending transaction"
                                }) { Text("Add credit") }
                                TextButton(onClick={
                                    val accepted=store.resolveBml(tx,true,true)
                                    revision++
                                    notice=if(accepted) "BML own transfer recorded; excluded from spending" else "Transfer approval not applied"
                                }) { Text("Own transfer") }
                                TextButton(onClick={
                                    val removed=store.resolveBml(tx,false)
                                    revision++
                                    notice=if(removed) "Pending credit dismissed" else "Pending credit already resolved"
                                }) { Text("Discard") }
                            }
                        }
                    }
                }
            }
            }
            Spacer(Modifier.height(14.dp))
            when(tab) {
                4 -> {
                    FinancialPlanningScreen(
                        context = context,
                        store = store,
                        entries = transactions,
                        labels = accountLabels,
                        budgets = budgets,
                        selectedMonth = selectedMonth,
                        onMonthChange = { selectedMonth = it },
                        onChanged = { revision++ },
                        onNotice = { notice = it },
                        openCashAccount = { accountChoice = "Cash"; dashboardView = "Account balance"; tab = 0 },
                        exportPdf = { pdfLauncher.launch("BudgetTracker-${selectedMonth}.pdf") },
                        sharePdf = {
                            lastPdfUri?.let { uri ->
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(share, "Share financial report"))
                            }
                        },
                        canSharePdf = lastPdfUri != null
                    )
                }
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
                                Text("Spending heatmap",Modifier.weight(1f))
                                Switch(checked=showSpendingHeatmap,onCheckedChange={showSpendingHeatmap=it;dashboardPrefs.edit().putBoolean("heatmap",it).apply()})
                            }
                            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                Text("Spending insights",Modifier.weight(1f))
                                Switch(checked=showSpendingInsights,onCheckedChange={showSpendingInsights=it;dashboardPrefs.edit().putBoolean("insights",it).apply()})
                            }
                        } }
                    }
                    if(showDashboardOptions) {
                        HomePlanningControls(context) { planningLayoutRevision++ }
                    }
                    HomePlanningHighlights(context, transactions, accountLabels, store, planningLayoutRevision)
                    if(showAccountCards) {
                        Text("Accounts",style=MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            listOf("MIB","BML","Cash").forEach { bank ->
                                FilterChip(selected=selectedAccountCard==bank,onClick={
                                    selectedAccountCard=bank
                                    dashboardPrefs.edit().putString("account_card",bank).apply()
                                },label={Text(bank)})
                            }
                        }
                        val minor=balancePrefs.getLong("${selectedAccountCard.lowercase(java.util.Locale.ROOT)}_minor",Long.MIN_VALUE)
                        Card(onClick={dashboardView="Account balance";accountChoice=selectedAccountCard},
                            colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer),
                            modifier=Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("$selectedAccountCard · Account snapshot",color=MaterialTheme.colorScheme.primary)
                                Text(if(minor==Long.MIN_VALUE) "Balance not set" else Money.format(minor),
                                    style=MaterialTheme.typography.headlineMedium)
                                Text("Tap to manage balance",style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if(showSpendingInsights) {
                        val monthExpenses=monthlyTransactions.filter { it.amountLaari<0 && it.category!="Internal Transfer" }
                        val monthIncome=monthlyTransactions.filter { it.amountLaari>0 && it.category!="Internal Transfer" }.sumOf { it.amountLaari }
                        val monthOut=monthExpenses.sumOf { -it.amountLaari }
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                Text("Monthly insights",style=MaterialTheme.typography.titleMedium)
                                TextButton(onClick={insightsExpanded=!insightsExpanded;dashboardPrefs.edit().putBoolean("insights_expanded",insightsExpanded).apply()}) { Text(if(insightsExpanded) "Collapse ↑" else "Expand ↓") }
                            }
                            if(insightsExpanded) {
                            Text("Income: ${Money.format(monthIncome)}")
                            Text("Expenses: ${Money.format(monthOut)}")
                            LinearProgressIndicator(progress={if(monthIncome>0) (monthOut.toFloat()/monthIncome).coerceIn(0f,1f) else 0f},modifier=Modifier.fillMaxWidth())
                            Text(if(monthIncome>0) "${monthOut*100/monthIncome}% of recorded income spent" else "No recorded income this month",style=MaterialTheme.typography.bodySmall)
                            HorizontalDivider()
                            var spendingChartStyle by remember { mutableStateOf(dashboardPrefs.getString("spending_chart_style","Pie") ?: "Pie") }
                            var selectedSunburstCategory by remember { mutableStateOf<String?>(null) }
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                                verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                Text("Spending breakdown",style=MaterialTheme.typography.titleSmall)
                            }
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                                    FilterChip(selected=spendingChartStyle=="Pie",onClick={spendingChartStyle="Pie";dashboardPrefs.edit().putString("spending_chart_style","Pie").apply()},label={Text("Pie")})
                                    Spacer(Modifier.width(6.dp))
                                    FilterChip(selected=spendingChartStyle=="Sunburst",onClick={spendingChartStyle="Sunburst";dashboardPrefs.edit().putString("spending_chart_style","Sunburst").apply()},label={Text("Sunburst")})
                                    Spacer(Modifier.width(6.dp))
                                    FilterChip(selected=spendingChartStyle=="List",onClick={spendingChartStyle="List";dashboardPrefs.edit().putString("spending_chart_style","List").apply()},label={Text("List")})
                                }
                            }
                            val categoryTotals=monthExpenses.groupBy { it.category }
                                .mapValues { (_,items) -> items.sumOf { -it.amountLaari } }
                                .toList().sortedByDescending { it.second }
                            val topCategories=categoryTotals.take(5)
                            if(categoryTotals.isEmpty()) Text("No recorded expenses for this month.",style=MaterialTheme.typography.bodySmall)
                            if(spendingChartStyle=="Pie" && monthOut>0L) {
                                val chartSlices=if(categoryTotals.size>5)
                                    topCategories + ("Other categories" to categoryTotals.drop(5).sumOf { it.second })
                                    else topCategories
                                val sliceColors=listOf(
                                    Color(0xFFB5A0FF),Color(0xFF80CBC4),Color(0xFFFFC78E),
                                    Color(0xFF90CAF9),Color(0xFFF48FB1),Color(0xFFA5A5A5))
                                Canvas(Modifier.fillMaxWidth().height(190.dp).padding(8.dp)) {
                                    val diameter=kotlin.math.min(size.width,size.height)
                                    var start=-90f
                                    chartSlices.forEachIndexed { index,(_,amount) ->
                                        val sweep=amount.toFloat()/monthOut.toFloat()*360f
                                        drawArc(sliceColors[index % sliceColors.size],start,sweep,true,
                                            topLeft=Offset((size.width-diameter)/2f,(size.height-diameter)/2f),
                                            size=androidx.compose.ui.geometry.Size(diameter,diameter))
                                        start+=sweep
                                    }
                                }
                                chartSlices.forEachIndexed { index,(category,total) ->
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                                        verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                            Box(Modifier.size(10.dp).background(sliceColors[index % sliceColors.size],RoundedCornerShape(3.dp)))
                                            Spacer(Modifier.width(8.dp))
                                            Text(category,style=MaterialTheme.typography.bodySmall)
                                        }
                                        Text("${"%.1f".format(java.util.Locale.US,total.toDouble()*100.0/monthOut)}%",
                                            style=MaterialTheme.typography.bodySmall)
                                    }
                                }
                            } else if(spendingChartStyle=="List") {
                                topCategories.forEach { (category,total) ->
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                        Text(category,style=MaterialTheme.typography.bodySmall)
                                        Text(Money.format(total),style=MaterialTheme.typography.bodySmall)
                                    }
                                    LinearProgressIndicator(progress={if(monthOut>0L) (total.toFloat()/monthOut).coerceIn(0f,1f) else 0f},
                                        modifier=Modifier.fillMaxWidth())
                                }
                            }
                            if(spendingChartStyle=="Sunburst" && monthOut>0L) {
                                val sunColors=listOf(Color(0xFFB5A0FF),Color(0xFF80CBC4),Color(0xFFFFC78E),
                                    Color(0xFF90CAF9),Color(0xFFF48FB1),Color(0xFFA5A5A5))
                                val segments=categoryTotals.take(6)
                                val other=categoryTotals.drop(6).sumOf { it.second }
                                val rings=if(other>0L) segments+("Other categories" to other) else segments
                                val merchantRings=rings.map { (category,_) ->
                                    if(category=="Other categories") emptyList()
                                    else monthExpenses.filter { it.category==category }
                                        .groupBy { VendorProfiles.key(it.description) }
                                        .mapValues { (_,items) -> items.sumOf { -it.amountLaari } }
                                        .toList().sortedByDescending { it.second }
                                }
                                Canvas(Modifier.fillMaxWidth().height(210.dp).padding(horizontal=12.dp,vertical=8.dp)
                                    .pointerInput(rings,monthOut) {
                                        detectTapGestures { tap ->
                                            val diameter=kotlin.math.min(size.width,size.height).toFloat()
                                            val x=tap.x-size.width/2f
                                            val y=tap.y-size.height/2f
                                            val radius=kotlin.math.sqrt(x*x+y*y)
                                            if(radius>diameter*0.16f && radius<diameter*0.49f) {
                                                val angle=((Math.toDegrees(kotlin.math.atan2(y.toDouble(),x.toDouble()))+450.0)%360.0)
                                                var cursor=0.0
                                                rings.forEach { (category,total) ->
                                                    val sweep=total.toDouble()/monthOut*360.0
                                                    if(angle>=cursor && angle<cursor+sweep) selectedSunburstCategory=category
                                                    cursor+=sweep
                                                }
                                            }
                                        }
                                    }) {
                                    val diameter=kotlin.math.min(size.width,size.height)*0.76f
                                    val origin=Offset((size.width-diameter)/2f,(size.height-diameter)/2f)
                                    val bounds=androidx.compose.ui.geometry.Size(diameter,diameter)
                                    var angle=-90f
                                    rings.forEachIndexed { index,(category,total) ->
                                        val sweep=total.toFloat()/monthOut*360f
                                        val base=sunColors[index%sunColors.size]
                                        drawArc(base,angle,sweep,false,
                                            topLeft=Offset(origin.x+diameter*0.10f,origin.y+diameter*0.10f),
                                            size=androidx.compose.ui.geometry.Size(diameter*0.80f,diameter*0.80f),
                                            style=androidx.compose.ui.graphics.drawscope.Stroke(width=diameter*0.17f))
                                        var innerAngle=angle
                                        merchantRings[index].forEachIndexed { merchantIndex,(_,value) ->
                                            val merchantSweep=value.toFloat()/monthOut*360f
                                            drawArc(base.copy(alpha=if(merchantIndex%2==0) 0.95f else 0.5f),
                                                innerAngle,merchantSweep,false,
                                                topLeft=Offset(origin.x+diameter*0.29f,origin.y+diameter*0.29f),
                                                size=androidx.compose.ui.geometry.Size(diameter*0.42f,diameter*0.42f),
                                                style=androidx.compose.ui.graphics.drawscope.Stroke(width=diameter*0.13f))
                                            innerAngle+=merchantSweep
                                        }
                                        angle+=sweep
                                    }
                                }
                                Text("Outer: categories · Inner: merchants · Tap to select",style=MaterialTheme.typography.labelSmall)
                                rings.forEachIndexed { index,(category,total) ->
                                    Row(Modifier.fillMaxWidth().clickable {
                                        selectedSunburstCategory=category
                                    },horizontalArrangement=Arrangement.SpaceBetween) {
                                        Text("● $category",color=sunColors[index%sunColors.size],
                                            style=MaterialTheme.typography.bodySmall)
                                        Text("${"%.1f".format(java.util.Locale.US,total.toDouble()*100/monthOut)}%",
                                            style=MaterialTheme.typography.bodySmall)
                                    }
                                }
                                selectedSunburstCategory?.let { category ->
                                    val total=if(category=="Other categories") other
                                        else categoryTotals.firstOrNull { it.first==category }?.second ?: 0L
                                    Text("$category · ${"%.1f".format(java.util.Locale.US,total.toDouble()*100/monthOut)}%")
                                    TextButton(onClick={
                                        categoryFilter=if(category=="Other categories") "All" else category
                                        categoryDrilldownMonth=selectedMonth
                                        transactionFilter="Expenses"
                                        accountFilter="All accounts"
                                        counterpartyFilter="All types"
                                        searchText=""
                                        tab=1
                                    }) { Text("View category transactions →") }
                                    if(category!="Other categories") merchantRings.getOrNull(rings.indexOfFirst { it.first==category })
                                        ?.take(4)?.forEach { (merchant,amount) ->
                                            Text("$merchant · ${"%.1f".format(java.util.Locale.US,amount.toDouble()*100/monthOut)}%",
                                                style=MaterialTheme.typography.bodySmall)
                                        }
                                }
                            }
                            val net=monthIncome-monthOut
                            Text("Net cash flow: ${Money.format(net)}",style=MaterialTheme.typography.titleSmall)
                            Text("Based on recorded transactions; transfers and incomplete imports can affect totals.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            } else Text("Expenses: ${Money.format(monthOut)} · Net: ${Money.format(monthIncome-monthOut)}")
                        } }
                    }
                    if(showSpendingHeatmap) {
                        val daily=monthlyTransactions.filter { it.amountLaari<0 && it.category!="Internal Transfer" }
                            .groupBy { it.timestamp.take(10) }.mapValues { (_,rows) -> rows.sumOf { -it.amountLaari } }
                        val peak=daily.values.maxOrNull() ?: 0L
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                Text("Spending heatmap · $selectedMonth",style=MaterialTheme.typography.titleMedium)
                                repeat((selectedMonth.lengthOfMonth()+6)/7) { week ->
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                        repeat(7) { col ->
                                            val day=week*7+col+1
                                            if(day<=selectedMonth.lengthOfMonth()) {
                                                val amount=daily["$selectedMonth-${day.toString().padStart(2,'0')}"] ?: 0L
                                                val intensity=if(peak>0L) amount.toFloat()/peak else 0f
                                                Box(Modifier.weight(1f).height(32.dp)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha=0.12f+0.88f*intensity),RoundedCornerShape(6.dp)),
                                                    contentAlignment=androidx.compose.ui.Alignment.Center) {
                                                    Text("$day",style=MaterialTheme.typography.labelSmall)
                                                }
                                            } else Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                                Text("Brighter cells indicate higher daily spending.",style=MaterialTheme.typography.bodySmall)
                            }
                        }
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
                        val anchorDate=balancePrefs.getString("mib_date","").orEmpty()
                        val anchorMinor=balancePrefs.getLong("mib_minor",Long.MIN_VALUE)
                        val accountMonthTransactions=monthlyTransactions.filter {
                            FinanceCalculations.assignedAccount(it,accountLabels)=="MIB"
                        }
                        val unassignedMonth=monthlyTransactions.filter { FinanceCalculations.assignedAccount(it,accountLabels)=="Unassigned" }
                        val monthNet=accountMonthTransactions.sumOf { it.amountLaari }
                        val monthStart=selectedMonth.atDay(1).toString()
                        val monthEnd=selectedMonth.atEndOfMonth().toString()
                        var reconciliationExpanded by remember { mutableStateOf(false) }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                    Text("MIB balance",style=MaterialTheme.typography.titleMedium)
                                    TextButton(onClick={reconciliationExpanded=!reconciliationExpanded}) {
                                        Text(if(reconciliationExpanded) "Less details ↑" else "Details ↓")
                                    }
                                }
                                if(anchorMinor==Long.MIN_VALUE || anchorDate.isBlank()) {
                                    Text("No bank balance saved",style=MaterialTheme.typography.titleLarge)
                                    Text("Import a bank statement to compare cash flow with your balance.",style=MaterialTheme.typography.bodySmall)
                                } else {
                                    val anchorDay=runCatching { java.time.LocalDate.parse(anchorDate) }.getOrNull()
                                    val startDay=selectedMonth.atDay(1)
                                    val endDay=selectedMonth.atEndOfMonth()
                                    val inMonth=anchorDay!=null && !anchorDay.isBefore(startDay) && !anchorDay.isAfter(endDay)
                                    Text(Money.format(anchorMinor),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                                    Text("Bank balance · $anchorDate",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    if(inMonth) {
                                        val movementToAnchor=accountMonthTransactions.filter { it.timestamp.take(10)<=anchorDate }.sumOf { it.amountLaari }
                                        val opening=anchorMinor-movementToAnchor
                                        val later=accountMonthTransactions.filter { it.timestamp.take(10)>anchorDate }.sumOf { it.amountLaari }
                                        if(later!=0L) Text("After-statement activity: ${Money.format(later)}",style=MaterialTheme.typography.bodySmall)
                                        if(reconciliationExpanded) {
                                            HorizontalDivider()
                                            Text("Opening balance: ${Money.format(opening)}")
                                            Text("MIB movements: ${Money.format(monthNet)}")
                                            Text("Estimated month-end: ${Money.format(opening+monthNet)}")
                                            Text("Based on a saved statement balance, not independently verified against a second statement.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else if(reconciliationExpanded) {
                                        Text("Select the month of the bank statement to see the balance calculation.",style=MaterialTheme.typography.bodySmall)
                                    }
                                }
                                if(unassignedMonth.isNotEmpty()) {
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                        Text("${unassignedMonth.size} transactions need an account",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error,modifier=Modifier.weight(1f))
                                        TextButton(onClick={tab=1;accountFilter="Unassigned";showAdvancedFilters=true}) { Text("Review →") }
                                    }
                                    if(reconciliationExpanded) Text("${Money.format(unassignedMonth.sumOf { it.amountLaari })} excluded from MIB movements",style=MaterialTheme.typography.bodySmall)
                                } else {
                                    Text("All recorded transactions are assigned",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
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
                        Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("Balance on a selected date",style=MaterialTheme.typography.titleMedium)
                                var calendarOpen by remember { mutableStateOf(false) }
                                var calendarMonth by remember { mutableStateOf(java.time.YearMonth.now()) }
                                OutlinedButton(onClick={calendarOpen=!calendarOpen},modifier=Modifier.fillMaxWidth()) { Text("Select date: ${historicalBalanceDate}  ▾") }
                                if(calendarOpen) {
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                        TextButton(onClick={calendarMonth=calendarMonth.minusMonths(1)}) { Text("‹") }
                                        Text(calendarMonth.toString(),style=MaterialTheme.typography.titleMedium)
                                        TextButton(onClick={calendarMonth=calendarMonth.plusMonths(1)}) { Text("›") }
                                    }
                                    OutlinedTextField(value=largeMovementThreshold,onValueChange={largeMovementThreshold=it},
                                        label={Text("Highlight movements from (MVR)")},singleLine=true,modifier=Modifier.fillMaxWidth())
                                    val thresholdMinor=Money.parseLaari(largeMovementThreshold)?.takeIf { it>0L } ?: 100000L
                                    Row(Modifier.fillMaxWidth()) {
                                        listOf("M","T","W","T","F","S","S").forEach { label ->
                                            Box(Modifier.weight(1f),contentAlignment=androidx.compose.ui.Alignment.Center) {
                                                Text(label,style=MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                    val offset=calendarMonth.atDay(1).dayOfWeek.value-1
                                    val count=calendarMonth.lengthOfMonth()
                                    repeat((offset+count+6)/7) { week ->
                                        Row(Modifier.fillMaxWidth()) {
                                            repeat(7) { weekday ->
                                                val day=week*7+weekday-offset+1
                                                if(day in 1..count) {
                                                    val date=calendarMonth.atDay(day).toString()
                                                    val daily=transactions.filter { FinanceCalculations.assignedAccount(it,accountLabels)==accountChoice && it.timestamp.startsWith(date) }
                                                    val incoming=daily.any { it.amountLaari>=thresholdMinor }
                                                    val outgoing=daily.any { it.amountLaari<=-thresholdMinor }
                                                    Column(Modifier.weight(1f),horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally) {
                                                        TextButton(onClick={historicalBalanceDate=date;calendarOpen=false},contentPadding=PaddingValues(0.dp)) { Text(day.toString()) }
                                                        Row(horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                                                            if(incoming) Text("●",color=Color(0xFF15966C),style=MaterialTheme.typography.labelSmall)
                                                            if(outgoing) Text("●",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelSmall)
                                                        }
                                                    }
                                                } else Spacer(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                        Text("● Incoming",color=Color(0xFF15966C),style=MaterialTheme.typography.labelSmall)
                                        Text("● Outgoing",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelSmall)
                                    }
                                    val selectedDay=transactions.filter { FinanceCalculations.assignedAccount(it,accountLabels)==accountChoice && it.timestamp.startsWith(historicalBalanceDate) }
                                    Text("${selectedDay.size} transactions on selected date · Net ${Money.format(selectedDay.sumOf { it.amountLaari })}",style=MaterialTheme.typography.bodySmall)
                                }
                                val key=accountChoice.lowercase(java.util.Locale.ROOT)
                                val anchorAmount=balancePrefs.getLong("${key}_minor",Long.MIN_VALUE)
                                val anchorDate=balancePrefs.getString("${key}_date","").orEmpty()
                                val requested=runCatching { java.time.LocalDate.parse(historicalBalanceDate) }.getOrNull()
                                val anchorDay=runCatching { java.time.LocalDate.parse(anchorDate) }.getOrNull()
                                if(requested==null) Text("Enter a valid date.",color=MaterialTheme.colorScheme.error)
                                else if(anchorAmount==Long.MIN_VALUE || anchorDay==null)
                                    Text("Save a dated ${accountChoice} balance snapshot first.",style=MaterialTheme.typography.bodySmall)
                                else {
                                    val movement=transactions.filter { tx ->
                                        (accountLabels[tx.fingerprint] ?: if(accountChoice=="MIB" && tx.fingerprint.startsWith("mib-",true)) "MIB" else "Unassigned")==accountChoice &&
                                        (if(requested.isAfter(anchorDay)) tx.timestamp.take(10)>anchorDate && tx.timestamp.take(10)<=historicalBalanceDate
                                         else tx.timestamp.take(10)>historicalBalanceDate && tx.timestamp.take(10)<=anchorDate)
                                    }.sumOf { it.amountLaari }
                                    val estimate=if(requested.isAfter(anchorDay)) anchorAmount+movement else anchorAmount-movement
                                    val affected=transactions.count { tx ->
                                        FinanceCalculations.assignedAccount(tx,accountLabels)==accountChoice &&
                                        (if(requested.isAfter(anchorDay)) tx.timestamp.take(10)>anchorDate && tx.timestamp.take(10)<=historicalBalanceDate
                                         else tx.timestamp.take(10)>historicalBalanceDate && tx.timestamp.take(10)<=anchorDate)
                                    }
                                    val unassigned=transactions.count { tx ->
                                        (accountLabels[tx.fingerprint] ?: "Unassigned")=="Unassigned" &&
                                        (if(requested.isAfter(anchorDay)) tx.timestamp.take(10)>anchorDate && tx.timestamp.take(10)<=historicalBalanceDate
                                         else tx.timestamp.take(10)>historicalBalanceDate && tx.timestamp.take(10)<=anchorDate)
                                    }
                                    Text(Money.format(estimate),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                                    Text("${affected} assigned transactions included · ${unassigned} unassigned records excluded",style=MaterialTheme.typography.bodySmall)
                                    if(unassigned>0) Text("Some transactions have no account assignment. The balance may be incomplete.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                                    if(requested.isAfter(java.time.LocalDate.now())) Text("Future dates are projections, not verified balances.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                                    Text("Estimated from the ${anchorDate} balance and recorded ${accountChoice} transactions. Missing or duplicate transactions affect accuracy; this is not a bank-confirmed historical balance.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                    Text("${accountChoice} recent activity",style=MaterialTheme.typography.titleMedium)
                                    TextButton(onClick={revision++}) { Text("Refresh") }
                                }
                                Text("Recorded app transactions, not a live bank feed. Only assigned ${accountChoice} transactions are included.",style=MaterialTheme.typography.bodySmall)
                                val accountActivity=transactions.filter { FinanceCalculations.assignedAccount(it,accountLabels)==accountChoice }.sortedByDescending { it.timestamp }
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
                        val accountMovements=transactions
                            .filter { it.category!="Internal Transfer" }
                            .groupBy { accountLabels[it.fingerprint]?.takeIf(String::isNotBlank) ?: "Unassigned" }
                        Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp)) {
                            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("Account activity overview",style=MaterialTheme.typography.titleMedium,
                                    fontWeight=FontWeight.Bold)
                                Text("Recorded net movements, not bank balances. Internal transfers excluded.",
                                    style=MaterialTheme.typography.bodySmall)
                                accountMovements.toSortedMap().forEach { (account, entries) ->
                                    Text("$account · ${entries.size} transactions · Net ${Money.format(entries.sumOf { it.amountLaari })}",
                                        style=MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        if(savedBalanceMinor!=Long.MIN_VALUE && savedBalanceDate.isNotBlank()) {
                            val later=transactions.filter { it.timestamp.take(10)>savedBalanceDate && FinanceCalculations.assignedAccount(it,accountLabels)=="MIB" }
                            val unlabeled=transactions.count { it.timestamp.take(10)>savedBalanceDate && accountLabels[it.fingerprint].isNullOrBlank() }
                            val estimated=savedBalanceMinor+later.sumOf { it.amountLaari }
                            val referenceDayTransactions=transactions.filter {
                                it.timestamp.take(10)==savedBalanceDate &&
                                FinanceCalculations.assignedAccount(it,accountLabels)=="MIB"
                            }
                            val referenceDateWarning=referenceDayTransactions.isNotEmpty()
                            Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primary)) {
                                Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Text("MIB ESTIMATED BALANCE",color=Color.White)
                                    Text(Money.format(estimated),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold,color=Color.White)
                                    Text("${balanceSource} at ${savedBalanceDate}: ${Money.format(savedBalanceMinor)}",color=Color.White)
                                    Text("MIB transactions after reference: ${later.size}",color=Color.White)
                                    if(referenceDateWarning) Text(
                                        "${referenceDayTransactions.size} MIB transactions occurred on the reference date and are excluded. Use an end-of-day bank balance for accurate reconciliation.",
                                        color=Color.White)
                                    if(unlabeled>0) Text(
                                        "$unlabeled later transactions have no account label and are excluded from the MIB estimate.",
                                        color=Color.White)
                                    if(balanceSource.startsWith("MIB screenshot") && later.isNotEmpty()) Text("Estimated after recorded transactions: ${Money.format(estimated)}",color=Color.White)
                                    Text(if(balanceSource.startsWith("MIB screenshot")) "Estimated from your confirmed screenshot balance plus subsequent MIB transactions (not a live bank balance)." else "Estimated from reference balance and later labeled MIB transactions; not a live bank balance.",color=Color.White)
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
                                val related=transactions.filter {
                                    FinanceCalculations.assignedAccount(it,accountLabels)==accountChoice &&
                                        it.timestamp.take(10)>storedDate
                                }
                                Text("Recorded account movements after reference: ${related.size}")
                                val projected=FinanceCalculations.projectedBalance(
                                    accountChoice,storedMinor,storedDate,transactions,accountLabels)
                                if(projected!=null) Text("Estimated current ${accountChoice} balance: ${Money.format(projected)}",
                                    style=MaterialTheme.typography.titleMedium)
                                else Text("Account estimate unavailable. Check snapshot date and transaction values.")
                                Text("This is a projection, not a live bank balance; internal transfers change account balances but not spending.")
                            }
                        }
                    }
                    ReconciliationPanel(context,accountChoice,transactions,accountLabels) {
                        notice=it
                    }
                    Spacer(Modifier.height(20.dp))
                    var largeTransfersExpanded by remember { mutableStateOf(false) }
                    var transferThreshold by remember { mutableStateOf("1000") }
                    val thresholdMinor=Money.parseLaari(transferThreshold) ?: 100000L
                    var transferDirection by remember { mutableStateOf("Both") }
                    val reviewedTransferPrefs=remember { context.getSharedPreferences("reviewed_large_transfers",android.content.Context.MODE_PRIVATE) }
                    val largeTransfers=monthlyTransactions.filter { tx ->
                        !reviewedTransferPrefs.getBoolean(tx.id.toString(),false) &&
                        VendorProfiles.isTransfer(tx.description,tx.category) &&
                        kotlin.math.abs(tx.amountLaari)>=thresholdMinor &&
                        (transferDirection=="Both" || (transferDirection=="Incoming" && tx.amountLaari>0) ||
                            (transferDirection=="Outgoing" && tx.amountLaari<0))
                    }.sortedByDescending { kotlin.math.abs(it.amountLaari) }
                    val incomingTransferCount=largeTransfers.count { tx ->
                        VendorProfiles.isTransfer(tx.description,tx.category) && tx.amountLaari>=thresholdMinor
                    }
                    val outgoingTransferCount=largeTransfers.count { tx ->
                        VendorProfiles.isTransfer(tx.description,tx.category) && tx.amountLaari<=-thresholdMinor
                    }
                    val originalTransferPrefs=remember { context.getSharedPreferences("internal_transfer_original_categories",android.content.Context.MODE_PRIVATE) }
                    if(largeTransfers.isNotEmpty()) Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                                verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                Text("Review large transfers",style=MaterialTheme.typography.titleMedium)
                                TextButton(onClick={largeTransfersExpanded=!largeTransfersExpanded}) {
                                    Text(if(largeTransfersExpanded) "Hide ↑" else "${largeTransfers.size} to check ↓")
                                }
                            }
                            Text("Internal transfers should not count as spending or income. They remain in bank history and account balance calculations.",
                                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            if(largeTransfersExpanded) {
                                Text("Incoming: $incomingTransferCount · Outgoing: $outgoingTransferCount",style=MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    listOf("Both","Incoming","Outgoing").forEach { direction ->
                                        FilterChip(selected=transferDirection==direction,
                                            onClick={transferDirection=direction},label={Text(direction)})
                                    }
                                }
                                OutlinedTextField(value=transferThreshold,onValueChange={transferThreshold=it},
                                    label={Text("Minimum transfer (MVR)")},singleLine=true,modifier=Modifier.fillMaxWidth())
                                if(largeTransfers.isEmpty()) Text("No transfers above this amount this month.",style=MaterialTheme.typography.bodySmall)
                                largeTransfers.forEach { tx ->
                                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                        Text(TransactionPresentation.label(tx.description),style=MaterialTheme.typography.bodyMedium)
                                        Text("${if(tx.amountLaari>0) "Incoming" else "Outgoing"} · ${tx.timestamp.take(10)} · ${Money.format(tx.amountLaari)}",
                                            style=MaterialTheme.typography.bodySmall)
                                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                            if(tx.category=="Internal Transfer") {
                                                TextButton(onClick={
                                                    val previous=originalTransferPrefs.getString(tx.id.toString(),"Transfers") ?: "Transfers"
                                                    store.setTransactionCategory(tx.id,previous)
                                                    originalTransferPrefs.edit().remove(tx.id.toString()).apply()
                                                    revision++
                                                    notice="Transfer restored to $previous"
                                                }) { Text("Undo internal") }
                                            } else {
                                                OutlinedButton(onClick={
                                                    originalTransferPrefs.edit().putString(tx.id.toString(),tx.category).apply()
                                                    store.markInternal(tx.id)
                                                    reviewedTransferPrefs.edit().putBoolean(tx.id.toString(),true).apply()
                                                    revision++
                                                    notice="Marked internal; transaction retained"
                                                }) { Text("Mark internal") }
                                            }
                                            TextButton(onClick={
                                                reviewedTransferPrefs.edit().putBoolean(tx.id.toString(),true).apply()
                                                revision++
                                            }) { Text("Reviewed") }
                                            TextButton(onClick={selectedTransactionId=tx.id}) { Text("Details") }
                                        }
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }
                    val monthInsight=FinanceInsights.monthly(monthlyTransactions)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text("Spending insights",style=MaterialTheme.typography.titleMedium)
                            Text("Income: ${Money.format(monthInsight.income)} · Expenses: ${Money.format(monthInsight.spending)}")
                            Text("Net cash flow: ${Money.format(monthInsight.income-monthInsight.spending)}")
                            if(monthInsight.biggestExpenseCategory!=null)
                                Text("Largest category: ${monthInsight.biggestExpenseCategory} · ${Money.format(monthInsight.biggestExpense)}")
                            if(monthInsight.spending>monthInsight.income && monthInsight.income>0)
                                Text("Alert: Spending exceeds income this month.",color=MaterialTheme.colorScheme.error)
                            if(monthInsight.internalMoved>0)
                                Text("Internal movement excluded: ${Money.format(monthInsight.internalMoved)}",
                                    style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    val suggestedPairs=FinanceInsights.suggestedInternalPairs(monthlyTransactions,accountLabels)
                    var showSuggestedPairs by remember { mutableStateOf(false) }
                    if(suggestedPairs.isNotEmpty()) Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                Text("Possible internal transfers",style=MaterialTheme.typography.titleMedium)
                                TextButton(onClick={showSuggestedPairs=!showSuggestedPairs}) {
                                    Text("${suggestedPairs.size} pairs ${if(showSuggestedPairs) "↑" else "↓"}")
                                }
                            }
                            Text("Suggestions match equal outgoing and incoming transfer amounts within 3 days. Confirm only your own-account movements.",
                                style=MaterialTheme.typography.bodySmall)
                            if(showSuggestedPairs) suggestedPairs.forEach { pair ->
                                Text("${Money.format(pair.incoming.amountLaari)} · ${pair.outgoing.timestamp.take(10)} → ${pair.incoming.timestamp.take(10)}")
                                Text(TransactionPresentation.label(pair.outgoing.description),style=MaterialTheme.typography.bodySmall)
                                Text(TransactionPresentation.label(pair.incoming.description),style=MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick={
                                    originalTransferPrefs.edit()
                                        .putString(pair.outgoing.id.toString(),pair.outgoing.category)
                                        .putString(pair.incoming.id.toString(),pair.incoming.category).apply()
                                    store.markInternal(pair.outgoing.id)
                                    store.markInternal(pair.incoming.id)
                                    revision++
                                    notice="Both sides marked internal"
                                }) { Text("Confirm both as internal") }
                                HorizontalDivider()
                            }
                        }
                    }
                    val accountGroups=monthlyTransactions.groupBy { FinanceCalculations.assignedAccount(it,accountLabels) }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text("Account activity (selected month)",style=MaterialTheme.typography.titleMedium)
                            Text("Monthly net movements are not bank balances without a verified opening balance.",
                                style=MaterialTheme.typography.bodySmall)
                            accountGroups.forEach { (account,rows) ->
                                Text("$account · ${Money.format(rows.sumOf { it.amountLaari })} net · ${rows.size} transactions")
                            }
                        }
                    }
                    val scheduledPayments=remember(revision) { store.recurring() }
                    val pendingPayments=scheduledPayments.filter { (name,expected,category) ->
                        monthlyTransactions.none { tx ->
                            tx.amountLaari<0 && tx.category!="Internal Transfer" &&
                            -tx.amountLaari==expected &&
                            (tx.description.contains(name,true) || tx.category==category)
                        }
                    }
                    if(pendingPayments.isNotEmpty()) Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text("Recurring payment review",style=MaterialTheme.typography.titleMedium)
                            Text("Check scheduled payments against the selected month. Missing matches are reminders, not confirmed missed payments.",
                                style=MaterialTheme.typography.bodySmall)
                            pendingPayments.forEach { (name,expected,category) ->
                                val matches=monthlyTransactions.filter { tx ->
                                    tx.amountLaari<0 && tx.category!="Internal Transfer" &&
                                    -tx.amountLaari==expected &&
                                    (tx.description.contains(name,true) || tx.category==category)
                                }
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                    Text(name,modifier=Modifier.weight(1f))
                                    TextButton(onClick={
                                        categoryFilter="All"
                                        categoryDrilldownMonth=selectedMonth
                                        transactionFilter="Expenses"
                                        accountFilter="All accounts"
                                        counterpartyFilter="All types"
                                        searchText=name
                                        transactionMinAmount=""
                                        transactionMaxAmount=""
                                        showAdvancedFilters=true
                                        tab=1
                                    }) { Text("Check payment") }
                                }
                            }
                        }
                    }
                    Text("Spending by category",style=MaterialTheme.typography.titleMedium)
                    val grouped = monthlyTransactions.filter { it.amountLaari<0 && it.category != "Internal Transfer" }.groupBy { it.category }
                    grouped.forEach { (cat,txs) ->
                        val spent=txs.sumOf{-it.amountLaari}
                        Card(onClick={
                            categoryFilter=cat
                            categoryDrilldownMonth=selectedMonth
                            transactionFilter="Expenses"
                            accountFilter="All accounts"
                            counterpartyFilter="All types"
                            searchText=""
                            transactionMinAmount=""
                            transactionMaxAmount=""
                            showAdvancedFilters=true
                            tab=1
                        },modifier=Modifier.fillMaxWidth().padding(top=8.dp)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                                Text(cat,fontWeight=FontWeight.Medium)
                                Text("${Money.format(spent)}  ›",color=MaterialTheme.colorScheme.primary)
                            }
                        }
                        val limit=budgets[cat]?.let {
                            FinanceCalculations.effectiveLimit(cat,selectedMonth,it,transactions,rolloverEnabled)
                        }
                        if(limit!=null) { LinearProgressIndicator(progress={ (spent.toFloat()/limit).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth().height(8.dp),strokeCap=androidx.compose.ui.graphics.StrokeCap.Round); if(spent>=limit) Text("Budget reached",color=MaterialTheme.colorScheme.error) }
                    }
                    if(grouped.isEmpty()) Text("No transactions yet. Add one or import a bank SMS.")
                    OutlinedButton(onClick={pdfLauncher.launch("BudgetTracker-Report-${selectedMonth}.pdf")},modifier=Modifier.fillMaxWidth()) { Text("Export monthly PDF report") }
                    Text("Monthly spending overview",style=MaterialTheme.typography.titleMedium)
                    val totalSpent=grouped.values.flatten().sumOf { -it.amountLaari }
                    grouped.forEach { (cat,txs) ->
                        val spent=txs.sumOf { -it.amountLaari }
                        TextButton(onClick={
                            categoryFilter=cat
                            categoryDrilldownMonth=selectedMonth
                            transactionFilter="Expenses"
                            accountFilter="All accounts"
                            counterpartyFilter="All types"
                            searchText=""
                            transactionMinAmount=""
                            transactionMaxAmount=""
                            showAdvancedFilters=true
                            tab=1
                        },modifier=Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                Text(cat)
                                Text("${if(totalSpent>0) (spent*100/totalSpent) else 0}%  ›")
                            }
                        }
                        LinearProgressIndicator(progress={if(totalSpent>0) spent.toFloat()/totalSpent else 0f},modifier=Modifier.fillMaxWidth())
                    }
                    budgets.forEach { (cat,baseLimit) ->
                        val limit=FinanceCalculations.effectiveLimit(
                            cat,selectedMonth,baseLimit,transactions,rolloverEnabled)
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
                    val recurringSchedulePrefs = remember {
                        context.getSharedPreferences("recurring_due_dates",android.content.Context.MODE_PRIVATE)
                    }
                    store.recurring().forEach { (name,value,cat) ->
                        val recordedThisMonth = monthlyTransactions.count { tx ->
                            tx.amountLaari == -value && tx.category == cat &&
                                tx.description.equals(name, ignoreCase = true)
                        }
                        var dueDayInput by remember(name) {
                            mutableStateOf(recurringSchedulePrefs.getInt(name,0).takeIf { it>0 }?.toString() ?: "")
                        }
                        val dueDay=dueDayInput.toIntOrNull()
                        val today=java.time.LocalDate.now()
                        val dueThisMonth=dueDay?.let { day ->
                            today.withDayOfMonth(day.coerceAtMost(today.lengthOfMonth()))
                        }
                        val recordedCurrentMonth=transactions.any { tx ->
                            tx.timestamp.startsWith(java.time.YearMonth.now().toString()) &&
                                tx.amountLaari == -value && tx.category == cat &&
                                tx.description.equals(name,ignoreCase=true)
                        }
                        if(dueThisMonth!=null && !recordedCurrentMonth) {
                            Text("$name · " + when {
                                today.isAfter(dueThisMonth) -> "Overdue since $dueThisMonth"
                                today==dueThisMonth -> "Due today"
                                else -> "Due $dueThisMonth"
                            },style=MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                            OutlinedTextField(value=dueDayInput,onValueChange={ dueDayInput=it.take(2) },
                                label={Text("Due day (1–31)")},singleLine=true,modifier=Modifier.weight(1f))
                            TextButton(onClick={
                                val day=dueDayInput.toIntOrNull()
                                if(day!=null && day in 1..31) {
                                    recurringSchedulePrefs.edit().putInt(name,day).apply()
                                    notice="Due date saved for $name"
                                } else notice="Enter a day from 1 to 31"
                            }) { Text("Save due day") }
                        }
                        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                            Text("$name: ${Money.format(value)}",modifier=Modifier.weight(1f))
                            TextButton(onClick={
                                if (recordedThisMonth > 0) {
                                    notice = "This recurring payment was already recorded in the selected month."
                                } else {
                                    store.add(-value,cat,name)
                                    revision++
                                    notice = "Recurring payment recorded"
                                }
                            }) { Text(if(recordedThisMonth>0) "Already recorded" else "Record now") }
                        }
                    }
                    OutlinedTextField(value=recurringName,onValueChange={recurringName=it},label={Text("Recurring name")})
                    OutlinedTextField(value=recurringAmount,onValueChange={recurringAmount=it},label={Text("Monthly amount MVR")})
                    OutlinedButton(onClick={
                        val v=Money.parseLaari(recurringAmount)
                        if(recurringName.isNotBlank() && v!=null && v>0) {store.setRecurring(recurringName,v,"Bills");revision++;recurringName="";recurringAmount=""} else notice="Invalid recurring amount"
                    }) { Text("Save recurring template") }
                    Text("Security & privacy",style=MaterialTheme.typography.titleMedium,
                        fontWeight=FontWeight.Bold)
                    Text("App lock is optional. When enabled, your device verifies your fingerprint or screen lock. Financial data stays on this device.",
                        style=MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Biometric app lock",modifier=Modifier.weight(1f))
                        Switch(checked=lockEnabled,onCheckedChange={ enabled ->
                            if(!enabled || ((context.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isDeviceSecure || BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)==BiometricManager.BIOMETRIC_SUCCESS)) {
                                lockEnabled=enabled
                                securityPrefs.edit().putBoolean("biometric_lock",enabled).apply()
                                unlocked=true
                            } else notice="Set up biometrics or a device screen lock before enabling app lock"
                        })
                    }
                    Text("Uses Android biometric enrollment. Keep a device backup before enabling.",style=MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Hide app from screenshots and screen recording",modifier=Modifier.weight(1f))
                        Switch(checked=screenshotPrivacy,onCheckedChange={
                            screenshotPrivacy=it
                            securityPrefs.edit().putBoolean("screenshot_privacy",it).apply()
                        })
                    }

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
                    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),
                        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text("Transactions",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                            Text("Search, select and organize your money",
                                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick={tab=0},modifier=Modifier.weight(1f)) { Text("Overview") }
                                Button(onClick={showTransactionForm=true},modifier=Modifier.weight(1f)) { Text("+ Add") }
                            }
                        }
                    }
                    var showChangeHistory by remember { mutableStateOf(false) }
                    var pendingUndo by remember { mutableStateOf<TransactionAudit.Entry?>(null) }
                    val recentChanges=remember(revision) { store.recentTransactionChanges(35) }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                Text("Transaction edit history",style=MaterialTheme.typography.titleMedium)
                                TextButton(onClick={showChangeHistory=!showChangeHistory}) {
                                    Text(if(showChangeHistory) "Hide" else "Review ${recentChanges.size}")
                                }
                            }
                            Text("New additions, edits and deletions are recorded. Undo checks for newer changes before touching the ledger.",
                                style=MaterialTheme.typography.bodySmall)
                            if(showChangeHistory) {
                                if(recentChanges.isEmpty()) Text("No recorded transaction changes yet.")
                                recentChanges.take(20).forEach { change ->
                                    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("${change.operation.lowercase().replaceFirstChar { it.uppercase() }} · ${change.description.take(45)}",
                                                style=MaterialTheme.typography.bodyMedium)
                                            Text("${change.changedAt} · ID ${change.txId}" +
                                                if(change.undone) " · Undone" else "",
                                                style=MaterialTheme.typography.bodySmall)
                                        }
                                        TextButton(onClick={pendingUndo=change},enabled=!change.undone) {
                                            Text("Undo")
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                    pendingUndo?.let { change ->
                        AlertDialog(onDismissRequest={pendingUndo=null},
                            title={Text("Undo transaction change?")},
                            text={Text("Reverse ${change.operation.lowercase()} for ${change.description.take(70)}? This only succeeds when the transaction has not changed since this action.")},
                            confirmButton={TextButton(onClick={
                                val restored=store.undoTransactionChange(change.id)
                                pendingUndo=null
                                revision++
                                notice=if(restored) "Transaction change reversed" else
                                    "Undo unavailable: transaction was changed later or has a conflict"
                            }) { Text("Undo change") }},
                            dismissButton={TextButton(onClick={pendingUndo=null}) { Text("Cancel") }})
                    }
                    // Recipient and daily spending rankings use existing transaction data only.
                    var rankingMonth by remember { mutableStateOf(java.time.YearMonth.now().toString()) }
                    var rankingExpanded by remember { mutableStateOf(false) }
                    var rankingMode by remember { mutableStateOf("Recipients") }
                    val availableMonths = transactions.map { it.timestamp.take(7) }
                        .filter { it.matches(Regex("\\d{4}-\\d{2}")) }.distinct().sortedDescending()
                    val rankedExpenses = transactions.filter { it.amountLaari < 0 &&
                        it.timestamp.startsWith(rankingMonth) &&
                        !it.category.contains("Internal Transfer",ignoreCase=true) }
                    val rankingGroups = if(rankingMode=="Recipients")
                        rankedExpenses.groupBy { tx ->
                            (recipientPrefs.getString(tx.fingerprint,null)?.takeIf { it.isNotBlank() }
                                ?: TransactionPresentation.label(tx.description).trim().ifBlank { "Unknown recipient" })
                                .trim().uppercase(java.util.Locale.ROOT)
                        }
                    else rankedExpenses.groupBy { it.timestamp.take(10) }
                    val rankedTotals = rankingGroups.map { (name, entries) ->
                        name to entries.sumOf { kotlin.math.abs(it.amountLaari) }
                    }.sortedByDescending { it.second }
                    val monthSpend = rankedExpenses.sumOf { kotlin.math.abs(it.amountLaari) }
                    val previousRankingMonth = runCatching {
                        java.time.YearMonth.parse(rankingMonth).minusMonths(1).toString()
                    }.getOrDefault("")
                    val previousMonthSpend = transactions.filter {
                        it.amountLaari < 0 && it.timestamp.startsWith(previousRankingMonth) &&
                            !it.category.contains("Internal Transfer",ignoreCase=true)
                    }.sumOf { kotlin.math.abs(it.amountLaari) }
                    val dailySpend = rankedExpenses.groupBy { it.timestamp.take(10) }
                        .mapValues { (_, entries) -> entries.sumOf { kotlin.math.abs(it.amountLaari) } }
                    val highestDay = dailySpend.maxByOrNull { it.value }
                    val rankingMonthPeriod=runCatching { YearMonth.parse(rankingMonth) }.getOrDefault(YearMonth.now())
                    val effectiveRankingBudgets=budgets.mapValues { (category,limit) ->
                        FinanceCalculations.effectiveLimit(category,rankingMonthPeriod,limit,transactions,rolloverEnabled)
                    }
                    val monthBudget = effectiveRankingBudgets.values.sum()
                    val budgetedSpend = rankedExpenses.filter { budgets.containsKey(it.category) }
                        .sumOf { kotlin.math.abs(it.amountLaari) }
                    val monthBudgetRemaining = monthBudget - budgetedSpend
                    // Flag possible duplicates for review; never delete repeated purchases automatically.
                    val duplicateCandidates = rankedExpenses.groupBy { tx ->
                        Triple(tx.timestamp.take(10), VendorProfiles.key(tx.description), tx.amountLaari)
                    }.values.filter { group -> group.size > 1 && group.first().fingerprint != "" }
                    val categoryBudgetUsage = effectiveRankingBudgets.entries.map { (category, limit) ->
                        Triple(category, limit, rankedExpenses.filter { it.category==category }
                            .sumOf { kotlin.math.abs(it.amountLaari) })
                    }
                    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("Spending rankings",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                            Text("Previous month: ${Money.format(previousMonthSpend)} · ${if(monthSpend>=previousMonthSpend) "Increase" else "Decrease"} of ${Money.format(kotlin.math.abs(monthSpend-previousMonthSpend))}",
                                style=MaterialTheme.typography.bodySmall)
                            Text("Total spent: ${Money.format(monthSpend)} · ${rankedExpenses.size} transactions",
                                style=MaterialTheme.typography.bodyMedium)
                            if(highestDay!=null) Text("Highest spending day: ${highestDay.key} · ${Money.format(highestDay.value)}",
                                style=MaterialTheme.typography.bodySmall)
                            if(duplicateCandidates.isNotEmpty()) {
                                var showDuplicateDetails by remember(rankingMonth) { mutableStateOf(false) }
                                Text("${duplicateCandidates.sumOf { it.size }} transactions in ${duplicateCandidates.size} possible duplicate groups (same day, merchant and amount). Review before deleting.",
                                    style=MaterialTheme.typography.bodySmall)
                                TextButton(onClick={showDuplicateDetails=!showDuplicateDetails}) {
                                    Text(if(showDuplicateDetails) "Hide duplicate review" else "Review possible duplicates")
                                }
                                if(showDuplicateDetails) {
                                    duplicateCandidates.take(20).forEach { group ->
                                        val first=group.first()
                                        Text("${first.timestamp.take(10)} · ${TransactionPresentation.label(first.description)} · ${Money.format(kotlin.math.abs(first.amountLaari))}",
                                            style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold)
                                        group.forEach { candidate ->
                                            Text("ID ${candidate.id} · ${candidate.timestamp} · ${candidate.category} · ${candidate.description}",
                                                style=MaterialTheme.typography.bodySmall)
                                        }
                                        HorizontalDivider()
                                    }
                                    Text("These are possible matches, not confirmed duplicates. No transactions are automatically removed.",
                                        style=MaterialTheme.typography.bodySmall)
                                }
                            }
                            if(monthBudget>0L) {
                                Text("Combined category budgets: ${Money.format(monthBudget)} · ${if(monthBudgetRemaining>=0) "Remaining" else "Over budget"}: ${Money.format(kotlin.math.abs(monthBudgetRemaining))}",
                                    style=MaterialTheme.typography.bodySmall)
                                LinearProgressIndicator(
                                    progress={ (budgetedSpend.toFloat()/monthBudget.toFloat()).coerceIn(0f,1f) },
                                    modifier=Modifier.fillMaxWidth())
                            }
                            categoryBudgetUsage.forEach { (category, limit, spent) ->
                                Text("$category: ${Money.format(spent)} / ${Money.format(limit)}" +
                                    if(spent>=limit) " · Over limit" else if(spent*100>=limit*80) " · Near limit" else "",
                                    style=MaterialTheme.typography.bodySmall)
                                LinearProgressIndicator(
                                    progress={ (spent.toFloat()/limit.toFloat()).coerceIn(0f,1f) },
                                    modifier=Modifier.fillMaxWidth())
                            }
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick={rankingMode="Recipients"}) { Text("Recipients") }
                                OutlinedButton(onClick={rankingMode="Days"}) { Text("Highest days") }
                            }
                            Box {
                                OutlinedButton(onClick={rankingExpanded=true}) { Text("Month: $rankingMonth ▾") }
                                DropdownMenu(expanded=rankingExpanded,onDismissRequest={rankingExpanded=false}) {
                                    availableMonths.forEach { month ->
                                        DropdownMenuItem(text={Text(month)},onClick={rankingMonth=month;rankingExpanded=false})
                                    }
                                }
                            }
                            if(rankedTotals.isEmpty()) Text("No spending recorded for this month.")
                            else {
                                val maximum = rankedTotals.first().second.coerceAtLeast(1L)
                                rankedTotals.take(10).forEachIndexed { index, item ->
                                    Text("${index+1}. ${item.first} · ${Money.format(item.second)}")
                                    LinearProgressIndicator(
                                        progress={item.second.toFloat()/maximum.toFloat()},
                                        modifier=Modifier.fillMaxWidth())
                                    if(rankingMode=="Recipients") {
                                        TextButton(onClick={
                                            rankedRecipientFilter=item.first
                                            rankedRecipientMonth=rankingMonth
                                            tab=1
                                            searchText=""
                                            accountFilter="All accounts"
                                            categoryFilter="All categories"
                                            categoryDrilldownMonth=null
                                            counterpartyFilter="All types"
                                            transactionFilter="Expenses"
                                            transactionMinAmount=""
                                            transactionMaxAmount=""
                                            showAdvancedFilters=false
                                        }) { Text("View transactions") }
                                    }
                                }
                                if(rankedTotals.size>10) Text("Showing top 10 of ${rankedTotals.size} recipients or days")
                            }
                        }
                    }
                    OutlinedTextField(value=searchText,onValueChange={searchText=it;rankedRecipientFilter=null},
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
                                            DropdownMenuItem(text={Text(option)},onClick={categoryFilter=option;categoryDrilldownMonth=null;categoryFilterExpanded=false})
                                        }
                                    }
                                }
                                Text("Classify a business or individual from transaction details.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick={accountFilter="All accounts";counterpartyFilter="All types";categoryFilter="All categories";categoryDrilldownMonth=null;transactionFilter="All";searchText="";rankedRecipientFilter=null;rankedRecipientMonth=null}) { Text("Clear all filters") }
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
                                    OutlinedButton(onClick={backupLauncher.launch(if(useEncryptedBackup) "mib-budget-backup.enc" else "mib-budget-backup.json")},enabled=!useEncryptedBackup || encryptedBackupPassword.length>=12) { Text("Export backup") }
                                    OutlinedButton(onClick={restoreLauncher.launch(arrayOf("*/*"))}) { Text("Restore") }
                                }
                                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                    Checkbox(checked=useEncryptedBackup,onCheckedChange={useEncryptedBackup=it})
                                    Text("Encrypt backup with a password")
                                }
                                if(useEncryptedBackup) OutlinedTextField(value=encryptedBackupPassword,onValueChange={encryptedBackupPassword=it},
                                    label={Text("Backup password (12+ characters)")},visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
                                OutlinedTextField(value=restoreBackupPassword,onValueChange={restoreBackupPassword=it},
                                    label={Text("Password for encrypted restore")},visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
                                Text("Keep the password safe. Encrypted backups cannot be restored without it. Existing JSON backups remain supported.",style=MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick={csvLauncher.launch(arrayOf("text/*","application/csv","application/octet-stream"))}) { Text("Cross-check CSV vs SMS") }
                                OutlinedButton(onClick={receiptLauncher.launch("image/*")}) { Text("Scan receipt") }
                            }
                        }
                    }
                    val reviewNeeded=transactions.filter { tx ->
                        val isTransfer=VendorProfiles.isTransfer(tx.description,tx.category)
                        val recipient=recipientPrefs.getString(tx.fingerprint,"").orEmpty()
                        val parsed=TransactionPresentation.label(tx.description)
                        val missingRecipient=isTransfer && recipient.isBlank() && !parsed.contains(" · ")
                        val merchantUnverified=!isTransfer && VendorProfiles.get(context,tx.description)==null
                        missingRecipient || merchantUnverified
                    }
                    LaunchedEffect(revision,recipientRevision,socialRevision) {
                        reviewQueue.addTransactions(reviewNeeded)
                        reviewQueueRevision++
                    }
                    val queuedReviews=remember(reviewQueueRevision) { reviewQueue.all() }
                    var reviewInboxExpanded by remember { mutableStateOf(false) }
                    var reviewPageSize by remember { mutableIntStateOf(5) }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                Text("Review queue",style=MaterialTheme.typography.titleMedium)
                                TextButton(onClick={reviewInboxExpanded=!reviewInboxExpanded}) {
                                    Text("${queuedReviews.count { it.status=="PENDING" }} pending")
                                }
                            }
                            if(reviewInboxExpanded) {
                                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                    listOf("PENDING","APPROVED","REJECTED","RESOLVED").forEach { state ->
                                        FilterChip(selected=reviewStatusFilter==state,onClick={reviewStatusFilter=state},label={Text(state.lowercase().replaceFirstChar { it.uppercase() },style=MaterialTheme.typography.labelSmall)})
                                    }
                                }
                                val visibleReviews=queuedReviews.filter { it.status==reviewStatusFilter }
                                visibleReviews.take(reviewPageSize).forEach { item ->
                                    Column(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                        Text(item.title,style=MaterialTheme.typography.bodyMedium)
                                        Text(item.detail,style=MaterialTheme.typography.bodySmall)
                                        if(item.txId>0) TextButton(onClick={selectedTransactionId=item.txId}) { Text("Open transaction") }
                                        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                            listOf("PENDING","APPROVED","REJECTED","RESOLVED").filter { it!=item.status }.forEach { state ->
                                                TextButton(onClick={reviewQueue.status(item.id,state);reviewQueueRevision++;if(state=="APPROVED") notice="Review marked approved. Existing transactions are not added again."}) {
                                                    Text(state.lowercase().replaceFirstChar { it.uppercase() },style=MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                    HorizontalDivider()
                                }
                                if(visibleReviews.size>reviewPageSize) {
                                    TextButton(onClick={reviewPageSize+=5}) {
                                        Text("Show 5 more · ${visibleReviews.size-reviewPageSize} remaining")
                                    }
                                }
                            }
                        }
                    }
                    val minFilterLaari=Money.parseLaari(transactionMinAmount)
                    val maxFilterLaari=Money.parseLaari(transactionMaxAmount)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value=transactionMinAmount,onValueChange={transactionMinAmount=it},
                            label={Text("Min MVR")},singleLine=true,modifier=Modifier.weight(1f))
                        OutlinedTextField(value=transactionMaxAmount,onValueChange={transactionMaxAmount=it},
                            label={Text("Max MVR")},singleLine=true,modifier=Modifier.weight(1f))
                    }
                    if(rankedRecipientFilter!=null) {
                        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                            Text("Recipient: $rankedRecipientFilter",modifier=Modifier.weight(1f))
                            TextButton(onClick={rankedRecipientFilter=null;rankedRecipientMonth=null}) { Text("Clear") }
                        }
                        var merchantRuleCategory by remember(rankedRecipientFilter) { mutableStateOf("Food") }
                        var merchantRuleExpanded by remember { mutableStateOf(false) }
                        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Box {
                                OutlinedButton(onClick={merchantRuleExpanded=true}) {
                                    Text("Category: $merchantRuleCategory")
                                }
                                DropdownMenu(expanded=merchantRuleExpanded,onDismissRequest={merchantRuleExpanded=false}) {
                                    categories.filter { it!="Internal Transfer" }.forEach { option ->
                                        DropdownMenuItem(text={Text(option)},onClick={
                                            merchantRuleCategory=option
                                            merchantRuleExpanded=false
                                        })
                                    }
                                }
                            }
                            Button(onClick={
                                val merchant = transactions.firstOrNull { tx ->
                                    (recipientPrefs.getString(tx.fingerprint,null)?.takeIf { it.isNotBlank() }
                                        ?: TransactionPresentation.label(tx.description).trim())
                                        .equals(rankedRecipientFilter,ignoreCase=true)
                                }
                                if(merchant!=null) {
                                    val count=store.reclassifyMerchant(merchant.description,merchantRuleCategory)
                                    revision++
                                    notice="Saved merchant rule; updated $count transactions"
                                } else notice="No matching merchant transactions"
                            }) { Text("Apply to merchant") }
                        }
                        Text("Applies to existing matching transactions and future imports.",
                            style=MaterialTheme.typography.bodySmall)
                    }
                    val filteredTransactions = transactions.filter { t ->
                        (rankedRecipientFilter==null || (
                            (recipientPrefs.getString(t.fingerprint,null)?.takeIf { it.isNotBlank() }
                                ?: TransactionPresentation.label(t.description).trim().ifBlank { "Unknown recipient" })
                                .equals(rankedRecipientFilter,ignoreCase=true) &&
                            t.timestamp.startsWith(rankedRecipientMonth ?: rankingMonth))) &&
                        (searchText.isBlank() || t.description.contains(searchText,true) || t.category.contains(searchText,true) || t.timestamp.contains(searchText) || Money.format(t.amountLaari).contains(searchText,true)) &&
                        (minFilterLaari==null || kotlin.math.abs(t.amountLaari)>=minFilterLaari) &&
                        (maxFilterLaari==null || kotlin.math.abs(t.amountLaari)<=maxFilterLaari) &&
                        (accountFilter=="All accounts" || (accountLabels[t.fingerprint] ?: "Unassigned")==accountFilter) &&
                        (categoryFilter=="All categories" || t.category==categoryFilter) &&
                        (categoryDrilldownMonth==null || t.timestamp.take(7)==categoryDrilldownMonth.toString()) &&
                        (counterpartyFilter=="All types" || (counterpartyPrefs.getString(t.fingerprint,"Unclassified") ?: "Unclassified")==counterpartyFilter) &&
                        when(transactionFilter) {
                            "Expenses" -> t.amountLaari < 0
                            "Income" -> t.amountLaari > 0
                            "Transfers" -> t.category.contains("Transfer",ignoreCase=true)
                            else -> true
                        }
                    }
                    // Imports arrive out of order; group by transaction date, not insertion ID.
                    // Sorting is presentation-only and never changes stored records.
                    val orderedTransactions=filteredTransactions.sortedWith(
                        compareByDescending<Tx> { it.timestamp.take(10) }
                            .thenByDescending { TransactionPresentation.time(it.description,it.timestamp) ?: "00:00" }
                            .thenByDescending { it.id }
                    )
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                                verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                Text("Money owed to me",style=MaterialTheme.typography.titleMedium)
                                TextButton(onClick={
                                    owedSelectionMode=!owedSelectionMode
                                    if(owedSelectionMode) {
                                        internalSelectionMode=false
                                        selectedInternalIds=emptySet()
                                    }
                                }) { Text(if(owedSelectionMode) "Done" else "Select & calculate") }
                            }
                            Text("Search by person, recipient, date or amount above, then select transactions below.",
                                style=MaterialTheme.typography.bodySmall)
                            if(owedSelectionMode || owedSelectedIds.isNotEmpty()) {
                                val selectedOwedTransactions=transactions.filter { it.id in owedSelectedIds }
                                val lent=selectedOwedTransactions.filter { it.amountLaari<0 }.sumOf { -it.amountLaari }
                                val repaid=selectedOwedTransactions.filter { it.amountLaari>0 }.sumOf { it.amountLaari }
                                OutlinedTextField(value=debtPersonName,onValueChange={debtPersonName=it},
                                    label={Text("Person / debt name")},singleLine=true,modifier=Modifier.fillMaxWidth())
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    listOf("Owed to me","I owe").forEach { direction ->
                                        FilterChip(selected=debtDirection==direction,onClick={debtDirection=direction},label={Text(direction)})
                                    }
                                }
                                Text("Selected: ${owedSelectedIds.size} transactions",style=MaterialTheme.typography.bodyMedium)
                                Text("Money sent: ${Money.format(lent)} · Money received: ${Money.format(repaid)}",
                                    style=MaterialTheme.typography.bodySmall)
                                val debtBalance=if(debtDirection=="Owed to me") lent-repaid else repaid-lent
                                Text("Outstanding for $debtDirection: ${Money.format(debtBalance)}",
                                    style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                                if(editingDebtKey!=null) TextButton(onClick={
                                    editingDebtKey=null
                                    owedSelectedIds=emptySet()
                                    debtPersonName=""
                                }) { Text("Start a new debt group") }
                                Button(enabled=debtPersonName.isNotBlank() && owedSelectedIds.isNotEmpty(),
                                    onClick={
                                        val key=editingDebtKey ?: "debt_${java.util.UUID.randomUUID()}"
                                        val record=org.json.JSONObject()
                                            .put("name",debtPersonName.trim())
                                            .put("direction",debtDirection)
                                            .put("ids",org.json.JSONArray(owedSelectedIds.toList()))
                                        debtPrefs.edit().putString(key,record.toString()).apply()
                                        debtRecordsRevision++
                                        editingDebtKey=key
                                        notice="Debt group saved"
                                    },modifier=Modifier.fillMaxWidth()) { Text(if(editingDebtKey==null) "Save new debt group" else "Update debt group") }
                                Text("Raw outgoing minus incoming: ${Money.format(lent-repaid)}",
                                    style=MaterialTheme.typography.bodySmall)
                                // Previous calculator display retained for comparison.
                                Text("Estimated outstanding: ${Money.format(lent-repaid)}",
                                    style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,
                                    color=if(lent-repaid<0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                Text("Outgoing selected payments count as money lent; incoming payments count as repayments. Select only transactions related to the debt. A negative balance means more was received than sent.",
                                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick={
                                        owedSelectedIds=owedSelectedIds+orderedTransactions.map { it.id }
                                    }) { Text("Add all matching") }
                                    TextButton(onClick={owedSelectedIds=emptySet()}) { Text("Clear all") }
                                }
                            }
                        }
                    }
                    val savedDebtGroups=remember(debtRecordsRevision) {
                        debtPrefs.all.filterKeys { it.startsWith("debt_") }.mapNotNull { (key,value) ->
                            try { key to org.json.JSONObject(value as String) } catch (_: Exception) { null }
                        }
                    }
                    if(savedDebtGroups.isNotEmpty()) Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("Saved debts",style=MaterialTheme.typography.titleMedium)
                            savedDebtGroups.forEach { (key,record) ->
                                val ids=record.optJSONArray("ids")
                                val chosenIds=(0 until (ids?.length() ?: 0)).map { ids!!.optLong(it) }.toSet()
                                val rows=transactions.filter { it.id in chosenIds }
                                val sent=rows.filter { it.amountLaari<0 }.sumOf { -it.amountLaari }
                                val received=rows.filter { it.amountLaari>0 }.sumOf { it.amountLaari }
                                val direction=record.optString("direction","Owed to me")
                                val balance=if(direction=="I owe") received-sent else sent-received
                                Text("${record.optString("name")} · $direction",fontWeight=FontWeight.SemiBold)
                                Text("${Money.format(balance)} outstanding · ${rows.size} transactions",
                                    style=MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick={
                                        owedSelectedIds=chosenIds
                                        editingDebtKey=key
                                        debtPersonName=record.optString("name")
                                        debtDirection=direction
                                        owedSelectionMode=true
                                        internalSelectionMode=false
                                    }) { Text("Load") }
                                    TextButton(onClick={
                                        debtPrefs.edit().remove(key).apply()
                                        if(editingDebtKey==key) editingDebtKey=null
                                        debtRecordsRevision++
                                    }) { Text("Remove group") }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement=Arrangement.SpaceBetween) {
                                Text("Internal transfers",style=MaterialTheme.typography.titleMedium)
                                TextButton(onClick={
                                    internalSelectionMode=!internalSelectionMode
                                    selectedInternalIds=emptySet()
                                    if(internalSelectionMode) owedSelectionMode=false
                                }) { Text(if(internalSelectionMode) "Cancel selection" else "Select transactions") }
                            }
                            if(internalSelectionMode) {
                                Text("Select incoming or outgoing transactions below. Marking them internal excludes them from spending and income, but keeps bank records and balances.",style=MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick={
                                        selectedInternalIds=selectedInternalIds+orderedTransactions.filter { it.category!="Internal Transfer" }.map { it.id }
                                    }) { Text("Add all matching") }
                                    TextButton(onClick={selectedInternalIds=emptySet()}) { Text("Clear") }
                                }
                                Button(onClick={confirmInternalSelection=true},
                                    enabled=selectedInternalIds.isNotEmpty(),modifier=Modifier.fillMaxWidth()) {
                                    Text("Mark ${selectedInternalIds.size} selected as internal")
                                }
                            }
                        }
                    }
                    if(confirmInternalSelection) AlertDialog(
                        onDismissRequest={confirmInternalSelection=false},
                        title={Text("Mark selected transactions as internal?")},
                        text={Text("${selectedInternalIds.size} incoming/outgoing transactions will be excluded from income and spending totals. They remain in transaction history and bank balance calculations. You can undo each classification.")},
                        confirmButton={TextButton(onClick={
                            val chosen=transactions.filter { it.id in selectedInternalIds && it.category!="Internal Transfer" }
                            chosen.forEach { tx ->
                                originalInternalCategories.edit().putString(tx.id.toString(),tx.category).apply()
                                store.markInternal(tx.id)
                            }
                            notice="${chosen.size} transactions marked internal"
                            revision++
                            selectedInternalIds=emptySet()
                            internalSelectionMode=false
                            confirmInternalSelection=false
                        }) { Text("Confirm ${selectedInternalIds.size}") }},
                        dismissButton={TextButton(onClick={confirmInternalSelection=false}) { Text("Cancel") }}
                    )
                    if(categoryFilter!="All categories") {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("Reclassify $categoryFilter transactions",style=MaterialTheme.typography.titleMedium)
                                var bulkDestination by remember(categoryFilter) { mutableStateOf("Other") }
                                var bulkDestinationExpanded by remember { mutableStateOf(false) }
                                Text("Select transactions from this filtered list, then choose their new category.",style=MaterialTheme.typography.bodySmall)
                                TextButton(onClick={
                                    categorySelectionMode=!categorySelectionMode
                                    categorySelectedIds=emptySet()
                                    if(categorySelectionMode) {
                                        owedSelectionMode=false
                                        internalSelectionMode=false
                                    }
                                }) { Text(if(categorySelectionMode) "Cancel selection" else "Select transactions") }
                                if(categorySelectionMode) {
                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick={
                                            categorySelectedIds=categorySelectedIds+orderedTransactions.map { it.id }
                                        }) { Text("Select all matching") }
                                        TextButton(onClick={categorySelectedIds=emptySet()}) { Text("Clear") }
                                    }
                                    Box {
                                        OutlinedButton(onClick={bulkDestinationExpanded=true}) { Text("Move to: $bulkDestination") }
                                        DropdownMenu(expanded=bulkDestinationExpanded,onDismissRequest={bulkDestinationExpanded=false}) {
                                            (categories+transactions.map { it.category }).distinct().filter { it.isNotBlank() && it!=categoryFilter }.forEach { destination ->
                                                DropdownMenuItem(text={Text(destination)},onClick={
                                                    bulkDestination=destination
                                                    bulkDestinationExpanded=false
                                                })
                                            }
                                        }
                                    }
                                    Button(enabled=categorySelectedIds.isNotEmpty() && bulkDestination!=categoryFilter,onClick={
                                        val matching=transactions.filter { it.id in categorySelectedIds && it.category==categoryFilter }
                                        matching.forEach { store.setTransactionCategory(it.id,bulkDestination) }
                                        val changed=matching.size
                                        categorySelectedIds=emptySet()
                                        categorySelectionMode=false
                                        revision++
                                        notice="Reclassified $changed transactions to $bulkDestination"
                                    },modifier=Modifier.fillMaxWidth()) { Text("Move ${categorySelectedIds.size} to $bulkDestination") }
                                }
                            }
                        }
                    }
                    if(orderedTransactions.isEmpty()) Text("No transactions match this filter.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    var lastTransactionDay = ""
                    orderedTransactions.take(150).forEach { t ->
                        val day=t.timestamp.take(10)
                        if(day!=lastTransactionDay) {
                            Text(day,style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(top=12.dp,bottom=4.dp))
                            lastTransactionDay=day
                        }
                        val merchant=TransactionPresentation.label(t.description)
                        Card(onClick={
                            if(categorySelectionMode) categorySelectedIds=if(t.id in categorySelectedIds) categorySelectedIds-t.id else categorySelectedIds+t.id
                            else if(owedSelectionMode) owedSelectedIds=if(t.id in owedSelectedIds) owedSelectedIds-t.id else owedSelectedIds+t.id
                            else if(internalSelectionMode) selectedInternalIds=if(t.id in selectedInternalIds) selectedInternalIds-t.id else selectedInternalIds+t.id
                            else selectedTransactionId=t.id
                        },modifier=Modifier.fillMaxWidth().padding(vertical=2.dp),shape=RoundedCornerShape(16.dp),
                            colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=12.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                if(owedSelectionMode) Checkbox(checked=t.id in owedSelectedIds,onCheckedChange={ checked ->
                                    owedSelectedIds=if(checked) owedSelectedIds+t.id else owedSelectedIds-t.id
                                })
                                if(internalSelectionMode) Checkbox(checked=t.id in selectedInternalIds,onCheckedChange={ checked ->
                                    selectedInternalIds=if(checked) selectedInternalIds+t.id else selectedInternalIds-t.id
                                })
                                Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.secondaryContainer,RoundedCornerShape(14.dp)),contentAlignment=androidx.compose.ui.Alignment.Center) {
                                    val vendorKey=VendorProfiles.key(t.description)
                                    val avatarPath=remember(avatarRevision,vendorKey) { vendorAvatarPrefs.getString(vendorKey,null) }
                                    val avatar=remember(avatarPath) { avatarPath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
                                    if(avatar!=null) Image(bitmap=avatar,contentDescription="Vendor icon",modifier=Modifier.size(42.dp),contentScale=ContentScale.Crop)
                                    else Text(if(t.category.contains("Transfer",true)) "↗" else vendorKey.split("_").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.ifBlank { "▤" },color=MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                    val savedName=recipientPrefs.getString(t.fingerprint,null).orEmpty()
                                    val displayMerchant=if(savedName.isNotBlank() && t.category.contains("Transfer",true)) "Transfer · $savedName" else merchant
                                    Text(displayMerchant,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold,maxLines=1)
                                    val displayTime=TransactionPresentation.time(t.description,t.timestamp)
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
                    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text("CSV statements",style=MaterialTheme.typography.titleMedium)
                            Text("Import or cross-check your MIB bank statement against saved transactions. Review matches and missing entries before saving.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick={csvLauncher.launch(arrayOf("text/*","application/csv","application/octet-stream"))},modifier=Modifier.fillMaxWidth()) {
                                Text("Select CSV statement")
                            }
                            Text("CSV comparison does not add transactions automatically.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            csvImportPrefs.getString("last_import",null)?.let { Text("Last import: $it",style=MaterialTheme.typography.bodySmall) }
                        }
                    }
                    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("Secure backup & restore",style=MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                Checkbox(checked=useEncryptedBackup,onCheckedChange={useEncryptedBackup=it})
                                Text("Password-encrypted export (recommended)")
                            }
                            if(useEncryptedBackup) OutlinedTextField(value=encryptedBackupPassword,onValueChange={encryptedBackupPassword=it},
                                label={Text("Export password (at least 12 characters)")},visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
                            Button(onClick={backupLauncher.launch(if(useEncryptedBackup) "mib-budget-backup.enc" else "mib-budget-backup.json")},
                                enabled=!useEncryptedBackup || encryptedBackupPassword.length>=12,modifier=Modifier.fillMaxWidth()) { Text("Export backup") }
                            OutlinedTextField(value=restoreBackupPassword,onValueChange={restoreBackupPassword=it},
                                label={Text("Password if restoring encrypted backup")},visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
                            OutlinedButton(onClick={restoreLauncher.launch(arrayOf("*/*"))},modifier=Modifier.fillMaxWidth()) { Text("Restore from file (merge safely)") }
                            Text("Keep your password private. The app cannot recover a forgotten password. Older JSON backups still work.",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    if(confirmMissingImport) {
                        AlertDialog(
                            onDismissRequest={confirmMissingImport=false},
                            title={Text("Import missing transactions?")},
                            text={Text("Import ${selectedMissingCsvLines.size} missing and ${selectedPossibleCsvLines.size} possible records? Possible matches may duplicate existing transactions. Review each selection and export a backup first.")},
                            confirmButton={TextButton(onClick={
                                confirmMissingImport=false
                                try {
                                    val data=csvText ?: error("CSV not available")
                                    val selectedRows=selectedMissingCsvLines+selectedPossibleCsvLines
                                    val result=store.importMissingCsv(data,selectedRows,includePossible=selectedPossibleCsvLines.isNotEmpty())
                                    csvImportPrefs.edit().putString("last_import","${java.time.LocalDateTime.now()} · Added ${result.first} records; excluded ${result.second}").apply()
                                    selectedMissingCsvLines=emptySet()
                                    selectedPossibleCsvLines=emptySet()
                                    revision++
                                    csvReview=store.reviewCsv(data)
                                    notice="Added ${result.first} missing transactions; excluded ${result.second} matched or uncertain records."
                                } catch(e:Exception) { notice="Import failed: ${e.message?.take(100)}" }
                            }) { Text("Import missing") }},
                            dismissButton={TextButton(onClick={confirmMissingImport=false}) { Text("Cancel") }}
                        )
                    }
                    if(confirmNamingReset) AlertDialog(onDismissRequest={confirmNamingReset=false},
                        title={Text("Reset recipient naming?")},
                        text={Text("Remove all manually saved recipient names and start naming again. Transactions, original bank descriptions, amounts and balances will NOT be deleted. This cannot be undone.")},
                        confirmButton={TextButton(onClick={
                            recipientPrefs.edit().clear().apply()
                            recipientRevision++
                            confirmNamingReset=false
                            notice="Manually saved recipient names cleared. Transactions preserved."
                        }) { Text("Reset names") }},
                        dismissButton={TextButton(onClick={confirmNamingReset=false}) { Text("Cancel") }})
                    OutlinedButton(onClick={confirmNamingReset=true},modifier=Modifier.fillMaxWidth()) {
                        Text("Start over: clear manually saved recipient names")
                    }
                    csvReview?.let { review ->
                        Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("Statement cross-check",color=MaterialTheme.colorScheme.onSurface,style=MaterialTheme.typography.titleMedium)
                                Text("Matched: ${review.confirmed}  •  Possible: ${review.possible}  •  Missing: ${review.missing}  •  Ignored: ${review.ignored}",color=MaterialTheme.colorScheme.onSurface)
                                Text("Read-only preview. No CSV transactions have been added. Possible matches need manual review.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                    listOf("MISSING","POSSIBLE","MATCHED").forEach { status ->
                                        FilterChip(selected=csvReviewFilter==status,onClick={csvReviewFilter=status},label={Text(status)})
                                    }
                                }
                                if(csvReviewFilter=="MISSING" || csvReviewFilter=="POSSIBLE") {
                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        if(csvReviewFilter=="MISSING") TextButton(onClick={selectedMissingCsvLines=review.matches.filter { it.status=="MISSING" }.map { it.line }.toSet()}) { Text("Select all missing") }
                                        TextButton(onClick={if(csvReviewFilter=="MISSING") selectedMissingCsvLines=emptySet() else selectedPossibleCsvLines=emptySet()}) { Text("Clear selection") }
                                    }
                                    Text("${selectedMissingCsvLines.size} missing + ${selectedPossibleCsvLines.size} possible selected",style=MaterialTheme.typography.bodySmall)
                                    if(csvReviewFilter=="POSSIBLE") Text("Possible matches can duplicate an existing transaction. Select only after comparing the original bank statement.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                                }
                                review.matches.filter { it.status==csvReviewFilter }.take(100).forEach { item ->
                                    HorizontalDivider()
                                    if(item.status=="MISSING" || item.status=="POSSIBLE") {
                                        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                            Checkbox(checked=if(item.status=="MISSING") item.line in selectedMissingCsvLines else item.line in selectedPossibleCsvLines,onCheckedChange={checked ->
                                                if(item.status=="MISSING") selectedMissingCsvLines=if(checked) selectedMissingCsvLines+item.line else selectedMissingCsvLines-item.line
                                                else selectedPossibleCsvLines=if(checked) selectedPossibleCsvLines+item.line else selectedPossibleCsvLines-item.line
                                            })
                                            Column {
                                                Text("${item.date}  •  ${Money.format(item.amountLaari)}",color=MaterialTheme.colorScheme.primary)
                                                Text(item.description,color=MaterialTheme.colorScheme.onSurface)
                                                Text(item.reason,style=MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    } else {
                                        Text("${item.status}  •  ${item.date}  •  ${Money.format(item.amountLaari)}",color=MaterialTheme.colorScheme.primary)
                                        Text(item.description,color=MaterialTheme.colorScheme.onSurface)
                                        Text(item.reason,style=MaterialTheme.typography.bodySmall)
                                    }
                                }
                                if(review.matches.count { it.status==csvReviewFilter }>100) Text("Showing first 100 records in this group",color=MaterialTheme.colorScheme.onSurfaceVariant)
                                if(review.missing>0 || selectedPossibleCsvLines.isNotEmpty() && csvText!=null) {
                                    Button(onClick={confirmMissingImport=true},enabled=selectedMissingCsvLines.isNotEmpty() || selectedPossibleCsvLines.isNotEmpty()) { Text("Import ${selectedMissingCsvLines.size+selectedPossibleCsvLines.size} selected transactions") }
                                }
                                OutlinedButton(onClick={csvReview=null;csvText=null}) { Text("Close comparison") }
                            }
                        }
                    }
                    OutlinedButton(onClick={showDataReset=!showDataReset},modifier=Modifier.fillMaxWidth()) {
                        Text(if(showDataReset) "Hide transaction reset" else "Start over from a date (advanced)")
                    }
                    if(showDataReset) {
                        Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                Text("Remove transactions before a date",style=MaterialTheme.typography.titleMedium)
                                Text("Only transactions earlier than the chosen date are removed. Records on or after the date remain. This cannot be undone without a backup.",style=MaterialTheme.typography.bodySmall)
                                OutlinedTextField(value=resetDate,onValueChange={resetDate=it},label={Text("Keep transactions from (YYYY-MM-DD)")},singleLine=true,modifier=Modifier.fillMaxWidth())
                                val cutoff=runCatching { java.time.LocalDate.parse(resetDate) }.getOrNull()
                                val removeCount=if(cutoff!=null) transactions.count { it.timestamp.take(10)<resetDate } else 0
                                Text(if(cutoff!=null) "${removeCount} of ${transactions.size} transactions would be removed" else "Enter a valid date",style=MaterialTheme.typography.bodyMedium)
                                OutlinedButton(onClick={backupLauncher.launch("mib-budget-backup-before-reset.json")},modifier=Modifier.fillMaxWidth()) { Text("Export backup before reset") }
                                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                    Checkbox(checked=resetBackupAcknowledged,onCheckedChange={resetBackupAcknowledged=it})
                                    Text("I have saved a backup",style=MaterialTheme.typography.bodySmall)
                                }
                                OutlinedTextField(value=resetPhrase,onValueChange={resetPhrase=it},label={Text("Type DELETE to confirm")},singleLine=true)
                                Button(onClick={confirmReset=true},enabled=cutoff!=null && removeCount>0 && resetBackupAcknowledged && resetPhrase=="DELETE",modifier=Modifier.fillMaxWidth()) { Text("Review permanent removal") }
                            }
                        }
                    }
                    if(confirmReset) AlertDialog(onDismissRequest={confirmReset=false},title={Text("Permanently remove older transactions?")},
                        text={Text("Delete ${transactions.count { it.timestamp.take(10)<resetDate }} transactions before ${resetDate}? Existing account balance anchors will not be changed. You must set a reliable dated balance to calculate historical balances.")},
                        confirmButton={TextButton(onClick={
                            confirmReset=false
                            try {
                                val removed=store.deleteTransactionsBefore(resetDate)
                                revision++
                                resetPhrase=""
                                resetBackupAcknowledged=false
                                notice="Removed ${removed} transactions before ${resetDate}. Your backup is needed to restore them."
                            } catch(e:Exception) { notice="Reset failed: ${e.message?.take(80)}" }
                        }) { Text("Delete older transactions") }},
                        dismissButton={TextButton(onClick={confirmReset=false}) { Text("Cancel") }})
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
                            if(inserted) store.setAccount(candidate.fingerprint,"MIB")
                            notice=if(inserted) "Transaction imported to MIB" else "Duplicate transaction ignored"
                            if(inserted) revision++
                            input="";clearShared()
                        }) { Text("Confirm and save") }
                    }
                }
                3 -> {
                    Text("Monthly budgets",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                    val budgetCategories=categories.filter { it!="Income" && it!="Internal Transfer" }
                    val totalLimit=budgetCategories.sumOf { cat ->
                        budgets[cat]?.let { FinanceCalculations.effectiveLimit(
                            cat,selectedMonth,it,transactions,rolloverEnabled) } ?: 0L
                    }
                    val totalSpent=monthlyTransactions.filter { it.amountLaari<0 && it.category in budgetCategories }
                        .sumOf { -it.amountLaari }
                    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("This month's spending",style=MaterialTheme.typography.titleMedium)
                            Text(Money.format(totalSpent),style=MaterialTheme.typography.headlineMedium)
                            Text(if(totalLimit>0) "Combined category limits: ${Money.format(totalLimit)}" else "No category limits set yet",
                                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            if(totalLimit>0) {
                                LinearProgressIndicator(progress={ (totalSpent.toFloat()/totalLimit).coerceIn(0f,1f) },
                                    modifier=Modifier.fillMaxWidth())
                                if(totalSpent>totalLimit) Text("Over combined limits by ${Money.format(totalSpent-totalLimit)}",
                                    color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    var editingBudgetCategory by remember { mutableStateOf<String?>(null) }
                    var editingBudgetValue by remember { mutableStateOf("") }
                    budgetCategories.forEach { cat ->
                        val spent=monthlyTransactions.filter { it.category==cat && it.amountLaari<0 }.sumOf { -it.amountLaari }
                        val baseLimit=budgets[cat] ?: 0L
                        val limit=FinanceCalculations.effectiveLimit(
                            cat,selectedMonth,baseLimit,transactions,rolloverEnabled)
                        Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(horizontal=14.dp,vertical=12.dp),
                                verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,
                                    horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    Box(contentAlignment=androidx.compose.ui.Alignment.Center,modifier=Modifier.size(42.dp)) {
                                        CircularProgressIndicator(progress={if(limit>0) (spent.toFloat()/limit).coerceIn(0f,1f) else 0f},
                                            modifier=Modifier.fillMaxSize(),strokeWidth=4.dp)
                                        Text(if(limit>0) "${spent*100/limit}%" else "—",style=MaterialTheme.typography.labelSmall)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(cat,style=MaterialTheme.typography.titleMedium)
                                        Text("${Money.format(spent)} spent",style=MaterialTheme.typography.bodySmall)
                                        Text(if(limit>0) "${Money.format(limit)} allowance" else "No budget set",
                                            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick={
                                        editingBudgetCategory=if(editingBudgetCategory==cat) null else cat
                                        editingBudgetValue=if(baseLimit>0) "${baseLimit/100}.${(baseLimit%100).toString().padStart(2,'0')}" else ""
                                    }) { Text(if(editingBudgetCategory==cat) "Cancel" else if(limit>0) "Edit" else "Set limit") }
                                }
                                if(limit>0) LinearProgressIndicator(
                                    progress={ (spent.toFloat()/limit).coerceIn(0f,1f) },
                                    modifier=Modifier.fillMaxWidth())
                                if(limit>0 && spent>limit) Text("Over budget by ${Money.format(spent-limit)}",
                                    color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                                if(editingBudgetCategory==cat) {
                                    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,
                                        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(value=editingBudgetValue,onValueChange={editingBudgetValue=it},
                                            label={Text("Monthly limit (MVR)")},singleLine=true,
                                            modifier=Modifier.weight(1f))
                                        Button(onClick={
                                            val amount=Money.parseLaari(editingBudgetValue)
                                            if(amount!=null && amount>0) {
                                                store.setBudget(cat,amount)
                                                revision++
                                                editingBudgetCategory=null
                                                notice="$cat budget saved"
                                            } else notice="Enter a valid amount greater than zero"
                                        }) { Text("Save") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
                    val selectedTransaction=transactions.firstOrNull { it.id==selectedTransactionId }
                    if(selectedTransaction!=null) {
                        ModalBottomSheet(onDismissRequest={selectedTransactionId=null}) {
                            Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                Text("Transaction details",style=MaterialTheme.typography.headlineSmall)
                                val isTransfer=selectedTransaction.category.contains("Transfer",ignoreCase=true) ||
                                    selectedTransaction.description.contains("transfer",ignoreCase=true)
                                val savedRecipient=recipientPrefs.getString(selectedTransaction.fingerprint,null)?.trim().orEmpty()
                                val parsedLabel=TransactionPresentation.label(selectedTransaction.description)
                                val parsedRecipient=parsedLabel.substringAfter(" · ","").trim()
                                val knownRecipient=savedRecipient.ifBlank { parsedRecipient }
                                val recipient=knownRecipient.ifBlank {
                                    // SMS-only Favara records contain no recipient. Match only when one
                                    // distinct CSV statement recipient shares the exact amount and day.
                                    val candidates=transactions.filter { other ->
                                        other.id!=selectedTransaction.id &&
                                        other.amountLaari==selectedTransaction.amountLaari &&
                                        other.timestamp.take(10)==selectedTransaction.timestamp.take(10) &&
                                        other.description!=selectedTransaction.description &&
                                        (other.description.contains("Favara Transfer Web:",true) ||
                                         other.description.contains("Internet banking transfer:",true))
                                    }.mapNotNull { other ->
                                        TransactionPresentation.label(other.description).substringAfter(" · ","").trim().takeIf { it.isNotBlank() }
                                    }.distinct()
                                    candidates.singleOrNull().orEmpty()
                                }
                                Text(TransactionPresentation.label(selectedTransaction.description),style=MaterialTheme.typography.titleMedium)
                                if(!isTransfer) {
                                    val matchedProfile=remember(socialRevision,selectedTransaction.description) {
                                        VendorProfiles.get(context,selectedTransaction.description)
                                    }
                                    Text(if(matchedProfile==null) "Social profile: Not verified"
                                        else "Social profile: User-confirmed · ${matchedProfile.platform}",
                                        style=MaterialTheme.typography.bodyMedium)
                                    if(matchedProfile!=null) {
                                        Text(matchedProfile.socialUrl,style=MaterialTheme.typography.bodySmall)
                                        OutlinedButton(onClick={
                                            context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(matchedProfile.socialUrl)))
                                        }) { Text("View linked social profile") }
                                    }
                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick={
                                            socialUrl=matchedProfile?.socialUrl.orEmpty()
                                            socialEvidence=matchedProfile?.evidenceUrl.orEmpty()
                                            showSocialMatch=true
                                        }) { Text("Verify social profile") }
                                        OutlinedButton(onClick={avatarPicker.launch("image/*")}) { Text("Set vendor picture") }
                                    }
                                    if(showSocialMatch) {
                                        Text("Match: ${VendorProfiles.vendor(selectedTransaction.description)}",style=MaterialTheme.typography.titleSmall)
                                        Text("Find the business's official account and confirm it is linked from an independent official website. Similar names alone are not proof.",style=MaterialTheme.typography.bodySmall)
                                        OutlinedButton(onClick={
                                            val query=java.net.URLEncoder.encode(VendorProfiles.vendor(selectedTransaction.description)+" Maldives official Instagram Facebook","UTF-8")
                                            context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q="+query)))
                                        }) { Text("Search public social profiles") }
                                        OutlinedTextField(value=socialUrl,onValueChange={socialUrl=it.take(500)},
                                            label={Text("Social profile HTTPS URL")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                                        OutlinedTextField(value=socialEvidence,onValueChange={socialEvidence=it.take(500)},
                                            label={Text("Independent official website evidence URL")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                                        Text("Saving confirms you checked that the business website links to this exact profile. The app does not independently authenticate the link.",style=MaterialTheme.typography.bodySmall)
                                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                            TextButton(onClick={showSocialMatch=false}) { Text("Cancel") }
                                            Button(onClick={
                                                val error=VendorProfiles.validate(socialUrl.trim(),socialEvidence.trim())
                                                if(error!=null) notice=error
                                                else {
                                                    VendorProfiles.save(context,selectedTransaction.description,socialUrl.trim(),socialEvidence.trim())
                                                    socialRevision++
                                                    showSocialMatch=false
                                                    notice="Vendor profile saved as user-confirmed"
                                                }
                                            }) { Text("Confirm match") }
                                        }
                                        if(matchedProfile!=null) TextButton(onClick={
                                            VendorProfiles.remove(context,selectedTransaction.description)
                                            socialRevision++
                                            showSocialMatch=false
                                        }) { Text("Remove linked profile") }
                                    }
                                    Text("Profile pictures are assigned from your gallery after checking the vendor identity. They are not downloaded automatically from social networks.",style=MaterialTheme.typography.bodySmall)
                                }
                                if(isTransfer) {
                                    Text("Recipient: "+recipient.ifBlank { "Not identified" },
                                        style=MaterialTheme.typography.bodyLarge)
                                    Text(if(recipient.isBlank()) "The saved SMS does not contain a recipient. Add it manually or import a matching bank statement."
                                        else if(savedRecipient.isNotBlank()) "Recipient manually confirmed"
                                        else if(parsedRecipient.isNotBlank()) "Recipient from bank description"
                                        else "Possible recipient from same-date, same-amount statement; verify before relying on it.",
                                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    OutlinedButton(onClick={recipientText=savedRecipient.ifBlank { recipient };recipientEdit=true}) {
                                        Text(if(savedRecipient.isNotBlank()) "Edit recipient" else "Set recipient")
                                    }
                                }
                                Text(Money.format(selectedTransaction.amountLaari),style=MaterialTheme.typography.headlineMedium)
                                Text("Original bank description",style=MaterialTheme.typography.labelMedium)
                                Text(selectedTransaction.description,style=MaterialTheme.typography.bodySmall)
                                if(recipientEdit && isTransfer) {
                                    OutlinedTextField(value=recipientText,onValueChange={recipientText=it.take(100)},
                                        label={Text("Recipient name")},singleLine=true,modifier=Modifier.fillMaxWidth())
                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick={recipientEdit=false}) { Text("Cancel") }
                                        Button(onClick={
                                            if(recipientText.isBlank()) recipientPrefs.edit().remove(selectedTransaction.fingerprint).apply()
                                            else recipientPrefs.edit().putString(selectedTransaction.fingerprint,recipientText.trim()).apply()
                                            recipientRevision++
                                            recipientEdit=false
                                        }) { Text("Save recipient") }
                                    }
                                }
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
                                run {
                                    var showCategoryChoices by remember(selectedTransaction.id) { mutableStateOf(false) }
                                    val availableCategories = (categories +
                                        transactions.map { it.category }).filter { it.isNotBlank() }.distinct().filter { it != selectedTransaction.category }
                                    Box(Modifier.fillMaxWidth()) {
                                        OutlinedButton(onClick={showCategoryChoices=true},modifier=Modifier.fillMaxWidth()) {
                                            Text("Change category (currently ${selectedTransaction.category})")
                                        }
                                        DropdownMenu(expanded=showCategoryChoices,onDismissRequest={showCategoryChoices=false}) {
                                            availableCategories.forEach { destination ->
                                                DropdownMenuItem(text={Text(destination)},onClick={
                                                    showCategoryChoices=false
                                                    val recipient = TransactionPresentation.label(selectedTransaction.description).trim()
                                                     val savedRecipient=recipientPrefs.getString(selectedTransaction.fingerprint,null)?.trim().orEmpty()
                                                     val matching = transactions.filter { tx ->
                                                         val candidate=recipientPrefs.getString(tx.fingerprint,null)?.trim().orEmpty()
                                                             .ifBlank { TransactionPresentation.label(tx.description).trim() }
                                                         tx.amountLaari < 0 && candidate.equals(savedRecipient.ifBlank { recipient }, ignoreCase=true)
                                                     }
                                                     matching.forEach { tx -> store.setTransactionCategory(tx.id,destination) }
                                                    revision++
                                                    selectedTransactionId=null
                                                    notice="Reclassified ${matching.size} transactions from $recipient to $destination"
                                                })
                                            }
                                        }
                                    }
                                }
                                if(selectedTransaction.category.contains("Transfer",true))
                                    OutlinedButton(onClick={store.markInternal(selectedTransaction.id);revision++;selectedTransactionId=null}) { Text("Mark internal transfer") }
                                TextButton(onClick={deletingId=selectedTransaction.id;selectedTransactionId=null}) { Text("Delete transaction",color=MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
    }
}
