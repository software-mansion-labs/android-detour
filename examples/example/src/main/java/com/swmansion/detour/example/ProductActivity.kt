package com.swmansion.detour.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.swmansion.detour.example.databinding.ActivityProductBinding
import com.swmansion.detour.analytics.DetourAnalytics
import com.swmansion.detour.analytics.DetourEventNames

class ProductActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val productId = intent.getStringExtra("productId") ?: "unknown"
        binding.productText.text = "Product ID: $productId"

        // --- Analytics: log a ViewItem event ---
        DetourAnalytics.logEvent(
            DetourEventNames.ViewItem,
            mapOf("item_id" to productId)
        )

        binding.btnBack.setOnClickListener {
            finish()
        }
    }
}
