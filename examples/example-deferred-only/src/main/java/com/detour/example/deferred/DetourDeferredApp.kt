package com.detour.example.deferred

import android.app.Application

/**
 * Application class for Detour deferred-only example.
 *
 * SDK initialization is handled in MainActivity with DEFERRED_ONLY mode.
 */
class DetourDeferredApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Detour SDK is initialized in MainActivity
    }
}
