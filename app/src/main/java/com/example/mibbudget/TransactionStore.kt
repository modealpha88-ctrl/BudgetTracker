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

class TransactionStore(context: Context): SQLiteOpenHelper(context, "budget.db", null, 8) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE transactions(id INTEGER PRIMARY KEY AUTOINCREMENT, amount_laari INTEGER NOT NULL, category TEXT NOT NULL, description TEXT NOT NULL, created_at TEXT NOT NULL, fingerprint TEXT UNIQUE NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS savings_goals(name TEXT PRIMARY KEY, target_laari INTEGER NOT NULL, saved_laari INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS recurring_expenses(name TEXT PRIMARY KEY, amount_laari INTEGER NOT NULL, category TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS account_labels(fingerprint TEXT PRIMARY KEY, label TEXT NOT NULL)")
        db.execSQL("CREATE TABLE budgets(category TEXT PRIMARY KEY, limit_laari INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS merchant_category_rules(merchant_key TEXT PRIMARY KEY, category TEXT NOT NULL)")
        db.execSQL("CREATE TABLE declined_transactions(id INTEGER PRIMARY KEY AUTOINCREMENT, amount_laari INTEGER NOT NULL, currency TEXT NOT NULL, merchant TEXT NOT NULL, occurred_at TEXT NOT NULL, reason TEXT NOT NULL, fingerprint TEXT UNIQUE NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS bml_pending(id INTEGER PRIMARY KEY AUTOINCREMENT, amount_laari INTEGER NOT NULL, occurred_at TEXT NOT NULL, fingerprint TEXT UNIQUE NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS foreign_transactions(id INTEGER PRIMARY KEY AUTOINCREMENT, amount_minor INTEGER NOT NULL, currency TEXT NOT NULL, merchant TEXT NOT NULL, occurred_at TEXT NOT NULL, fingerprint TEXT UNIQUE NOT NULL)")
        TransactionAudit.install(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 7) db.execSQL("CREATE TABLE IF NOT EXISTS merchant_category_rules(merchant_key TEXT PRIMARY KEY, category TEXT NOT NULL)")
        if (oldVersion < 6) {
            db.execSQL("CREATE TABLE IF NOT EXISTS savings_goals(name TEXT PRIMARY KEY, target_laari INTEGER NOT NULL, saved_laari INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE TABLE IF NOT EXISTS recurring_expenses(name TEXT PRIMARY KEY, amount_laari INTEGER NOT NULL, category TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS account_labels(fingerprint TEXT PRIMARY KEY, label TEXT NOT NULL)")
        }
        if (oldVersion < 3) db.execSQL("CREATE TABLE IF NOT EXISTS declined_transactions(id INTEGER PRIMARY KEY AUTOINCREMENT, amount_laari INTEGER NOT NULL, currency TEXT NOT NULL, merchant TEXT NOT NULL, occurred_at TEXT NOT NULL, reason TEXT NOT NULL, fingerprint TEXT UNIQUE NOT NULL)")
        if (oldVersion < 5) db.execSQL("CREATE TABLE IF NOT EXISTS foreign_transactions(id INTEGER PRIMARY KEY AUTOINCREMENT, amount_minor INTEGER NOT NULL, currency TEXT NOT NULL, merchant TEXT NOT NULL, occurred_at TEXT NOT NULL, fingerprint TEXT UNIQUE NOT NULL)")
        if (oldVersion < 4) db.execSQL("CREATE TABLE IF NOT EXISTS bml_pending(id INTEGER PRIMARY KEY AUTOINCREMENT, amount_laari INTEGER NOT NULL, occurred_at TEXT NOT NULL, fingerprint TEXT UNIQUE NOT NULL)")
        if (oldVersion < 8) TransactionAudit.install(db)
    }


    // Parse RFC-style quoted CSV including escaped quotes, commas and embedded newlines.
    private fun parseCsvRecords(text: String): List<List<String>> {
        val rows=mutableListOf<List<String>>()
        var row=mutableListOf<String>()
        val field=StringBuilder()
        var quoted=false
        var i=0
        while(i<text.length) {
            val ch=text[i]
            when {
                ch=='"' && quoted && i+1<text.length && text[i+1]=='"' -> { field.append('"');i++ }
                ch=='"' -> { quoted=!quoted }
                ch==',' && !quoted -> { row.add(field.toString());field.setLength(0) }
                (ch=='\n' || ch=='\r') && !quoted -> {
                    row.add(field.toString());field.setLength(0)
                    if(row.any { it.isNotBlank() }) rows.add(row)
                    row=mutableListOf()
                    if(ch=='\r' && i+1<text.length && text[i+1]=='\n') i++
                }
                else -> field.append(ch)
            }
            i++
        }
        require(!quoted) { "Unclosed CSV quotation" }
        row.add(field.toString())
        if(row.any { it.isNotBlank() }) rows.add(row)
        return rows
    }


    data class StatementBalance(val amountLaari: Long, val date: String, val reference: String, val time: String? = null, val confidence: String = "Date only; end-of-day assumed")

    /** The final non-opening-balance row is the statement's closing balance. */
    fun statementBalance(text: String): StatementBalance? {
        require(text.length<=5_000_000) { "CSV too large" }
        val rows=parseCsvRecords(text.removePrefix("\uFEFF"))
        val expected=listOf("POSTED DATE","VALUE DATE","TRANSACTION TYPE","REFERENCE","DESCRIPTION","AMOUNT","RUNNING BALANCE")
        if(rows.firstOrNull()?.map { it.trim().uppercase(Locale.ROOT) }!=expected) return null
        require(rows.size<=10001) { "Too many rows" }
        val entries=rows.drop(1).filter { it.size==7 && !it[2].trim().startsWith("**B/F",true) }
        val last=entries.lastOrNull() ?: return null
        val raw=last[6].trim().replace(",","")
        val amount=Money.parseLaari(raw.removePrefix("-").removePrefix("+")) ?: return null
        val date=runCatching { java.time.LocalDate.parse(last[0].trim().take(10)).toString() }.getOrNull() ?: return null
        // The final running balance is associated with this row, not the CSV file's
        // filesystem creation/modification time (which can be export/download time).
        // Only trust a clock actually embedded in the statement row.
        val dateField=last[0].trim()
        val embeddedClock=Regex("""(?:T|\\s)(\\d{2}:\\d{2}(?::\\d{2})?)""").find(dateField)?.groupValues?.get(1)
            ?.let { runCatching { java.time.LocalTime.parse(it).toString() }.getOrNull() }
        return StatementBalance(if(raw.startsWith("-")) -amount else amount,date,last[3].trim(),embeddedClock,
            if(embeddedClock!=null) "Transaction timestamp in CSV" else "Date only; end-of-day assumed")
    }

    data class CsvMatch(val line: Int, val amountLaari: Long, val date: String, val description: String,
                        val status: String, val reason: String)
    data class CsvReview(val matches: List<CsvMatch>, val confirmed: Int, val possible: Int, val missing: Int, val ignored: Int)

    private fun normalizedWords(value: String): Set<String> =
        value.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9 ]")," ").split(Regex("\\s+"))
            .filter { it.length>=4 && it !in setOf("TRANSFER","FAVARA","PURCHASE","TRANSACTION","PROCESSED","FROM","ACCOUNT","BANK","PAYMENT","MVR") }.toSet()

    private fun dayDistance(a: String, b: String): Long? = try {
        kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(
            java.time.LocalDate.parse(a.take(10)), java.time.LocalDate.parse(b.take(10))))
    } catch(e:Exception) { null }

    /** Read-only reconciliation. A possible match is never silently imported. */
    fun reviewCsv(text: String): CsvReview {
        require(text.length<=5_000_000) { "CSV too large" }
        val records=parseCsvRecords(text.removePrefix("\uFEFF"))
        require(records.isNotEmpty() && records.size<=10001) { "Empty or oversized CSV" }
        val header=records.first().map { it.trim().uppercase(Locale.ROOT) }
        val mib=header==listOf("POSTED DATE","VALUE DATE","TRANSACTION TYPE","REFERENCE","DESCRIPTION","AMOUNT","RUNNING BALANCE")
        val simple=header==listOf("DATE","AMOUNT","DESCRIPTION","CATEGORY","ACCOUNT")
        require(mib || simple) { "Unsupported CSV statement" }
        val existing=all()
        val assigned=mutableSetOf<Long>()
        val results=mutableListOf<CsvMatch>()
        var ignored=0
        records.drop(1).forEachIndexed { index,cols ->
            require(cols.size==header.size) { "Invalid CSV row ${index+2}" }
            val raw=(if(mib) cols[5] else cols[1]).trim().replace(",","")
            val amount=Money.parseLaari(raw.removePrefix("-").removePrefix("+"))
                ?: throw IllegalArgumentException("Invalid amount at row ${index+2}")
            val type=if(mib) cols[2].trim() else ""
            if(amount==0L || (mib && type.startsWith("**B/F",true))) { ignored++;return@forEachIndexed }
            val signed=if(raw.startsWith("-")) -amount else amount
            val date=java.time.LocalDate.parse(cols[0].trim().take(10)).toString()
            val desc=if(mib) listOf(type,cols[4].trim()).filter { it.isNotBlank() }.joinToString(": ") else cols[2].trim()
            val reference=if(mib) cols[3].trim() else ""
            val nearby=existing.filter { t ->
                t.amountLaari==signed &&
                (dayDistance(date,t.timestamp)?.let { it<=2 }==true)
            }
            val candidates=nearby.filter { it.id !in assigned }
            val exact=candidates.filter { t ->
                t.timestamp.take(10)==date &&
                (reference.length>=6 && t.description.contains(reference,true) ||
                 normalizedWords(desc).intersect(normalizedWords(t.description)).isNotEmpty() ||
                 (t.fingerprint.startsWith("csv-") && t.description.equals(desc,true)))
            }
            val status: String
            val reason: String
            val chosen: Tx?
            when {
                exact.size==1 -> { status="MATCHED";reason="Same amount, date and reference/description";chosen=exact.first() }
                exact.size>1 -> { status="POSSIBLE";reason="Multiple matching records";chosen=null }
                candidates.isNotEmpty() -> { status="POSSIBLE";reason="Same amount and nearby date; verify merchant/reference";chosen=null }
                nearby.isNotEmpty() -> { status="POSSIBLE";reason="Amount and date overlap a record already matched to another statement row";chosen=null }
                else -> { status="MISSING";reason="No matching saved transaction";chosen=null }
            }
            if(chosen!=null) assigned.add(chosen.id)
            results.add(CsvMatch(index+2,signed,date,desc.take(120),status,reason))
        }
        return CsvReview(results,results.count { it.status=="MATCHED" },
            results.count { it.status=="POSSIBLE" },results.count { it.status=="MISSING" },ignored)
    }


    /** Only inserts records still classified MISSING at commit time. Never imports POSSIBLE. */
    fun importMissingCsv(text: String, selectedLines: Set<Int>? = null, includePossible: Boolean = false): Pair<Int,Int> {
        val db=writableDatabase
        var added=0
        var excluded=0
        db.beginTransaction()
        try {
            // Reconcile again under the write transaction so prior imports are respected.
            val review=reviewCsv(text)
            val rows=review.matches.filter { (it.status=="MISSING" || (includePossible && it.status=="POSSIBLE")) && (selectedLines==null || it.line in selectedLines) }
            excluded=review.matches.size-rows.size
            val records=parseCsvRecords(text.removePrefix("\uFEFF"))
            val header=records.first().map { it.trim().uppercase(Locale.ROOT) }
            val mib=header.firstOrNull()=="POSTED DATE"
            val already=mutableSetOf<String>()
            rows.forEach { row ->
                val cols=records[row.line-1]
                val canonical=if(mib) listOf(cols[0].trim(),cols[2].trim(),cols[3].trim(),cols[4].trim(),cols[5].trim().replace(",","")).joinToString("|")
                    else cols.joinToString("|") { it.trim() }
                val hash=MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
                val fingerprint="csv-"+hash
                if(!already.add(fingerprint)) { excluded++;return@forEach }
                val type=if(mib) cols[2].trim() else ""
                val category=if(!mib) cols[3].trim().ifBlank { "Other" }.take(40)
                    else when {
                        row.amountLaari>0 -> "Income"
                        type.contains("POS",true) -> MerchantCategories.classify(row.description).takeUnless { it=="Other" } ?: "Shopping"
                        type.contains("ATM",true) -> "Cash"
                        type.contains("Favara",true) || type.contains("transfer",true) -> "Transfers"
                        else -> "Other"
                    }
                val timestamp=if(mib) cols[0].trim().replace(' ','T') else row.date+"T12:00:00"
                val v=ContentValues().apply {
                    put("amount_laari",row.amountLaari);put("category",category)
                    put("description",row.description);put("created_at",timestamp);put("fingerprint",fingerprint)
                }
                if(db.insertWithOnConflict("transactions",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1L) {
                    added++
                    val label=ContentValues().apply { put("fingerprint",fingerprint);put("label",if(mib) "MIB" else cols[4].trim().take(40)) }
                    db.insertWithOnConflict("account_labels",null,label,SQLiteDatabase.CONFLICT_REPLACE)
                } else excluded++
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return added to excluded
    }

    fun importCsv(text: String): Pair<Int,Int> {
        require(text.length<=5_000_000) { "CSV too large" }
        val records=parseCsvRecords(text.removePrefix("\uFEFF"))
        require(records.isNotEmpty() && records.size<=10001) { "Empty or oversized CSV" }
        val header=records.first().map { it.trim().uppercase(Locale.ROOT) }
        val mib=header==listOf("POSTED DATE","VALUE DATE","TRANSACTION TYPE","REFERENCE","DESCRIPTION","AMOUNT","RUNNING BALANCE")
        val simple=header==listOf("DATE","AMOUNT","DESCRIPTION","CATEGORY","ACCOUNT")
        require(mib || simple) { "Unsupported CSV columns. Expected MIB statement or date,amount,description,category,account" }
        data class ImportRow(val amount:Long,val category:String,val description:String,val time:String,val account:String,val fingerprint:String)
        var skippedNonTransactions=0
        val parsed=mutableListOf<ImportRow>()
        records.drop(1).forEachIndexed { index,cols ->
            require(cols.size==header.size) { "Invalid CSV row ${index+2}" }
            val amountText=(if(mib) cols[5] else cols[1]).trim().replace(",","")
            val sign=amountText.startsWith("-")
            val amount=Money.parseLaari(amountText.removePrefix("-").removePrefix("+"))
                ?: throw IllegalArgumentException("Invalid amount on row ${index+2}")
            val kind=if(mib) cols[2].trim() else ""
            if(amount==0L || (mib && kind.startsWith("**B/F",true))) {
                skippedNonTransactions++
            } else {
                val date=try { java.time.LocalDate.parse(cols[0].trim().take(10)) }
                    catch(e:Exception) { throw IllegalArgumentException("Invalid date on row ${index+2}") }
                val description=if(mib) {
                    listOf(kind,cols[4].trim()).filter { it.isNotBlank() }.joinToString(": ").take(120)
                } else cols[2].trim().take(120)
                require(description.isNotBlank()) { "Missing description on row ${index+2}" }
                val category=if(mib) {
                    when {
                        !sign && (kind.contains("salary",true)) -> "Income"
                        !sign -> "Income"
                        kind.contains("POS",true) -> "Shopping"
                        kind.contains("ATM",true) -> "Cash"
                        kind.contains("Favara",true) || kind.contains("transfer",true) -> "Transfers"
                        kind.contains("FaisaPay",true) -> "Bills"
                        else -> "Other"
                    }
                } else cols[3].trim().ifBlank { "Other" }.take(40)
                val account=if(mib) "MIB" else cols[4].trim().take(40)
                val time=if(mib) cols[0].trim().replace(' ','T') else date.toString()+"T12:00:00"
                // Stable row fingerprint; repeated import of the same statement is idempotent.
                val canonical=if(mib) listOf(cols[0].trim(),cols[2].trim(),cols[3].trim(),cols[4].trim(),amountText).joinToString("|")
                    else cols.joinToString("|") { it.trim() }
                val digest=MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
                val fingerprint="csv-"+digest.joinToString("") { "%02x".format(it) }
                parsed.add(ImportRow(if(sign) -amount else amount,category,description,time,account,fingerprint))
            }
        }
        val db=writableDatabase
        var imported=0
        db.beginTransaction()
        try {
            parsed.forEach { row ->
                val v=ContentValues().apply {
                    put("amount_laari",row.amount);put("category",row.category)
                    put("description",row.description);put("created_at",row.time);put("fingerprint",row.fingerprint)
                }
                if(db.insertWithOnConflict("transactions",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1L) {
                    imported++
                    val label=ContentValues().apply { put("fingerprint",row.fingerprint);put("label",row.account) }
                    db.insertWithOnConflict("account_labels",null,label,SQLiteDatabase.CONFLICT_REPLACE)
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return imported to (parsed.size-imported+skippedNonTransactions)
    }
    fun updateTransaction(id: Long, amount: Long, category: String, description: String): Boolean {
        if (amount == 0L || amount == Long.MIN_VALUE || description.isBlank()) return false
        val v = ContentValues().apply {
            put("amount_laari", amount); put("category", category.take(40)); put("description", description.trim().take(120))
        }
        return writableDatabase.update("transactions", v, "id=?", arrayOf(id.toString())) > 0
    }
    /** Removes only records earlier than the chosen ISO date. Caller must confirm and back up. */
    fun deleteTransactionsBefore(date: String): Int {
        require(Regex("""[0-9]{4}-[0-9]{2}-[0-9]{2}""").matches(date))
        java.time.LocalDate.parse(date)
        val db=writableDatabase
        db.beginTransaction()
        return try {
            val removed=db.delete("transactions","substr(created_at,1,10) < ?",arrayOf(date))
            db.setTransactionSuccessful()
            removed
        } finally { db.endTransaction() }
    }

    fun deleteTransaction(id: Long): Boolean =
        writableDatabase.delete("transactions", "id=?", arrayOf(id.toString())) > 0

    /** Review-only exact timestamp/amount matches; account and reference conflicts remain manual. */
    fun exactDuplicateGroups(): List<List<Tx>> {
        val rows=mutableListOf<Tx>()
        readableDatabase.rawQuery("SELECT id,amount_laari,category,description,created_at,fingerprint FROM transactions",null).use { cursor ->
            while(cursor.moveToNext()) rows.add(Tx(cursor.getLong(0),cursor.getLong(1),cursor.getString(2),cursor.getString(3),cursor.getString(4),cursor.getString(5)))
        }
        return rows.filter { Regex("""\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}""").containsMatchIn(it.timestamp) }
            .groupBy { it.timestamp.take(19).replace(' ','T') to it.amountLaari }
            .values.filter { it.size>1 }.sortedByDescending { it.size }
    }

    /** Archive removed records in the same SQLite transaction, allowing a later undo. */
    fun mergeExactDuplicates(ids: List<Long>): Int {
        require(ids.size>=2 && ids.distinct().size==ids.size)
        val db=writableDatabase
        db.beginTransaction()
        try {
            val entries=mutableListOf<Tx>()
            ids.forEach { id ->
                db.rawQuery("SELECT id,amount_laari,category,description,created_at,fingerprint FROM transactions WHERE id=?",arrayOf(id.toString())).use { cursor ->
                    require(cursor.moveToFirst()) { "Transaction no longer exists" }
                    entries.add(Tx(cursor.getLong(0),cursor.getLong(1),cursor.getString(2),cursor.getString(3),cursor.getString(4),cursor.getString(5)))
                }
            }
            require(entries.map { it.amountLaari }.distinct().size==1 && entries.map { it.timestamp.take(19).replace(' ','T') }.distinct().size==1) { "Exact time and amount must match" }
            require(entries.all { Regex("""\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}""").containsMatchIn(it.timestamp) })
            val labels=entries.map { tx ->
                db.rawQuery("SELECT label FROM account_labels WHERE fingerprint=?",arrayOf(tx.fingerprint)).use { cur -> if(cur.moveToFirst()) cur.getString(0) else "" }
            }.filter { it.isNotBlank() }.distinct()
            require(labels.size<=1) { "Different accounts: review manually" }
            db.execSQL("CREATE TABLE IF NOT EXISTS duplicate_merge_archive (id INTEGER PRIMARY KEY, amount_laari INTEGER, category TEXT, description TEXT, created_at TEXT, fingerprint TEXT, kept_id INTEGER)")
            val keep=entries.first()
            entries.drop(1).forEach { tx ->
                val values=ContentValues().apply { put("id",tx.id);put("amount_laari",tx.amountLaari);put("category",tx.category);put("description",tx.description);put("created_at",tx.timestamp);put("fingerprint",tx.fingerprint);put("kept_id",keep.id) }
                check(db.insertOrThrow("duplicate_merge_archive",null,values)>0)
                check(db.delete("transactions","id=?",arrayOf(tx.id.toString()))==1)
            }
            // Preserve source descriptions in the archive; never overwrite bank-origin data.
            db.setTransactionSuccessful()
            return entries.size-1
        } finally { db.endTransaction() }
    }

    fun undoDuplicateMerge(keptId: Long): Int {
        val db=writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS duplicate_merge_archive (id INTEGER PRIMARY KEY, amount_laari INTEGER, category TEXT, description TEXT, created_at TEXT, fingerprint TEXT, kept_id INTEGER)")
            var restored=0
            db.rawQuery("SELECT id,amount_laari,category,description,created_at,fingerprint FROM duplicate_merge_archive WHERE kept_id=?",arrayOf(keptId.toString())).use { cur ->
                while(cur.moveToNext()) {
                    val v=ContentValues().apply { put("id",cur.getLong(0));put("amount_laari",cur.getLong(1));put("category",cur.getString(2));put("description",cur.getString(3));put("created_at",cur.getString(4));put("fingerprint",cur.getString(5)) }
                    db.insertOrThrow("transactions",null,v)
                    restored++
                }
            }
            db.delete("duplicate_merge_archive","kept_id=?",arrayOf(keptId.toString()))
            db.setTransactionSuccessful()
            return restored
        } finally { db.endTransaction() }
    }

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
            "account_labels" to listOf("fingerprint","label"),
            "merchant_category_rules" to listOf("merchant_key","category"),
            "transaction_audit_history" to TransactionAudit.backupColumns
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
    data class BackupPreview(
        val tableCounts: Map<String,Int>,
        val existingTransactions: Int,
        val newTransactions: Int
    )

    /** Validate the complete SQLite backup structure without writing any data. */
    fun previewJson(text: String): BackupPreview {
        require(text.length <= 20_000_000) { "Backup too large" }
        val root=JSONObject(text)
        require(root.optString("format")=="mib-budget-backup" &&
            root.optInt("version")==1) { "Unsupported backup" }
        val specs=mapOf(
            "transactions" to listOf("amount_laari","category","description","created_at","fingerprint"),
            "budgets" to listOf("category","limit_laari"),
            "foreign_transactions" to listOf("amount_minor","currency","merchant","occurred_at","fingerprint"),
            "declined_transactions" to listOf("amount_laari","currency","merchant","occurred_at","reason","fingerprint"),
            "bml_pending" to listOf("amount_laari","occurred_at","fingerprint"),
            "savings_goals" to listOf("name","target_laari","saved_laari"),
            "recurring_expenses" to listOf("name","amount_laari","category"),
            "account_labels" to listOf("fingerprint","label"),
            "merchant_category_rules" to listOf("merchant_key","category"),
            "transaction_audit_history" to TransactionAudit.backupColumns
        )
        val counts=linkedMapOf<String,Int>()
        val incomingFingerprints=mutableSetOf<String>()
        specs.forEach { (table,cols) ->
            val rows=root.optJSONArray(table) ?: if(table in
                setOf("merchant_category_rules","transaction_audit_history")) JSONArray()
                else throw IllegalArgumentException("Missing $table")
            require(rows.length()<=100000) { "Too many $table records" }
            counts[table]=rows.length()
            for(i in 0 until rows.length()) {
                val item=rows.getJSONObject(i)
                cols.forEach { key ->
                    require(item.has(key) && !item.isNull(key)) { "Invalid $table row" }
                    require(item.get(key) is String || item.get(key) is Number) {
                        "Unsupported $table value"
                    }
                }
                if(table=="transactions") {
                    val fingerprint=item.getString("fingerprint")
                    require(fingerprint.isNotBlank()) { "Missing transaction fingerprint" }
                    incomingFingerprints.add(fingerprint)
                }
            }
        }
        val existing=mutableSetOf<String>()
        readableDatabase.rawQuery("SELECT fingerprint FROM transactions",null).use { c ->
            while(c.moveToNext()) existing.add(c.getString(0))
        }
        val duplicates=incomingFingerprints.count { it in existing }
        return BackupPreview(counts,duplicates,incomingFingerprints.size-duplicates)
    }

    fun restoreJson(text: String): Int {
        previewJson(text)
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
            "account_labels" to listOf("fingerprint","label"),
            "merchant_category_rules" to listOf("merchant_key","category"),
            "transaction_audit_history" to TransactionAudit.backupColumns
        )
        // Validate every row before any database write.
        var total=0
        specs.forEach { (table,cols) ->
            val rows=root.optJSONArray(table) ?: if(table=="merchant_category_rules" ||
                table=="transaction_audit_history") JSONArray() else throw IllegalArgumentException("Missing $table")
            require(rows.length()<=100000) { "Too many records" }
            for(i in 0 until rows.length()) {
                val item=rows.getJSONObject(i)
                cols.forEach { key -> require(item.has(key) && !item.isNull(key)) { "Invalid $table row" } }
            }
        }
        val db=writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE transaction_audit_control SET enabled=0 WHERE id=1")
            specs.forEach { (table,cols) ->
                val rows=root.optJSONArray(table) ?: JSONArray()
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
            db.execSQL("UPDATE transaction_audit_control SET enabled=1 WHERE id=1")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return total
    }

    fun queueBml(amount: Long, timestamp: String, fingerprint: String): Boolean {
        val db=writableDatabase
        db.beginTransaction()
        try {
            val recorded=db.rawQuery("SELECT 1 FROM transactions WHERE fingerprint=?",
                arrayOf(fingerprint)).use { it.moveToFirst() }
            if(recorded) return false
            val v = ContentValues().apply {
                put("amount_laari",amount)
                put("occurred_at",timestamp)
                put("fingerprint",fingerprint)
            }
            val inserted=db.insertWithOnConflict(
                "bml_pending", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1L
            if(inserted) db.setTransactionSuccessful()
            return inserted
        } finally {
            db.endTransaction()
        }
    }
    fun pendingBml(): List<PendingBml> {
        val result = mutableListOf<PendingBml>()
        readableDatabase.rawQuery("SELECT id,amount_laari,occurred_at,fingerprint FROM bml_pending ORDER BY id DESC", null).use { c ->
            while (c.moveToNext()) result += PendingBml(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3))
        }
        return result
    }
    /**
     * Resolve once, atomically. An approved own-bank transfer is still a BML
     * balance movement; Internal Transfer excludes it from spending, not balances.
     */
    fun resolveBml(tx: PendingBml, approve: Boolean, internalTransfer: Boolean = false): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val pending = db.rawQuery(
                "SELECT amount_laari,occurred_at FROM bml_pending WHERE id=? AND fingerprint=?",
                arrayOf(tx.id.toString(), tx.fingerprint)
            ).use { c ->
                if (c.moveToFirst()) c.getLong(0) to c.getString(1) else null
            } ?: return false
            if (pending.first != tx.amountLaari || pending.second != tx.timestamp) return false

            if (approve) {
                val existing = db.rawQuery(
                    "SELECT amount_laari FROM transactions WHERE fingerprint=?",
                    arrayOf(tx.fingerprint)
                ).use { c -> if (c.moveToFirst()) c.getLong(0) else null }
                if (existing != null && existing != tx.amountLaari) return false
                if (existing == null) {
                    val row = ContentValues().apply {
                        put("amount_laari", tx.amountLaari)
                        put("category", if (internalTransfer) "Internal Transfer" else "Incoming Transfer")
                        put("description", "BML Favara funds received")
                        put("created_at", tx.timestamp)
                        put("fingerprint", tx.fingerprint)
                    }
                    if (db.insertWithOnConflict(
                            "transactions", null, row, SQLiteDatabase.CONFLICT_IGNORE
                        ) == -1L) return false
                }
                // Keep an existing explicit account label if one was already assigned.
                val label = ContentValues().apply {
                    put("fingerprint", tx.fingerprint)
                    put("label", "BML")
                }
                db.insertWithOnConflict("account_labels", null, label, SQLiteDatabase.CONFLICT_IGNORE)
            }
            val removed = db.delete(
                "bml_pending", "id=? AND fingerprint=?",
                arrayOf(tx.id.toString(), tx.fingerprint)
            ) == 1
            if (removed) db.setTransactionSuccessful()
            return removed
        } finally {
            db.endTransaction()
        }
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
        // Never discard a genuine payment just because its amount matches another.
        // CSV is authoritative only when description/reference or exact timestamp corroborates.
        if(!fingerprint.startsWith("manual-") && !fingerprint.startsWith("csv-") && transactionTime!=null) {
            val sameAmount=all().filter { it.fingerprint.startsWith("csv-") && it.amountLaari==amountLaari }
            if(sameAmount.any { CsvSmsMatcher.isConfirmedDuplicate(
                    amountLaari,description,transactionTime,it.amountLaari,it.description,it.timestamp) }) return false
        }
        val values = android.content.ContentValues().apply {
            put("amount_laari", amountLaari); put("category", (if(amountLaari < 0L && category != "Internal Transfer") (merchantCategoryRule(description) ?: category) else category).take(40)); put("description", description.take(120)); put("created_at", (transactionTime ?: LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))); put("fingerprint", fingerprint)
        }
        val inserted=db.insertWithOnConflict("transactions", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
        if(inserted && fingerprint.startsWith("mib-",ignoreCase=true)) {
            val account=ContentValues().apply { put("fingerprint",fingerprint);put("label","MIB") }
            db.insertWithOnConflict("account_labels",null,account,SQLiteDatabase.CONFLICT_IGNORE)
        }
        return inserted
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
    private fun merchantCategoryRule(description: String): String? {
        val key = VendorProfiles.key(description)
        if (key.isBlank()) return null
        readableDatabase.rawQuery(
            "SELECT category FROM merchant_category_rules WHERE merchant_key=?",
            arrayOf(key)
        ).use { cursor -> return if(cursor.moveToFirst()) cursor.getString(0) else null }
    }

    /** Explicit opt-in merchant rule; updates prior matching transactions atomically. */
    /** Explicitly exported only on user request; never includes transaction amounts or SMS. */
    fun shareableMerchantPreferences(): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        readableDatabase.rawQuery(
            "SELECT merchant_key, category FROM merchant_category_rules ORDER BY merchant_key LIMIT 200", null
        ).use { cursor ->
            while (cursor.moveToNext()) rows += cursor.getString(0) to cursor.getString(1)
        }
        return rows
    }

    fun reclassifyMerchant(description: String, category: String): Int {
        require(category.isNotBlank() && category.length <= 40)
        val key = VendorProfiles.key(description)
        require(key.isNotBlank()) { "Merchant name is missing" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val rule = ContentValues().apply {
                put("merchant_key", key)
                put("category", category)
            }
            db.insertWithOnConflict("merchant_category_rules", null, rule, SQLiteDatabase.CONFLICT_REPLACE)
            var changed = 0
            val candidates = mutableListOf<Pair<Long,String>>()
            db.rawQuery("SELECT id,description FROM transactions WHERE amount_laari<0 AND category!='Internal Transfer'", null).use { cursor ->
                while(cursor.moveToNext()) candidates.add(cursor.getLong(0) to cursor.getString(1))
            }
            candidates.filter { VendorProfiles.key(it.second) == key }.forEach { (id, _) ->
                val values = ContentValues().apply { put("category", category) }
                changed += db.update("transactions", values, "id=?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
            return changed
        } finally { db.endTransaction() }
    }


    /** Audit entries are recorded at SQLite level, including edits outside the UI. */
    fun recentTransactionChanges(limit: Int = 30): List<TransactionAudit.Entry> =
        TransactionAudit.recent(readableDatabase,limit)

    /** Undo fails safely when another operation changed the transaction later. */
    fun undoTransactionChange(historyId: Long): Boolean =
        TransactionAudit.undo(writableDatabase,historyId)

    fun setTransactionCategory(id: Long, category: String) {
        require(category.isNotBlank())
        writableDatabase.execSQL("UPDATE transactions SET category=? WHERE id=?",arrayOf(category,id))
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
    private val fundIncoming = Regex("""(?is)^Fund\s+Transfer\s+to\s+your\s+account\s+(\d+[\d*]*\d+)\s+for\s+MVR\s+([\d,]+\.\d{2})\s+was\s+processed\s+on\s+(\d{2}/\d{2}/\d{4}\s+\d{2}:\d{2}:\d{2})\.\s+Ref\.\s*no\.\s*([\w-]+)\s*$""")
    private val favaraIncoming = Regex("""(?is)^Favara\s+Transfer\s+to\s+your\s+account\s+\S+\s+for\s+MVR\s+([\d,]+\.\d{2})\s+was\s+processed\s+on\s+(\d{2}/\d{2}/\d{4}\s+\d{2}:\d{2}:\d{2})\.\s+Ref\.\s*no\.\s*[\w-]+\s*$""")
    private val ecommerce = Regex("""(?is)^Your\s+E-COMMERCE\s+TRX\s+from\s+(\*+\d{4})\s+for\s+([\d,]+\.\d{2})\s+MVR\s+at\s+(.+?),\s*MV\s+on\s+(\d{2}\.\d{2}\.\d{2}\s+\d{2}:\d{2})\s+was\s+processed\s+successfully\.\s+Approval\s+Code:\s*([\w-]+)\s*$""")
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
        fundIncoming.matchEntire(text)?.let { m ->
            val amount = Money.parseLaari(m.groupValues[2]) ?: return null
            val whenAt = date(m.groupValues[3], "dd/MM/uuuu HH:mm:ss") ?: return null
            if (amount <= 0) return null
            return Candidate(amount, "Incoming Transfer", "Fund transfer received (Ref: ${m.groupValues[4]})", fingerprint, whenAt)
        }
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
        ecommerce.matchEntire(text)?.let { m ->
            val amount = Money.parseLaari(m.groupValues[2]) ?: return null
            val merchant = m.groupValues[3].trim().replace(Regex("""\s+"""), " ")
            val whenAt = date(m.groupValues[4], "dd.MM.uu HH:mm") ?: return null
            if (amount <= 0 || merchant.length !in 2..70) return null
            return Candidate(-amount, MerchantCategories.classify(merchant),
                "E-commerce purchase: $merchant", fingerprint, whenAt)
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
