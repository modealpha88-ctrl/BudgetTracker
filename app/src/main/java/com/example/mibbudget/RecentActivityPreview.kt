package com.example.mibbudget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Separate activity list to avoid the 64KB JVM method limit in BudgetApp. */
@Composable
internal fun RecentActivityPreview(transactions: List<Tx>, onOpen: (Long) -> Unit) {
    Text("Recent transactions", style = MaterialTheme.typography.titleMedium)
    transactions.sortedByDescending { it.timestamp }.take(4).forEach { tx ->
        Card(
            onClick = { onOpen(tx.id) },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(MerchantIcons.icon(tx.description, tx.category), style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f)) {
                    Text(tx.description, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                    Text(tx.timestamp.take(16), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(Money.format(tx.amountLaari), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
