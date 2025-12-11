package com.detour.example

import android.app.Application

/**
 * Application class for Detour SDK example.
 *
 * Note: SDK initialization is now handled in MainActivity using DetourDelegate.
 * This provides better flexibility and demonstrates the recommended usage pattern.
 */
class DetourExampleApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Detour SDK is initialized in MainActivity with DetourDelegate
    }
}
