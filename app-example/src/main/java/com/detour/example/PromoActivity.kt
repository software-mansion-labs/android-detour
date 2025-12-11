package com.detour.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.detour.example.databinding.ActivityPromoBinding

class PromoActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityPromoBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPromoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        binding.btnBack.setOnClickListener {
            finish()
        }
    }
}
