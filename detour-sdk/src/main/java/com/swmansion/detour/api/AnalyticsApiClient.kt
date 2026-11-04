package com.swmansion.detour.api

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.swmansion.detour.DetourConfig
import com.swmansion.detour.analytics.AnalyticsRequest
import com.swmansion.detour.analytics.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * API client for Detour analytics endpoints.
 *
 * Fire-and-forget: it logs warnings on failure and never throws.
 */
internal class AnalyticsApiClient(
    private val config: DetourConfig,
    private val requestContext: RequestContextProvider,
) {

    suspend fun send(request: AnalyticsRequest, deviceId: String, userId: String?) = withContext(Dispatchers.IO) {
        val endpoint = endpointOf(request)
        val url = DetourEndpoints.url(endpoint, config) ?: return@withContext
        try {
            val json = HttpClient.gson.toJson(body(request, requestContext.current(), deviceId, userId))
            val httpRequest = Request.Builder()
                .url(url)
                .post(json.toRequestBody(HttpClient.JSON))
                .detourHeaders(config)
                .build()

            HttpClient.okHttp.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "[Detour:ANALYTICS] ${endpoint.path} send failed: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[Detour:ANALYTICS] ${endpoint.path} send exception", e)
        }
    }

    companion object {
        private const val TAG = "AnalyticsApiClient"

        private fun endpointOf(request: AnalyticsRequest): DetourEndpoint = when (request) {
            is AnalyticsRequest.Event -> DetourEndpoint.ANALYTICS_EVENT
            is AnalyticsRequest.Retention -> DetourEndpoint.ANALYTICS_RETENTION
            is AnalyticsRequest.Session -> DetourEndpoint.ANALYTICS_SESSION
            is AnalyticsRequest.Conversion -> DetourEndpoint.ANALYTICS_CONVERSION
        }

        private fun timestamp(atMs: Long = System.currentTimeMillis()): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date(atMs))

        private fun fieldsOf(request: AnalyticsRequest): Map<String, Any?> = when (request) {
            is AnalyticsRequest.Event -> mapOf(
                "event_name" to request.name,
                "data" to request.data,
                "timestamp" to timestamp(),
                "platform" to "android",
            )
            is AnalyticsRequest.Retention -> mapOf(
                "event_name" to request.name,
                "timestamp" to timestamp(),
                "platform" to "android",
            )
            is AnalyticsRequest.Session -> when (val event = request.event) {
                is SessionManager.Event.Start -> mapOf(
                    "type" to "start",
                    "session_id" to event.sessionId,
                    "timestamp" to timestamp(event.atMs),
                )
                is SessionManager.Event.End -> mapOf(
                    "type" to "end",
                    "session_id" to event.sessionId,
                    "timestamp" to timestamp(event.atMs),
                    "duration_ms" to event.durationMs,
                )
            }
            is AnalyticsRequest.Conversion -> with(request.conversion) {
                mapOf(
                    "name" to name,
                    // Gson writes BigDecimal as is, so 0.001234 does not turn into 0.0012339999...
                    "revenue" to revenue,
                    "currency" to currency,
                    "revenue_type" to revenueType.wireName,
                    "product_id" to productId,
                    "quantity" to quantity,
                    "transaction_id" to transactionId,
                    "timestamp" to timestamp(request.atMs),
                )
            }
        }

        /** Context, then the request's own fields, which win on a shared name, then the identity. */
        @VisibleForTesting
        internal fun body(
            request: AnalyticsRequest,
            context: RequestContext,
            deviceId: String,
            userId: String?,
        ): Map<String, Any?> = buildMap {
            putAll(context.toMap())
            putAll(fieldsOf(request))
            put("device_id", deviceId)
            if (userId != null) put("user_id", userId)
        }
    }
}
