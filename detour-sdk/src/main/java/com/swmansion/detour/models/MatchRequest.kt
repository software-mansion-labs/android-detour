package com.swmansion.detour.models

import com.google.gson.annotations.SerializedName

/**
 * Base interface for link-matching requests.
 */
sealed interface MatchRequest

/**
 * Signal-based match request built from device signals.
 */
data class SignalMatchRequest(
    @SerializedName("platform")
    val platform: String,
    
    @SerializedName("model")
    val model: String,
    
    @SerializedName("manufacturer")
    val manufacturer: String,
    
    @SerializedName("systemVersion")
    val systemVersion: String,
    
    @SerializedName("screenWidth")
    val screenWidth: Int,
    
    @SerializedName("screenHeight")
    val screenHeight: Int,
    
    @SerializedName("scale")
    val scale: Float,
    
    @SerializedName("locale")
    val locale: List<LocaleInfo>,
    
    @SerializedName("timezone")
    val timezone: String?,
    
    @SerializedName("userAgent")
    val userAgent: String?,
    
    @SerializedName("timestamp")
    val timestamp: Long,
    
    @SerializedName("pastedLink")
    val pastedLink: String? = null
) : MatchRequest

/**
 * Direct match request using the click ID from the install referrer.
 */
data class DirectMatchRequest(
    @SerializedName("clickId")
    val clickId: String
) : MatchRequest

/**
 * Locale information.
 */
data class LocaleInfo(
    @SerializedName("languageTag")
    val languageTag: String
)

/**
 * API response from link matching endpoint.
 */
data class LinkMatchResponse(
    @SerializedName("link")
    val link: String?
)
