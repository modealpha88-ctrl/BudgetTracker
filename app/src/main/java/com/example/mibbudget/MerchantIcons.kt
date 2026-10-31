package com.example.mibbudget

/** Offline, deterministic merchant pictograms. No network calls or identity claims. */
object MerchantIcons {
    fun icon(description: String, category: String): String {
        val name = VendorProfiles.vendor(description).lowercase()
        // User-selected category wins over guesses made from the bank description.
        val selected = category.trim().lowercase()
        when {
            selected.contains("food") || selected.contains("dining") || selected.contains("restaurant") -> return "🍽"
            selected.contains("transport") || selected.contains("travel") -> return "🚗"
            selected.contains("shopping") || selected.contains("grocer") -> return "🛍"
            selected.contains("health") || selected.contains("medical") -> return "✚"
            selected.contains("bill") || selected.contains("utilit") -> return "💡"
            selected.contains("entertain") -> return "🎬"
            selected.contains("education") -> return "📚"
            selected.contains("salary") || selected.contains("income") -> return "↓"
            selected.contains("savings") -> return "💰"
            selected.contains("internal transfer") || selected == "transfer" || selected == "transfers" -> return "↗"
        }
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
