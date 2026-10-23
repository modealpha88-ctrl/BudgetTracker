package com.example.mibbudget

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.util.Locale

/**
 * Vendor identities are merchant-scoped, never transaction-scoped or person-scoped.
 * A social handle is never verified merely by a similar name.
 * Verification records are user-attested and include independent evidence.
 */
object VendorProfiles {
    data class Profile(val vendor: String, val socialUrl: String, val evidenceUrl: String,
                       val platform: String, val verifiedAt: Long)
    fun vendor(description: String): String {
        val label=TransactionPresentation.label(description)
        return label.removePrefix("POS purchase: ").removePrefix("POS Payment: ")
            .substringBefore(" | ").trim().uppercase(Locale.ROOT)
    }
    fun key(description: String): String = vendor(description).replace(Regex("[^A-Z0-9]"),"_").take(80)
    fun isTransfer(description: String, category: String): Boolean =
        category.contains("Transfer",true) || description.contains("transfer",true)
    private fun host(url: String): String? = try {
        val parsed=Uri.parse(url)
        if(parsed.scheme!="https" || parsed.userInfo!=null) null else parsed.host?.lowercase(Locale.ROOT)?.removePrefix("www.")
    } catch (_: Exception) { null }
    fun platform(url: String): String? {
        val domain=host(url) ?: return null
        return when(domain) {
            "instagram.com" -> "Instagram"
            "facebook.com","fb.com" -> "Facebook"
            "tiktok.com" -> "TikTok"
            else -> null
        }
    }
    fun validate(social: String, evidence: String): String? {
        val platform=platform(social) ?: return "Use a full HTTPS Instagram, Facebook or TikTok profile URL."
        val uri=Uri.parse(social)
        if(uri.pathSegments.isEmpty() || uri.pathSegments.first().isBlank()) return "Enter a specific social profile, not the platform homepage."
        val proofHost=host(evidence) ?: return "Evidence must be a full HTTPS website URL."
        if(proofHost in setOf("instagram.com","facebook.com","fb.com","tiktok.com"))
            return "Use an independent business website linking to this social profile as evidence."
        if(evidence.length>500 || social.length>500) return "Link is too long."
        return null
    }
    fun save(context: Context, description: String, social: String, evidence: String) {
        require(validate(social,evidence)==null)
        val json=JSONObject().put("vendor",vendor(description)).put("socialUrl",social.trim())
            .put("evidenceUrl",evidence.trim()).put("platform",platform(social))
            .put("verifiedAt",System.currentTimeMillis())
        context.getSharedPreferences("vendor_verified_profiles",Context.MODE_PRIVATE)
            .edit().putString(key(description),json.toString()).apply()
    }
    fun get(context: Context, description: String): Profile? = try {
        val json=JSONObject(context.getSharedPreferences("vendor_verified_profiles",Context.MODE_PRIVATE)
            .getString(key(description),null) ?: return null)
        if(json.getString("vendor")!=vendor(description)) return null
        Profile(json.getString("vendor"),json.getString("socialUrl"),json.getString("evidenceUrl"),
            json.getString("platform"),json.getLong("verifiedAt"))
    } catch (_:Exception) { null }
    fun remove(context: Context, description: String) {
        context.getSharedPreferences("vendor_verified_profiles",Context.MODE_PRIVATE)
            .edit().remove(key(description)).apply()
    }
}
