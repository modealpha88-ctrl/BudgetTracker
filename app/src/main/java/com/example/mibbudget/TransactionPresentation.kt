package com.example.mibbudget

/** Pure display-only parsing. Never modifies the original bank statement or saved transactions. */
object TransactionPresentation {
    private val timestamp=Regex("""\b\d{2}-\d{2}-\d{4}\s+(\d{2})-(\d{2})-\d{2}\b""")
    fun time(description: String, stored: String): String? {
        val bank=timestamp.find(description)?.let {
            val hour=it.groupValues[1].toIntOrNull()
            val minute=it.groupValues[2].toIntOrNull()
            if(hour!=null && hour in 0..23 && minute!=null && minute in 0..59) "%02d:%02d".format(java.util.Locale.US,hour,minute) else null
        }
        if(bank!=null) return bank
        val fromStored=Regex("""(?:T|\s)(\d{2}):(\d{2})(?::\d{2})?""").find(stored)
        return fromStored?.let {
            val h=it.groupValues[1].toInt()
            val m=it.groupValues[2].toInt()
            if(h in 0..23 && m in 0..59 && (h!=0 || m!=0)) "%02d:%02d".format(java.util.Locale.US,h,m) else null
        }
    }
    /** A manually confirmed recipient is a display identity, regardless of category. */
    fun displayLabel(description: String, category: String, savedRecipient: String?): String {
        val recipient = savedRecipient?.trim().orEmpty()
        val transfer = description.contains("transfer", ignoreCase = true) ||
            category.equals("Transfer", ignoreCase = true) ||
            category.equals("Internal Transfer", ignoreCase = true)
        return if (transfer && recipient.isNotEmpty()) "Transfer · $recipient" else label(description)
    }

    fun label(description: String): String {
        val value=description.trim()
        if(value.startsWith("Internet banking transfer:",true)) {
            val name=value.substringAfter(" | ","").substringBefore(" | ").trim()
            return "Transfer"+(name.takeIf { it.length in 3..90 && it.any(Char::isLetter) }?.let { " · "+it } ?: "")
        }
        if(value.startsWith("Favara Transfer Web:",true)) {
            val name=value.substringAfter(" | BML - ","").substringBefore(",").trim()
            return "Transfer"+(name.takeIf { it.length in 3..90 && !it.startsWith("MADV",true) }?.let { " · "+it } ?: "")
        }
        if(value.startsWith("ATM Withdrawal:",true)) return "ATM withdrawal"
        if(value.startsWith("POS Payment:",true)) {
            val merchant=value.substringAfter("@","").substringBefore(" | ").substringBefore(" VISA").substringBefore(" MASTERCARD").trim()
            return merchant.takeIf { it.isNotBlank() }?.take(48) ?: "Card payment"
        }
        return value.substringBefore(" | ").take(48).ifBlank { "Transaction" }
    }
}
