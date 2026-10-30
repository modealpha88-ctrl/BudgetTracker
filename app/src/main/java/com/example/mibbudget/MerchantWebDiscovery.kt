package com.example.mibbudget

import android.content.Context
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Conservative public directory matching. Run on a background dispatcher only. */
object MerchantWebDiscovery {
    data class Match(val website: String, val social: String)
    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")
    private fun read(url: String): String {
        val u = URL(url)
        if (u.protocol != "https" || u.userInfo != null) return ""
        val c = u.openConnection() as HttpURLConnection
        c.connectTimeout = 4000
        c.readTimeout = 4000
        c.instanceFollowRedirects = false
        c.setRequestProperty("User-Agent", "BudgetTracker/0.2")
        return try {
            if (c.responseCode != 200) "" else c.inputStream.bufferedReader().use { it.readText().take(120000) }
        } finally { c.disconnect() }
    }
    fun discover(description: String, category: String): Match? {
        if (AutomaticVendorIdentity.discoveryQuery(description, category) == null) return null
        val name = VendorProfiles.vendor(description)
        val query = URLEncoder.encode("$name Maldives", "UTF-8")
        val data = JSONArray(read("https://nominatim.openstreetmap.org/search?format=json&extratags=1&limit=5&q=$query"))
        for (i in 0 until data.length()) {
            val item = data.getJSONObject(i)
            val found = item.optString("name").ifBlank { item.optString("display_name").substringBefore(",") }
            if (norm(name) != norm(found)) continue
            val tags = item.optJSONObject("extratags") ?: continue
            val site = tags.optString("website").ifBlank { tags.optString("contact:website") }
            if (!site.startsWith("https://")) continue
            val html = read(site)
            val social = Regex("https://(?:www\\.)?(?:instagram\\.com|facebook\\.com|tiktok\\.com)/[A-Za-z0-9_.-]+")
                .findAll(html).map { it.value }.distinct().singleOrNull() ?: continue
            if (VendorProfiles.validate(social, site) == null) return Match(site, social)
        }
        return null
    }
    fun apply(context: Context, description: String, category: String): Boolean {
        if (!MerchantDiscoveryPolicy.eligible(context, description, category)) return false
        val match = discover(description, category) ?: return false
        if (!MerchantDiscoveryPolicy.eligible(context, description, category)) return false
        AutomaticVendorIdentity.confirmProfile(context, description, match.social, match.website)
        return true
    }
}
