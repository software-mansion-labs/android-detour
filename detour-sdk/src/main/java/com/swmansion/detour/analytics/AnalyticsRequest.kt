package com.swmansion.detour.analytics

/** One analytics request; `AnalyticsApiClient.send` builds its body and endpoint. */
internal sealed interface AnalyticsRequest {
    data class Event(val name: String, val data: Any?, val atMs: Long) : AnalyticsRequest
    data class Retention(val name: String, val atMs: Long) : AnalyticsRequest
    data class Session(val event: SessionManager.Event) : AnalyticsRequest
    data class Conversion(val conversion: DetourConversion, val atMs: Long) : AnalyticsRequest
}
