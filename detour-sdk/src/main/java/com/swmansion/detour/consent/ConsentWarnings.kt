package com.swmansion.detour.consent

import android.util.Log
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Warnings about a consent integration that holds data back without saying so. Debuggable app
 * builds only, each once per process.
 */
internal object ConsentWarnings {
    @Volatile
    var isDevelopment = false

    var write: (String) -> Unit = { Log.w("Detour", it) }

    private val given = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    fun warnOnce(key: String, message: String) {
        if (!isDevelopment || !given.add(key)) return
        write("[Detour:CONSENT] $message")
    }

    fun resetForTests() = given.clear()

    /**
     * For the places that hold a request back while the gate waits. Warns and returns true when a
     * grant stored on an earlier launch is why it waits.
     */
    fun warnIfStoredGrantWaits(purpose: String, storedConsent: DetourConsent?): Boolean {
        if (storedConsent?.purposes?.get(purpose) != true) return false
        warnOnce(
            "storedGrantNotPassed",
            "A consent to $purpose from an earlier launch does not count until setConsent passes it again on this launch.",
        )
        return true
    }
}
