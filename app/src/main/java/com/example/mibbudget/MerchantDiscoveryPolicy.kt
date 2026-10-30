package com.example.mibbudget

import android.content.Context

/** Never discover transfers, manually excluded merchants, or already confirmed profiles. */
object MerchantDiscoveryPolicy {
    fun enabled(context: Context): Boolean = context.getSharedPreferences("merchant_discovery_settings",Context.MODE_PRIVATE).getBoolean("online_opt_in",false)
    fun setEnabled(context: Context, enabled: Boolean) { context.getSharedPreferences("merchant_discovery_settings",Context.MODE_PRIVATE).edit().putBoolean("online_opt_in",enabled).apply() }
    fun eligible(context: Context, description: String, category: String): Boolean =
        enabled(context) && !AutomaticVendorIdentity.isManuallyUnassigned(context, description) &&
        VendorProfiles.get(context, description) == null &&
        AutomaticVendorIdentity.discoveryQuery(description, category) != null
}
