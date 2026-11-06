package com.example.mibbudget

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Diagnostics contain counts and timestamps only, never message text. */
object ImportHealth {
    private val changes = MutableStateFlow(0L)
    val revision = changes.asStateFlow()
    fun success(context: Context, scanned: Int, imported: Int, duplicates: Int, ignored: Int) {
        context.getSharedPreferences("import_health", Context.MODE_PRIVATE).edit()
            .putString("success", java.time.LocalDateTime.now().toString().take(19))
            .putInt("scanned", scanned).putInt("imported", imported).putInt("duplicates", duplicates)
            .putInt("ignored", ignored).remove("error").apply()
        changes.value++
    }
    fun failure(context: Context, message: String) {
        context.getSharedPreferences("import_health", Context.MODE_PRIVATE).edit().putString("error", message).apply()
        changes.value++
    }
}
