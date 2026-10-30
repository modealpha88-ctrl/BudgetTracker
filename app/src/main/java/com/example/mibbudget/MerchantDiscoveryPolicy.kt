package com.example.mibbudget

import android.content.Context

/** Never discover transfers, manually excluded merchants, or already confirmed profiles. */
object MerchantDiscoveryPolicy {
    fun eligible(context: Context, description: String, category: String): Boolean =
        !AutomaticVendorIdentity.isManuallyUnassigned(context, description) &&
        VendorProfiles.get(context, description) == null &&
        AutomaticVendorIdentity.discoveryQuery(description, category) != null
}
