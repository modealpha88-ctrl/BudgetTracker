package com.example.mibbudget

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One parser/storage path for live delivery, automatic recovery and manual history. */
object BankSmsImporter {
    enum class Outcome { IMPORTED, DUPLICATE, IGNORED }
    private val changes = MutableStateFlow(0L)
    val revision = changes.asStateFlow()

    @Synchronized
    fun importBody(store: TransactionStore, body: String): Outcome {
        val declined = BankSmsParser.parseDeclined(body)
        val foreign = if (declined == null) BankSmsParser.parseBmlCard(body) else null
        val inserted = when {
            declined != null -> store.addDeclined(declined)
            foreign != null -> store.addForeign(foreign)
            else -> {
                val candidate = SalarySmsParser.parse(body) ?: BankSmsParser.parse(body)
                    ?: return Outcome.IGNORED
                store.add(candidate.amountLaari, candidate.category, candidate.description,
                    candidate.fingerprint, candidate.transactionTime, sourceAccount = "MIB")
            }
        }
        if (inserted) changes.value++
        return if (inserted) Outcome.IMPORTED else Outcome.DUPLICATE
    }
}
