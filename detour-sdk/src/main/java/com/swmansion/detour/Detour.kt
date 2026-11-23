package com.swmansion.detour

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.ApplicationInfo
import android.util.Log
import com.swmansion.detour.analytics.DetourAnalytics
import com.swmansion.detour.api.DetourApiClient
import com.swmansion.detour.api.RequestContextProvider
import com.swmansion.detour.api.DetourEndpoints
import com.swmansion.detour.api.HttpClient
import com.swmansion.detour.api.SdkApiClient
import com.swmansion.detour.api.requestSdkPackage
import com.swmansion.detour.consent.ConsentDecider
import com.swmansion.detour.consent.ConsentStore
import com.swmansion.detour.consent.ConsentWarnings
import com.swmansion.detour.consent.GateDecision
import com.swmansion.detour.consent.DetourConsent
import com.swmansion.detour.consent.DetourPurpose
import com.swmansion.detour.consent.KillSwitchReporter
import com.swmansion.detour.consent.SdkConfigStore
import com.swmansion.detour.identity.UserIdStore
import com.swmansion.detour.identity.userIdRejection
import com.swmansion.detour.models.DirectMatchRequest
import com.swmansion.detour.models.LinkProcessingMode
import com.swmansion.detour.models.LinkResult
import com.swmansion.detour.models.LinkType
import com.swmansion.detour.models.MetaInstallReferrer
import com.swmansion.detour.models.ShortLinkResponse
import com.swmansion.detour.referrer.InstallReferrerHelper
import com.swmansion.detour.referrer.MetaInstallReferrerReader
import com.swmansion.detour.signals.SignalCollector
import com.swmansion.detour.storage.DefaultStorageProvider
import com.swmansion.detour.storage.DetourStorage
import com.swmansion.detour.storage.InstallStore
import com.swmansion.detour.utils.UrlHelpers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Main entry point for Detour SDK.
 *
 * Usage:
 * ```
 * // Configure in Application.onCreate() or Activity.onCreate()
 * val config = DetourConfig(
 *     appId = "your-app-id",
 *     apiKey = "your-api-key"
 * )
 * Detour.configure(context, config)
 *
 * // Process any link (Verified, Scheme, or Deferred) in onCreate()
 * lifecycleScope.launch {
 *     when (val result = Detour.processLink(intent)) {
 *         is LinkResult.Success -> {
 *             // Navigate to result.route
 *             // Access result.params, result.pathname, result.type
 *         }
 *         is LinkResult.NoLink -> { /* Normal app launch */ }
 *         is LinkResult.NotFirstLaunch -> { /* Already processed deferred link */ }
 *         is LinkResult.Error -> { /* Handle error */ }
 *     }
 * }
 * ```
 */
object Detour {

    private const val TAG = "Detour"

    @Volatile
    private var isInitialized = false

    /** The deferred link match of this process, kept when the Activity that asked for it is recreated. */
    private class DeferredMatch(val result: Deferred<LinkResult>) {
        val isDelivered = AtomicBoolean(false)
    }

    @Volatile
    private var deferredMatch: DeferredMatch? = null

    private val started = CompletableDeferred<Unit>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var applicationContext: Context
    private lateinit var config: DetourConfig
    private lateinit var storage: DetourStorage
    private lateinit var apiClient: DetourApiClient
    private lateinit var signalCollector: SignalCollector
    private lateinit var installReferrerHelper: InstallReferrerHelper
    private lateinit var metaInstallReferrerReader: MetaInstallReferrerReader
    private lateinit var installStore: InstallStore
    private lateinit var userIdStore: UserIdStore
    private lateinit var sdkApiClient: SdkApiClient
    private lateinit var sdkConfigStore: SdkConfigStore
    private lateinit var consentStore: ConsentStore
    private lateinit var consentDecider: ConsentDecider

    private lateinit var killSwitchReporter: KillSwitchReporter

    /** A consent passed before [configure]; recorded and sent once the SDK is set up. */
    private var consentBeforeConfigure: Pair<DetourConsent, String?>? = null

    private data class KillSwitchChange(val enabled: Boolean, val atMs: Long)

    // setEnabled applies at once; the file is written in the background, the latest call last.
    @Volatile
    private var enabledThisLaunch: KillSwitchChange? = null
    private val enabledWrite = Mutex()

