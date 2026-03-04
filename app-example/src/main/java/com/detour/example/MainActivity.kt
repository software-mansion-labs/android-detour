package com.detour.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.detour.example.BuildConfig
import com.detour.example.databinding.ActivityMainBinding
import com.detour.example.storage.EncryptedStorageProvider
import com.detour.sdk.Detour
import com.detour.sdk.DetourConfig
import com.detour.sdk.DetourDelegate
import com.detour.sdk.models.LinkProcessingMode
import com.detour.sdk.models.LinkResult
import com.detour.sdk.models.LinkType

/**
 * Main example: DetourDelegate + ALL mode.
 *
 * Handles Universal Links, custom scheme links, AND deferred links automatically.
 * This is the recommended setup for most apps.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val detourDelegate by lazy { DetourDelegate(
        lifecycleOwner = this,
        config = DetourConfig(
            apiKey = BuildConfig.DETOUR_API_KEY,
            appId = BuildConfig.DETOUR_APP_ID,
            shouldUseClipboard = true,
            // LinkProcessingMode controls which link sources the SDK handles:
            //   ALL           – Universal Links + custom schemes + deferred (default)
            //   WEB_ONLY      – Universal Links + deferred, ignores custom schemes
            //   DEFERRED_ONLY – only deferred links; use when your nav framework
            //                   (e.g. Jetpack Navigation) already handles intents
            linkProcessingMode = LinkProcessingMode.ALL,
            storage = EncryptedStorageProvider(this)
        ),
        onLinkResult = { result -> handleLinkResult(result) }
    ) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Detour.initialize(this, detourDelegate.config)
        setupButtons()

        // Process links (Universal + Scheme + Deferred)
        detourDelegate.onCreate(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        detourDelegate.onNewIntent(intent)
    }

    private fun handleLinkResult(result: LinkResult) {
        when (result) {
            is LinkResult.Success -> {
                binding.statusText.text = buildString {
                    append("Link processed!\n\n")
                    append("Type: ${result.type}\n")
                    append("Link: ${result.link}\n")
                    append("Route: ${result.route}\n")
                    append("Pathname: ${result.pathname}\n")
                    if (result.params.isNotEmpty()) {
                        append("Params: ${result.params}\n")
                    }
                }

                Log.d(TAG, "Link: ${result.link}")
                Log.d(TAG, "Route: ${result.route}")
                Log.d(TAG, "Pathname: ${result.pathname}")
                Log.d(TAG, "Params: ${result.params}")
                Log.d(TAG, "Type: ${result.type}")

                when (result.type) {
                    LinkType.DEFERRED -> Log.d(TAG, "Deferred link click detected")
                    LinkType.UNIVERSAL -> Log.d(TAG, "Universal link click detected")
                    LinkType.SCHEME -> Log.d(TAG, "Scheme link click detected")
                }

                navigateToRoute(result.route)
            }

            is LinkResult.NotFirstLaunch -> {
                binding.statusText.text =
                    "Not first launch\n\nDeferred links only work on first app install."
                Log.d(TAG, "Not first launch")
            }

            is LinkResult.NoLink -> {
                binding.statusText.text = "No deep link detected"
                Log.d(TAG, "Normal app launch")
            }

            is LinkResult.Error -> {
                binding.statusText.text = "Error: ${result.exception.message}"
                Log.e(TAG, "Error processing link", result.exception)
                Toast.makeText(this, "Error processing link", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun navigateToRoute(route: String) {
        when {
            route.startsWith("/products/") -> {
                val productId = route.removePrefix("/products/").split("?").first().trim('/')
                if (productId.isNotEmpty()) {
                    startActivity(Intent(this, ProductActivity::class.java).apply {
                        putExtra("productId", productId)
                    })
                }
            }

            route.startsWith("/promo") -> {
                startActivity(Intent(this, PromoActivity::class.java))
            }

            else -> {
                Toast.makeText(this, "Unknown route: $route", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupButtons() {
        binding.btnProduct.setOnClickListener {
            startActivity(Intent(this, ProductActivity::class.java))
        }

        binding.btnPromo.setOnClickListener {
            startActivity(Intent(this, PromoActivity::class.java))
        }

        binding.btnDeferredOnly.setOnClickListener {
            startActivity(Intent(this, DeferredOnlyExampleActivity::class.java))
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
