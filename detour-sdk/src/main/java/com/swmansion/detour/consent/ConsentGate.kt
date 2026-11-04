package com.swmansion.detour.consent

internal enum class GateDecision {
    SEND,

    /** Kept until the app passes a consent that decides it. */
    WAIT,
    DROP,
}

/** Decides whether a request for a purpose may leave the device: the kill switch first, then consent. */
internal object ConsentGate {
    // PROVISIONAL: the stricter cold start rule until the legal answer. A consent stored on an
    // earlier launch can only block: the user may have withdrawn it in the consent platform since,
    // so a grant counts only once the app passes it again on this launch.
    fun decide(
        purpose: String,
        isEnabled: Boolean,
        consentPassedThisLaunch: DetourConsent?,
        storedConsent: DetourConsent?,
        gateDefault: ConsentGateDefault,
    ): GateDecision {
        if (!isEnabled) return GateDecision.DROP
        if (consentPassedThisLaunch != null) {
            return if (consentPassedThisLaunch.purposes[purpose] == true) GateDecision.SEND else GateDecision.DROP
        }
        if (storedConsent != null && storedConsent.purposes[purpose] != true) return GateDecision.DROP
        return if (gateDefault == ConsentGateDefault.OPEN) GateDecision.SEND else GateDecision.WAIT
    }
}
