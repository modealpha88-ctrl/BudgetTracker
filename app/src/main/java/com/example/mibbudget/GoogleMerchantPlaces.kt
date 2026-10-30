package com.example.mibbudget

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Google Places API (New) Text Search. Requires a user-configured API key. */
object GoogleMerchantPlaces {
    data class Candidate(val name: String, val address: String, val website: String?)
    fun key(context: Context): String = context.getSharedPreferences("google_places_config",Context.MODE_PRIVATE).getString("api_key","").orEmpty()
    fun setKey(context: Context, value: String) {
        context.getSharedPreferences("google_places_config",Context.MODE_PRIVATE).edit().putString("api_key",value.trim()).apply()
    }
    fun search(context: Context, description: String, category: String): List<Candidate> {
        val apiKey = key(context)
        val query = AutomaticVendorIdentity.discoveryQuery(description,category) ?: return emptyList()
        if (apiKey.isBlank()) return emptyList()
        val conn = URL("https://places.googleapis.com/v1/places:searchText").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        conn.doOutput = true
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("Content-Type","application/json")
        conn.setRequestProperty("X-Goog-Api-Key",apiKey)
        conn.setRequestProperty("X-Goog-FieldMask","places.displayName,places.formattedAddress,places.websiteUri")
        return try {
            val body = JSONObject().put("textQuery",query).put("regionCode","MV").put("pageSize",5)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode != 200) throw IllegalStateException("Google Places HTTP " + conn.responseCode)
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val places = JSONObject(response).optJSONArray("places") ?: return emptyList()
            (0 until places.length()).map { i ->
                val item = places.getJSONObject(i)
                Candidate(item.optJSONObject("displayName")?.optString("text").orEmpty(),
                    item.optString("formattedAddress"),item.optString("websiteUri").takeIf { it.startsWith("https://") })
            }.filter { it.address.contains("Maldives",true) }
        } finally { conn.disconnect() }
    }
}
