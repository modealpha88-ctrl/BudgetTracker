package com.example.mibbudget

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/**
 * A read-only comparison against a prior confirmed balance snapshot. It does not
 * silently rewrite saved bank balances or manufacture missing ledger transactions.
 */
object BankReconciliation {
    data class Report(
        val account: String,
        val statementDate: String,
        val anchorDate: String,
        val openingMinor: Long,
        val expectedMinor: Long,
        val actualMinor: Long,
        val appliedMovements: Int,
        val unassignedMovements: Int,
        val createdAt: String
    ) {
        val differenceMinor: Long get() = actualMinor - expectedMinor
    }

    fun compare(
        context: Context, account: String, dateText: String, amountMinor: Long,
        transactions: List<Tx>, labels: Map<String, String>
    ): Report {
        require(account in listOf("MIB","BML","Cash")) { "Choose a supported account" }
        val statementDate = LocalDate.parse(dateText)
        require(!statementDate.isAfter(LocalDate.now())) { "Statement date cannot be in the future" }
        val prefs=context.getSharedPreferences("balance_anchor",Context.MODE_PRIVATE)
        val key=account.lowercase(Locale.ROOT)
        val anchorDate=prefs.getString("${key}_date","").orEmpty()
        val opening=prefs.getLong("${key}_minor",Long.MIN_VALUE)
        val anchor=runCatching { LocalDate.parse(anchorDate) }.getOrNull()
        require(anchor!=null && opening!=Long.MIN_VALUE) {
            "Save an earlier dated $account balance snapshot before comparing."
        }
        require(statementDate.isAfter(anchor)) {
            "Choose a statement dated after the saved $account snapshot ($anchorDate)."
        }
        val expected=FinanceCalculations.projectedBalance(
            account,opening,anchorDate,transactions,labels,statementDate
        ) ?: throw IllegalArgumentException("Unable to project balance safely")
        val range=transactions.filter { it.timestamp.take(10)>anchorDate &&
            it.timestamp.take(10)<=statementDate.toString() }
        return Report(
            account,statementDate.toString(),anchorDate,opening,expected,amountMinor,
            range.count { FinanceCalculations.assignedAccount(it,labels)==account },
            range.count { FinanceCalculations.assignedAccount(it,labels)=="Unassigned" },
            LocalDateTime.now().toString()
        )
    }

    fun save(context: Context, report: Report) {
        val prefs=context.getSharedPreferences("reconciliation_history",Context.MODE_PRIVATE)
        val rows=JSONArray(prefs.getString("reports","[]") ?: "[]")
        val entry=JSONObject()
            .put("account",report.account).put("statementDate",report.statementDate)
            .put("anchorDate",report.anchorDate).put("opening",report.openingMinor)
            .put("expected",report.expectedMinor).put("actual",report.actualMinor)
            .put("movements",report.appliedMovements).put("unassigned",report.unassignedMovements)
            .put("createdAt",report.createdAt)
        val limited=JSONArray().put(entry)
        for(i in 0 until minOf(rows.length(),99)) limited.put(rows.getJSONObject(i))
        prefs.edit().putString("reports",limited.toString()).apply()
    }

    fun history(context: Context): List<Report> = runCatching {
        val rows=JSONArray(context.getSharedPreferences(
            "reconciliation_history",Context.MODE_PRIVATE).getString("reports","[]") ?: "[]")
        (0 until rows.length()).map { i ->
            val item=rows.getJSONObject(i)
            Report(item.getString("account"),item.getString("statementDate"),
                item.getString("anchorDate"),item.getLong("opening"),item.getLong("expected"),
                item.getLong("actual"),item.getInt("movements"),item.getInt("unassigned"),
                item.getString("createdAt"))
        }
    }.getOrDefault(emptyList())
}

@Composable
fun ReconciliationPanel(
    context: Context, account: String, transactions: List<Tx>,
    labels: Map<String,String>, onNotice: (String) -> Unit
) {
    var dateInput by remember(account) { mutableStateOf(LocalDate.now().toString()) }
    var amountInput by remember(account) { mutableStateOf("") }
    var comparison by remember(account) { mutableStateOf<BankReconciliation.Report?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    val history=remember(revision) { BankReconciliation.history(context) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Bank statement reconciliation · $account",
                style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            Text("Compare a later statement closing balance with a previously saved snapshot and intervening $account transactions. This does not change your balance.",
                style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(value=dateInput,onValueChange={ dateInput=it.take(10);comparison=null },
                label={Text("Statement date (YYYY-MM-DD)")},singleLine=true,
                modifier=Modifier.fillMaxWidth())
            OutlinedTextField(value=amountInput,onValueChange={ amountInput=it;comparison=null },
                label={Text("Statement closing balance (MVR)")},singleLine=true,
                modifier=Modifier.fillMaxWidth())
            OutlinedButton(onClick={
                val amount=Money.parseLaari(amountInput.removePrefix("-"))
                if(amount==null) onNotice("Enter a valid statement balance.")
                else try {
                    comparison=BankReconciliation.compare(context,account,dateInput,
                        if(amountInput.startsWith("-")) -amount else amount,transactions,labels)
                } catch(e:Exception) { comparison=null;onNotice(e.message ?: "Comparison failed") }
            }) { Text("Compare statement") }
            comparison?.let { report ->
                HorizontalDivider()
                Text("Saved opening: ${Money.format(report.openingMinor)} on ${report.anchorDate}")
                Text("Calculated closing: ${Money.format(report.expectedMinor)}")
                Text("Statement closing: ${Money.format(report.actualMinor)}")
                Text("Difference (statement minus calculation): ${Money.format(report.differenceMinor)}",
                    color=if(report.differenceMinor==0L) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    fontWeight=FontWeight.Bold)
                Text("${report.appliedMovements} assigned movements reviewed; ${report.unassignedMovements} unassigned movements in date range.",
                    style=MaterialTheme.typography.bodySmall)
                Text("Unassigned, missing and same-day snapshot transactions may explain discrepancies. Reconcile against original bank statements.",
                    style=MaterialTheme.typography.bodySmall)
                Button(onClick={
                    BankReconciliation.save(context,report)
                    revision++
                    comparison=null
                    onNotice("Reconciliation report saved without changing account balance")
                }) { Text("Save comparison to history") }
            }
            if(history.isNotEmpty()) {
                HorizontalDivider()
                Text("Reconciliation history",style=MaterialTheme.typography.titleSmall)
                history.filter { it.account==account }.take(12).forEach { item ->
                    Text("${item.statementDate}: Statement ${Money.format(item.actualMinor)} · Difference ${Money.format(item.differenceMinor)}",
                        style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
