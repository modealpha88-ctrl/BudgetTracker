package com.example.mibbudget

import android.content.Context
import java.util.Locale

/**
 * Local, privacy-preserving merchant identity resolution.
 * Never sends bank descriptions, amounts, or account identifiers to a search service.
 * A manually unassigned merchant is never automatically reassigned.
 */
object AutomaticVendorIdentity {
    enum class Source { MANUAL_UNASSIGNED, MANUAL_PROFILE, VERIFIED_SAVED, GENERIC }
    data class Resolution(val merchant: String, val source: Source, val socialUrl: String? = null)

    private fun preferences(context: Context) =
        context.getSharedPreferences("merchant_identity_overrides", Context.MODE_PRIVATE)

    fun setManuallyUnassigned(context: Context, description: String) {
        preferences(context).edit().putBoolean("unassigned_" + VendorProfiles.key(description), true).apply()
    }

    fun clearManualUnassigned(context: Context, description: String) {
        preferences(context).edit().remove("unassigned_" + VendorProfiles.key(description)).apply()
    }

    fun confirmProfile(context: Context, description: String, social: String, evidence: String) {
        VendorProfiles.save(context, description, social, evidence)
        clearManualUnassigned(context, description)
    }

    fun isManuallyUnassigned(context: Context, description: String): Boolean =
        preferences(context).getBoolean("unassigned_" + VendorProfiles.key(description), false)

    /**
     * Sanitized discovery query: merchant label only, no SMS body or financial data.
     * Search results must not be treated as verified without independent evidence.
     */
    fun discoveryQuery(description: String, category: String): String? {
        if (VendorProfiles.isTransfer(description, category)) return null
        val merchant = VendorProfiles.vendor(description)
            .replace(Regex("[^A-Za-z0-9 &'-]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        if (merchant.length !in 3..80) return null
        return "$merchant official business Maldives"
    }

    fun resolve(context: Context, description: String, category: String): Resolution {
        val merchant = VendorProfiles.vendor(description).trim().uppercase(Locale.ROOT)
        if (VendorProfiles.isTransfer(description, category) || merchant.isBlank()) {
            return Resolution(merchant, Source.GENERIC)
        }
        if (isManuallyUnassigned(context, description)) {
            return Resolution(merchant, Source.MANUAL_UNASSIGNED)
        }
        val saved = VendorProfiles.get(context, description)
        if (saved != null) return Resolution(merchant, Source.VERIFIED_SAVED, saved.socialUrl)
        val picture = context.getSharedPreferences("vendor_avatar_files", Context.MODE_PRIVATE)
            .getString(VendorProfiles.key(description), null)
        if (picture != null && java.io.File(picture).isFile) return Resolution(merchant, Source.MANUAL_PROFILE)
        return Resolution(merchant, Source.GENERIC)
    }
}
