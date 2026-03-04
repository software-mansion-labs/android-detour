package com.detour.sdk.api

import android.util.Log
import com.detour.sdk.DetourConfig
import com.detour.sdk.models.DeviceFingerprint
import com.detour.sdk.models.LinkMatchResponse
import com.detour.sdk.models.ShortLinkResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * API client for Detour link-matching and short-link resolution.
 */
internal class DetourApiClient(private val config: DetourConfig) {

    /**
     * Match link using fingerprint data.
     *
     * @param fingerprint Device fingerprint (probabilistic or deterministic)
     * @return Matched link or null
     */
    suspend fun matchLink(fingerprint: DeviceFingerprint): String? = withContext(Dispatchers.IO) {
        try {
            val json = HttpClient.gson.toJson(fingerprint)
            Log.d(TAG, "Sending fingerprint to API")

            val requestBody = json.toRequestBody(HttpClient.JSON)

            val request = Request.Builder()
                .url(MATCH_LINK_URL)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("X-App-ID", config.appId)
                .build()

            HttpClient.okHttp.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val linkResponse = HttpClient.gson.fromJson(
                            responseBody,
                            LinkMatchResponse::class.java
                        )
                        if (linkResponse.link != null) {
                            Log.d(TAG, "Link matched successfully")
                        }
                        return@use linkResponse.link
                    }
                } else {
                    Log.w(TAG, "[Detour:NETWORK_ERROR] API request failed: ${response.code}")
                }
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "[Detour:NETWORK_ERROR] API request exception", e)
            null
        }
    }

    /**
     * Resolve a short link to its full link data.
     *
     * @param url The short link URL to resolve
     * @return ShortLinkResponse or null on 404/error (matches RN SDK behavior)
     */
    suspend fun resolveShortLink(url: String): ShortLinkResponse? = withContext(Dispatchers.IO) {
        try {
            val payload = HttpClient.gson.toJson(mapOf("url" to url))
            val requestBody = payload.toRequestBody(HttpClient.JSON)

            val request = Request.Builder()
                .url(RESOLVE_SHORT_URL)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("X-App-ID", config.appId)
                .build()

            HttpClient.okHttp.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        return@use HttpClient.gson.fromJson(
                            responseBody,
                            ShortLinkResponse::class.java
                        )
                    }
                } else {
                    Log.w(TAG, "[Detour:NETWORK_ERROR] Short link resolution failed: ${response.code}")
                }
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "[Detour:NETWORK_ERROR] Short link resolution exception", e)
            null
        }
    }

    companion object {
        private const val TAG = "DetourApiClient"
        private const val MATCH_LINK_URL = "https://godetour.dev/api/link/match-link"
        private const val RESOLVE_SHORT_URL = "https://godetour.dev/api/link/resolve-short"
    }
}
