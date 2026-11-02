package com.swmansion.detour.analytics

import java.math.BigDecimal

enum class RevenueType(val wireName: String) {
    /** In-app purchases and subscriptions. */
    IAP("iap"),

    /** Revenue from ads shown in the app. */
    IN_APP_ADS("in_app_ads"),
    OTHER("other"),
}

/**
 * A conversion with revenue, sent with [DetourAnalytics.logConversion].
 *
 * @property currency ISO 4217 code, e.g. `USD`.
 * @property transactionId The store's order ID. A conversion repeated with the same ID is counted once.
 */
data class DetourConversion @JvmOverloads constructor(
    val name: String,
    val revenue: BigDecimal,
    val currency: String,
    val revenueType: RevenueType,
    val productId: String? = null,
    val quantity: Int? = null,
    val transactionId: String? = null,
)
