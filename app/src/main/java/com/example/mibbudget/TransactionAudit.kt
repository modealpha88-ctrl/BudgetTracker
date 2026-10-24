package com.example.mibbudget

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

/**
 * Append-only transaction edit journal. SQLite triggers record changes from EVERY
 * import/edit path, including bulk edits and deletes. An undo verifies the current
 * row still matches the recorded after-state before changing anything.
 */
object TransactionAudit {
    private val attributes = listOf("amount_laari","category","description","created_at","fingerprint")
    private val snapshots = listOf("amount","category","description","created_at","fingerprint")

    fun install(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS transaction_audit_control(
            id INTEGER PRIMARY KEY CHECK(id=1), enabled INTEGER NOT NULL DEFAULT 1)""")
        db.execSQL("INSERT OR IGNORE INTO transaction_audit_control(id,enabled) VALUES(1,1)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS transaction_audit_history(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            operation TEXT NOT NULL,
            tx_id INTEGER NOT NULL,
            old_amount TEXT NOT NULL DEFAULT '',
            old_category TEXT NOT NULL DEFAULT '',
            old_description TEXT NOT NULL DEFAULT '',
            old_created_at TEXT NOT NULL DEFAULT '',
            old_fingerprint TEXT NOT NULL DEFAULT '',
            new_amount TEXT NOT NULL DEFAULT '',
            new_category TEXT NOT NULL DEFAULT '',
            new_description TEXT NOT NULL DEFAULT '',
            new_created_at TEXT NOT NULL DEFAULT '',
            new_fingerprint TEXT NOT NULL DEFAULT '',
            changed_at TEXT NOT NULL DEFAULT (datetime('now')),
            undone INTEGER NOT NULL DEFAULT 0)""")

