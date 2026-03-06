package com.swmansion.detour.example.deferred

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.swmansion.detour.example.deferred.databinding.ActivityDeepLinkDestinationBinding

/**
 * Simulated deep link destination.
 *
 * In a real app this would be replaced by your actual screen
 * (e.g. ProductScreen, PromoScreen, etc.) driven by the route and params.
 */
class DeepLinkDestinationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeepLinkDestinationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeepLinkDestinationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val route = intent.getStringExtra(EXTRA_ROUTE) ?: "/"
        val params = intent.getStringExtra(EXTRA_PARAMS)

        binding.routeText.text = "Route: $route"
        if (!params.isNullOrEmpty()) {
            binding.paramsText.text = "Params: $params"
        }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        const val EXTRA_PARAMS = "params"
    }
}
