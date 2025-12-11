package com.detour.sdk.api

import android.util.Log
import com.detour.sdk.DetourConfig
import com.detour.sdk.models.DeviceFingerprint
import com.detour.sdk.models.LinkMatchResponse
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * API client for Detour service.
 */
internal class DetourApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Match link using fingerprint data.
     *
     * @param config Detour configuration
     * @param fingerprint Device fingerprint (probabilistic or deterministic)
     * @return Matched link or null
     */
    suspend fun matchLink(
        config: DetourConfig,
        fingerprint: DeviceFingerprint
    ): String? = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(fingerprint)
            Log.d(TAG, "Sending fingerprint to API")

            val requestBody = json.toRequestBody(MEDIA_TYPE_JSON)

            val request = Request.Builder()
                .url(API_URL)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("X-App-ID", config.appId)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val linkResponse = gson.fromJson(responseBody, LinkMatchResponse::class.java)
                    Log.d(TAG, "Link matched: ${linkResponse.link}")
                    return@withContext linkResponse.link
                }
            } else {
                Log.e(TAG, "[Detour:NETWORK_ERROR] API request failed: ${response.code}")
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "[Detour:NETWORK_ERROR] API request exception", e)
            null
        }
    }

    companion object {
        private const val TAG = "DetourApiClient"
        private const val API_URL = "https://godetour.dev/api/link/match-link"
        private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()
    }
}
