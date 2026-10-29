package com.swmansion.detour

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.SavedStateRegistryOwner
import com.swmansion.detour.models.LinkResult
import kotlinx.coroutines.launch

/**
 * Convenience wrapper for handling Universal App Links, Scheme Links,
 * and Deferred Deep Links. Delegates link processing to the [Detour] SDK
 * and invokes a callback with the result.
 *
 * Compatible with any [LifecycleOwner] — `ComponentActivity`, `AppCompatActivity`,
 * or `Fragment`.
 *
 * A link opens once: when the owner is recreated (rotation, process death), the link from the
 * restored intent is not delivered again. This needs an owner with saved state, such as an
 * activity or a fragment.
 *
 * Usage:
 * ```
 * class MainActivity : AppCompatActivity() {
 *
 *     private val detourDelegate = DetourDelegate(
 *         lifecycleOwner = this,
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
 * }
 * ```
 *
 * @property lifecycleOwner The lifecycle owner whose scope is used for coroutines
 * @property config Detour SDK configuration
 * @property onLinkResult Callback invoked with the link processing result
 */
class DetourDelegate(
    private val lifecycleOwner: LifecycleOwner,
    val config: DetourConfig,
    private val onLinkResult: (LinkResult) -> Unit
) {

    /**
     * Call from Activity.onCreate() to handle Universal, Scheme, and Deferred links.
     *
     * @param intent The activity intent
     */
    fun onCreate(intent: Intent) {
        markOpenedIfRecreated(intent)

        lifecycleOwner.lifecycleScope.launch {
            val result = Detour.processLink(intent)
            onLinkResult(result)
        }
    }

    /**
     * Call from Activity.onNewIntent() to handle links when app is running.
     *
     * @param intent The new intent
     */
    fun onNewIntent(intent: Intent) {
        lifecycleOwner.lifecycleScope.launch {
            val result = Detour.processLink(intent)
            onLinkResult(result)
        }
    }

    /**
     * Marks the link from [intent] as opened when the owner was restored from saved state written
     * by an earlier DetourDelegate. Registers that state, so the next instance can tell too.
     */
    private fun markOpenedIfRecreated(intent: Intent) {
        val registry = (lifecycleOwner as? SavedStateRegistryOwner)?.savedStateRegistry ?: return
        // Saved state is readable only after the owner's super.onCreate.
        if (!registry.isRestored) return

        if (registry.consumeRestoredStateForKey(SAVED_STATE_KEY) != null) {
            LinkOpens.markOpenedBeforeProcessDeath(intent)
        }
        if (registry.getSavedStateProvider(SAVED_STATE_KEY) == null) {
            registry.registerSavedStateProvider(SAVED_STATE_KEY) { Bundle() }
        }
    }

    private companion object {
        const val SAVED_STATE_KEY = "com.swmansion.detour.DetourDelegate"
    }
}
