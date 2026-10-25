package com.example.mibbudget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Read-only spending visualisations. Categories always sum to recorded expenses.
 * Drill-down is delegated to the existing transaction filters.
 */
@Composable
fun SpendingBreakdown(
    expenses: List<Tx>,
    initialStyle: String,
    onStyleChange: (String) -> Unit,
    onCategory: (String) -> Unit,
    onMerchant: (String, String) -> Unit
) {
    val total = expenses.sumOf { -it.amountLaari }
    val categories = expenses.groupBy { it.category }
        .mapValues { (_, txs) -> txs.sumOf { -it.amountLaari } }
        .toList().filter { it.second > 0L }.sortedByDescending { it.second }
    val colors = listOf(Color(0xFFB5A0FF), Color(0xFF80CBC4), Color(0xFFFFC78E),
        Color(0xFF90CAF9), Color(0xFFF48FB1), Color(0xFFA5A5A5))
    var style by remember(initialStyle) { mutableStateOf(if(initialStyle=="List") "List" else "Treemap") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val visible = categories.take(5)
    val remainder = categories.drop(5).sumOf { it.second }
    val slices = if (remainder > 0L) visible + ("Other categories" to remainder) else visible
    val merchantEntries = selectedCategory?.let { cat ->
        expenses.filter { it.category == cat }
            .groupBy { VendorProfiles.key(it.description) }
            .mapValues { (_, rows) -> rows.sumOf { -it.amountLaari } }
            .toList().sortedByDescending { it.second }
    } ?: emptyList()

    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Spending breakdown", style=MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("Treemap", "List").forEach { choice ->
                FilterChip(selected=style==choice, onClick={
                    style=choice
                    selectedCategory=null
                    onStyleChange(choice)
                }, label={Text(choice)})
            }
        }
        if(total <= 0L) {
            Text("No recorded expenses for this month.")
        } else {
            if(style=="Treemap") {
                // A single row of proportional tiles cannot overlap or produce zero-sized labels.
                Row(Modifier.fillMaxWidth().height(126.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                    slices.forEachIndexed { index, (cat, amount) ->
                        Box(Modifier.weight(amount.toFloat().coerceAtLeast(1f)).fillMaxHeight()
                            .background(colors[index%colors.size],RoundedCornerShape(5.dp))
                            .clickable { if(cat=="Other categories") { selectedCategory=cat; expanded=true } else onCategory(cat) }) {
                            if(amount.toDouble()/total>=0.12)
                                Text("${cat.take(12)}\n${"%.0f".format(java.util.Locale.US,amount.toDouble()*100/total)}%",
                                    modifier=Modifier.padding(5.dp),color=Color(0xFF15121B),
                                    style=MaterialTheme.typography.labelSmall,
                                    maxLines=2,overflow=TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if(style!="List") Text("Tap a tile to open category transactions. Tap a category below for merchant details.",
                style=MaterialTheme.typography.labelSmall)
            val shown=if(expanded || style=="List") categories else slices
            shown.forEachIndexed { index, (cat, amount) ->
                Row(Modifier.fillMaxWidth().clickable { selectedCategory=if(selectedCategory==cat) null else cat }
                    .padding(vertical=3.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("● $cat  ›",color=colors[index%colors.size],
                        style=MaterialTheme.typography.bodySmall,modifier=Modifier.weight(1f),
                        maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text("${"%.1f".format(java.util.Locale.US,amount.toDouble()*100/total)}%",
                        style=MaterialTheme.typography.bodySmall)
                }
                if(style=="List") LinearProgressIndicator(
                    progress={ (amount.toFloat()/total).coerceIn(0f,1f) },
                    modifier=Modifier.fillMaxWidth())
            }
            if(categories.size>5 && style!="List") TextButton(onClick={expanded=!expanded}) {
                Text(if(expanded) "Show fewer categories ↑" else "Show all categories ↓")
            }
            selectedCategory?.let { cat ->
                HorizontalDivider()
                Text("$cat",style=MaterialTheme.typography.titleSmall)
                if(cat=="Other categories") {
                    categories.drop(5).forEach { (name, amount) ->
                        TextButton(onClick={onCategory(name)},modifier=Modifier.fillMaxWidth()) {
                            Text("$name · ${Money.format(amount)} →")
                        }
                    }
                } else {
                    TextButton(onClick={onCategory(cat)}) { Text("View $cat transactions →") }
                    val shownMerchants=if(expanded) merchantEntries else merchantEntries.take(5)
                    shownMerchants.forEach { (merchant, amount) ->
                        Row(Modifier.fillMaxWidth().clickable { onMerchant(cat,merchant) }
                            .padding(vertical=5.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text(merchant,modifier=Modifier.weight(1f),maxLines=1,
                                overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)
                            Text(Money.format(amount),style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    if(merchantEntries.size>5 && !expanded) TextButton(onClick={expanded=true}) {
                        Text("Show all merchants ↓")
                    }
                }
            }
        }
    }
}
