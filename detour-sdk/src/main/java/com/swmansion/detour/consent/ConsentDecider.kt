package com.swmansion.detour.consent

/** Decides a purpose with the kill switch, the consent and the server config, for every caller. */
internal class ConsentDecider(
    private val consentStore: ConsentStore,
    private val sdkConfigStore: SdkConfigStore,
    private val isEnabled: suspend () -> Boolean,
) {
    class Inputs(val isEnabled: Boolean, val storedConsent: DetourConsent?, val gateDefault: ConsentGateDefault)

    suspend fun read(purpose: String) =
        Inputs(isEnabled(), consentStore.stored(), sdkConfigStore.current().gateDefault(purpose))

    // Reads the consent passed on this launch last, so a caller deciding under a lock sees one
    // passed meanwhile.
    fun decide(purpose: String, inputs: Inputs): GateDecision = ConsentGate.decide(
        purpose, inputs.isEnabled, consentStore.passedThisLaunch, inputs.storedConsent, inputs.gateDefault
    )

    suspend fun decideNow(purpose: String): GateDecision = decide(purpose, read(purpose))
}
