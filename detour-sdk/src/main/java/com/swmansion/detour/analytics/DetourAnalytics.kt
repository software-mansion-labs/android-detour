package com.swmansion.detour.analytics

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.swmansion.detour.DetourConfig
import com.swmansion.detour.api.AnalyticsApiClient
import com.swmansion.detour.api.RequestContextProvider
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

    @Volatile
    private var isStarted = false

    private lateinit var apiClient: AnalyticsApiClient
    private lateinit var installStore: InstallStore
    private lateinit var userIdStore: UserIdStore

    @Volatile
    private var appOpenFired = false

    internal val sessionManager = SessionManager()

    /**
     * Internal — called from [com.swmansion.detour.Detour.configure].
     */
    internal fun initialize(
        config: DetourConfig,
        requestContext: RequestContextProvider,
        installStore: InstallStore,
        userIdStore: UserIdStore,
    ) {
        apiClient = AnalyticsApiClient(config, requestContext)
        this.installStore = installStore
        this.userIdStore = userIdStore
        isInitialized = true
    }

    /**
     * Internal — called from [com.swmansion.detour.Detour.start].
     */
    @Synchronized
    internal fun start() {
        if (isStarted) return
        isStarted = true
        startSessions()
        logAppOpenIfNeeded()
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
        if (eventName == DetourEventNames.Purchase && (data as? Map<*, *>)?.containsKey("revenue") == true) {
            Log.w(
                TAG,
                "[Detour:DEPRECATION] Revenue in logEvent(Purchase) is deprecated and not counted as revenue. " +
                    "Use logConversion instead."
            )
        }
        send(AnalyticsRequest.Event(eventName.eventName, data))
    }

    /**
     * Log a retention event.
     *
     * @param eventName Free-form retention event name.
     */
    @JvmStatic
    fun logRetention(eventName: String) {
        send(AnalyticsRequest.Retention(eventName))
    }

    /**
     * Log a conversion with revenue, e.g. a purchase or ad revenue.
     */
    @JvmStatic
    fun logConversion(conversion: DetourConversion) {
        send(AnalyticsRequest.Conversion(conversion, System.currentTimeMillis()))
    }

    private fun send(request: AnalyticsRequest) {
        if (!isStarted) {
            Log.w(TAG, "[Detour:ANALYTICS] Analytics not started — call Detour.start() (or Detour.configure()) first")
            return
        }

        scope.launch {
            try {
                apiClient.send(request, installStore.getDeviceId(), userIdStore.current())
            } catch (e: Exception) {
                Log.w(TAG, "[Detour:ANALYTICS] Sending ${request::class.simpleName} failed", e)
            }
        }
    }

    private fun startSessions() {
        sessionManager.start { event -> send(AnalyticsRequest.Session(event)) }
        // ProcessLifecycleOwner must be observed from the main thread; it replays ON_START
        // when the app is already in the foreground.
        Handler(Looper.getMainLooper()).post {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = sessionManager.onForeground()
                override fun onStop(owner: LifecycleOwner) = sessionManager.onBackground()
            })
        }
    }

    /**
     * Internal — fires an `app_open` retention event once per session.
     * Mirrors the RN SDK's `useAppOpenRetention`.
     */
    private fun logAppOpenIfNeeded() {
        if (appOpenFired) return
        appOpenFired = true
        logRetention("app_open")
    }
}