    // Bumped after a consent, config or kill switch change, so whatever waits for consent decides again.
    private val consentInputs = MutableStateFlow(0L)

    // PROVISIONAL: how long after setConsent the SDK expects start() before it warns.
    internal const val START_AFTER_CONSENT_GRACE_MS = 5_000L

    /**
     * Sets up the SDK. Call it once in Application.onCreate().
     *
     * With `autoStart = false` analytics, sessions and deferred link matching wait for [start],
     * so the app can wait for its consent platform first. Links the user taps are routed and
     * counted either way: for each one the SDK sends the link, app version, OS version and device
     * model before [start], because your plan's click limit applies to it.
     *
     * @param context Application or Activity context
     * @param config Detour configuration
     * @param autoStart Calls [start] right away (default)
     */
    @JvmStatic
    @JvmOverloads
    @Synchronized
    fun configure(context: Context, config: DetourConfig, autoStart: Boolean = true) {
        if (isInitialized) {
            Log.w(TAG, "[Detour:CONFIG] configure called again; the first config is kept.")
        } else if (!setUp(context, config)) {
            return
        }

        if (autoStart) start()
    }

    /**
     * Starts analytics, sessions and deferred link matching. Call it after [configure].
     * Until then [getDeferredLink] waits.
     */
    @JvmStatic
    @Synchronized
    fun start() {
        if (!isInitialized) {
            Log.w(TAG, "[Detour:CONFIG] start called before configure; nothing started.")
            return
        }
        DetourAnalytics.start()
        started.complete(Unit)
    }

    /**
     * Initialize Detour SDK and start it.
     *
     * @param context Application or Activity context
     * @param config Detour configuration
     */
    @Deprecated(
        "Use configure, which also starts the SDK unless autoStart is false.",
        ReplaceWith("Detour.configure(context, config)")
    )
    @JvmStatic
    fun initialize(context: Context, config: DetourConfig) = configure(context, config)

    private fun consentInputsChanged() {
        DetourAnalytics.reevaluateWaiting()
        consentInputs.update { it + 1 }
    }

    // Once per launch, so a value changed on the server reaches the app on its next launch.
    // shortcut: with the SDK off, the first launch never fetches, so its gates stay closed until the
    // next launch even after setEnabled(true); fetch in setEnabled too if that case matters.
    private fun refreshSdkConfig() {
        scope.launch {
            if (!isEnabled()) return@launch
            sdkConfigStore.apply(sdkApiClient.fetchConfig(sdkConfigStore.etag()))
            // Also when nothing changed: on a first launch the gates waited for this fetch.
            consentInputsChanged()
        }
    }

    /**
     * Passes the user's choice from your consent platform. Call it on every launch once the
     * platform has the choice, not only when it changes: a choice stored on an earlier launch
     * can stop requests for a purpose, but a grant counts only once passed again on this launch.
     * Each call replaces the previous consent; pass every purpose in one call.
     *
     * Calling it on every launch is cheap: a choice the Detour server already has is not sent
     * again, unless you pass its [DetourConsent.timestampMs].
     *
     * @param consentText The text the user agreed to, stored once per version on the Detour server.
     *   An empty text is treated as not given.
     */
    @JvmStatic
    @JvmOverloads
    @Synchronized
    fun setConsent(consent: DetourConsent, consentText: String? = null) {
        if (isInitialized) {
            recordConsent(consent, consentText)
        } else {
            consentBeforeConfigure = consent to consentText
        }
    }

    // Recorded before returning, so a request logged right after setConsent is decided by it.
    private fun recordConsent(consent: DetourConsent, consentText: String?) {
        val withSchema = consent.schemaVersion?.let { consent }
            ?: consent.copy(schemaVersion = sdkConfigStore.latest.schemaVersion)
        // Not sent while the SDK is off; it stays unconfirmed, so the next setConsent sends it.
        consentStore.record(withSchema, installStore::getDeviceId) { consent, deviceId ->
            isEnabled() && sdkApiClient.sendConsent(consent, consentText, deviceId, userIdStore.current())
        }
        warnAboutConsentPitfalls(consent)
        consentInputsChanged()
    }

