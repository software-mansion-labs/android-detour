package com.detour.sdk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.detour.sdk.api.DetourApiClient
import com.detour.sdk.fingerprint.FingerprintCollector
import com.detour.sdk.models.DeterministicFingerprint
import com.detour.sdk.models.LinkResult
import com.detour.sdk.models.LinkType
import com.detour.sdk.referrer.InstallReferrerHelper
import com.detour.sdk.storage.FirstLaunchDetector
import com.detour.sdk.utils.UrlHelpers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Main entry point for Detour SDK.
 *
 * Usage:
 * ```
 * // Initialize in Application.onCreate() or Activity.onCreate()
 * val config = DetourConfig(
 *     appId = "your-app-id",
 *     apiKey = "your-api-key"
 * )
 * Detour.initialize(context, config)
 *
 * // Process any link (Universal or Deferred) in onCreate()
 * lifecycleScope.launch {
 *     when (val result = Detour.processLink(intent)) {
 *         is LinkResult.Success -> {
 *             // Navigate to result.route
 *             // Check result.type (DEFERRED or UNIVERSAL)
 *         }
 *         is LinkResult.NoLink -> {
 *             // Normal app launch
 *         }
 *         is LinkResult.NotFirstLaunch -> {
 *             // Already processed deferred link
 *         }
 *         is LinkResult.Error -> {
 *             // Handle error
 *         }
 *     }
 * }
 * ```
 */
object Detour {

    private const val TAG = "Detour"

    @Volatile
    private var isInitialized = false

    @Volatile
    private var sessionHandled = false

    private lateinit var applicationContext: Context
    private lateinit var config: DetourConfig

    /**
     * Initialize Detour SDK.
     * Call this once in Application.onCreate() or before using processLink() or getDeferredLink().
     *
     * @param context Application or Activity context
     * @param config Detour configuration
     */
    @Synchronized
    fun initialize(context: Context, config: DetourConfig) {
        if (isInitialized) {
            Log.w(TAG, "SDK already initialized")
            return
        }

        this.applicationContext = context.applicationContext
        this.config = config
        this.isInitialized = true

        Log.d(TAG, "SDK initialized")
    }

    /**
     * Process intent and extract deep link.
     * Automatically detects link type (Universal vs Deferred) and returns appropriate result.
     *
     * Priority order:
     * 1. Universal App Link (if intent contains ACTION_VIEW with data)
     * 2. Deferred Deep Link (API call, only on first launch)
     *
     * @param intent Activity intent to process
     * @return LinkResult with link data or status
     * @throws IllegalStateException if SDK is not initialized
     */
    suspend fun processLink(intent: Intent): LinkResult = withContext(Dispatchers.Main) {
        check(isInitialized) {
            "Detour SDK not initialized. Call Detour.initialize() first."
        }

        try {
            // Priority 1: Check for Universal App Link
            if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
                return@withContext processUniversalLink(intent.data!!)
            }

            // Priority 2: Check for Deferred Link
            return@withContext processDeferredLink()

        } catch (e: Exception) {
            Log.e(TAG, "[Detour:RUNTIME_ERROR] Error processing link", e)
            LinkResult.Error(e)
        }
    }

    /**
     * Get deferred link only (ignore Universal Links).
     * Use this if you want to handle Universal Links separately.
     *
     * This should be called once per app session, typically in your main Activity.
     * The function is idempotent - multiple calls in the same session will return NotFirstLaunch.
     *
     * @return LinkResult.Success with DEFERRED type, or NotFirstLaunch/NoLink/Error
     * @throws IllegalStateException if SDK is not initialized
     */
    suspend fun getDeferredLink(): LinkResult = withContext(Dispatchers.Main) {
        check(isInitialized) {
            "Detour SDK not initialized. Call Detour.initialize() first."
        }

        try {
            processDeferredLink()
        } catch (e: Exception) {
            Log.e(TAG, "[Detour:RUNTIME_ERROR] Error processing deferred link", e)
            LinkResult.Error(e)
        }
    }

    /**
     * Process Universal App Link from URI.
     */
    private fun processUniversalLink(uri: Uri): LinkResult {
        Log.d(TAG, "Processing Universal Link: $uri")

        val link = uri.toString()
        val route = UrlHelpers.parseRoute(link)

        return LinkResult.Success(
            link = link,
            route = route,
            type = LinkType.UNIVERSAL
        )
    }

    /**
     * Process Deferred Deep Link via API.
     */
    private suspend fun processDeferredLink(): LinkResult {
        // Prevent duplicate API calls in same session
        if (sessionHandled) {
            Log.d(TAG, "Session already handled")
            return LinkResult.NotFirstLaunch
        }

        sessionHandled = true

        // Check if first launch
        val firstLaunchDetector = FirstLaunchDetector(applicationContext)
        if (!firstLaunchDetector.isFirstLaunch()) {
            Log.d(TAG, "Not first launch")
            return LinkResult.NotFirstLaunch
        }

        // Mark as launched
        firstLaunchDetector.markAsLaunched()
        Log.d(TAG, "First launch detected, processing deferred link")

        // Try to get click ID from install referrer (deterministic matching)
        val installReferrerHelper = InstallReferrerHelper(applicationContext)
        val clickId = installReferrerHelper.getClickId()

        val apiClient = DetourApiClient()
        val link: String?

        if (clickId != null) {
            Log.d(TAG, "Using deterministic matching with click_id")
            val fingerprint = DeterministicFingerprint(clickId)
            link = apiClient.matchLink(config, fingerprint)
        } else {
            Log.d(TAG, "The click_id not found, using probabilistic matching")
            val fingerprintCollector = FingerprintCollector(applicationContext)
            val fingerprint = fingerprintCollector.collectFingerprint(config.shouldUseClipboard)
            link = apiClient.matchLink(config, fingerprint)
        }

        if (link == null) {
            Log.d(TAG, "No deferred link matched")
            return LinkResult.NoLink
        }

        // Parse route from link
        val route = UrlHelpers.parseRoute(link)

        Log.d(TAG, "Deferred link matched: $link, route: $route")
        return LinkResult.Success(
            link = link,
            route = route,
            type = LinkType.DEFERRED
        )
    }
}
