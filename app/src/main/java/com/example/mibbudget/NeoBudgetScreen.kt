package com.example.mibbudget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.YearMonth

/** Neo Banking Pro monthly budgets, extracted to keep BudgetApp's generated JVM method small. */
@Composable
internal fun NeoBudgetScreen(
    categories: List<String>,
    budgets: Map<String, Long>,
    selectedMonth: YearMonth,
    transactions: List<Tx>,
    monthlyTransactions: List<Tx>,
    rolloverEnabled: Boolean,
    onSaveBudget: (String, Long) -> Unit,
    onBudgetError: (String) -> Unit
) {
                    Text("BUDGETS • PLAN YOUR SPENDING",style=MaterialTheme.typography.labelLarge,
                        color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                    Text("Monthly budgets",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold)
                    Text("Category limits, progress and rollover",style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    val budgetCategories=categories.filter { it!="Income" && it!="Internal Transfer" }
                    val totalLimit=budgetCategories.sumOf { cat ->
                        budgets[cat]?.let { FinanceCalculations.effectiveLimit(
                            cat,selectedMonth,it,transactions,rolloverEnabled) } ?: 0L
                    }
                    val totalSpent=monthlyTransactions.filter { it.amountLaari<0 && it.category in budgetCategories }
                        .sumOf { -it.amountLaari }
                    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(25.dp),
                        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.fillMaxWidth().background(
                            Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surfaceContainerHigh)))
                            .padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(),
                                verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                                    Text("MONTHLY SPENDING",style=MaterialTheme.typography.labelMedium,
                                        fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
                                    Text(Money.format(totalSpent),style=MaterialTheme.typography.headlineMedium,
                                        fontWeight=FontWeight.ExtraBold)
                                    Text(if(totalLimit>0) "Of ${Money.format(totalLimit)} in category limits" else "No category limits set yet",
                                        style=MaterialTheme.typography.bodySmall,
                                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if(totalLimit>0) Box(Modifier.size(76.dp),
                                    contentAlignment=androidx.compose.ui.Alignment.Center) {
                                    CircularProgressIndicator(
                                        progress={ (totalSpent.toFloat()/totalLimit).coerceIn(0f,1f) },
                                        modifier=Modifier.fillMaxSize(),strokeWidth=7.dp,
                                        color=if(totalSpent>totalLimit) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.primary
                                    )
                                    Text("${(totalSpent.toDouble()*100/totalLimit).toInt()}%",
                                        style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold)
                                }
                            }
                            if(totalLimit>0 && totalSpent>totalLimit)
                                Text("Over combined limits by ${Money.format(totalSpent-totalLimit)}",
                                    color=MaterialTheme.colorScheme.error,
                                    style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    var editingBudgetCategory by remember { mutableStateOf<String?>(null) }
                    var editingBudgetValue by remember { mutableStateOf("") }
                    budgetCategories.forEach { cat ->
                        val spent=monthlyTransactions.filter { it.category==cat && it.amountLaari<0 }.sumOf { -it.amountLaari }
                        val baseLimit=budgets[cat] ?: 0L
                        val limit=FinanceCalculations.effectiveLimit(
                            cat,selectedMonth,baseLimit,transactions,rolloverEnabled)
                        val accent = when(cat) {
                            "Food", "Fast Food" -> Color(0xFFFFB47D)
                            "Groceries" -> Color(0xFF73D4AF)
                            "Shopping" -> Color(0xFFBD9CFA)
                            "Transport" -> Color(0xFF84CAFC)
                            "Bills" -> Color(0xFF7FD0D3)
                            else -> MaterialTheme.colorScheme.primary
                        }
                        val symbol = when(cat) {
                            "Food", "Fast Food" -> "◈"
                            "Groceries" -> "▧"
                            "Shopping" -> "▣"
                            "Transport" -> "⇄"
                            "Bills" -> "▤"
                            "Health" -> "♥"
                            else -> "◉"
                        }
                        Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(19.dp),
                            colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)) {
                            Column(Modifier.padding(horizontal=14.dp,vertical=11.dp),
                                verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,
                                    horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    Box(contentAlignment=androidx.compose.ui.Alignment.Center,
                                        modifier=Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha=0.12f))) {
                                        if(limit>0) CircularProgressIndicator(
                                            progress={ (spent.toFloat()/limit).coerceIn(0f,1f) },
                                            modifier=Modifier.fillMaxSize(),strokeWidth=3.dp,color=accent)
                                        Text(symbol,style=MaterialTheme.typography.titleMedium,color=accent,
                                            fontWeight=FontWeight.Bold)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(cat,style=MaterialTheme.typography.titleMedium)
                                        Text("${Money.format(spent)} spent",style=MaterialTheme.typography.bodySmall)
                                        Text(if(limit>0) "${Money.format(limit)} allowance · ${(spent.toDouble()*100/limit).toInt()}% used" else "No budget set",
                                            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick={
                                        editingBudgetCategory=if(editingBudgetCategory==cat) null else cat
                                        editingBudgetValue=if(baseLimit>0) "${baseLimit/100}.${(baseLimit%100).toString().padStart(2,'0')}" else ""
                                    }) { Text(if(editingBudgetCategory==cat) "Cancel" else if(limit>0) "Edit" else "Set limit") }
                                }
                                if(limit>0) LinearProgressIndicator(
                                    progress={ (spent.toFloat()/limit).coerceIn(0f,1f) },
                                    modifier=Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                    color=accent,
                                    trackColor=MaterialTheme.colorScheme.surfaceContainerHigh)
                                if(limit>0 && spent>limit) Text("Over budget by ${Money.format(spent-limit)}",
                                    color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                                if(editingBudgetCategory==cat) {
                                    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,
                                        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(value=editingBudgetValue,onValueChange={editingBudgetValue=it},
                                            label={Text("Monthly limit (MVR)")},singleLine=true,
                                            modifier=Modifier.weight(1f))
                                        Button(onClick={
                                            val amount=Money.parseLaari(editingBudgetValue)
                                            if(amount!=null && amount>0) {
                                                onSaveBudget(cat, amount)
                                                editingBudgetCategory=null
                                            } else onBudgetError("Enter a valid amount greater than zero")
                                        }) { Text("Save") }
                                    }
                                }
                            }
                        }
                    }
}
