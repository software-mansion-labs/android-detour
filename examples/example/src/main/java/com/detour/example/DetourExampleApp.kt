package com.detour.example

import android.app.Application

/**
 * Application class for Detour SDK example.
 *
 * Note: SDK initialization is handled in MainActivity using DetourDelegate.
 */
class DetourExampleApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Detour SDK is initialized in MainActivity with DetourDelegate
    }
}
