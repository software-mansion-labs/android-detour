package com.detour.example

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.detour.example.databinding.ActivityDeferredOnlyBinding
import com.detour.sdk.Detour
import com.detour.sdk.models.LinkResult
import kotlinx.coroutines.launch

/**
 * Example: Manual deferred-link handling without DetourDelegate.
 *
 * Demonstrates calling [Detour.getDeferredLink] directly — the approach you'd
 * use when your navigation framework (Jetpack Navigation, etc.) already handles
 * Universal/Scheme intents and you only need Detour for deferred deep links.
 *
 * In a real app you would configure the SDK with [LinkProcessingMode.DEFERRED_ONLY]:
 * ```
 * val config = DetourConfig(
 *     apiKey = "...",
 *     appId = "...",
 *     linkProcessingMode = LinkProcessingMode.DEFERRED_ONLY
 * )
 * Detour.initialize(this, config)
 * ```
 *
 * Then call `Detour.getDeferredLink()` manually instead of `Detour.processLink(intent)`.
 */
class DeferredOnlyExampleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeferredOnlyBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeferredOnlyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.statusText.text = "Checking for deferred link..."

        // Manually fetch deferred link — no DetourDelegate, no intent processing
        lifecycleScope.launch {
            val result = Detour.getDeferredLink()
            handleResult(result)
        }

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun handleResult(result: LinkResult) {
        when (result) {
            is LinkResult.Success -> {
                binding.statusText.text = buildString {
                    append("Deferred link found!\n\n")
                    append("Link: ${result.link}\n")
                    append("Route: ${result.route}\n")
                    append("Pathname: ${result.pathname}\n")
                    if (result.params.isNotEmpty()) {
                        append("Params: ${result.params}\n")
                    }
                }
                Log.d(TAG, "Deferred link: ${result.link}")
            }

            is LinkResult.NotFirstLaunch -> {
                binding.statusText.text =
                    "Not first launch — deferred link already consumed on a previous session."
            }

            is LinkResult.NoLink -> {
                binding.statusText.text =
                    "No deferred link matched for this device."
            }

            is LinkResult.Error -> {
                binding.statusText.text = "Error: ${result.exception.message}"
                Log.e(TAG, "Deferred link error", result.exception)
            }
        }
    }

    companion object {
        private const val TAG = "DeferredOnlyExample"
    }
}
