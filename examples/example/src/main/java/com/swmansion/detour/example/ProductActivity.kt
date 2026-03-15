package com.swmansion.detour.example

import android.os.Build
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

        @Suppress("UNCHECKED_CAST", "DEPRECATION")
        val params: Map<String, String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("params", HashMap::class.java) as? HashMap<String, String>
        } else {
            intent.getSerializableExtra("params") as? HashMap<String, String>
        } ?: emptyMap()

        binding.productText.text = buildString {
            append("Product ID: $productId")
            if (params.isNotEmpty()) {
                append("\n\nQuery params:\n")
                params.forEach { (key, value) -> append("$key: $value\n") }
            }
        }.trimEnd()

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
