package com.swmansion.detour.api

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.google.gson.JsonParser
import com.swmansion.detour.DetourConfig
import com.swmansion.detour.analytics.AnalyticsRequest
import com.swmansion.detour.analytics.SessionManager
import com.swmansion.detour.utils.IsoTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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
                    val error = apiError(runCatching { response.body?.string() }.getOrNull())?.let { " $it" }.orEmpty()
                    Log.w(TAG, "[Detour:ANALYTICS] ${endpoint.path} send failed: ${response.code}$error")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[Detour:ANALYTICS] ${endpoint.path} send exception", e)
        }
    }

    companion object {
        private const val TAG = "AnalyticsApiClient"

        /**
         * `{ "error": ..., "code": ... }` from a failed response, as `error (code)`.
         * Read with JsonParser, not a data class, so R8 renaming fields in the app cannot break it.
         */
        @VisibleForTesting
        internal fun apiError(body: String?): String? {
            val json = body?.let { runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull() } ?: return null
            fun field(name: String) = json[name]?.takeIf { it.isJsonPrimitive }?.asString
            val error = field("error")
            val code = field("code")
            return if (error != null && code != null) "$error ($code)" else error ?: code
        }

        private fun endpointOf(request: AnalyticsRequest): DetourEndpoint = when (request) {
            is AnalyticsRequest.Event -> DetourEndpoint.ANALYTICS_EVENT
            is AnalyticsRequest.Retention -> DetourEndpoint.ANALYTICS_RETENTION
            is AnalyticsRequest.Session -> DetourEndpoint.ANALYTICS_SESSION
            is AnalyticsRequest.Conversion -> DetourEndpoint.ANALYTICS_CONVERSION
        }

        private fun fieldsOf(request: AnalyticsRequest): Map<String, Any?> = when (request) {
            is AnalyticsRequest.Event -> mapOf(
                "event_name" to request.name,
                "data" to request.data,
                "timestamp" to IsoTimestamp.format(request.atMs),
                "platform" to "android",
            )
            is AnalyticsRequest.Retention -> mapOf(
                "event_name" to request.name,
                "timestamp" to IsoTimestamp.format(request.atMs),
                "platform" to "android",
            )
            is AnalyticsRequest.Session -> when (val event = request.event) {
                is SessionManager.Event.Start -> mapOf(
                    "type" to "start",
                    "session_id" to event.sessionId,
                    "timestamp" to IsoTimestamp.format(event.atMs),
                )
                is SessionManager.Event.End -> mapOf(
                    "type" to "end",
                    "session_id" to event.sessionId,
                    "timestamp" to IsoTimestamp.format(event.atMs),
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
                    "timestamp" to IsoTimestamp.format(request.atMs),
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
