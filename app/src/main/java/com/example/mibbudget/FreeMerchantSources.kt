package com.example.mibbudget

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Public OpenStreetMap Overpass lookup; no API key required. */
object FreeMerchantSources {
    data class Candidate(val name: String, val source: String, val website: String?, val social: String?)
    fun search(description: String, category: String): List<Candidate> {
        if (AutomaticVendorIdentity.discoveryQuery(description,category)==null) return emptyList()
        val name = VendorProfiles.vendor(description).trim()
        if (name.length !in 3..70) return emptyList()
        val escaped = Regex.escape(name).replace("\\E","").replace("\\Q","")
        val q = "[out:json][timeout:12];nwr[\"name\"~\"^$escaped$\",i](4.0,72.0,8.0,74.0);out tags 15;"
        val endpoint = "https://overpass.kumi.systems/api/interpreter?data=" + URLEncoder.encode(q,"UTF-8")
        val c = URL(endpoint).openConnection() as HttpURLConnection
        c.connectTimeout=5000
        c.readTimeout=14000
        c.instanceFollowRedirects=false
        c.setRequestProperty("User-Agent","BudgetTracker/0.2 (merchant discovery)")
        return try {
            if(c.responseCode!=200) return emptyList()
            val data=c.inputStream.bufferedReader().use { it.readText().take(180000) }
            val elements=JSONObject(data).optJSONArray("elements") ?: return emptyList()
            (0 until elements.length()).mapNotNull { i ->
                val tags=elements.getJSONObject(i).optJSONObject("tags") ?: return@mapNotNull null
                val found=tags.optString("name")
                if(!found.equals(name,true)) return@mapNotNull null
                val website=tags.optString("website").ifBlank { tags.optString("contact:website") }
                val social=listOf("contact:instagram","contact:facebook","contact:tiktok","website:instagram")
                    .map { tags.optString(it) }.firstOrNull { it.startsWith("https://") }
                Candidate(found,"OpenStreetMap",website.takeIf { it.startsWith("https://") },social)
            }.distinctBy { it.name+it.website+it.social }
        } finally { c.disconnect() }
    }
}
