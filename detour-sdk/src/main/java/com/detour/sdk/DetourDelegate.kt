package com.detour.sdk

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.detour.sdk.models.LinkResult
import kotlinx.coroutines.launch

/**
 * This class provides a approach to handling both Universal App Links
 * and Deferred Deep Links. It automatically manages the activity
 * lifecycle and delegates link processing to the Detour SDK.
 *
 * Usage:
 * ```
 * class MainActivity : AppCompatActivity() {
 *
 *     private val detourDelegate = DetourDelegate(
 *         activity = this,
 *         config = DetourConfig(
 *             appId = "your-app-id",
 *             apiKey = "your-api-key"
 *         ),
 *         onLinkResult = { result ->
 *             handleLinkResult(result)
 *         }
 *     )
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         setContentView(R.layout.activity_main)
 *
 *         Detour.initialize(this, detourDelegate.config)
 *         detourDelegate.onCreate(intent)
 *     }
 *
 *     override fun onNewIntent(intent: Intent) {
 *         super.onNewIntent(intent)
 *         setIntent(intent)
 *         detourDelegate.onNewIntent(intent)
 *     }
 *
 *     private fun handleLinkResult(result: LinkResult) {
 *         when (result) {
 *             is LinkResult.Success -> {
 *                 // Navigate to result.route
 *                 // Check result.type (DEFERRED or UNIVERSAL)
 *             }
 *             is LinkResult.NoLink -> {
 *                 // Normal app launch
 *             }
 *             is LinkResult.NotFirstLaunch -> {
 *                 // Already processed deferred link
 *             }
 *             is LinkResult.Error -> {
 *                 // Handle error
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @property activity The AppCompatActivity where links should be processed
 * @property config Detour SDK configuration
 * @property onLinkResult Callback invoked with the link processing result
 */
class DetourDelegate(
    private val activity: AppCompatActivity,
    val config: DetourConfig,
    private val onLinkResult: (LinkResult) -> Unit
) {

    /**
     * Call from Activity.onCreate() to handle both Universal and Deferred links.
     * Automatically checks for Universal Link first, falls back to Deferred Link.
     *
     * @param intent The activity intent
     */
    fun onCreate(intent: Intent) {
        activity.lifecycleScope.launch {
            val result = Detour.processLink(intent)
            onLinkResult(result)
        }
    }

    /**
     * Call from Activity.onNewIntent() to handle Universal Links when app is running.
     *
     * @param intent The new intent
     */
    fun onNewIntent(intent: Intent) {
        activity.lifecycleScope.launch {
            val result = Detour.processLink(intent)
            onLinkResult(result)
        }
    }
}
