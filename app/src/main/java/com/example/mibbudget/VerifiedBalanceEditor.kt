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
    var statementTime by remember(account) { mutableStateOf("") }
    var captureSource by remember(account) { mutableStateOf("") }
    var balanceInput by remember(account) { mutableStateOf("") }
    var reading by remember(account) { mutableStateOf(false) }
    var screenshotRecognized by remember(account) { mutableStateOf(false) }
    var status by remember(account) { mutableStateOf("") }
    val today = LocalDate.now()
    val amount = Money.parseLaari(balanceInput)
    val date = runCatching { LocalDate.parse(statementDate) }.getOrNull()
    val validTime = statementTime.isBlank() || runCatching { java.time.LocalTime.parse(statementTime) }.isSuccess
    val canSave = amount != null && amount >= 0L && date != null && !date.isAfter(today) && validTime

    val screenshotPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            reading = true
            status = ""
            screenshotRecognized = false
            // Original EXIF creation time wins. File name is a fallback for screenshots without EXIF.
            // Content-provider modification time is NOT reliable capture metadata.
            val exifCapture = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = androidx.exifinterface.media.ExifInterface(stream)
                    val raw = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_DIGITIZED)
                    raw?.let { LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")) }
                }
            }.getOrNull()
            val screenshotName = runCatching {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()
            val filenameCapture = ScreenshotCaptureTime.fromFilename(screenshotName)
            val captured = exifCapture ?: filenameCapture
            val capturedFrom = when {
                exifCapture != null -> "EXIF capture timestamp"
                filenameCapture != null -> "Screenshot filename timestamp"
                else -> ""
            }
            statementTime = ""
            captureSource = ""
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
                            if (captured != null && !captured.toLocalDate().isAfter(today)) {
                                statementDate = captured.toLocalDate().toString()
                                statementTime = captured.toLocalTime().withNano(0).toString()
                                captureSource = capturedFrom
                            }
                            screenshotRecognized = true
                            status = if (statementTime.isNotBlank())
                                "Account-card balance found; $captureSource detected. Confirm the capture date, time and amount."
                            else "Account-card balance found, but capture time was unavailable. Enter the time manually to include later same-day transactions."
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
                onValueChange = { statementDate = it.take(10); statementTime = ""; captureSource = ""; status = "" },
                label = { Text("Snapshot date (YYYY-MM-DD)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = statementTime,
                onValueChange = { statementTime = it.take(8); captureSource = ""; status = "" },
                label = { Text("Snapshot capture time (HH:mm:ss, optional)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            if (captureSource.isNotBlank()) Text("Capture time source: $captureSource",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
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
            Text(if (statementTime.isBlank())
                    "Date-only snapshot: same-day transactions are excluded. Add a verified capture time to include transactions later that day."
                else "Transactions after $statementDate $statementTime are applied, including later transactions on the same day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = {
                    val saving = amount
                    val savingDate = date
                    if (saving == null || saving < 0L || savingDate == null || savingDate.isAfter(today) || !validTime) {
                        status = "Enter a valid balance and a date that is not in the future."
                    } else {
                        val saved = prefs.edit()
                            .putString("${key}_date", savingDate.toString())
                            .putLong("${key}_minor", saving)
                            .remove("${key}_pending_time")
                            .putString("${key}_time", statementTime)
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
