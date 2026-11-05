package com.example.mibbudget

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Reusable verified snapshot editor; no inferred card holds are ever applied. */
@Composable
internal fun VerifiedBalanceEditor(
    context: Context,
    account: String,
    onSaved: () -> Unit
) {
    val prefs = remember(context) { context.getSharedPreferences("balance_anchor", Context.MODE_PRIVATE) }
    val key = account.lowercase(Locale.ROOT)
    var statementDate by remember(account) { mutableStateOf(LocalDate.now().toString()) }
    var balanceInput by remember(account) { mutableStateOf("") }
    var reading by remember(account) { mutableStateOf(false) }
    var screenshotRecognized by remember(account) { mutableStateOf(false) }
    var status by remember(account) { mutableStateOf("") }
    val today = LocalDate.now()
    val amount = Money.parseLaari(balanceInput)
    val date = runCatching { LocalDate.parse(statementDate) }.getOrNull()
    val canSave = amount != null && amount >= 0L && date != null && !date.isAfter(today)

    val screenshotPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            reading = true
            status = ""
            screenshotRecognized = false
            val captureDate = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = androidx.exifinterface.media.ExifInterface(stream)
                    val raw = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_DIGITIZED)
                    raw?.let {
                        LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
                            .toLocalDate()
                    }
                }
            }.getOrNull()
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
            if (image == null) {
                reading = false
                recognizer.close()
                status = "Could not open the image. Enter the balance manually."
            } else {
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        val parsed = MibScreenshotParser.parse(result.text)
                        if (parsed == null) {
                            status = "The MIB account-card balance was not recognized. Enter it manually."
                        } else {
                            balanceInput = java.math.BigDecimal.valueOf(parsed.availableMinor, 2).toPlainString()
                            captureDate?.let { statementDate = it.toString() }
                            screenshotRecognized = true
                            status = "Account-card balance found. Confirm the date and amount below before saving."
                        }
                    }
                    .addOnFailureListener {
                        status = "Screenshot reading failed. Enter the balance manually."
                    }
                    .addOnCompleteListener {
                        reading = false
                        recognizer.close()
                    }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Verify $account balance",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (account == "MIB") "Use the balance on your MIB account card, not the larger ledger balance. No card holds are added or subtracted."
                else "Enter the bank or cash balance on the statement date.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            if (account == "MIB") {
                OutlinedButton(
                    onClick = { screenshotPicker.launch("image/*") },
                    enabled = !reading, modifier = Modifier.fillMaxWidth()
                ) { Text(if (reading) "Reading screenshot…" else "Import MIB screenshot") }
                if (reading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(
                value = statementDate,
                onValueChange = { statementDate = it.take(10); status = "" },
                label = { Text("Snapshot date (YYYY-MM-DD)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = balanceInput,
                onValueChange = { balanceInput = it; screenshotRecognized = false; status = "" },
                label = { Text("Available balance (MVR)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            if (amount != null && amount >= 0L) {
                Text(Money.format(amount), style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            if (screenshotRecognized) Text("Recognized from screenshot · review before saving",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("Only transactions dated after this snapshot are applied to your balance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = {
                    val saving = amount
                    val savingDate = date
                    if (saving == null || saving < 0L || savingDate == null || savingDate.isAfter(today)) {
                        status = "Enter a valid balance and a date that is not in the future."
                    } else {
                        val saved = prefs.edit()
                            .putString("${key}_date", savingDate.toString())
                            .putLong("${key}_minor", saving)
                            .remove("${key}_pending_time")
                            .remove("${key}_time")
                            .commit()
                        if (saved) {
                            status = "Verified ${Money.format(saving)} saved for $account."
                            screenshotRecognized = false
                            onSaved()
                        } else status = "Could not save the balance. Please try again."
                    }
                },
                enabled = canSave && !reading, modifier = Modifier.fillMaxWidth()
            ) { Text("Save verified balance") }
            if (status.isNotBlank()) Text(status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
