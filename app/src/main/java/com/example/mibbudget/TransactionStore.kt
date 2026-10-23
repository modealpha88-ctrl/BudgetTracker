package com.example.mibbudget

import android.content.Context
import android.content.ContentValues
import org.json.JSONArray
import org.json.JSONObject
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import java.time.YearMonth
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ForeignTx(val id: Long, val amountMinor: Long, val currency: String, val merchant: String, val timestamp: String)

data class PendingBml(val id: Long, val amountLaari: Long, val timestamp: String, val fingerprint: String)

data class DeclinedTx(val id: Long, val amountLaari: Long, val currency: String, val merchant: String, val timestamp: String, val reason: String)

data class Tx(val id: Long, val amountLaari: Long, val category: String, val description: String, val timestamp: String, val fingerprint: String)

class TransactionStore(context: Context): SQLiteOpenHelper(context, "budget.db", null, 6) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE transactions(id INTEGER PRIMARY KEY AUTOINCREMENT, amount_laari INTEGER NOT NULL, category TEXT NOT NULL, description TEXT NOT NULL, created_at TEXT NOT NULL, fingerprint TEXT UNIQUE NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS savings_goals(name TEXT PRIMARY KEY, target_laari INTEGER NOT NULL, saved_laari INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS recurring_expenses(name TEXT PRIMARY KEY, amount_laari INTEGER NOT NULL, category TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS account_labels(fingerprint TEXT PRIMARY KEY, label TEXT NOT NULL)")
        db.execSQL("CREATE TABLE budgets(category TEXT PRIMARY KEY, limit_laari INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE declined_transactions(id INTEGER PRIMARY KEY AUTOINCREMENT, amount_laari INTEGER NOT NULL, currency TEXT NOT NULL, merchant TEXT NOT NULL, occurred_at TEXT NOT NULL, reason TEXT NOT NULL, fingerprint TEXT UNIQUE NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS bml_pending(id INTEGER PRIMARY KEY AUTOINCREMENT, amount_laari INTEGER NOT NULL, occurred_at TEXT NOT NULL, fingerprint TEXT UNIQUE NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS foreign_transactions(id INTEGER PRIMARY KEY AUTOINCREMENT, amount_minor INTEGER NOT NULL, currency TEXT NOT NULL, merchant TEXT NOT NULL, occurred_at TEXT NOT NULL, fingerprint TEXT UNIQUE NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 6) {
            db.execSQL("CREATE TABLE IF NOT EXISTS savings_goals(name TEXT PRIMARY KEY, target_laari INTEGER NOT NULL, saved_laari INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE TABLE IF NOT EXISTS recurring_expenses(name TEXT PRIMARY KEY, amount_laari INTEGER NOT NULL, category TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS account_labels(fingerprint TEXT PRIMARY KEY, label TEXT NOT NULL)")
        }
        if (oldVersion < 3) db.execSQL("CREATE TABLE IF NOT EXISTS declined_transactions(id INTEGER PRIMARY KEY AUTOINCREMENT, amount_laari INTEGER NOT NULL, currency TEXT NOT NULL, merchant TEXT NOT NULL, occurred_at TEXT NOT NULL, reason TEXT NOT NULL, fingerprint TEXT UNIQUE NOT NULL)")
        if (oldVersion < 5) db.execSQL("CREATE TABLE IF NOT EXISTS foreign_transactions(id INTEGER PRIMARY KEY AUTOINCREMENT, amount_minor INTEGER NOT NULL, currency TEXT NOT NULL, merchant TEXT NOT NULL, occurred_at TEXT NOT NULL, fingerprint TEXT UNIQUE NOT NULL)")
        if (oldVersion < 4) db.execSQL("CREATE TABLE IF NOT EXISTS bml_pending(id INTEGER PRIMARY KEY AUTOINCREMENT, amount_laari INTEGER NOT NULL, occurred_at TEXT NOT NULL, fingerprint TEXT UNIQUE NOT NULL)")
    }
    fun updateTransaction(id: Long, amount: Long, category: String, description: String): Boolean {
        if (amount == 0L || amount == Long.MIN_VALUE || description.isBlank()) return false
        val v = ContentValues().apply {
            put("amount_laari", amount); put("category", category.take(40)); put("description", description.trim().take(120))
        }
        return writableDatabase.update("transactions", v, "id=?", arrayOf(id.toString())) > 0
    }
    fun deleteTransaction(id: Long): Boolean =
        writableDatabase.delete("transactions", "id=?", arrayOf(id.toString())) > 0

    fun setAccount(fingerprint: String, label: String) {
        val v=ContentValues().apply { put("fingerprint", fingerprint);put("label", label.take(40)) }
        writableDatabase.insertWithOnConflict("account_labels",null,v,SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun accounts(): Map<String,String> {
        val out=mutableMapOf<String,String>()
        readableDatabase.rawQuery("SELECT fingerprint,label FROM account_labels",null).use { c ->
            while(c.moveToNext()) out[c.getString(0)]=c.getString(1)
        }
        return out
    }
    fun setGoal(name: String, target: Long, saved: Long) {
        require(name.isNotBlank() && target>0 && saved>=0)
        val v=ContentValues().apply { put("name",name.take(80));put("target_laari",target);put("saved_laari",saved) }
        writableDatabase.insertWithOnConflict("savings_goals",null,v,SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun goals(): List<Triple<String,Long,Long>> {
        val out=mutableListOf<Triple<String,Long,Long>>()
        readableDatabase.rawQuery("SELECT name,target_laari,saved_laari FROM savings_goals ORDER BY name",null).use { c ->
            while(c.moveToNext()) out+=Triple(c.getString(0),c.getLong(1),c.getLong(2))
        }
        return out
    }
    fun setRecurring(name: String, amount: Long, category: String) {
        require(name.isNotBlank() && amount>0)
        val v=ContentValues().apply { put("name",name.take(80));put("amount_laari",amount);put("category",category.take(40)) }
        writableDatabase.insertWithOnConflict("recurring_expenses",null,v,SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun recurring(): List<Triple<String,Long,String>> {
        val out=mutableListOf<Triple<String,Long,String>>()
        readableDatabase.rawQuery("SELECT name,amount_laari,category FROM recurring_expenses ORDER BY name",null).use { c ->
            while(c.moveToNext()) out+=Triple(c.getString(0),c.getLong(1),c.getString(2))
        }
        return out
    }
    // JSON export retains source fingerprints to prevent duplicates on restore.
    fun exportJson(): String {
        val root=JSONObject().put("format","mib-budget-backup").put("version",1)
        val db=readableDatabase
        val specs=mapOf(
            "transactions" to listOf("amount_laari","category","description","created_at","fingerprint"),
            "budgets" to listOf("category","limit_laari"),
            "foreign_transactions" to listOf("amount_minor","currency","merchant","occurred_at","fingerprint"),
            "declined_transactions" to listOf("amount_laari","currency","merchant","occurred_at","reason","fingerprint"),
            "bml_pending" to listOf("amount_laari","occurred_at","fingerprint"),
            "savings_goals" to listOf("name","target_laari","saved_laari"),
            "recurring_expenses" to listOf("name","amount_laari","category"),
            "account_labels" to listOf("fingerprint","label")
        )
        specs.forEach { (table,cols) ->
            val rows=JSONArray()
            db.query(table,cols.toTypedArray(),null,null,null,null,null).use { c ->
                while(c.moveToNext()) {
                    val item=JSONObject()
                    cols.forEachIndexed { i,key ->
                        if(c.getType(i)==android.database.Cursor.FIELD_TYPE_INTEGER) item.put(key,c.getLong(i))
                        else item.put(key,c.getString(i))
                    }
                    rows.put(item)
                }
            }
            root.put(table,rows)
        }
        return root.toString(2)
    }
    fun restoreJson(text: String): Int {
        require(text.length <= 20_000_000) { "Backup too large" }
        val root=JSONObject(text)
        require(root.optString("format")=="mib-budget-backup" && root.optInt("version")==1) { "Unsupported backup" }
        val specs=mapOf(
            "transactions" to listOf("amount_laari","category","description","created_at","fingerprint"),
            "budgets" to listOf("category","limit_laari"),
            "foreign_transactions" to listOf("amount_minor","currency","merchant","occurred_at","fingerprint"),
            "declined_transactions" to listOf("amount_laari","currency","merchant","occurred_at","reason","fingerprint"),
            "bml_pending" to listOf("amount_laari","occurred_at","fingerprint"),
            "savings_goals" to listOf("name","target_laari","saved_laari"),
            "recurring_expenses" to listOf("name","amount_laari","category"),
            "account_labels" to listOf("fingerprint","label")
        )
        // Validate every row before any database write.
        var total=0
        specs.forEach { (table,cols) ->
            val rows=root.optJSONArray(table) ?: throw IllegalArgumentException("Missing $table")
            require(rows.length()<=100000) { "Too many records" }
            for(i in 0 until rows.length()) {
                val item=rows.getJSONObject(i)
                cols.forEach { key -> require(item.has(key) && !item.isNull(key)) { "Invalid $table row" } }
            }
        }
        val db=writableDatabase
        db.beginTransaction()
        try {
            specs.forEach { (table,cols) ->
                val rows=root.getJSONArray(table)
                for(i in 0 until rows.length()) {
                    val item=rows.getJSONObject(i)
                    val v=ContentValues()
                    cols.forEach { key ->
                        val value=item.get(key)
                        when(value) {
                            is Number -> v.put(key,value.toLong())
                            is String -> v.put(key,value.take(500))
                            else -> throw IllegalArgumentException("Invalid value")
                        }
                    }
                    if(db.insertWithOnConflict(table,null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1L) total++
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return total
    }

    fun queueBml(amount: Long, timestamp: String, fingerprint: String): Boolean {
        val v = android.content.ContentValues().apply { put("amount_laari", amount); put("occurred_at", timestamp); put("fingerprint", fingerprint) }
        return writableDatabase.insertWithOnConflict("bml_pending", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }
    fun pendingBml(): List<PendingBml> {
        val result = mutableListOf<PendingBml>()
        readableDatabase.rawQuery("SELECT id,amount_laari,occurred_at,fingerprint FROM bml_pending ORDER BY id DESC", null).use { c ->
            while (c.moveToNext()) result += PendingBml(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3))
        }
        return result
    }
    fun resolveBml(tx: PendingBml, approve: Boolean, internalTransfer: Boolean = false): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (approve && !internalTransfer) {
                val v = android.content.ContentValues().apply {
                    put("amount_laari", tx.amountLaari); put("category", "Incoming Transfer")
                    put("description", "BML Favara funds received"); put("created_at", tx.timestamp)
                    put("fingerprint", tx.fingerprint)
                }
                db.insertWithOnConflict("transactions", null, v, SQLiteDatabase.CONFLICT_IGNORE)
            }
            val removed = db.delete("bml_pending", "id=?", arrayOf(tx.id.toString())) > 0
            db.setTransactionSuccessful()
            return removed
        } finally { db.endTransaction() }
    }
    fun addForeign(tx: BankSmsParser.ForeignPurchase): Boolean {
        val v = android.content.ContentValues().apply {
            put("amount_minor", tx.amountMinor); put("currency", tx.currency)
            put("merchant", tx.merchant); put("occurred_at", tx.transactionTime); put("fingerprint", tx.fingerprint)
        }
        return writableDatabase.insertWithOnConflict("foreign_transactions", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }
    fun foreign(): List<ForeignTx> {
        val result = mutableListOf<ForeignTx>()
        readableDatabase.rawQuery("SELECT id,amount_minor,currency,merchant,occurred_at FROM foreign_transactions ORDER BY occurred_at DESC", null).use { c ->
            while (c.moveToNext()) result += ForeignTx(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getString(4))
        }
        return result
    }
    fun addDeclined(candidate: BankSmsParser.Declined): Boolean {
        val values = android.content.ContentValues().apply {
            put("amount_laari", candidate.amountLaari); put("currency", candidate.currency)
            put("merchant", candidate.merchant.take(100)); put("occurred_at", candidate.transactionTime)
            put("reason", candidate.reason.take(100)); put("fingerprint", candidate.fingerprint)
        }
        return writableDatabase.insertWithOnConflict("declined_transactions", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }
    fun declined(): List<DeclinedTx> {
        val out = mutableListOf<DeclinedTx>()
        readableDatabase.rawQuery("SELECT id,amount_laari,currency,merchant,occurred_at,reason FROM declined_transactions ORDER BY occurred_at DESC", null).use { c ->
            while(c.moveToNext()) out += DeclinedTx(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5))
        }
        return out
    }
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
    fun markInternal(id: Long) { writableDatabase.execSQL("UPDATE transactions SET category=? WHERE id=?", arrayOf("Internal Transfer", id)) }
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
    fun format(laari: Long): String { val sign=if(laari<0) "-" else ""; return sign+"MVR "+String.format(Locale.US,"%,d.%02d",kotlin.math.abs(laari/100),kotlin.math.abs(laari%100)) }
}

/** MIB sample-based parser. Fail closed on ambiguous SMS. Never persist raw SMS. */
object BankSmsParser {
    data class Candidate(val amountLaari: Long, val category: String, val description: String, val fingerprint: String, val transactionTime: String? = null)
    private val sensitive = Regex("""(?i)\b(otp|one.time.password|verification code|login code|password|pin|authentication code)\b""")
    private val favara = Regex("""(?is)^Favara\s+Transfer\s+from\s+your\s+account\s+\S+\s+for\s+MVR\s+([\d,]+\.\d{2})\s+was\s+processed\s+on\s+(\d{2}/\d{2}/\d{4}\s+\d{2}:\d{2}:\d{2})\.\s+Ref\.\s*no\.\s*[\w-]+\s*$""")
    data class Declined(val amountLaari: Long, val currency: String, val merchant: String, val transactionTime: String, val reason: String, val fingerprint: String)
    private val declinedPattern = Regex("""(?is)^Your\s+last\s+transaction\s+from\s+\S+\s+for\s+([\d,]+\.\d{2})\s+([A-Z]{3})\s+at\s+(.+?),\s*[A-Z]{2}\s+on\s+(\d{2}\.\d{2}\.\d{2}\s+\d{2}:\d{2})\s+has\s+declined\s+due\s+to\s+([A-Z ]+)\.\s+Please\s+call\s+[\d ]+\s+to\s+update\s+limits\.\s+Decline\s+Code:\s*[\w-]+\s*$""")
    fun parseDeclined(raw: String): Declined? {
        val text = raw.trim()
        if (text.length !in 30..1000 || sensitive.containsMatchIn(text)) return null
        val m = declinedPattern.matchEntire(text) ?: return null
        val amount = Money.parseLaari(m.groupValues[1]) ?: return null
        val whenAt = date(m.groupValues[4], "dd.MM.uu HH:mm") ?: return null
        val merchant = m.groupValues[3].trim()
        if (amount <= 0 || merchant.length !in 2..100) return null
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(text.lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return Declined(amount, m.groupValues[2].uppercase(Locale.ROOT), merchant, whenAt, m.groupValues[5].trim(), fingerprint)
    }
    data class ForeignPurchase(val amountMinor: Long, val currency: String, val merchant: String, val transactionTime: String, val fingerprint: String)
    private val bmlCard = Regex("""(?is)^Transaction\s+from\s+\d{4}\s+on\s+(\d{2}/\d{2}/\d{2}\s+at\s+\d{2}:\d{2}:\d{2})\s+for\s+([A-Z]{3})\s*([\d,]+\.\d{2})\s+at\s+(.+?)\s+was\s+processed\.\s+Reference\s+No:\s*[\w-]+,\s*Approval\s+Code:\s*[\w-]+\.\s*$""")
    fun parseBmlCard(raw: String): ForeignPurchase? {
        val text = raw.trim()
        if (text.length !in 30..1000 || sensitive.containsMatchIn(text)) return null
        val m = bmlCard.matchEntire(text) ?: return null
        val amount = Money.parseLaari(m.groupValues[3]) ?: return null
        val whenAt = date(m.groupValues[1], "dd/MM/uu 'at' HH:mm:ss") ?: return null
        val merchant = m.groupValues[4].trim().replace(Regex("""\s+"""), " ")
        if (amount <= 0 || merchant.length !in 2..100) return null
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(text.lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return ForeignPurchase(amount, m.groupValues[2].uppercase(Locale.ROOT), merchant, whenAt, fingerprint)
    }
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
