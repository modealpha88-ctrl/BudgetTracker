package com.example.mibbudget

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/**
 * Privacy-first, manual-only diagnostic export.
 * No telemetry SDK, network permission or automatic transmission is used.
 * Improvement is a suggested level, NOT permission to transmit.
 */
@Composable
fun PrivacyFeedbackScreen(context: Context, transactionCount: Int, merchantRuleCount: Int = 0) {
    val prefs = remember { context.getSharedPreferences("privacy_feedback_v1", Context.MODE_PRIVATE) }
    var consent by remember { mutableStateOf(prefs.getBoolean("consent", false)) }
    var merchant by remember { mutableStateOf(prefs.getBoolean("merchant", true)) }
    var imports by remember { mutableStateOf(prefs.getBoolean("imports", true)) }
    var balances by remember { mutableStateOf(prefs.getBoolean("balances", true)) }
    var reliability by remember { mutableStateOf(prefs.getBoolean("reliability", true)) }
    var device by remember { mutableStateOf(prefs.getBoolean("device", false)) }
    var preview by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    val payload = remember(transactionCount, merchantRuleCount, merchant, imports, balances, reliability, device, note) {
        JSONObject().apply {
            put("schema_version", 1)
            put("privacy_level", "improvement")
            put("app_version", "0.2")
            put("transaction_count", transactionCount)
            put("merchant_preference_rules", merchantRuleCount)
            val metrics = JSONObject()
            if (merchant) metrics.put("merchant_recognition", "not_instrumented")
            if (imports) metrics.put("sms_csv_import", "not_instrumented")
            if (balances) metrics.put("balance_reconciliation", "not_instrumented")
            if (reliability) metrics.put("app_reliability", "not_instrumented")
            if (device) metrics.put("android_sdk", android.os.Build.VERSION.SDK_INT)
            put("diagnostics", metrics)
            // Free text can contain sensitive data: exclude it from automatic payloads.
        }.toString(2)
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Privacy & Improvement", style = MaterialTheme.typography.headlineSmall)
        Text("Improvement level selected. No information is transmitted without your explicit action. Raw SMS, transaction amounts, balances, account/card numbers and timestamps are never included.")
        Row { Checkbox(consent, onCheckedChange = { consent = it; prefs.edit().putBoolean("consent", it).apply() }); Text("I consent to preparing and sharing selected sanitized diagnostics", modifier = Modifier.padding(top = 12.dp)) }
        @Composable fun Category(title: String, value: Boolean, change: (Boolean)->Unit) {
            Row { Checkbox(value, onCheckedChange = change); Text(title, modifier = Modifier.padding(top = 12.dp)) }
        }
        Category("Merchant recognition", merchant) { merchant=it; prefs.edit().putBoolean("merchant",it).apply() }
        Category("SMS / CSV import diagnostics", imports) { imports=it; prefs.edit().putBoolean("imports",it).apply() }
        Category("Balance / reconciliation diagnostics", balances) { balances=it; prefs.edit().putBoolean("balances",it).apply() }
        Category("App reliability diagnostics", reliability) { reliability=it; prefs.edit().putBoolean("reliability",it).apply() }
        Category("Android version", device) { device=it; prefs.edit().putBoolean("device",it).apply() }
        Text("Optional private notes (not included in exported diagnostics)")
        OutlinedTextField(value=note, onValueChange={note=it}, label={Text("Notes stay on this device")}, modifier=Modifier.fillMaxWidth())
        OutlinedButton(onClick={preview = !preview}, modifier=Modifier.fillMaxWidth()) { Text(if(preview) "Hide exact payload" else "Review exact payload") }
        if (preview) {
            Text(payload, style=MaterialTheme.typography.bodySmall)
            Button(enabled=consent, onClick={
                val send=Intent(Intent.ACTION_SEND).apply {
                    type="application/json"
                    putExtra(Intent.EXTRA_TEXT,payload)
                }
                context.startActivity(Intent.createChooser(send,"Share sanitized Budget Tracker diagnostics"))
            }, modifier=Modifier.fillMaxWidth()) { Text("Share reviewed diagnostics") }
        }
        TextButton(onClick={
            prefs.edit().clear().apply()
            consent=false; merchant=true; imports=true; balances=true; reliability=true; device=false
            note=""; preview=false
        }) { Text("Revoke consent and clear feedback preferences") }
        Text("No automatic uploading is enabled. Additional event instrumentation and a secure backend are required before enabling background submissions.",style=MaterialTheme.typography.bodySmall)
    }
}
