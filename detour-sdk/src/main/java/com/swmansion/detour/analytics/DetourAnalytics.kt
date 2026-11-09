package com.swmansion.detour.analytics

import android.util.Log
import com.swmansion.detour.DetourConfig
import com.swmansion.detour.api.AnalyticsApiClient
import com.swmansion.detour.identity.UserIdStore
import com.swmansion.detour.storage.InstallStore
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
    private lateinit var installStore: InstallStore
    private lateinit var userIdStore: UserIdStore

    @Volatile
    private var appOpenFired = false

    /**
     * Internal — called from [com.swmansion.detour.Detour.initialize].
     */
    internal fun initialize(config: DetourConfig, installStore: InstallStore, userIdStore: UserIdStore) {
        apiClient = AnalyticsApiClient(config)
        this.installStore = installStore
        this.userIdStore = userIdStore
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
        if (!isInitialized) {
            Log.w(TAG, "[Detour:ANALYTICS] Analytics not initialized — call Detour.initialize() first")
            return
        }

        scope.launch {
            try {
                apiClient.sendEvent(eventName.eventName, data, installStore.getDeviceId(), userIdStore.current())
            } catch (e: Exception) {
                Log.w(TAG, "[Detour:ANALYTICS] logEvent failed", e)
            }
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
                apiClient.sendRetentionEvent(eventName, installStore.getDeviceId(), userIdStore.current())
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
