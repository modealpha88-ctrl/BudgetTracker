package com.example.mibbudget

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.DayOfWeek
import java.util.UUID
import kotlin.math.abs

private val planningAccountNames = listOf("MIB", "BML", "Cash")
private val planningHighlights = listOf("Safe to spend", "Forecast", "Cash", "Bills")

@Composable
private fun PlanningCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

private fun expensesByCategory(entries: List<Tx>): Map<String, Long> =
    entries.filter(FinanceCalculations::isExpense).groupBy { it.category }
        .mapValues { (_, items) -> items.sumOf { -it.amountLaari } }

private fun plannedBalances(
    context: Context, entries: List<Tx>, labels: Map<String, String>
): Map<String, Long> {
    val prefs = context.getSharedPreferences("balance_anchor", Context.MODE_PRIVATE)
    return planningAccountNames.mapNotNull { bank ->
        val key = bank.lowercase(java.util.Locale.ROOT)
        val amount = prefs.getLong("${key}_minor", Long.MIN_VALUE)
        val date = prefs.getString("${key}_date", "") ?: ""
        // A snapshot dated in the future is not a confirmed starting balance.
        if (runCatching { LocalDate.parse(date) }.getOrNull()?.isAfter(LocalDate.now()) != false)
            null
        else FinanceCalculations.projectedBalance(bank, amount, date, entries, labels)?.let { bank to it }
    }.toMap()
}

private fun upcomingBills(context: Context, store: TransactionStore, entries: List<Tx>): Long {
    val month = YearMonth.now().toString()
    return store.recurring().filter { (name, amount, category) ->
        entries.none { it.timestamp.startsWith(month) && it.amountLaari == -amount &&
            it.category == category && it.description.equals(name, ignoreCase = true) }
    }.sumOf { it.second }
}

private fun pots(context: Context): Map<String, Long> =
    context.getSharedPreferences("financial_planning", Context.MODE_PRIVATE).all
        .mapNotNull { (key, value) ->
            if (!key.startsWith("pot:") || value !is Long || value < 0) null
            else key.removePrefix("pot:") to value
        }.toMap()

/** These are configurable highlights; existing account and insight cards remain intact. */
@Composable
fun HomePlanningControls(context: Context, onChanged: () -> Unit) {
    val prefs = remember(context) { context.getSharedPreferences("dashboard_layout", Context.MODE_PRIVATE) }
    var order by remember { mutableStateOf(readHighlightOrder(prefs.getString("planning_order", null))) }
    var hidden by remember { mutableStateOf(readHiddenHighlights(prefs.all["planning_hidden"])) }
    Text("Planning highlights: visibility and order", style = MaterialTheme.typography.titleSmall)
    order.forEachIndexed { index, name ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = name !in hidden, onCheckedChange = { checked ->
                hidden = if (checked) hidden - name else hidden + name
                prefs.edit().putString("planning_hidden", hidden.joinToString("|")).apply()
                onChanged()
            })
            Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(enabled = index > 0, onClick = {
                order = order.toMutableList().also { it[index] = order[index - 1]; it[index - 1] = name }
                prefs.edit().putString("planning_order", order.joinToString("|")).apply()
                onChanged()
            }) { Text("↑") }
            TextButton(enabled = index < order.lastIndex, onClick = {
                order = order.toMutableList().also { it[index] = order[index + 1]; it[index + 1] = name }
                prefs.edit().putString("planning_order", order.joinToString("|")).apply()
                onChanged()
            }) { Text("↓") }
        }
    }
}

/** Supports the first release's StringSet preference while making new backups portable. */
private fun readHiddenHighlights(value: Any?): Set<String> = when (value) {
    is String -> value.split("|").filter { it in planningHighlights }.toSet()
    is Set<*> -> value.filterIsInstance<String>().filter { it in planningHighlights }.toSet()
    else -> emptySet()
}

