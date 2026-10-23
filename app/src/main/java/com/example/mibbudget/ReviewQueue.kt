package com.example.mibbudget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class ReviewQueue(context: Context) {
    private val prefs=context.getSharedPreferences("review_queue",Context.MODE_PRIVATE)
    data class Entry(val id:String,val type:String,val title:String,val detail:String,val status:String,val txId:Long=0)
    fun all():List<Entry> = try {
        val data=JSONArray(prefs.getString("entries","[]"))
        (0 until data.length()).map { i ->
            val x=data.getJSONObject(i)
            Entry(x.getString("id"),x.getString("type"),x.getString("title"),x.getString("detail"),x.getString("status"),x.optLong("txId"))
        }
    } catch(_:Exception) { emptyList() }
    private fun save(items:List<Entry>) {
        val array=JSONArray()
        items.forEach { x -> array.put(JSONObject().put("id",x.id).put("type",x.type).put("title",x.title).put("detail",x.detail).put("status",x.status).put("txId",x.txId)) }
        prefs.edit().putString("entries",array.toString()).apply()
    }
    fun add(items:List<Entry>) {
        val current=all().toMutableList()
        val known=current.map { it.id }.toMutableSet()
        items.forEach { if(known.add(it.id)) current.add(it) }
        save(current)
    }
    fun status(id:String,status:String) {
        require(status in listOf("PENDING","APPROVED","REJECTED","RESOLVED"))
        save(all().map { if(it.id==id) it.copy(status=status) else it })
    }
    fun addCsv(csv:String,review:TransactionStore.CsvReview) {
        val hash=MessageDigest.getInstance("SHA-256").digest(csv.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
        add(review.matches.filter { it.status=="MISSING" || it.status=="POSSIBLE" }.map { row ->
            Entry("csv:$hash:${row.line}","CSV",row.description.take(100),
                "${row.date} · ${Money.format(row.amountLaari)} · ${row.status} · ${row.reason}","PENDING")
        })
    }
    fun addTransactions(transactions:List<Tx>) {
        add(transactions.map { tx ->
            Entry("tx:${tx.id}","TRANSACTION",TransactionPresentation.label(tx.description),
                "${tx.timestamp.take(10)} · ${Money.format(tx.amountLaari)}","PENDING",tx.id)
        })
    }
}
