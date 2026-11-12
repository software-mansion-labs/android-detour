package com.swmansion.detour.api

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.swmansion.detour.DetourConfig
import com.swmansion.detour.consent.DetourConsent
import com.swmansion.detour.consent.KillSwitchReporter.Outcome
import com.swmansion.detour.utils.IsoTimestamp
import com.swmansion.detour.consent.SdkConfigStore.FetchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Requests to the `api/sdk/` endpoints. */
internal class SdkApiClient(private val config: DetourConfig) {

    suspend fun fetchConfig(etag: String?): FetchResult = withContext(Dispatchers.IO) {
        val url = DetourEndpoints.url(DetourEndpoint.SDK_CONFIG, config) ?: return@withContext FetchResult.Failed
        val request = Request.Builder()
            .url(url)
            .get()
            .detourHeaders(config)
            // The SDK keeps its own copy and sends the ETag itself.
            .cacheControl(CacheControl.FORCE_NETWORK)
            .apply { if (etag != null) header("If-None-Match", etag) }
            .build()

        try {
            HttpClient.okHttp.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> response.body?.string()
                        ?.let { FetchResult.Updated(it, response.header("ETag")) }
                        ?: FetchResult.Failed
                    304 -> FetchResult.NotModified
                    else -> {
                        Log.w(TAG, "[Detour:CONFIG] sdk/config fetch failed: ${response.code}; using the last config.")
                        FetchResult.Failed
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[Detour:CONFIG] sdk/config fetch exception; using the last config.", e)
            FetchResult.Failed
        }
    }

    /** `true` when the server confirmed the consent. */
    suspend fun sendConsent(consent: DetourConsent, consentText: String?, deviceId: String, userId: String?): Boolean {
        val status = post(DetourEndpoint.SDK_CONSENT, consentBody(consent, consentText, deviceId, userId)) ?: return false
        val confirmed = status in 200..299
        if (!confirmed) Log.w(TAG, "[Detour:CONSENT] sdk/consent send failed: $status")
        return confirmed
    }

    suspend fun sendKillSwitch(enabled: Boolean, atMs: Long, deviceId: String): Outcome {
        if (DetourEndpoints.url(DetourEndpoint.SDK_DISABLE, config) == null) return Outcome.STOP
        val body = mapOf(
            "device_id" to deviceId,
            "disabled" to !enabled,
            "timestamp" to IsoTimestamp.format(atMs),
        )
        // The server stops writing about 28 s after a request starts, so a request given up
        // after 60 s can no longer land after the next one. The shared client gives up at 15 s.
        val client = HttpClient.okHttp.newBuilder()
            .callTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        val status = post(DetourEndpoint.SDK_DISABLE, body, client) ?: return Outcome.RETRY

        return when {
            status in 200..299 -> Outcome.CONFIRMED
            status == 429 || status >= 500 -> Outcome.RETRY
            else -> {
                Log.e(TAG, "[Detour:KILL_SWITCH] sdk/disable rejected: $status; sent again on the next launch.")
                Outcome.STOP
            }
        }
    }

    /** The response status, or null when no response arrived. */
    private suspend fun post(
        endpoint: DetourEndpoint,
        body: Map<String, Any>,
        client: OkHttpClient = HttpClient.okHttp,
    ): Int? = withContext(Dispatchers.IO) {
        val url = DetourEndpoints.url(endpoint, config) ?: return@withContext null
        val request = Request.Builder()
            .url(url)
            .post(HttpClient.gson.toJson(body).toRequestBody(HttpClient.JSON))
            .detourHeaders(config)
            .build()
        try {
            client.newCall(request).execute().use { it.code }
        } catch (e: Exception) {
            Log.w(TAG, "[Detour:NETWORK_ERROR] ${endpoint.path} exception", e)
            null
        }
    }

    companion object {
        private const val TAG = "SdkApiClient"

        // The server rejects the whole consent for an empty text, so an empty text is not sent.
        @VisibleForTesting
        internal fun consentBody(consent: DetourConsent, consentText: String?, deviceId: String, userId: String?) =
            buildMap<String, Any> {
                put("device_id", deviceId)
                userId?.let { put("user_id", it) }
                put("consent", consent.toWire())
                consentText?.takeIf { it.isNotEmpty() }?.let { put("consent_text", it) }
            }
    }
}
