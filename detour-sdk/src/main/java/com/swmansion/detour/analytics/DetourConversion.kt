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

private const val MAX_TEXT_LENGTH = 255
private val MAX_REVENUE = BigDecimal("1000000000000")
private val CURRENCY_CODE = Regex("[A-Za-z]{3}")

/**
 * The backend rejects the whole conversion when one field is off, so the SDK checks it first.
 * Lengths are UTF-16 units, the same as the backend's JavaScript `length`.
 *
 * @return The broken rule, or `null` when the conversion can be sent.
 */
internal fun DetourConversion.validationError(): String? = when {
    name.length !in 1..MAX_TEXT_LENGTH -> "name must have 1 to $MAX_TEXT_LENGTH characters"
    revenue.signum() < 0 || revenue >= MAX_REVENUE -> "revenue must be at least 0 and less than ${MAX_REVENUE.toPlainString()}"
    !CURRENCY_CODE.matches(currency) -> "currency must be a three-letter ISO 4217 code"
    quantity != null && quantity < 0 -> "quantity must be at least 0"
    productId != null && productId.length > MAX_TEXT_LENGTH -> "productId must have at most $MAX_TEXT_LENGTH characters"
    transactionId != null && transactionId.length !in 1..MAX_TEXT_LENGTH ->
        "transactionId must have 1 to $MAX_TEXT_LENGTH characters"
    else -> null
}