    private fun warnAboutConsentPitfalls(consent: DetourConsent) {
        if (DetourPurpose.ANALYTICS !in consent.purposes) {
            ConsentWarnings.warnOnce(
                "consentWithoutAnalytics",
                "setConsent has no analytics key, so analytics stops: a purpose missing from the map is not granted.",
            )
        }
        if (!ConsentWarnings.isDevelopment || started.isCompleted) return
        scope.launch {
            delay(START_AFTER_CONSENT_GRACE_MS)
            if (started.isCompleted) return@launch
            ConsentWarnings.warnOnce(
                "consentWithoutStart",
                "setConsent was called, but start() was not. With autoStart = false, analytics and deferred link matching wait for it.",
            )
        }
    }

    private suspend fun decideNow(purpose: String): GateDecision = consentDecider.decideNow(purpose)

    private suspend fun isWaitingForAttributionConsent(): Boolean =
        config.attribution != null && decideNow(DetourPurpose.ATTRIBUTION) == GateDecision.WAIT

    // The backend never attributes an install whose first match went as core, so with attribution
    // on that match waits until the gate decides: mmp after a grant, core after a refusal.
    private suspend fun awaitAttributionDecision() {
        while (true) {
            val seen = consentInputs.value
            if (!isWaitingForAttributionConsent()) return
            consentInputs.first { it != seen }
        }
    }

    /** What the SDK does now: started, enabled, consent on this launch, and each purpose's decision. */
    @JvmStatic
    suspend fun getStatus(): DetourStatus {
        if (!isInitialized) {
            Log.w(TAG, "[Detour:CONFIG] getStatus called before configure; nothing is set up.")
            return DetourStatus(isStarted = false, isEnabled = false, hasConsentThisLaunch = false, purposes = emptyMap())
        }
        val passed = consentStore.passedThisLaunch
        val purposeIds = setOf(
            DetourPurpose.ANALYTICS, DetourPurpose.ATTRIBUTION,
            DetourPurpose.AD_NETWORK_SHARING, DetourPurpose.AD_PERSONALIZATION,
        ) + sdkConfigStore.current().consentGateDefaults.keys + passed?.purposes?.keys.orEmpty()
        return DetourStatus(
            isStarted = started.isCompleted,
            isEnabled = isEnabled(),
            hasConsentThisLaunch = passed != null,
            purposes = purposeIds.associateWith { DetourStatus.Decision.of(decideNow(it)) },
        )
    }

    /**
     * `false` turns the SDK off for this install, also on later launches, until `setEnabled(true)`.
     * While off it sends nothing: no analytics, sessions, conversions, consent or matching by device
     * signals. Links the user taps are still routed, and a click ID from the Play install referrer
     * still matches the deferred link, without the device ID. The device ID does not change.
     */
    @JvmStatic
    @Synchronized
    fun setEnabled(enabled: Boolean) {
        enabledThisLaunch = KillSwitchChange(enabled, System.currentTimeMillis())
        if (isInitialized) applyKillSwitch()
    }

    private suspend fun isEnabled(): Boolean = enabledThisLaunch?.enabled ?: installStore.isEnabled()

    // Also resumes a state an earlier launch could not report.
    private fun applyKillSwitch() {
        scope.launch {
            enabledWrite.withLock {
                enabledThisLaunch?.let { installStore.setEnabled(it.enabled, it.atMs) }
            }
            // Drops what waited for consent when the SDK goes off.
            consentInputsChanged()
            killSwitchReporter.sync()
        }
    }

