package com.swmansion.detour.api

import android.util.Log
import com.swmansion.detour.DetourConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal enum class DetourEndpoint(val path: String) {
    MATCH_LINK("api/link/match-link"),
    RESOLVE_SHORT("api/link/resolve-short"),
    UNIVERSAL_LINK_CLICK("api/link/universal-link-click"),
    ANALYTICS_EVENT("api/analytics/event"),
    ANALYTICS_RETENTION("api/analytics/retention"),
}

internal object DetourEndpoints {
    private const val TAG = "DetourEndpoints"

    const val DEFAULT_BASE_URL = "https://godetour.dev"

    // PROVISIONAL: an invalid baseUrl blocks requests instead of falling back to production,
    // so test traffic never reaches production data.
    /** Returns null when [DetourConfig.baseUrl] is not an https URL, so no request leaves the device. */
    fun url(endpoint: DetourEndpoint, config: DetourConfig): String? =
        resolveBaseUrl(config.baseUrl)
            ?.newBuilder()
            ?.addPathSegments(endpoint.path)
            ?.build()
            ?.toString()

    /** Logs once at startup when [DetourConfig.baseUrl] is set, so a test host does not end up in a production build. */
    fun reportBaseUrl(config: DetourConfig) {
        val configured = config.baseUrl ?: return
        if (configured == DEFAULT_BASE_URL) return

        if (resolveBaseUrl(configured) != null) {
            Log.w(TAG, "[Detour:CONFIG] Using custom baseUrl $configured. Remove it before releasing to production.")
        } else {
            Log.e(TAG, "[Detour:CONFIG] Invalid baseUrl $configured: only https URLs are allowed. Requests are not sent.")
        }
    }

    private fun resolveBaseUrl(configured: String?): HttpUrl? {
        val url = (configured ?: DEFAULT_BASE_URL).toHttpUrlOrNull() ?: return null
        return url.takeIf { it.scheme == "https" && it.host.isNotEmpty() }
    }
}
