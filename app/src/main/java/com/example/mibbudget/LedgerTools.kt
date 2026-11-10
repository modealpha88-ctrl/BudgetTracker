package com.example.mibbudget

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

/** Allocations are positive minor units; the source payment always remains a single row. */
data class SpendingAllocation(val category: String, val amount: Long)

object SplitSpending {
    fun validate(tx: Tx, parts: List<SpendingAllocation>) {
        require(tx.amountLaari < 0 && tx.amountLaari != Long.MIN_VALUE && tx.category != "Internal Transfer") { "Split an outgoing expense only" }
        require(parts.size in 2..20) { "Use 2–20 allocations" }
        require(parts.all { it.amount > 0 && it.category.isNotBlank() && it.category.length <= 40 && it.category !in listOf("Internal Transfer", "Income") }) { "Choose expense categories and positive amounts" }
        require(parts.map { it.category }.distinct().size == parts.size) { "Combine repeated categories" }
        require(parts.fold(0L) { total, p -> Math.addExact(total, p.amount) } == -tx.amountLaari) { "Allocations must equal the original payment" }
    }
    fun encode(parts: List<SpendingAllocation>): String = JSONArray().apply {
        parts.forEach { put(JSONObject().put("category", it.category).put("amount", it.amount)) }
    }.toString()
    fun decode(value: String): List<SpendingAllocation> {
        val rows = JSONArray(value)
        require(rows.length() <= 20)
        return (0 until rows.length()).map { rows.getJSONObject(it).let { p -> SpendingAllocation(p.getString("category"), p.getLong("amount")) } }
    }
    fun expand(tx: Tx, parts: List<SpendingAllocation>): List<Tx> =
        if (runCatching { validate(tx, parts) }.isSuccess) parts.map { tx.copy(amountLaari = -it.amount, category = it.category) } else listOf(tx)
}

/** Durable, atomic undo for related ledger metadata and bulk actions. No bank amounts are synthesized. */
object LedgerRecovery {
    val specs = linkedMapOf(
        "transactions" to listOf("id", "amount_laari", "category", "description", "created_at", "fingerprint"),
        "account_labels" to listOf("fingerprint", "label"),
        "csv_transaction_details" to listOf("fingerprint", "csv_row_hash", "recipient", "reference", "bank_description", "statement_date"),
        "spending_allocations" to listOf("fingerprint", "parts")
    )
    val backupColumns = listOf("action_key", "label", "before_state", "after_state", "created_at", "undone")
    fun install(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS spending_allocations(fingerprint TEXT PRIMARY KEY, parts TEXT NOT NULL)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS ledger_recovery(action_key TEXT PRIMARY KEY, label TEXT NOT NULL,
            before_state TEXT NOT NULL, after_state TEXT NOT NULL, created_at TEXT NOT NULL, undone INTEGER NOT NULL DEFAULT 0)""")
    }
    private fun snapshot(db: SQLiteDatabase, fingerprints: List<String>): String = JSONObject().apply {
        fingerprints.sorted().forEach { key ->
            put(key, JSONObject().apply {
                specs.forEach { (table, cols) ->
                    val rows = JSONArray()
                    db.query(table, cols.toTypedArray(), "fingerprint=?", arrayOf(key), null, null, null).use { c ->
                        while (c.moveToNext()) rows.put(JSONObject().apply {
                            cols.forEachIndexed { i, col -> if (c.getType(i) == android.database.Cursor.FIELD_TYPE_INTEGER) put(col, c.getLong(i)) else put(col, c.getString(i)) }
                        })
                    }
                    put(table, rows)
                }
            })
        }
    }.toString()
    fun <T> record(db: SQLiteDatabase, label: String, keys: List<String>, action: () -> T): T {
        db.beginTransaction()
        try {
            val before = snapshot(db, keys.distinct())
            val result = action()
            val after = snapshot(db, keys.distinct())
            if (before != after) db.insertOrThrow("ledger_recovery", null, ContentValues().apply {
                put("action_key", java.util.UUID.randomUUID().toString()); put("label", label)
                put("before_state", before); put("after_state", after); put("created_at", java.time.LocalDateTime.now().toString())
            })
            db.setTransactionSuccessful()
            return result
        } finally { db.endTransaction() }
    }
    data class Action(val key: String, val label: String, val date: String, val undone: Boolean)
    fun recent(db: SQLiteDatabase): List<Action> = buildList {
        db.rawQuery("SELECT action_key,label,created_at,undone FROM ledger_recovery ORDER BY created_at DESC LIMIT 40", null).use { c ->
            while (c.moveToNext()) add(Action(c.getString(0), c.getString(1), c.getString(2), c.getInt(3) == 1))
        }
    }
    fun undo(db: SQLiteDatabase, key: String): Boolean {
        db.beginTransaction()
        try {
            val states = db.rawQuery("SELECT before_state,after_state FROM ledger_recovery WHERE action_key=? AND undone=0", arrayOf(key)).use { c ->
                if (c.moveToFirst()) c.getString(0) to c.getString(1) else null
            } ?: return false
            val before = JSONObject(states.first)
            val keys = before.keys().asSequence().toList()
            if (snapshot(db, keys) != states.second) return false
            // Restored audit records carry original row IDs: check identity rather than trusting them.
            keys.forEach { fingerprint ->
                specs.forEach { (table, cols) ->
                    val rows = before.getJSONObject(fingerprint).getJSONArray(table)
                    if (table == "transactions") {
                        if (rows.length() != 1) return false
                        val item = rows.getJSONObject(0)
                        val values = ContentValues().apply { cols.filter { it != "id" }.forEach { col ->
                            val v = item.get(col); if (v is Number) put(col, v.toLong()) else put(col, v.toString())
                        } }
                        val updated = db.update(table, values, "fingerprint=?", arrayOf(fingerprint))
                        if (updated == 0) {
                            // Merge actions may have removed one transaction entirely.
                            // Restore its original row ID as part of the same atomic undo.
                            values.put("id", item.getLong("id"))
                            db.insertOrThrow(table, null, values)
                        } else check(updated == 1)
                    } else {
                        db.delete(table, "fingerprint=?", arrayOf(fingerprint))
                        for (i in 0 until rows.length()) db.insertOrThrow(table, null, ContentValues().apply {
                            cols.forEach { col -> val v = rows.getJSONObject(i).get(col); if (v is Number) put(col, v.toLong()) else put(col, v.toString()) }
                        })
                    }
                }
            }
            db.execSQL("UPDATE ledger_recovery SET undone=1 WHERE action_key=?", arrayOf(key))
            db.setTransactionSuccessful()
            return true
        } finally { db.endTransaction() }
    }
}
