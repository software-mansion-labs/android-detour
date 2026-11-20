package com.swmansion.detour

import com.swmansion.detour.consent.GateDecision

// PROVISIONAL: the names until the React Native and iOS SDKs ship the same status.
/** What the SDK does now, for debugging a consent integration. From [Detour.getStatus]. */
data class DetourStatus(
    /** [Detour.start] was called, or `configure` called it. */
    val isStarted: Boolean,
    /** `false` after `setEnabled(false)`. */
    val isEnabled: Boolean,
    /** [Detour.setConsent] was called on this launch. */
    val hasConsentThisLaunch: Boolean,
    /** Purpose ID → what happens now to a request for it. */
    val purposes: Map<String, Decision>,
) {
    enum class Decision {
        /** Sent. */
        SEND,
        /** Kept until consent decides it. */
        WAIT,
        /** Dropped. */
        DROP;

        internal companion object {
            fun of(decision: GateDecision) = when (decision) {
                GateDecision.SEND -> SEND
                GateDecision.WAIT -> WAIT
                GateDecision.DROP -> DROP
            }
        }
    }
}
