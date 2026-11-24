package com.swmansion.detour.analytics

import android.util.Log
import com.swmansion.detour.DetourConfig
import com.swmansion.detour.api.AnalyticsApiClient
import com.swmansion.detour.api.HttpClient
import com.swmansion.detour.storage.DetourStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Public analytics API for the Detour SDK.
 *
 * All calls are fire-and-forget — they run on a background dispatcher
 * and never propagate exceptions to the caller.
 */
object DetourAnalytics {

    private const val TAG = "DetourAnalytics"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var isInitialized = false

    private lateinit var apiClient: AnalyticsApiClient
    private lateinit var deviceIdProvider: DeviceIdProvider

    @Volatile
    private var isDebuggable = false

    @Volatile
    private var appOpenFired = false

    /**
     * Internal — called from [com.swmansion.detour.Detour.initialize].
     */
    internal fun initialize(config: DetourConfig, storage: DetourStorage, isDebuggable: Boolean) {
        apiClient = AnalyticsApiClient(config)
        deviceIdProvider = DeviceIdProvider(storage)
        this.isDebuggable = isDebuggable
        isInitialized = true
    }

    /**
     * Log a predefined analytics event with optional data.
     *
     * @param eventName One of the [DetourEventNames] constants.
     * @param data Optional payload — maps, primitives, or any Gson-serializable object.
     */
    @JvmStatic
    @JvmOverloads
    fun logEvent(eventName: DetourEventNames, data: Any? = null) {
        logEvent(eventName.eventName, data)
    }

    /**
     * Log an analytics event under a custom name with optional data.
     *
     * Custom names must match `^[a-z0-9_]{1,64}$`. Names starting with `af_` or `dt_`,
     * and `opened_via_universal_link`, are reserved. An invalid name is dropped and
     * logged as an error.
     *
     * @param eventName The event name, e.g. `level_complete`.
     * @param data Optional payload — maps, primitives, or any Gson-serializable object.
     */
    @JvmStatic
    @JvmOverloads
    fun logEvent(eventName: String, data: Any? = null) {
        if (!isInitialized) {
            Log.w(TAG, "[Detour:ANALYTICS] Analytics not initialized — call Detour.initialize() first")
            return
        }

        val problem = EventNameRules.problem(eventName)
        if (problem != null) {
            Log.e(TAG, "[Detour:ANALYTICS] Event \"$eventName\" dropped: $problem.")
            return
        }

        scope.launch {
            try {
                if (isDebuggable && data != null) warnIfDataTooLarge(eventName, data)
                val deviceId = deviceIdProvider.getDeviceId()
                apiClient.sendEvent(eventName, data, deviceId)
            } catch (e: Exception) {
                Log.w(TAG, "[Detour:ANALYTICS] logEvent failed", e)
            }
        }
    }

    // Debuggable apps only: it serializes `data` a second time just to measure it.
    private fun warnIfDataTooLarge(eventName: String, data: Any) {
        val length = runCatching { HttpClient.gson.toJson(data).length }.getOrNull() ?: return
        if (length > EventNameRules.MAX_DATA_BYTES) {
            Log.w(
                TAG,
                "[Detour:ANALYTICS] Event \"$eventName\" has $length characters of data as JSON. " +
                    "Detour stores at most ${EventNameRules.MAX_DATA_BYTES} bytes of data per event and may refuse larger events."
            )
        }
    }

    /**
     * Log a retention event.
     *
     * @param eventName Free-form retention event name.
     */
    @JvmStatic
    fun logRetention(eventName: String) {
        if (!isInitialized) {
            Log.w(TAG, "[Detour:ANALYTICS] Analytics not initialized — call Detour.initialize() first")
            return
        }

        scope.launch {
            try {
                val deviceId = deviceIdProvider.getDeviceId()
                apiClient.sendRetentionEvent(eventName, deviceId)
            } catch (e: Exception) {
                Log.w(TAG, "[Detour:ANALYTICS] logRetention failed", e)
            }
        }
    }

    /**
     * Internal — fires an `app_open` retention event once per session.
     * Mirrors the RN SDK's `useAppOpenRetention`.
     */
    internal fun logAppOpenIfNeeded() {
        if (appOpenFired) return
        appOpenFired = true
        logRetention("app_open")
    }
}
