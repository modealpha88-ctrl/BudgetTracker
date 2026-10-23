package com.example.mibbudget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Named, reversible debt ledgers. Only references transaction IDs; never mutates bank records. */
object DebtLedgers {
    data class Ledger(val name: String, val direction: String, val ids: Set<Long>)
    private fun prefs(context: Context)=context.getSharedPreferences("debt_ledgers",Context.MODE_PRIVATE)
    fun all(context: Context): List<Ledger> {
        val raw=prefs(context).getString("ledgers","[]") ?: "[]"
        return try {
            val array=JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val item=array.getJSONObject(i)
                val ids=item.optJSONArray("ids") ?: JSONArray()
                val name=item.optString("name").trim()
                val direction=item.optString("direction","Owed to me")
                if(name.isBlank()) null else Ledger(name,direction,(0 until ids.length()).map { ids.getLong(it) }.toSet())
            }
        } catch (_: Exception) { emptyList() }
    }
    fun save(context: Context, name: String, direction: String, ids: Set<Long>) {
        require(name.isNotBlank() && name.length<=80 && direction in listOf("Owed to me","I owe"))
        val existing=all(context).filterNot { it.name.equals(name,true) && it.direction==direction }
        write(context,existing+Ledger(name,direction,ids))
    }
    fun remove(context: Context, name: String, direction: String) {
        write(context,all(context).filterNot { it.name==name && it.direction==direction })
    }
    private fun write(context: Context, ledgers: List<Ledger>) {
        val array=JSONArray()
        ledgers.forEach { ledger ->
            val ids=JSONArray()
            ledger.ids.forEach { ids.put(it) }
            array.put(JSONObject().put("name",ledger.name).put("direction",ledger.direction).put("ids",ids))
        }
        prefs(context).edit().putString("ledgers",array.toString()).apply()
    }
    fun balance(ledger: Ledger, transactions: List<Tx>): Long {
        val net=transactions.filter { it.id in ledger.ids }.sumOf { it.amountLaari }
        return if(ledger.direction=="Owed to me") -net else net
    }
}
