package com.swmansion.detour.example.deferred

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.swmansion.detour.example.deferred.databinding.ActivityMainBinding
import com.swmansion.detour.Detour
import com.swmansion.detour.DetourConfig
import com.swmansion.detour.models.LinkProcessingMode
import com.swmansion.detour.models.LinkResult
import com.swmansion.detour.example.deferred.BuildConfig
import kotlinx.coroutines.launch

/**
 * Deferred-only example: calls [Detour.getDeferredLink] directly without [com.swmansion.detour.DetourDelegate].
 *
 * This is the pattern for apps where a navigation framework (Jetpack Navigation, etc.)
 * already handles Universal Links and custom scheme links, and Detour is only needed
 * for deferred deep link resolution on first install.
 *
 * Key differences from the full example:
 * - [LinkProcessingMode.DEFERRED_ONLY] — SDK skips intent processing entirely.
 * - No [com.swmansion.detour.DetourDelegate] — no [onNewIntent] wiring needed.
 * - [Detour.getDeferredLink] called directly instead of [Detour.processLink].
 * - No Universal Link or custom scheme intent-filters in AndroidManifest.xml.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Detour.initialize(
            this,
            DetourConfig(
                apiKey = BuildConfig.DETOUR_API_KEY,
                appId = BuildConfig.DETOUR_APP_ID,
                linkProcessingMode = LinkProcessingMode.DEFERRED_ONLY
            )
        )

        binding.statusText.text = "Checking for deferred link..."

        lifecycleScope.launch {
            val result = Detour.getDeferredLink()
            handleResult(result)
        }
    }

    private fun handleResult(result: LinkResult) {
        when (result) {
            is LinkResult.Success -> {
                binding.statusText.text = buildString {
                    append("Deferred link found!\n\n")
                    append("Link: ${result.url}\n")
                    append("Route: ${result.route}\n")
                    append("Pathname: ${result.pathname}\n")
                    if (result.params.isNotEmpty()) {
                        append("Params: ${result.params}\n")
                    }
                }
                Log.d(TAG, "Deferred link: ${result.url}")
            }

            is LinkResult.NotFirstLaunch -> {
                binding.statusText.text =
                    "Not first launch — deferred link already consumed on a previous session."
                Log.d(TAG, "Not first launch")
            }

            is LinkResult.NoLink -> {
                binding.statusText.text = "No deferred link matched for this device."
                Log.d(TAG, "No deferred link")
            }

            is LinkResult.Error -> {
                binding.statusText.text = "Error: ${result.exception.message}"
                Log.e(TAG, "Deferred link error", result.exception)
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
