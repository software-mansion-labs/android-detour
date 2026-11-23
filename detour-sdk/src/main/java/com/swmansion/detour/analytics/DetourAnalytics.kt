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
import com.swmansion.detour.consent.ConsentDecider
import com.swmansion.detour.consent.ConsentWarnings
import com.swmansion.detour.consent.DetourPurpose
import com.swmansion.detour.consent.GateDecision
import com.swmansion.detour.consent.SdkConfigStore
import com.swmansion.detour.identity.UserIdStore
import com.swmansion.detour.storage.InstallStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

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
    private lateinit var consentDecider: ConsentDecider
    private lateinit var sdkConfigStore: SdkConfigStore

    private val waitingForConsent = mutableListOf<AnalyticsRequest>()

    // PROVISIONAL: kept in memory and capped, so a closed gate the app never answers
    // cannot grow without bound. The oldest requests (app_open, the session start) are kept.
    private const val MAX_WAITING_FOR_CONSENT = 100

    @Volatile
    private var appOpenFired = false

    internal val sessionManager = SessionManager()

    private val revenueWarnedEvents = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    /**
     * Internal — called from [com.swmansion.detour.Detour.configure].
     */
    internal fun initialize(
        config: DetourConfig,
        requestContext: RequestContextProvider,
        installStore: InstallStore,
        userIdStore: UserIdStore,
        consentDecider: ConsentDecider,
        sdkConfigStore: SdkConfigStore,
    ) {
        apiClient = AnalyticsApiClient(config, requestContext)
        this.installStore = installStore
        this.userIdStore = userIdStore
        this.consentDecider = consentDecider
        this.sdkConfigStore = sdkConfigStore
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
        reevaluateWaiting()
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
        if (takeRevenueWarning(eventName.eventName, data)) {
            Log.w(
                TAG,
                "[Detour:DEPRECATION] Revenue in logEvent(${eventName.eventName}) is not counted as revenue. " +
                    "Use DetourAnalytics.logConversion instead."
            )
        }
        send(AnalyticsRequest.Event(eventName.eventName, data, System.currentTimeMillis()))
    }

    /**
     * Log a retention event.
     *
     * @param eventName Free-form retention event name.
     */
    @JvmStatic
    fun logRetention(eventName: String) {
        send(AnalyticsRequest.Retention(eventName, System.currentTimeMillis()))
    }

    /**
     * Log a conversion with revenue, e.g. a purchase or ad revenue.
     */
    @JvmStatic
    fun logConversion(conversion: DetourConversion) {
        val error = conversion.validationError()
        if (error != null) {
            Log.w(TAG, "[Detour:ANALYTICS] logConversion dropped: $error.")
            return
        }
        send(AnalyticsRequest.Conversion(conversion, System.currentTimeMillis()))
    }

    /** Once per event name per process, so frequent events like ad impressions do not flood the log. */
    internal fun takeRevenueWarning(eventName: String, data: Any?): Boolean =
        (data as? Map<*, *>)?.containsKey("revenue") == true && revenueWarnedEvents.add(eventName)

    private fun send(request: AnalyticsRequest) {
        if (!isInitialized) {
            Log.w(TAG, "[Detour:ANALYTICS] Analytics not set up — call Detour.configure() first")
            return
        }
        // Requests logged before start() wait like those of a closed gate; the gate decides them after it.
        if (!isStarted) {
            synchronized(waitingForConsent) {
                if (!isStarted) {
                    if (waitingForConsent.size < MAX_WAITING_FOR_CONSENT) waitingForConsent.add(request)
                    return
                }
            }
        }

        scope.launch { deliver(request) }
    }

    /** Sends the requests that waited for consent once a new consent or config decides them. */
    internal fun reevaluateWaiting() {
        val waiting = synchronized(waitingForConsent) {
            waitingForConsent.toList().also { waitingForConsent.clear() }
        }
        waiting.forEach { scope.launch { deliver(it) } }
    }

    private suspend fun deliver(request: AnalyticsRequest) {
        val inputs = consentDecider.read(DetourPurpose.ANALYTICS)
        // Decided under the lock, so a consent passed meanwhile either decides this request
        // or finds it in the waiting list.
        val decision = synchronized(waitingForConsent) {
            val decision = consentDecider.decide(DetourPurpose.ANALYTICS, inputs)
            if (decision == GateDecision.WAIT && waitingForConsent.size < MAX_WAITING_FOR_CONSENT) {
                waitingForConsent.add(request)
            }
            decision
        }
        if (decision == GateDecision.WAIT) {
            val isStoredGrantWarned = ConsentWarnings.warnIfStoredGrantWaits(DetourPurpose.ANALYTICS, inputs.storedConsent)
            if (!isStoredGrantWarned && sdkConfigStore.isKnown()) {
                ConsentWarnings.warnOnce(
                    "analyticsWaitsForConsent",
                    "Analytics waits: the Detour server keeps it closed until your app calls setConsent on this launch.",
                )
            }
        }
        if (decision != GateDecision.SEND) return

        try {
            apiClient.send(request, installStore.getDeviceId(), userIdStore.current())
        } catch (e: Exception) {
            Log.w(TAG, "[Detour:ANALYTICS] Sending ${request::class.simpleName} failed", e)
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
