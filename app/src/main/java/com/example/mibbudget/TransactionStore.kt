package com.example.mibbudget

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import java.time.YearMonth
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Tx(val id: Long, val amountLaari: Long, val category: String, val description: String, val timestamp: String, val fingerprint: String)

class TransactionStore(context: Context): SQLiteOpenHelper(context, "budget.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE transactions(id INTEGER PRIMARY KEY AUTOINCREMENT, amount_laari INTEGER NOT NULL, category TEXT NOT NULL, description TEXT NOT NULL, created_at TEXT NOT NULL, fingerprint TEXT UNIQUE NOT NULL)")
        db.execSQL("CREATE TABLE budgets(category TEXT PRIMARY KEY, limit_laari INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit // Version 2 retains the existing schema; imported dates use created_at.
    fun add(amountLaari: Long, category: String, description: String, fingerprint: String = "manual-${java.util.UUID.randomUUID()}", transactionTime: String? = null): Boolean {
        require(amountLaari != 0L && amountLaari != Long.MIN_VALUE)
        val db = writableDatabase
        val values = android.content.ContentValues().apply {
            put("amount_laari", amountLaari); put("category", category.take(40)); put("description", description.take(120)); put("created_at", (transactionTime ?: LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))); put("fingerprint", fingerprint)
        }
        return db.insertWithOnConflict("transactions", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }
    fun all(): List<Tx> {
        val out = mutableListOf<Tx>()
        readableDatabase.rawQuery("SELECT id,amount_laari,category,description,created_at,fingerprint FROM transactions ORDER BY id DESC", null).use { c ->
            while(c.moveToNext()) out += Tx(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5))
        }
        return out
    }
    fun forMonth(month: YearMonth): List<Tx> = all().filter { it.timestamp.startsWith(month.toString()) }
    fun budgets(): Map<String,Long> {
        val result = mutableMapOf<String,Long>()
        readableDatabase.rawQuery("SELECT category,limit_laari FROM budgets",null).use { c -> while(c.moveToNext()) result[c.getString(0)] = c.getLong(1) }
        return result
    }
    fun setBudget(category: String, limit: Long) {
        require(limit > 0)
        val v = android.content.ContentValues().apply { put("category",category);put("limit_laari",limit) }
        writableDatabase.insertWithOnConflict("budgets",null,v,SQLiteDatabase.CONFLICT_REPLACE)
    }
}

object Money {
    fun parseLaari(text: String): Long? {
        if (!text.matches(Regex("""\d{1,12}(?:,\d{3})*(?:\.\d{1,2})?"""))) return null
        val pieces = text.replace(",", "").split(".")
        val whole = pieces[0].toLongOrNull() ?: return null
        val fractional = pieces.getOrNull(1)?.padEnd(2,'0')?.toLongOrNull() ?: 0L
        if (whole > (Long.MAX_VALUE - fractional)/100) return null
        return whole*100+fractional
    }
    fun format(laari: Long): String = "MVR " + String.format(Locale.US,"%,d.%02d",kotlin.math.abs(laari/100),kotlin.math.abs(laari%100))
}

/** MIB sample-based parser. Fail closed on ambiguous SMS. Never persist raw SMS. */
object BankSmsParser {
    data class Candidate(val amountLaari: Long, val category: String, val description: String, val fingerprint: String, val transactionTime: String? = null)
    private val sensitive = Regex("""(?i)\b(otp|one.time.password|verification code|login code|password|pin|authentication code)\b""")
    private val favara = Regex("""(?is)^Favara\s+Transfer\s+from\s+your\s+account\s+\S+\s+for\s+MVR\s+([\d,]+\.\d{2})\s+was\s+processed\s+on\s+(\d{2}/\d{2}/\d{4}\s+\d{2}:\d{2}:\d{2})\.\s+Ref\.\s*no\.\s*[\w-]+\s*$""")
    private val favaraIncoming = Regex("""(?is)^Favara\s+Transfer\s+to\s+your\s+account\s+\S+\s+for\s+MVR\s+([\d,]+\.\d{2})\s+was\s+processed\s+on\s+(\d{2}/\d{2}/\d{4}\s+\d{2}:\d{2}:\d{2})\.\s+Ref\.\s*no\.\s*[\w-]+\s*$""")
    private val pos = Regex("""(?is)^Your\s+POS\s+PURCHASE\s+from\s+\S+\s+for\s+([\d,]+\.\d{2})\s+MVR\s+at\s+(.+?),\s*MV\s+on\s+(\d{2}\.\d{2}\.\d{2}\s+\d{2}:\d{2})\s+was\s+processed\s+successfully\.\s+Approval\s+Code:\s*[\w-]+\s*$""")
    private fun date(value: String, pattern: String): String? = try {
        val formatter = DateTimeFormatter.ofPattern(pattern, Locale.US).withResolverStyle(java.time.format.ResolverStyle.STRICT)
        LocalDateTime.parse(value.trim(), formatter).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    } catch (_: Exception) { null }
    fun parse(raw: String): Candidate? {
        val text = raw.trim()
        if (text.length !in 30..1000 || sensitive.containsMatchIn(text)) return null
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(text.lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        favaraIncoming.matchEntire(text)?.let { m ->
            val amount = Money.parseLaari(m.groupValues[1]) ?: return null
            val whenAt = date(m.groupValues[2], "dd/MM/uuuu HH:mm:ss") ?: return null
            if (amount <= 0) return null
            return Candidate(amount, "Incoming Transfer", "Favara incoming transfer", fingerprint, whenAt)
        }
        favara.matchEntire(text)?.let { m ->
            val amount = Money.parseLaari(m.groupValues[1]) ?: return null
            val whenAt = date(m.groupValues[2], "dd/MM/uuuu HH:mm:ss") ?: return null
            if (amount <= 0) return null
            return Candidate(-amount, "Transfers", "Favara transfer", fingerprint, whenAt)
        }
        pos.matchEntire(text)?.let { m ->
            val amount = Money.parseLaari(m.groupValues[1]) ?: return null
            val merchant = m.groupValues[2].trim().replace(Regex("""\s+"""), " ")
            val whenAt = date(m.groupValues[3], "dd.MM.uu HH:mm") ?: return null
            if (amount <= 0 || merchant.length !in 2..70) return null
            // Merchant category is unknown until the user confirms it.
            return Candidate(-amount, MerchantCategories.classify(merchant), "POS purchase: $merchant", fingerprint, whenAt)
        }
        return null
    }
}

/** Conservative keyword categorization; unknown merchants stay Other. */
object MerchantCategories {
    fun classify(merchant: String): String {
        val s = merchant.uppercase(Locale.ROOT)
        return when {
            listOf("CAFE", "RESTAURANT", "COFFEE", "PIZZA", "BAKERY", "HOTEL DINING").any { s.contains(it) } -> "Food"
            listOf("SUPERMARKET", "GROCERY", "MART", "FIHAARA", "STO ").any { s.contains(it) } -> "Groceries"
            listOf("FUEL", "PETROL", "TAXI", "FERRY", "TRANSPORT").any { s.contains(it) } -> "Transport"
            listOf("OOREDOO", "DHIRAAGU", "ELECTRIC", "WATER", "INTERNET").any { s.contains(it) } -> "Bills"
            listOf("PHARMACY", "CLINIC", "HOSPITAL").any { s.contains(it) } -> "Health"
            else -> "Other"
        }
    }
}
