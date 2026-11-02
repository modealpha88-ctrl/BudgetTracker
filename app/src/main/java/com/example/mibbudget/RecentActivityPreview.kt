package com.example.mibbudget

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

internal fun neoDayHeading(dayKey: String): String {
    val date = runCatching { LocalDate.parse(dayKey) }.getOrNull()
    return when (date) {
        LocalDate.now() -> "Today"
        LocalDate.now().minusDays(1) -> "Yesterday"
        null -> dayKey
        else -> "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${date.year}"
    }
}

/** Read-only, date-grouped Activity preview. Search doesn't alter stored transactions. */
@Composable
internal fun RecentActivityPreview(
    transactions: List<Tx>,
    savedRecipient: (Tx) -> String? = { null },
    query: String = "",
    filter: String = "All",
    onOpen: (Long) -> Unit
) {
    val search = query.trim()
    val matching = transactions.asSequence().filter { tx ->
        when (filter) {
            "Expenses" -> tx.amountLaari < 0L
            "Income" -> tx.amountLaari > 0L
            "Transfers" -> VendorProfiles.isTransfer(tx.description, tx.category)
            else -> true
        }
    }.filter { tx ->
        if (search.isEmpty()) true else {
            val recipient = savedRecipient(tx)
            val name = TransactionPresentation.displayLabel(tx.description, tx.category, recipient)
            listOf(name, tx.description, tx.category, tx.timestamp, Money.format(tx.amountLaari))
                .any { it.contains(search, ignoreCase = true) }
        }
    }.sortedByDescending { it.timestamp }
        .take(if (search.isEmpty() && filter == "All") 6 else 20).toList()

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(if (search.isEmpty() && filter == "All") "Recent transactions" else "Matching transactions",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (search.isNotEmpty() || filter != "All")
            Text("${matching.size} shown", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (matching.isEmpty()) {
        Text("No matching transactions.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    matching.groupBy { it.timestamp.take(10) }.forEach { (dayKey, dailyTransactions) ->
        val dayTitle = neoDayHeading(dayKey)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(dayTitle, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
            Text("${dailyTransactions.size} transaction${if (dailyTransactions.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        dailyTransactions.forEach { tx ->
            NeoTransactionRow(tx, savedRecipient(tx)) { onOpen(tx.id) }
        }
        Spacer(Modifier.height(3.dp))
    }
}
