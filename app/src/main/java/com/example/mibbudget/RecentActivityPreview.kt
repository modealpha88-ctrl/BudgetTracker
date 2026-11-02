package com.example.mibbudget

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

/** Compact activity-first presentation, with full transaction details on tap. */
@Composable
internal fun RecentActivityPreview(
    transactions: List<Tx>,
    savedRecipient: (Tx) -> String? = { null },
    onOpen: (Long) -> Unit
) {
    Text("Recent transactions", style = MaterialTheme.typography.titleMedium)
    transactions.sortedByDescending { it.timestamp }.take(5).forEach { tx ->
        NeoTransactionRow(tx, savedRecipient(tx)) { onOpen(tx.id) }
    }
}
