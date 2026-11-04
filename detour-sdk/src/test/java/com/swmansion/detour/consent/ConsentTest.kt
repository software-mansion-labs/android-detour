package com.swmansion.detour.consent

import com.google.gson.JsonParser
import com.swmansion.detour.api.DetourApiClient
import com.swmansion.detour.api.HttpClient
import com.swmansion.detour.api.RequestContext
import com.swmansion.detour.models.DirectMatchRequest
import com.swmansion.detour.test.InMemoryStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConsentGateTest {

    private val granted = DetourConsent(mapOf(DetourPurpose.ANALYTICS to true))
    private val denied = DetourConsent(mapOf(DetourPurpose.ANALYTICS to false))
    private val otherPurposeOnly = DetourConsent(mapOf(DetourPurpose.ATTRIBUTION to true))

    private fun decide(
        passedThisLaunch: DetourConsent? = null,
        stored: DetourConsent? = null,
        gate: ConsentGateDefault,
        isEnabled: Boolean = true,
    ) = ConsentGate.decide(DetourPurpose.ANALYTICS, isEnabled, passedThisLaunch, stored, gate)

    @Test
    fun `without consent the server default decides`() {
        assertEquals(GateDecision.SEND, decide(gate = ConsentGateDefault.OPEN))
        assertEquals(GateDecision.WAIT, decide(gate = ConsentGateDefault.CLOSED))
    }

    @Test
    fun `consent from this launch decides whatever the default`() {
        assertEquals(GateDecision.SEND, decide(passedThisLaunch = granted, gate = ConsentGateDefault.CLOSED))
        assertEquals(GateDecision.DROP, decide(passedThisLaunch = denied, gate = ConsentGateDefault.OPEN))
        assertEquals(GateDecision.DROP, decide(passedThisLaunch = otherPurposeOnly, gate = ConsentGateDefault.OPEN))
    }

    @Test
    fun `stored refusal blocks on cold start`() {
        assertEquals(GateDecision.DROP, decide(stored = denied, gate = ConsentGateDefault.OPEN))
        assertEquals(GateDecision.DROP, decide(stored = otherPurposeOnly, gate = ConsentGateDefault.OPEN))
    }

    @Test
    fun `stored grant does not pass a closed gate on its own`() {
        assertEquals(GateDecision.WAIT, decide(stored = granted, gate = ConsentGateDefault.CLOSED))
        assertEquals(GateDecision.SEND, decide(stored = granted, gate = ConsentGateDefault.OPEN))
    }

    @Test
    fun `kill switch stops even granted purposes`() {
        assertEquals(GateDecision.DROP, decide(passedThisLaunch = granted, gate = ConsentGateDefault.OPEN, isEnabled = false))
    }

    @Test
    fun `new choice on this launch replaces the stored one`() {
        assertEquals(GateDecision.SEND, decide(passedThisLaunch = granted, stored = denied, gate = ConsentGateDefault.CLOSED))
    }
}

class DetourConsentTest {

    private val consent = DetourConsent(
        purposes = mapOf(DetourPurpose.ANALYTICS to true, DetourPurpose.AD_NETWORK_SHARING to false),
        timestampMs = 1_760_443_200_000,
        consentTextRef = "cmp-v12",
        schemaVersion = 1,
        source = "cmp",
    )

    @Test
    fun `wire format matches the api contract`() {
        val json = JsonParser.parseString(HttpClient.gson.toJson(consent.toWire())).asJsonObject

        assertEquals(true, json["purposes"].asJsonObject["analytics"].asBoolean)
        assertEquals(false, json["purposes"].asJsonObject["ad_network_sharing"].asBoolean)
        assertEquals("2025-10-14T12:00:00.000Z", json["timestamp"].asString)
        assertEquals("cmp-v12", json["consent_text_ref"].asString)
        assertEquals(1, json["schema_version"].asInt)
        assertEquals("cmp", json["source"].asString)
        assertNull(json["region"])
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `stored consent survives relaunch`() = runTest {
        val storage = InMemoryStorage()
        ConsentStore(storage, scope = this).record(consent)
        advanceUntilIdle()

        assertEquals(consent, ConsentStore(storage).stored())
    }

    @Test
    fun `match link carries the consent snapshot as an object`() {
        val body = DetourApiClient.matchLinkJson(
            DirectMatchRequest(clickId = "click"),
            RequestContext(consentSnapshot = consent.toWire()),
        )
        val snapshot = JsonParser.parseString(body).asJsonObject["consent_snapshot"].asJsonObject
        assertEquals(true, snapshot["purposes"].asJsonObject["analytics"].asBoolean)
    }
}
