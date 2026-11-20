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

    /** The stored grant warning, for every place that decides a purpose. */
    fun warnIfStoredGrantWaits(purpose: String, decision: GateDecision, storedConsent: DetourConsent?) {
        if (decision != GateDecision.WAIT || storedConsent?.purposes?.get(purpose) != true) return
        warnOnce(
            "storedGrantNotPassed",
            "A consent to $purpose from an earlier launch does not count until setConsent passes it again on this launch.",
        )
    }
}
