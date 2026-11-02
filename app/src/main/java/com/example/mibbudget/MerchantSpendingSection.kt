package com.example.mibbudget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Read-only ranking of merchant purchases already present in the transaction ledger. */
internal object MerchantSpending {
    data class Summary(val name: String, val amountLaari: Long, val transactions: List<Tx>) {
        val count: Int get() = transactions.size
    }

    fun merchantName(tx: Tx): String? {
        if (tx.amountLaari >= 0 || VendorProfiles.isTransfer(tx.description, tx.category)) return null
        val name = VendorProfiles.vendor(tx.description).replace(Regex("""\s+"""), " ")
            .trim().uppercase(Locale.ROOT)
        if (name.isEmpty() || name in setOf("TRANSACTION", "ATM WITHDRAWAL", "CARD PAYMENT", "PURCHASE")) return null
        return name
    }

    /** null month = all available history. Never mutates transactions or balance. */
    fun summarize(transactions: List<Tx>, month: YearMonth?): List<Summary> =
        transactions.asSequence()
            .filter { month == null || it.timestamp.startsWith(month.toString()) }
            .mapNotNull { tx -> merchantName(tx)?.let { it to tx } }
            .groupBy({ it.first }, { it.second })
            .map { (name, entries) ->
                Summary(name, entries.sumOf { -it.amountLaari }, entries.sortedByDescending { it.timestamp })
            }
            .sortedWith(compareByDescending<Summary> { it.amountLaari }.thenBy { it.name })
}

@Composable
internal fun TopMerchantsSection(transactions: List<Tx>, onOpenTransaction: (Long) -> Unit) {
    val currentMonth = remember { YearMonth.now() }
    val months = remember(transactions) {
        (transactions.mapNotNull { runCatching { YearMonth.parse(it.timestamp.take(7)) }.getOrNull() }
            + currentMonth).distinct().sortedDescending()
    }
    var period by remember { mutableStateOf(currentMonth.toString()) }
    var menuOpen by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<String?>(null) }
    var shown by remember { mutableIntStateOf(6) }
    val month = if (period == "all") null else runCatching { YearMonth.parse(period) }.getOrNull()
    val ranked = remember(transactions, period) { MerchantSpending.summarize(transactions, month) }
    val total = ranked.sumOf { it.amountLaari }.coerceAtLeast(1L)
    val formatter = remember { DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Most spent merchants", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text("Purchases only · transfers excluded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                OutlinedButton(onClick = { menuOpen = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(if (period == "all") "All time" else month?.format(formatter) ?: period)
                    Text(" ▾")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("All time") }, onClick = {
                        period = "all"; menuOpen = false; chosen = null
                    })
                    months.forEach { option ->
                        DropdownMenuItem(text = { Text(option.format(formatter)) }, onClick = {
                            period = option.toString(); menuOpen = false; chosen = null
                        })
                    }
                }
            }
        }
        if (ranked.isEmpty()) {
            Card(Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(18.dp)) {
                Text("No merchant purchases for this period. Choose another month or All time.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            ranked.take(if (expanded) 30 else 5).forEachIndexed { index, merchant ->
                val selected = chosen == merchant.name
                val percent = merchant.amountLaari.toDouble() / total.toDouble()
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow
                    )) {
                    Column(Modifier.fillMaxWidth().clickable {
                        chosen = if (selected) null else merchant.name
                        shown = 6
                    }.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${index + 1}", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val sample = merchant.transactions.first()
                            Text(MerchantIcons.icon(sample.description, sample.category),
                                style = MaterialTheme.typography.titleLarge)
                            Column(Modifier.weight(1f)) {
                                Text(merchant.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("${merchant.count} transactions · ${(percent * 100).toInt()}% of merchant spend",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(Money.format(merchant.amountLaari),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        LinearProgressIndicator(progress = { percent.toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth())
                        if (selected) {
                            HorizontalDivider()
                            Text("Transactions at ${merchant.name}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold)
                            merchant.transactions.take(shown).forEach { tx ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text(neoDayHeading(tx.timestamp.take(10)),
                                            style = MaterialTheme.typography.bodySmall)
                                        Text(tx.category, style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(Money.format(tx.amountLaari),
                                        style = MaterialTheme.typography.bodySmall)
                                    TextButton(onClick = { onOpenTransaction(tx.id) }) { Text("Open") }
                                }
                            }
                            if (merchant.count > shown)
                                TextButton(onClick = { shown += 10 }) {
                                    Text("Show more · ${merchant.count - shown} remaining")
                                }
                        } else {
                            Text("Tap to view transactions", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (ranked.size > 5)
                TextButton(onClick = { expanded = !expanded; chosen = null },
                    modifier = Modifier.align(Alignment.End)) {
                    Text(if (expanded) "Show top 5" else "Show more merchants · ${ranked.size}")
                }
            if (expanded && ranked.size > 30)
                Text("Showing the top 30 merchants", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
