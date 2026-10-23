package com.example.mibbudget

import android.content.Context
import org.json.JSONObject

/** Encrypted by SecureBackup at the call site. Legacy database-only backups remain supported. */
object FullBackup {
    private val preferenceFiles=listOf(
        "transaction_recipients","vendor_avatar_files","vendor_verified_profiles",
        "sms_settings","appearance","balance_settings","csv_import_history",
        "counterparty_classification","account_settings","app_security",
        "internal_transfer_original_categories","debt_tracker","dashboard_layout","balance_anchor",
        "debt_ledgers","internal_transfer_original_categories","balance_anchor","dashboard_layout"
    )
    fun export(context: Context, store: TransactionStore): String {
        val root=JSONObject(store.exportJson())
        val prefs=JSONObject()
        preferenceFiles.forEach { name ->
            val values=JSONObject()
            context.getSharedPreferences(name,Context.MODE_PRIVATE).all.forEach { (key,value) ->
                // Never export device biometric state or passwords; local image paths are not portable.
                if(name=="app_security" || name=="vendor_avatar_files" ||
                   key.contains("password",true) || key.contains("token",true)) return@forEach
                when(value) {
                    is Boolean,is Int,is Long,is Float,is String -> values.put(key,value)
                }
            }
            prefs.put(name,values)
        }
        root.put("preferences",prefs)
        root.put("backup_schema",2)
        return root.toString(2)
    }
    fun restore(context: Context, store: TransactionStore, text: String): Int {
        val root=JSONObject(text)
        val prefs=root.optJSONObject("preferences")
        // Validate preference values before touching the database. Never erase existing settings.
        val validated=mutableListOf<Triple<String,String,Any>>()
        if(prefs!=null) {
            require(prefs.toString().length<2_000_000) { "Settings backup too large" }
            for(name in preferenceFiles) {
                if(name=="app_security" || name=="vendor_avatar_files") continue
                val obj=prefs.optJSONObject(name) ?: continue
                for(key in obj.keys()) {
                    require(key.length<=200 && !key.contains("password",true) && !key.contains("token",true))
                    val value=obj.get(key)
                    require(value is Boolean || value is Number || value is String) { "Unsupported setting" }
                    validated.add(Triple(name,key,value))
                }
            }
        }
        val count=store.restoreJson(text)
        validated.groupBy { it.first }.forEach { (name,items) ->
            val editor=context.getSharedPreferences(name,Context.MODE_PRIVATE).edit()
            items.forEach { (_,key,value) ->
                when(value) {
                    is Boolean -> editor.putBoolean(key,value)
                    is Int -> editor.putInt(key,value)
                    is Long -> editor.putLong(key,value)
                    is Number -> editor.putLong(key,value.toLong())
                    is String -> editor.putString(key,value.take(5000))
                }
            }
            editor.apply()
        }
        return count
    }
}
