package com.example.mibbudget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** One deterministic, category-sensitive icon for Home and both Activity lists. */
@Composable
internal fun NeoTransactionIcon(tx: Tx) {
    val category = tx.category.lowercase()
    // Explicit spending category takes priority over the bank's transfer template.
    val accent = when {
        category.contains("shopping") -> Color(0xFFAD91F1)
        category.contains("food") || category.contains("grocer") || category.contains("dining") -> Color(0xFFF5BF87)
        category.contains("transport") || category.contains("travel") -> Color(0xFF8CBDF1)
        category.contains("bill") || category.contains("utilit") -> Color(0xFF7BCBC3)
        tx.amountLaari > 0L || VendorProfiles.isTransfer(tx.description, tx.category) ->
            MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
    Box(
        Modifier.size(43.dp).clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        val icon = MerchantIcons.icon(tx.description, tx.category)
        Text(if (icon == "↗" && tx.amountLaari > 0L) "↙" else icon,
            style = MaterialTheme.typography.titleLarge)
    }
}
