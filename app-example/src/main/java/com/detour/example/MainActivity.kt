package com.detour.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.detour.example.databinding.ActivityMainBinding
import com.detour.sdk.Detour
import com.detour.sdk.DetourConfig
import com.detour.sdk.DetourDelegate
import com.detour.sdk.models.LinkResult
import com.detour.sdk.models.LinkType

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // DetourDelegate handles all link logic automatically
    private val detourDelegate = DetourDelegate(
        activity = this,
        config = DetourConfig(
            apiKey = "<YOUR_API_KEY>",
            appId = "<YOUR_APP_ID>",
            shouldUseClipboard = true
        ),
        onLinkResult = { result -> handleLinkResult(result) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize SDK
        Detour.initialize(this, detourDelegate.config)

        // Setup manual navigation buttons
        setupButtons()

        // Process links (Universal + Deferred)
        detourDelegate.onCreate(intent)
    }

    // Handles links if the activity was already running in the background
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        detourDelegate.onNewIntent(intent)
    }

    private fun handleLinkResult(result: LinkResult) {
        when (result) {
            is LinkResult.Success -> {
                // Update UI
                binding.statusText.text = buildString {
                    append("✅ Link processed!\n\n")
                    append("Type: ${result.type}\n")
                    append("Link: ${result.link}\n")
                    append("Route: ${result.route ?: "none"}")
                }

                Log.d(TAG, "Link: ${result.link}")
                Log.d(TAG, "Route: ${result.route}")
                Log.d(TAG, "Type: ${result.type}")

                // Different behavior based on type
                when (result.type) {
                    LinkType.DEFERRED -> {
                        Log.d(TAG, "Deferred link click detected")
                    }

                    LinkType.UNIVERSAL -> {
                        Log.d(TAG, "Universal link click detected")
                    }
                }

                // Navigate to route
                result.route?.let { route ->
                    navigateToRoute(route)
                }
            }

            is LinkResult.NotFirstLaunch -> {
                binding.statusText.text =
                    "ℹ️ Not first launch\n\nDeferred links only work on first app install."
                Log.d(TAG, "Not first launch")
            }

            is LinkResult.NoLink -> {
                binding.statusText.text = "No deep link detected"
                Log.d(TAG, "Normal app launch")
            }

            is LinkResult.Error -> {
                binding.statusText.text = "❌ Error: ${result.exception.message}"
                Log.e(TAG, "Error processing link", result.exception)
                Toast.makeText(this, "Error processing link", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun navigateToRoute(route: String) {
        when {
            route.startsWith("/products/") -> {
                val productId = route.removePrefix("/products/").trim('/')
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
            startActivity(Intent(this, ProductActivity::class.java).apply {
                putExtra("productId", "123")
            })
        }

        binding.btnPromo.setOnClickListener {
            startActivity(Intent(this, PromoActivity::class.java))
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
