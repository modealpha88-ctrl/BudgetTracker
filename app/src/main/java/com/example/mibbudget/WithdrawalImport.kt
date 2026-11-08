package com.example.mibbudget

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.*

@Composable
internal fun WithdrawalImport(
    sms: BankSmsParser.Candidate,
    store: TransactionStore,
    revision: Int,
    onResult: (BankSmsImporter.Outcome) -> Unit
) {
    val rows = remember(sms, revision) { store.withdrawalCandidates(sms) }
    var selectedId by remember(sms, rows) { mutableStateOf<Long?>(null) }
    Column {
        Text("MIB → Cash · ${Money.format(-sms.amountLaari)}")
        Text("${sms.atm} · ${sms.transactionTime?.replace('T', ' ')}")
        if (rows.isNotEmpty()) {
            Text("Choose the matching MIB statement debit, or record a separate withdrawal.")
            rows.forEach { row ->
                FilterChip(selected = selectedId == row.id, onClick = { selectedId = row.id },
                    label = { Text("${row.timestamp} · ${row.description}") })
            }
            FilterChip(selected = selectedId == 0L, onClick = { selectedId = 0L },
                label = { Text("Separate withdrawal") })
        }
        Button(enabled = rows.isEmpty() || selectedId != null, onClick = {
            onResult(store.importWithdrawal(sms, selectedId))
        }) { Text("Confirm withdrawal") }
    }
}
