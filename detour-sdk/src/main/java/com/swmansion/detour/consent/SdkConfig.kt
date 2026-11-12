package com.swmansion.detour.consent

import android.util.Log
import com.google.gson.annotations.SerializedName
import com.swmansion.detour.api.HttpClient
import com.swmansion.detour.storage.DetourStorage
import com.swmansion.detour.storage.StorageKeys
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal enum class ConsentGateDefault {
    /** Sends while the app has not passed a consent decision. */
    OPEN,

    /** Waits for `setConsent`. */
    CLOSED;

    companion object {
        fun fromWire(value: String): ConsentGateDefault? = when (value) {
            "open" -> OPEN
            "closed" -> CLOSED
            else -> null
        }
    }
}

/** The per-app values from `GET /api/sdk/config` that the SDK uses. */
internal data class SdkConfig(
    val schemaVersion: Int,
    val consentGateDefaults: Map<String, ConsentGateDefault>,
) {
    /** A purpose the config does not list waits for consent. */
    fun gateDefault(purpose: String): ConsentGateDefault =
        consentGateDefaults[purpose] ?: ConsentGateDefault.CLOSED

    private class Response(
        @SerializedName("schema_version") val schemaVersion: Int?,
        @SerializedName("consent_gate_default") val consentGateDefault: Map<String, String>?,
    )

    companion object {
        // The only gate values written in the SDK, used before the first successful fetch
        // (API contract, consent gate fallback). Every other value comes from the server.
        val FALLBACK = SdkConfig(
            schemaVersion = 1,
            consentGateDefaults = mapOf(DetourPurpose.ANALYTICS to ConsentGateDefault.OPEN),
        )

        /** null for a body in another shape, so an unexpected response never replaces a good config. */
        fun fromJson(json: String): SdkConfig? {
            val response = runCatching { HttpClient.gson.fromJson(json, Response::class.java) }.getOrNull()
                ?: return null
            val schemaVersion = response.schemaVersion ?: return null
            val gates = response.consentGateDefault ?: return null
            return SdkConfig(
                schemaVersion = schemaVersion,
                consentGateDefaults = gates.mapNotNull { (purpose, value) ->
                    ConsentGateDefault.fromWire(value)?.let { purpose to it }
                }.toMap(),
            )
        }
    }
}

/** The last config the server sent, kept across launches and used while a fetch fails. */
internal class SdkConfigStore(private val storage: DetourStorage, private val appId: String) {

    sealed interface FetchResult {
        data class Updated(val body: String, val etag: String?) : FetchResult
        object NotModified : FetchResult
        object Failed : FetchResult
    }

    private data class Cached(val appId: String, val etag: String?, val body: String)

    private val mutex = Mutex()

    @Volatile
    private var loaded = false

    @Volatile
    private var cached: Cached? = null

    @Volatile
    private var config = SdkConfig.FALLBACK

    /** The config as loaded so far: the fallback until the cache is read. */
    val latest: SdkConfig get() = config

    /** Waits for the cached config on first use, so no request is decided by the fallback by mistake. */
    suspend fun current(): SdkConfig {
        load()
        return config
    }

    suspend fun etag(): String? {
        load()
        return cached?.etag
    }

    /** Returns true when the config changed. */
    suspend fun apply(result: FetchResult): Boolean {
        load()
        if (result !is FetchResult.Updated) return false
        val fetched = SdkConfig.fromJson(result.body) ?: return false

        val saved = Cached(appId, result.etag, result.body)
        mutex.withLock {
            cached = saved
            runCatching { storage.setItem(StorageKeys.SDK_CONFIG, HttpClient.gson.toJson(saved)) }
                .onFailure { Log.w(TAG, "[Detour:CONFIG] Failed to save the sdk config.", it) }
        }
        val changed = fetched != config
        config = fetched
        return changed
    }

    // A cache from another app ID is ignored.
    private suspend fun load() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            val saved = runCatching {
                storage.getItem(StorageKeys.SDK_CONFIG)?.let { HttpClient.gson.fromJson(it, Cached::class.java) }
            }.getOrNull()
            val savedConfig = saved?.takeIf { it.appId == appId }?.let { SdkConfig.fromJson(it.body) }
            if (saved != null && savedConfig != null) {
                cached = saved
                config = savedConfig
            }
            loaded = true
        }
    }

    private companion object {
        const val TAG = "SdkConfigStore"
    }
}