        db.execSQL("""CREATE TRIGGER IF NOT EXISTS audit_transaction_insert
            AFTER INSERT ON transactions
            WHEN (SELECT enabled FROM transaction_audit_control WHERE id=1)=1
            BEGIN
                INSERT INTO transaction_audit_history(
                    operation,tx_id,new_amount,new_category,new_description,new_created_at,new_fingerprint)
                VALUES('INSERT',NEW.id,CAST(NEW.amount_laari AS TEXT),NEW.category,
                    NEW.description,NEW.created_at,NEW.fingerprint);
            END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS audit_transaction_update
            AFTER UPDATE ON transactions
            WHEN (SELECT enabled FROM transaction_audit_control WHERE id=1)=1
                AND (OLD.amount_laari!=NEW.amount_laari OR OLD.category!=NEW.category
                OR OLD.description!=NEW.description OR OLD.created_at!=NEW.created_at
                OR OLD.fingerprint!=NEW.fingerprint)
            BEGIN
                INSERT INTO transaction_audit_history(
                    operation,tx_id,old_amount,old_category,old_description,old_created_at,old_fingerprint,
                    new_amount,new_category,new_description,new_created_at,new_fingerprint)
                VALUES('UPDATE',NEW.id,CAST(OLD.amount_laari AS TEXT),OLD.category,OLD.description,
                    OLD.created_at,OLD.fingerprint,CAST(NEW.amount_laari AS TEXT),
                    NEW.category,NEW.description,NEW.created_at,NEW.fingerprint);
            END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS audit_transaction_delete
            AFTER DELETE ON transactions
            WHEN (SELECT enabled FROM transaction_audit_control WHERE id=1)=1
            BEGIN
                INSERT INTO transaction_audit_history(
                    operation,tx_id,old_amount,old_category,old_description,old_created_at,old_fingerprint)
                VALUES('DELETE',OLD.id,CAST(OLD.amount_laari AS TEXT),OLD.category,
                    OLD.description,OLD.created_at,OLD.fingerprint);
            END""")
    }

    val backupColumns = listOf("id","operation","tx_id",
        "old_amount","old_category","old_description","old_created_at","old_fingerprint",
        "new_amount","new_category","new_description","new_created_at","new_fingerprint",
        "changed_at","undone")

    data class Entry(
        val id: Long, val operation: String, val txId: Long,
        val before: Tx?, val after: Tx?, val changedAt: String, val undone: Boolean
    ) {
        val description: String get() = after?.description ?: before?.description ?: ""
    }

    private fun entry(c: Cursor): Entry {
        val id=c.getLong(0)
        val operation=c.getString(1)
        val txId=c.getLong(2)
        fun snapshot(start: Int): Tx? {
            val amount=c.getString(start).toLongOrNull() ?: return null
            return Tx(txId,amount,c.getString(start+1),c.getString(start+2),
                c.getString(start+3),c.getString(start+4))
        }
        return Entry(id,operation,txId,snapshot(3),snapshot(8),c.getString(13),c.getInt(14)==1)
    }

    fun recent(db: SQLiteDatabase, limit: Int = 30): List<Entry> {
        val items=mutableListOf<Entry>()
        db.rawQuery("""SELECT id,operation,tx_id,
            old_amount,old_category,old_description,old_created_at,old_fingerprint,
            new_amount,new_category,new_description,new_created_at,new_fingerprint,
            changed_at,undone FROM transaction_audit_history ORDER BY id DESC LIMIT ?""",
            arrayOf(limit.coerceIn(1,100).toString())).use { c ->
            while(c.moveToNext()) items.add(entry(c))
        }
        return items
    }

    private fun current(db: SQLiteDatabase, id: Long): Tx? =
        db.rawQuery("""SELECT id,amount_laari,category,description,created_at,fingerprint
            FROM transactions WHERE id=?""",arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) Tx(c.getLong(0),c.getLong(1),c.getString(2),
                c.getString(3),c.getString(4),c.getString(5)) else null
        }

    private fun values(tx: Tx): ContentValues = ContentValues().apply {
        put("id",tx.id)
        put("amount_laari",tx.amountLaari)
        put("category",tx.category)
        put("description",tx.description)
        put("created_at",tx.timestamp)
        put("fingerprint",tx.fingerprint)
    }

    /** Safely undo only this change, not unrelated later edits. False = stale/conflict. */
    fun undo(db: SQLiteDatabase, auditId: Long): Boolean {
        db.beginTransaction()
        try {
            val change=db.rawQuery("""SELECT id,operation,tx_id,
                old_amount,old_category,old_description,old_created_at,old_fingerprint,
                new_amount,new_category,new_description,new_created_at,new_fingerprint,
                changed_at,undone FROM transaction_audit_history WHERE id=?""",
                arrayOf(auditId.toString())).use { c ->
                    if(c.moveToFirst()) entry(c) else null
                } ?: return false
            if(change.undone) return false
            val present=current(db,change.txId)
            when(change.operation) {
                "INSERT" -> if(present==null || present!=change.after) return false
                "UPDATE" -> if(present==null || present!=change.after || change.before==null) return false
                "DELETE" -> {
                    if(present!=null || change.before==null) return false
                    val exists=db.rawQuery("SELECT 1 FROM transactions WHERE fingerprint=?",
                        arrayOf(change.before.fingerprint)).use { it.moveToFirst() }
                    if(exists) return false
                }
                else -> return false
            }
            db.execSQL("UPDATE transaction_audit_control SET enabled=0 WHERE id=1")
            val changed=when(change.operation) {
                "INSERT" -> db.delete("transactions","id=?",
                    arrayOf(change.txId.toString()))==1
                "UPDATE" -> db.update("transactions",values(change.before!!),
                    "id=?",arrayOf(change.txId.toString()))==1
                "DELETE" -> db.insertWithOnConflict("transactions",null,
                    values(change.before!!),SQLiteDatabase.CONFLICT_IGNORE)!=-1L
                else -> false
            }
            if(!changed) return false
            db.execSQL("UPDATE transaction_audit_history SET undone=1 WHERE id=?",
                arrayOf(change.id))
            db.execSQL("UPDATE transaction_audit_control SET enabled=1 WHERE id=1")
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }
}
