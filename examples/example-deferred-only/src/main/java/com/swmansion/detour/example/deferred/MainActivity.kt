package com.swmansion.detour.example.deferred

import android.content.Intent
import android.os.Bundle
import androidx.core.app.TaskStackBuilder
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
 * already handles Verified App Links and custom scheme links, and Detour is only needed
 * for deferred deep link resolution on first install.
 *
 * Key differences from the full example:
 * - [LinkProcessingMode.DEFERRED_ONLY] — SDK skips intent processing entirely.
 * - No [com.swmansion.detour.DetourDelegate] — no [onNewIntent] wiring needed.
 * - [Detour.getDeferredLink] called directly instead of [Detour.processLink].
 * - No Verified App Link or custom scheme intent-filters in AndroidManifest.xml.
 *
 * Splash screen pattern:
 * The splash screen stays visible while [Detour.getDeferredLink] resolves.
 * On success the user is taken directly to [DeepLinkDestinationActivity] — MainActivity
 * is finished immediately so the user never sees it. On no link the splash dismisses
 * normally and the home screen is shown.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Splash screen holds while this is false
    private var isLinkResolved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate()
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Keep splash on screen until getDeferredLink() returns
        splashScreen.setKeepOnScreenCondition { !isLinkResolved }

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

        lifecycleScope.launch {
            val result = Detour.getDeferredLink()
            handleResult(result)
        }
    }

    private fun handleResult(result: LinkResult) {
        when (result) {
            is LinkResult.Success -> {
                // Build a synthetic back stack: DeepLinkDestinationActivity on top of MainActivity.
                // This way pressing back from the destination lands on the home screen
                // rather than exiting the app — standard Android deep link pattern.
                Log.d(TAG, "Deferred link: ${result.url}")
                val intent = Intent(this, DeepLinkDestinationActivity::class.java).apply {
                    putExtra(DeepLinkDestinationActivity.EXTRA_ROUTE, result.route)
                    putExtra(DeepLinkDestinationActivity.EXTRA_PARAMS, result.params.toString())
                }
                TaskStackBuilder.create(this)
                    .addNextIntentWithParentStack(intent)
                    .startActivities()
                finish()
            }

            is LinkResult.NotFirstLaunch -> {
                // Release splash — show home screen normally
                isLinkResolved = true
                binding.statusText.text =
                    "Not first launch — deferred link already consumed on a previous session."
                Log.d(TAG, "Not first launch")
            }

            is LinkResult.NoLink -> {
                // Release splash — show home screen normally
                isLinkResolved = true
                binding.statusText.text = "No deferred link matched for this device."
                Log.d(TAG, "No deferred link")
            }

            is LinkResult.Error -> {
                // Release splash — show home screen with error
                isLinkResolved = true
                binding.statusText.text = "Error: ${result.exception.message}"
                Log.e(TAG, "Deferred link error", result.exception)
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