private fun readHighlightOrder(value: String?): List<String> {
    val parsed = value?.split("|")?.filter { it in planningHighlights }?.distinct().orEmpty()
    return parsed + planningHighlights.filterNot { it in parsed }
}

@Composable
fun HomePlanningHighlights(
    context: Context, entries: List<Tx>, labels: Map<String, String>,
    store: TransactionStore, layoutRevision: Int
) {
    val prefs = remember(context) { context.getSharedPreferences("dashboard_layout", Context.MODE_PRIVATE) }
    val order = remember(layoutRevision) { readHighlightOrder(prefs.getString("planning_order", null)) }
    val hidden = remember(layoutRevision) { readHiddenHighlights(prefs.all["planning_hidden"]) }
    val balances = plannedBalances(context, entries, labels)
    val reserved = pots(context).values.sum()
    val bills = upcomingBills(context, store, entries)
    val month = YearMonth.now()
    val predicted = FinanceCalculations.spendingForecast(entries, month, LocalDate.now())
    order.filterNot { it in hidden }.forEach { title ->
        when (title) {
            "Safe to spend" -> PlanningCard("Safe to spend") {
                if (balances.isEmpty()) Text("Add a dated MIB, BML or Cash balance snapshot to estimate available funds.")
                else {
                    Text(Money.format((balances.values.sum() - reserved - bills).coerceAtLeast(0)),
                        style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Estimated snapshots less reserved savings pots and unpaid recurring templates. Not live bank data.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            "Forecast" -> PlanningCard("Month-end spending forecast") {
                Text(Money.format(predicted), style = MaterialTheme.typography.titleLarge)
                Text("Based on recorded month-to-date expense pace.", style = MaterialTheme.typography.bodySmall)
            }
            "Cash" -> PlanningCard("Cash wallet") {
                Text(balances["Cash"]?.let(Money::format) ?: "Cash opening balance not set",
                    style = MaterialTheme.typography.titleMedium)
                Text("Cash expenses are included only when transactions are assigned to Cash.", style = MaterialTheme.typography.bodySmall)
            }
            "Bills" -> PlanningCard("Unrecorded recurring bills") {
                Text(Money.format(bills), style = MaterialTheme.typography.titleLarge)
                Text("Templates without a matching current-month transaction.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Planning tools use the existing SQLite ledger. Settings and allocations are backed up separately. */
@Composable
fun FinancialPlanningScreen(
    context: Context,
    store: TransactionStore,
    entries: List<Tx>,
    labels: Map<String, String>,
    budgets: Map<String, Long>,
    selectedMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onChanged: () -> Unit,
    onNotice: (String) -> Unit,
    openCashAccount: () -> Unit,
    exportPdf: () -> Unit,
    sharePdf: () -> Unit,
    canSharePdf: Boolean
) {
    val prefs = remember(context) { context.getSharedPreferences("financial_planning", Context.MODE_PRIVATE) }
    val currentMonth = YearMonth.now()
    val today = LocalDate.now()
    var localRevision by remember { mutableIntStateOf(0) }
    val balances = plannedBalances(context, entries, labels)
    val currentPots = remember(localRevision, entries) { pots(context) }
    val totalReserved = currentPots.values.sum()
    val unpaidBills = upcomingBills(context, store, entries)
    val knownTotal = balances.values.sum()
    val safeToSpend = (knownTotal - totalReserved - unpaidBills).coerceAtLeast(0L)
    val missing = planningAccountNames.filterNot { it in balances }
    val unassigned = entries.count { FinanceCalculations.assignedAccount(it, labels) == "Unassigned" }
    val monthEntries = entries.filter { it.timestamp.startsWith(selectedMonth.toString()) }
    val spent = FinanceCalculations.expenseTotal(monthEntries)
    val income = FinanceCalculations.monthIncome(entries, selectedMonth)
    val forecast = FinanceCalculations.spendingForecast(entries, selectedMonth, today)
    val previousSpent = FinanceCalculations.monthExpenses(entries, selectedMonth.minusMonths(1))

    Text("Financial planning", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text("Forecasts and balances are estimates from recorded transactions, never live bank data.",
        style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        TextButton(onClick = { onMonthChange(selectedMonth.minusMonths(1)) }) { Text("‹ Previous") }
        Text(selectedMonth.toString(), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { onMonthChange(selectedMonth.plusMonths(1)) },
            enabled = selectedMonth < currentMonth) { Text("Next ›") }
    }

    PlanningCard("Safe-to-spend estimate") {
        if (balances.isEmpty()) {
            Text("Not available: save a dated balance snapshot for at least one account.")
        } else {
            Text(Money.format(safeToSpend), style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary)
            balances.forEach { (name, amount) -> Text("$name estimated: ${Money.format(amount)}") }
            Text("Less virtual savings allocations: ${Money.format(totalReserved)}")
            Text("Less unpaid recurring templates: ${Money.format(unpaidBills)}")
            if (knownTotal < totalReserved + unpaidBills)
                Text("Reserved commitments exceed the balances available to estimate.",
                    color = MaterialTheme.colorScheme.error)
        }
        if (missing.isNotEmpty()) Text("Missing balance snapshots: ${missing.joinToString()}.",
            style = MaterialTheme.typography.bodySmall)
        if (unassigned > 0) Text("$unassigned transactions are unassigned and cannot be attributed to an account.",
            style = MaterialTheme.typography.bodySmall)
        Text("Same-day bank movements may be excluded. This is not proof that funds are spendable.",
            style = MaterialTheme.typography.bodySmall)
    }

    PlanningCard("Financial calendar") {
        var selectedDate by remember(selectedMonth) {
            mutableStateOf(if (YearMonth.from(today) == selectedMonth) today else selectedMonth.atDay(1))
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("M","T","W","T","F","S","S").forEach { label ->
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val offset = selectedMonth.atDay(1).dayOfWeek.value - 1
        val days = selectedMonth.lengthOfMonth()
        repeat((offset + days + 6) / 7) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val day = week * 7 + column - offset + 1
                    if (day in 1..days) {
                        val date = selectedMonth.atDay(day)
                        val hasActivity = monthEntries.any { it.timestamp.take(10) == date.toString() }
                        TextButton(onClick = { selectedDate = date }, modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp)) {
                            Text(if (hasActivity) "$day•" else "$day",
                                color = if (date == selectedDate) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface)
                        }
                    } else Spacer(Modifier.weight(1f))
                }
            }
        }
        val daily = monthEntries.filter { it.timestamp.take(10) == selectedDate.toString() }
        Text("$selectedDate · ${daily.size} transactions", fontWeight = FontWeight.SemiBold)
        if (daily.isEmpty()) Text("No recorded activity for this day.")
        daily.take(25).forEach { tx ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tx.description.take(32), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text(Money.format(tx.amountLaari), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (daily.size > 25) Text("Showing 25 of ${daily.size} entries. See Transactions for the full day.")
    }

    PlanningCard("Spending forecast") {
        Text("Recorded spending: ${Money.format(spent)}")
        Text("Projected month-end spending: ${Money.format(forecast)}",
            style = MaterialTheme.typography.titleMedium)
        Text("Recorded income: ${Money.format(income)}")
        if (selectedMonth == currentMonth && forecast > income)
            Text("Projected spending exceeds recorded income by ${Money.format(forecast - income)}.",
                color = MaterialTheme.colorScheme.error)
        val limit = budgets.values.sum()
        if (limit > 0) {
            Text("Total category budget limits: ${Money.format(limit)}")
            LinearProgressIndicator(progress = { (forecast.toFloat() / limit).coerceIn(0f,1f) },
                modifier = Modifier.fillMaxWidth())
            if (forecast > limit) Text("Projected spending is above the combined category limits.")
        }
        Text("Straight-line estimate from elapsed days. Missing or late imports can substantially change it.",
            style = MaterialTheme.typography.bodySmall)
    }

    PlanningCard("Cash wallet") {
        val cashEntries = entries.filter { FinanceCalculations.assignedAccount(it, labels) == "Cash" }
        val cashSnapshot = balances["Cash"]
        Text(if (cashSnapshot == null) "Net recorded cash movements: ${Money.format(cashEntries.sumOf { it.amountLaari })}"
             else "Estimated cash balance: ${Money.format(cashSnapshot)}", style = MaterialTheme.typography.titleMedium)
        if (cashSnapshot == null) {
            Text("Cash movements alone do not establish your cash balance. Save a dated Cash snapshot.")
            OutlinedButton(onClick = openCashAccount) { Text("Set cash balance snapshot") }
        }
        var cashAmount by remember { mutableStateOf("") }
        var cashNote by remember { mutableStateOf("") }
        var cashExpense by remember { mutableStateOf(true) }
        var cashCategory by remember { mutableStateOf("Other") }
        var cashMenu by remember { mutableStateOf(false) }
        Row { FilterChip(selected = cashExpense, onClick = { cashExpense = true }, label = { Text("Expense") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = !cashExpense, onClick = { cashExpense = false }, label = { Text("Cash received") }) }
        OutlinedTextField(value = cashAmount, onValueChange = { cashAmount = it },
            label = { Text("Amount in MVR") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = cashNote, onValueChange = { cashNote = it.take(120) },
            label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
        if (cashExpense) Box {
            OutlinedButton(onClick = { cashMenu = true }) { Text("Category: $cashCategory ▾") }
            DropdownMenu(expanded = cashMenu, onDismissRequest = { cashMenu = false }) {
                listOf("Food","Fast Food","Groceries","Transport","Shopping","Bills","Health","Other").forEach { category ->
                    DropdownMenuItem(text = { Text(category) }, onClick = { cashCategory = category; cashMenu = false })
                }
            }
        }
        Button(onClick = {
            val value = Money.parseLaari(cashAmount)
            if (value == null || value <= 0L || cashNote.isBlank()) {
                onNotice("Enter a positive cash amount and description.")
            } else {
                val fingerprint = "manual-cash-" + UUID.randomUUID().toString()
                val amount = if (cashExpense) -value else value
                if (store.add(amount, if (cashExpense) cashCategory else "Income", cashNote.trim(), fingerprint)) {
                    store.setAccount(fingerprint, "Cash")
                    cashAmount = ""; cashNote = ""
                    onChanged()
                    onNotice("Cash transaction saved to the existing ledger.")
                } else onNotice("Cash transaction could not be saved.")
            }
        }) { Text("Record cash transaction") }
        Text("Record cash withdrawals from a bank as internal transfers, not expenses, to avoid counting them twice.",
            style = MaterialTheme.typography.bodySmall)
    }

    PlanningCard("Salary-cycle budgets") {
        var paydayText by remember { mutableStateOf(prefs.getInt("payday", 1).toString()) }
        val payday = prefs.getInt("payday", 1).coerceIn(1,28)
        val start = FinanceCalculations.cycleStart(today, payday)
        val end = start.plusMonths(1)
        val cycleEntries = FinanceCalculations.periodEntries(entries, start, end)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(value = paydayText, onValueChange = { paydayText = it.take(2) },
                modifier = Modifier.weight(1f), label = { Text("Payday (1–28)") }, singleLine = true)
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val day = paydayText.toIntOrNull()
                if (day == null || day !in 1..28) onNotice("Choose a payday between 1 and 28.")
                else { prefs.edit().putInt("payday", day).apply(); localRevision++; onChanged(); onNotice("Payday saved.") }
            }) { Text("Save") }
        }
        Text("Current salary cycle: $start to ${end.minusDays(1)}")
        val cycleSpent = FinanceCalculations.expenseTotal(cycleEntries)
        val cycleIncome = cycleEntries.filter { it.amountLaari > 0 && it.category != "Internal Transfer" }
            .sumOf { it.amountLaari }
        Text("Income: ${Money.format(cycleIncome)} · Spending: ${Money.format(cycleSpent)}")
        val cycleBudget = budgets.values.sum()
        if (cycleBudget > 0L) {
            Text("Cycle budget allowance: ${Money.format(cycleBudget)}")
            LinearProgressIndicator(progress = { (cycleSpent.toFloat() / cycleBudget).coerceIn(0f,1f) },
                modifier = Modifier.fillMaxWidth())
        }
        Text("Salary cycles use existing category limits without altering calendar-month budgets.",
            style = MaterialTheme.typography.bodySmall)
    }

    PlanningCard("Budget rollover") {
        var rolloverEnabled by remember { mutableStateOf(prefs.getBoolean("rollover", false)) }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Carry unused previous-month allowance", Modifier.weight(1f))
            Switch(checked = rolloverEnabled, onCheckedChange = {
                rolloverEnabled = it; prefs.edit().putBoolean("rollover", it).apply()
                localRevision++; onChanged()
            })
        }
        if (budgets.isEmpty()) Text("Set category budget limits in the Budgets screen to enable rollover.")
        budgets.toSortedMap().forEach { (category, limit) ->
            val carry = if (rolloverEnabled) FinanceCalculations.rollover(category,selectedMonth,limit,entries) else 0L
            val actual = monthEntries.filter { it.category == category && FinanceCalculations.isExpense(it) }
                .sumOf { -it.amountLaari }
            val effective = limit + carry
            Text("$category: ${Money.format(actual)} / ${Money.format(effective)}" +
                if (carry > 0L) " (includes ${Money.format(carry)} carried forward)" else "",
                style = MaterialTheme.typography.bodySmall)
            if (effective > 0L) LinearProgressIndicator(
                progress = { (actual.toFloat() / effective).coerceIn(0f,1f) }, modifier = Modifier.fillMaxWidth())
        }
        Text("Carryover is a planning allowance, not added money. Only the unused previous month's base limit carries once.",
            style = MaterialTheme.typography.bodySmall)
    }

    PlanningCard("Spending pattern insights") {
        val delta = spent - previousSpent
        Text("Previous month: ${Money.format(previousSpent)}")
        Text("Change: ${if(delta >= 0L) "+" else ""}${Money.format(delta)}")
        val byCategory = expensesByCategory(monthEntries)
        val previousCategories = expensesByCategory(entries.filter {
            it.timestamp.startsWith(selectedMonth.minusMonths(1).toString())
        })
        val weekend = monthEntries.filter { tx ->
            val day = runCatching { LocalDate.parse(tx.timestamp.take(10)).dayOfWeek }.getOrNull()
            day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
        }
        Text("Weekend spending: ${Money.format(FinanceCalculations.expenseTotal(weekend))}")
        val mostExpensiveDay = monthEntries.filter(FinanceCalculations::isExpense)
            .groupBy { it.timestamp.take(10) }
            .mapValues { (_, txs) -> txs.sumOf { -it.amountLaari } }
            .maxByOrNull { it.value }
        if (mostExpensiveDay != null)
            Text("Highest spending day: ${mostExpensiveDay.key} · ${Money.format(mostExpensiveDay.value)}")
        byCategory.toList().sortedByDescending { it.second }.take(5).forEach { (cat, value) ->
            val change = value - (previousCategories[cat] ?: 0L)
            Text("$cat: ${Money.format(value)} (${if(change >= 0L) "+" else ""}${Money.format(change)} vs previous month)",
                style = MaterialTheme.typography.bodySmall)
        }
        Text("Only captured transactions are included; gaps in bank SMS or statements affect comparisons.",
            style = MaterialTheme.typography.bodySmall)
    }

    PlanningCard("Spending goals & challenges") {
        val categories = (budgets.keys + entries.map { it.category } + listOf("Food","Shopping","Transport","Other"))
            .distinct().filter { it !in listOf("Income","Internal Transfer") }.sorted()
        var category by remember { mutableStateOf("Food") }
        var capText by remember { mutableStateOf("") }
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text("Target category: $category ▾") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                categories.forEach { item ->
                    DropdownMenuItem(text = { Text(item) }, onClick = { category = item; expanded = false })
                }
            }
        }
        OutlinedTextField(value = capText, onValueChange = { capText = it },
            label = { Text("Monthly spending target (MVR)") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            val amount = Money.parseLaari(capText)
            if (amount == null || amount <= 0L) onNotice("Enter a positive spending target.")
            else {
                prefs.edit().putLong("challenge:$category", amount).apply()
                capText = ""; localRevision++; onNotice("Spending challenge saved.")
            }
        }) { Text("Save spending target") }
        val challenges = prefs.all.filter { (key, value) ->
            key.startsWith("challenge:") && value is Long && value > 0L
        }.toSortedMap()
        if (challenges.isEmpty()) Text("No spending challenges set.")
        challenges.forEach { (key, value) ->
            val name = key.removePrefix("challenge:")
            val limit = value as Long
            val actual = monthEntries.filter { it.category == name && FinanceCalculations.isExpense(it) }
                .sumOf { -it.amountLaari }
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("$name: ${Money.format(actual)} / ${Money.format(limit)}",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    prefs.edit().remove(key).apply(); localRevision++; onNotice("Challenge removed.")
                }) { Text("Remove") }
            }
            LinearProgressIndicator(progress = { (actual.toFloat() / limit).coerceIn(0f,1f) },
                modifier = Modifier.fillMaxWidth())
        }
    }

    PlanningCard("Savings pots") {
        Text("Allocate a portion of tracked money to goals without moving or duplicating transactions.",
            style = MaterialTheme.typography.bodySmall)
        Text("Total reserved: ${Money.format(totalReserved)}", style = MaterialTheme.typography.titleMedium)
        currentPots.toSortedMap().forEach { (name, amount) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("$name: ${Money.format(amount)}", Modifier.weight(1f))
                TextButton(onClick = {
                    prefs.edit().remove("pot:$name").apply(); localRevision++
                    onNotice("Savings pot allocation removed.")
                }) { Text("Remove") }
            }
        }
        var potName by remember { mutableStateOf("") }
        var potAmount by remember { mutableStateOf("") }
        OutlinedTextField(value = potName, onValueChange = { potName = it.take(48) },
            label = { Text("Savings pot name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = potAmount, onValueChange = { potAmount = it },
            label = { Text("Reserved amount in MVR") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            val name = potName.trim()
            val amount = Money.parseLaari(potAmount)
            if (name.isEmpty() || amount == null || amount <= 0L || name.contains("|") || name.contains(":"))
                onNotice("Enter a valid pot name and positive amount.")
            else {
                prefs.edit().putLong("pot:$name", amount).apply()
                potName = ""; potAmount = ""; localRevision++
                onNotice("Savings allocation saved. No bank transfer was made.")
            }
        }) { Text("Save savings pot") }
    }

    PlanningCard("Monthly financial report") {
        Text("Export monthly income, spending, category breakdowns and transactions as a PDF.")
        Button(onClick = exportPdf, modifier = Modifier.fillMaxWidth()) { Text("Export ${selectedMonth} PDF") }
        OutlinedButton(onClick = sharePdf, enabled = canSharePdf,
            modifier = Modifier.fillMaxWidth()) { Text("Share last exported PDF") }
        Text("The report includes local financial information. Choose a trusted destination.",
            style = MaterialTheme.typography.bodySmall)
    }
}