    private fun setUp(context: Context, config: DetourConfig): Boolean {
        if (config.apiKey.isBlank() || config.appId.isBlank()) {
            Log.e(TAG, "[Detour] apiKey or appId is missing — SDK will not process links. " +
                       "Set your credentials in DetourConfig.")
            return false
        }

        this.applicationContext = context.applicationContext
        this.config = config
        DetourEndpoints.reportBaseUrl(config)
        this.storage = config.storage ?: DefaultStorageProvider(context.applicationContext)
        this.consentStore = ConsentStore(storage)
        val requestContext = RequestContextProvider(
            context.applicationContext,
            sessionId = { DetourAnalytics.sessionManager.currentSessionId },
            consentSnapshot = { consentStore.passedThisLaunch?.toWire() },
        )
        this.apiClient = DetourApiClient(config, context.applicationContext, requestContext)
        this.signalCollector = SignalCollector(context.applicationContext)
        this.installReferrerHelper = InstallReferrerHelper(context.applicationContext)
        this.metaInstallReferrerReader = MetaInstallReferrerReader(context.applicationContext)
        this.installStore = InstallStore(
            context.applicationContext.noBackupFilesDir,
            storage,
            isAppUpdate = { InstallStore.isAppUpdate(context.applicationContext) },
        )
        this.userIdStore = UserIdStore(storage, config.userIdValidator)
        this.sdkApiClient = SdkApiClient(config)
        this.sdkConfigStore = SdkConfigStore(storage, config.appId)
        this.killSwitchReporter = KillSwitchReporter(installStore, scope) { enabled, atMs ->
            sdkApiClient.sendKillSwitch(enabled, atMs, installStore.getDeviceId())
        }
        this.consentDecider = ConsentDecider(consentStore, sdkConfigStore, ::isEnabled)
        HttpClient.attributionDecision = { decideNow(DetourPurpose.ATTRIBUTION) }
        ConsentWarnings.isDevelopment =
            (context.applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        refreshSdkConfig()

        DetourAnalytics.initialize(config, requestContext, installStore, userIdStore, consentDecider, sdkConfigStore)

        this.isInitialized = true
        applyKillSwitch()
        consentBeforeConfigure?.let { (consent, text) -> recordConsent(consent, text) }
        consentBeforeConfigure = null
        Log.d(TAG, "SDK configured")
        return true
    }

    /**
     * Set the app's own user identifier, sent with every later request. `null` removes it.
     * A stable account ID: the same for the user on every install, never a guest or anonymous ID.
     * Must not be an e-mail address or a phone number.
     *
     * The value must have 1 to 256 characters; any other value is dropped with a warning and
     * clears the stored one. Call after [configure].
     *
     * [DetourConfig.userIdValidator] checks every value passed here and the stored value at
     * [configure]; returning `false` drops the value and clears the stored one.
     */
    @JvmStatic
    fun setUserId(userId: String?) {
        if (!isInitialized) {
            Log.w(TAG, "SDK not initialized. Call Detour.configure() first.")
            return
        }

        if (userId == null) {
            userIdStore.set(null)
            return
        }

        // A rejected value also clears the previous one, so requests never go out
        // with the identifier of the user the app has just switched away from.
        val rejection = userIdRejection(userId, config.userIdValidator)
        if (rejection != null) {
            Log.w(TAG, "[Detour:IDENTITY] setUserId value $rejection; not stored or sent.")
            userIdStore.set(null)
            return
        }

        userIdStore.set(userId)
    }

    /**
     * Process intent and extract deep link.
     * Automatically detects link type (Universal, Scheme, or Deferred) and returns appropriate result.
     *
     * Priority order:
     * 1. Universal App Link (if intent contains ACTION_VIEW with http/https data)
     * 2. Scheme Link (if intent contains ACTION_VIEW with custom scheme and mode is ALL)
     * 3. Deferred Deep Link (API call, only on first launch)
     *
     * In [LinkProcessingMode.DEFERRED_ONLY] mode, steps 1-2 are skipped entirely.
     *
     * @param intent Activity intent to process
     * @param onLinkProcessed Called once, before this function returns or before the SDK starts
     *   waiting for [start] or for consent to attribution, so an app that holds its UI until the
     *   initial link is handled can show its consent prompt. A deferred link found after that is
     *   still the return value.
     * @return LinkResult with link data or status
     */
    @JvmStatic
    @JvmOverloads
    suspend fun processLink(intent: Intent, onLinkProcessed: (() -> Unit)? = null): LinkResult {
        val report = LinkProcessedReport(onLinkProcessed)
        if (!isInitialized) {
            report.processed()
            return LinkResult.Error(
                IllegalStateException("Detour SDK not initialized. Call Detour.configure() first.")
            )
        }

        return try {
            // Step 1: Try to extract link from intent (unless DEFERRED_ONLY)
            if (config.linkProcessingMode != LinkProcessingMode.DEFERRED_ONLY) {
                if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
                    val uri = intent.data!!
                    val uriString = uri.toString()

                    if (UrlHelpers.isWebUrl(uriString)) {
                        // Mark first entrance so subsequent launches don't try deferred matching
                        installStore.markFirstLaunchHandled()
                        return processUniversalLink(uri)
                    }

                    if (config.linkProcessingMode == LinkProcessingMode.ALL) {
                        return processSchemeLink(uri)
                    }
                }
            }

            // Step 2: Fallback to deferred link
            processDeferredLink(report)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "[Detour:RUNTIME_ERROR] Error processing link", e)
            LinkResult.Error(e)
        } finally {
            report.processed()
        }
    }

    /**
     * Get deferred link only (ignore Universal/Scheme Links).
     * Use this if you want to handle other link types separately.
     *
     * This should be called once per app session, typically in your main Activity.
     * The function is idempotent - multiple calls in the same session will return NotFirstLaunch.
     *
     * @param onLinkProcessed As in [processLink].
     * @return LinkResult.Success with DEFERRED type, or NotFirstLaunch/NoLink/Error
     */
    @JvmStatic
    @JvmOverloads
    suspend fun getDeferredLink(onLinkProcessed: (() -> Unit)? = null): LinkResult {
        val report = LinkProcessedReport(onLinkProcessed)
        if (!isInitialized) {
            report.processed()
            return LinkResult.Error(
                IllegalStateException("Detour SDK not initialized. Call Detour.configure() first.")
            )
        }

        return try {
            processDeferredLink(report)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "[Detour:RUNTIME_ERROR] Error processing deferred link", e)
            LinkResult.Error(e)
        } finally {
            report.processed()
        }
    }

    /**
     * Resolve a short link URL to its full link data.
     *
     * @param url The short link URL to resolve
     * @return ShortLinkResponse or null on 404/error
     */
    @JvmStatic
    suspend fun resolveShortLink(url: String): ShortLinkResponse? {
        if (!isInitialized) {
            Log.w(TAG, "SDK not initialized. Call Detour.configure() first.")
            return null
        }

        return apiClient.resolveShortLink(url)
    }

    /**
     * Process Universal App Link from URI.
     * If the URI has a single path segment, attempt short-link resolution first.
     */
    private suspend fun processUniversalLink(uri: Uri): LinkResult {
        Log.d(TAG, "Processing Universal Link")

        val link = uri.toString()
        // The click is Detour's measurement, not routing, so the kill switch stops it.
        val clickResult = if (isEnabled()) {
            apiClient.sendUniversalLinkClick(link, UrlHelpers.parseQueryParams(link))
        } else {
            DetourApiClient.UniversalLinkClickResult(allowed = true)
        }
        if (!clickResult.allowed) {
            Log.e(TAG, "[Detour:CLICK_LIMIT_ERROR] Universal/App link blocked: url=$link error=${clickResult.error} code=${clickResult.code} clicksInPeriod=${clickResult.clicksInPeriod} effectiveLimit=${clickResult.effectiveLimit}")
            return LinkResult.NoLink
        }

        // Short link resolution: single-segment path indicates a short link
        if (UrlHelpers.isSingleSegmentPath(uri)) {
            val shortLinkResult = apiClient.resolveShortLink(link)
            if (shortLinkResult?.link != null) {
                Log.d(TAG, "Short link resolved successfully")
                // Re-process the resolved link through parseRoute (matches RN SDK's recursive resolveLink)
                val route = UrlHelpers.parseRoute(shortLinkResult.link) ?: "/"
                return LinkResult.Success(
                    url = shortLinkResult.link,
                    route = route,
                    pathname = UrlHelpers.extractPathname(route),
                    type = LinkType.VERIFIED,
                    params = UrlHelpers.parseQueryParams(shortLinkResult.link)
                )
            }
        }

        val route = UrlHelpers.parseRoute(link) ?: "/"

        return LinkResult.Success(
            url = link,
            route = route,
            pathname = UrlHelpers.extractPathname(route),
            type = LinkType.VERIFIED,
            params = UrlHelpers.parseQueryParams(link)
        )
    }

    /**
     * Process custom-scheme deep link.
     * Extracts route as host + path + query, matching RN SDK's `getRouteFromDeepLink()`.
     */
    private fun processSchemeLink(uri: Uri): LinkResult {
        Log.d(TAG, "Processing Scheme Link")

        val link = uri.toString()
        val route = UrlHelpers.getRouteFromDeepLink(uri)

        return LinkResult.Success(
            url = link,
            route = route,
            pathname = UrlHelpers.extractPathname(route),
            type = LinkType.SCHEME,
            params = UrlHelpers.parseQueryParams(link)
        )
    }

    // One decision for the header and the Meta referrer: the referrer never goes out as core.
    private suspend fun attributionFields(): Pair<String, MetaInstallReferrer?> {
        val sdkPackage = requestSdkPackage(config)
        val facebookAppId = config.attribution?.facebookAppId?.takeIf { it.isNotBlank() }
        if (sdkPackage != "mmp" || facebookAppId == null) return sdkPackage to null
        return sdkPackage to metaInstallReferrerReader.read(facebookAppId)
    }

    /**
     * Process Deferred Deep Link via API.
     */
    // The match runs in the SDK's scope, not the caller's: the caller is often an Activity, and a
    // rotation while the match waits for consent would cancel it. The next caller then gets the
    // same result. A result is delivered once, so a later call in the session gets NotFirstLaunch.
    private suspend fun processDeferredLink(report: LinkProcessedReport): LinkResult {
        val match = synchronized(this) {
            deferredMatch ?: DeferredMatch(scope.async { matchDeferredLink() }).also { deferredMatch = it }
        }
        if (match.isDelivered.get()) {
            Log.d(TAG, "Session already handled")
            return LinkResult.NotFirstLaunch
        }
        // Reported first, so an app that holds its UI until then can still ask for consent.
        if (!match.result.isCompleted && (!started.isCompleted || isWaitingForAttributionConsent())) {
            report.processed()
        }
        val result = match.result.await()
        return if (match.isDelivered.compareAndSet(false, true)) result else LinkResult.NotFirstLaunch
    }

    private suspend fun matchDeferredLink(): LinkResult {
        if (!installStore.isFirstLaunch()) {
            Log.d(TAG, "Not first launch")
            return LinkResult.NotFirstLaunch
        }

        // The first-launch flag is stored only when matching runs, so an app closed before start()
        // or before the consent decision still matches on its next launch.
        started.await()
        if (isWaitingForAttributionConsent()) {
            ConsentWarnings.warnOnce(
                "firstMatchWaits",
                "The first deferred link match waits for consent to attribution. Pass it with setConsent.",
            )
        }
        awaitAttributionDecision()
        installStore.markFirstLaunchHandled()
        Log.d(TAG, "First launch detected, processing deferred link")

        // The referrer goes with both match requests; a UUID click ID in it enables direct matching.
        val referrer = installReferrerHelper.getReferrerDetails()
        val clickId = referrer?.clickId

        val deviceId = installStore.getDeviceId()
        val userId = userIdStore.current()

        val isDirectMatch = clickId != null && InstallReferrerHelper.isDirectMatchClickId(clickId)
        val link: String? = if (!isEnabled()) {
            // Routing by the click ID still works with the SDK off, with the v1 body only.
            if (clickId == null || !isDirectMatch) return LinkResult.NoLink
            Log.d(TAG, "SDK disabled; direct matching with click_id and no device data")
            apiClient.matchLink(DirectMatchRequest(clickId), withContext = false)
        } else if (clickId != null && isDirectMatch) {
            Log.d(TAG, "Using direct matching with click_id")
            val (sdkPackage, metaReferrer) = attributionFields()
            apiClient.matchLink(DirectMatchRequest(clickId, deviceId, userId, referrer, metaReferrer), sdkPackage = sdkPackage)
        } else {
            if (clickId == null) {
                Log.d(TAG, "The click_id not found, using signal-based matching")
            } else {
                Log.d(TAG, "The click_id is not a UUID, using signal-based matching")
            }
            val (sdkPackage, metaReferrer) = attributionFields()
            apiClient.matchLink(
                signalCollector.collectSignals().copy(
                    deviceId = deviceId,
                    userId = userId,
                    installReferrer = referrer,
                    metaInstallReferrer = metaReferrer,
                ),
                sdkPackage = sdkPackage,
            )
        }

        if (link == null) {
            Log.d(TAG, "No deferred link matched")
            return LinkResult.NoLink
        }

        // Parse route from link (strips app hash, extracts path)
        val route = UrlHelpers.parseRoute(link) ?: "/"

        Log.d(TAG, "Deferred link matched successfully")
        return LinkResult.Success(
            url = link,
            route = route,
            pathname = UrlHelpers.extractPathname(route),
            type = LinkType.DEFERRED,
            params = UrlHelpers.parseQueryParams(link)
        )
    }
}

/** Calls `onLinkProcessed` once, whichever path gets there first. */
internal class LinkProcessedReport(private val onProcessed: (() -> Unit)?) {
    private val isReported = AtomicBoolean(false)

    fun processed() {
        if (isReported.compareAndSet(false, true)) onProcessed?.invoke()
    }
}
