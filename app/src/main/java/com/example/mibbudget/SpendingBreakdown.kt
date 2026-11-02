package com.example.mibbudget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
/** A selected treemap tile remains readable and opens the existing category drill-down. */
@Composable
private fun NeoTreemapTile(
    label: String,
    amount: Long,
    total: Long,
    color: Color,
    selected: Boolean,
    showLabel: Boolean,
    modifier: Modifier,
    onTap: () -> Unit
) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(color)
            .border(if (selected) 3.dp else 0.dp,
                if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                RoundedCornerShape(12.dp))
            .clickable(onClick = onTap)
            .padding(8.dp)
    ) {
        if (showLabel) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, color = Color(0xFF17202A),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${"%.0f".format(java.util.Locale.US, amount.toDouble() * 100 / total)}%",
                    color = Color(0xFF17202A),
                    style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun SpendingBreakdown(
    expenses: List<Tx>,
    initialStyle: String,
    onStyleChange: (String) -> Unit,
    onCategory: (String) -> Unit,
    onMerchant: (String, String) -> Unit,
    showTitle: Boolean = true
) {
    val total = expenses.sumOf { -it.amountLaari }
    val categories = expenses.groupBy { it.category }
        .mapValues { (_, txs) -> txs.sumOf { -it.amountLaari } }
        .toList().filter { it.second > 0L }.sortedByDescending { it.second }
    val colors = listOf(Color(0xFFB5A0FF), Color(0xFF80CBC4), Color(0xFFFFC78E),
        Color(0xFF90CAF9), Color(0xFFF48FB1), Color(0xFFA5A5A5))
    var style by remember(initialStyle) { mutableStateOf(if(initialStyle=="List") "List" else "Treemap") }
    var selectedCategory by remember(expenses) { mutableStateOf<String?>(null) }
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
        if(showTitle) Text("Spending by category", style=MaterialTheme.typography.titleSmall)
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
            if (style == "Treemap") {
                // True two-dimensional treemap: the largest category owns a proportional
                // left column; remaining categories share a weighted right-hand stack.
                // Very small tiles remain tappable without drawing overlapping text.
                val leading = slices.first()
                val rest = slices.drop(1)
                val remainingAmount = rest.sumOf { it.second }
                Row(
                    Modifier.fillMaxWidth().height(214.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NeoTreemapTile(
                        label = leading.first, amount = leading.second, total = total,
                        color = colors[0], selected = selectedCategory == leading.first,
                        showLabel = true,
                        modifier = Modifier.weight(leading.second.toFloat().coerceAtLeast(1f)).fillMaxHeight(),
                        onTap = {
                            selectedCategory = if (selectedCategory == leading.first) null else leading.first
                            if (leading.first == "Other categories") expanded = true
                        }
                    )
                    if (remainingAmount > 0L) {
                        Column(
                            Modifier.weight(remainingAmount.toFloat()).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rest.forEachIndexed { index, (name, amount) ->
                                NeoTreemapTile(
                                    label = name, amount = amount, total = total,
                                    color = colors[(index + 1) % colors.size],
                                    selected = selectedCategory == name,
                                    showLabel = amount.toDouble() / total >= 0.07,
                                    modifier = Modifier.fillMaxWidth().weight(amount.toFloat().coerceAtLeast(1f)),
                                    onTap = {
                                        selectedCategory = if (selectedCategory == name) null else name
                                        if (name == "Other categories") expanded = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
            if(style!="List") Text(selectedCategory?.let { "Selected: $it · Tap a category below for merchant details." } ?: "Select a tile or category to explore merchants and transactions.",
                style=MaterialTheme.typography.labelSmall)
            val shown=if(expanded || style=="List") categories else slices
            shown.forEachIndexed { index, (cat, amount) ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if(selectedCategory==cat) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { selectedCategory=if(selectedCategory==cat) null else cat }
                    .padding(horizontal=10.dp,vertical=9.dp),
                    verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("● $cat  ›",color=if(selectedCategory==cat) MaterialTheme.colorScheme.primary else colors[index%colors.size],
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
