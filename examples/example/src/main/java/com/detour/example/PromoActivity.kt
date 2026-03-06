package com.detour.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.detour.example.databinding.ActivityPromoBinding
import com.detour.sdk.analytics.DetourAnalytics
import com.detour.sdk.analytics.DetourEventNames

class PromoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPromoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPromoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- Analytics: log a Purchase event (checkout flow example) ---
        DetourAnalytics.logEvent(DetourEventNames.Purchase, mapOf("promo" to "summer_sale"))

        // --- Analytics: log a retention event ---
        DetourAnalytics.logRetention("subscription_renewed")

        binding.btnBack.setOnClickListener {
            finish()
        }
    }
}
