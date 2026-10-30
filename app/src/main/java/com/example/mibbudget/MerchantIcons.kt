package com.example.mibbudget

/** Offline, deterministic merchant pictograms. No network calls or identity claims. */
object MerchantIcons {
    fun icon(description: String, category: String): String {
        val name = VendorProfiles.vendor(description).lowercase()
        if (VendorProfiles.isTransfer(description, category)) return "↗"
        return when {
            Regex("cafe|coffee|tea|espresso|bakery|bake|pastry").containsMatchIn(name) -> "☕"
            Regex("fuel|petrol|gas station|pump").containsMatchIn(name) -> "⛽"
            Regex("mart|market|shop|store|supermarket|grocery|fihaara|fishi|foshi").containsMatchIn(name) -> "🛍"
            Regex("restaurant|dine|kitchen|grill|pizza|burger|food").containsMatchIn(name) -> "🍽"
            Regex("pharmacy|medical|clinic|health").containsMatchIn(name) -> "✚"
            Regex("taxi|ferry|transport|bus").containsMatchIn(name) -> "🚕"
            Regex("hotel|resort|guesthouse|stay").containsMatchIn(name) -> "🏨"
            Regex("telecom|mobile|internet|data").containsMatchIn(name) -> "📱"
            Regex("hardware|tools|construction").containsMatchIn(name) -> "🛠"
            Regex("salon|spa|beauty|barber").containsMatchIn(name) -> "✂"
            category.contains("Food",true) || category.contains("Dining",true) -> "🍽"
            category.contains("Transport",true) -> "🚗"
            category.contains("Shopping",true) || category.contains("Grocer",true) -> "🛍"
            category.contains("Health",true) -> "✚"
            category.contains("Bills",true) || category.contains("Utilities",true) -> "💡"
            category.contains("Entertainment",true) -> "🎬"
            else -> "🏬"
        }
    }
}
