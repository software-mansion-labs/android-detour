package com.swmansion.detour.api

import android.util.Log
import com.swmansion.detour.DetourConfig
import com.swmansion.detour.consent.SdkConfigStore.FetchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Request

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

    private companion object {
        const val TAG = "SdkApiClient"
    }
}
