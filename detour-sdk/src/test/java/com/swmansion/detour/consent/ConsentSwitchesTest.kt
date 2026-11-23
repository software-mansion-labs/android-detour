package com.swmansion.detour.consent

import com.swmansion.detour.DetourStatus
import com.swmansion.detour.LinkProcessedReport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ConsentSwitchesTest {
    private val warnings = mutableListOf<String>()

    @Before
    fun setUp() {
        ConsentWarnings.resetForTests()
        ConsentWarnings.isDevelopment = true
        ConsentWarnings.write = { warnings += it }
    }

    @After
    fun tearDown() {
        ConsentWarnings.resetForTests()
        ConsentWarnings.isDevelopment = false
    }

    @Test
    fun `each warning once in a debuggable build`() {
        ConsentWarnings.warnOnce("a", "first")
        ConsentWarnings.warnOnce("a", "again")
        ConsentWarnings.warnOnce("b", "other")
        assertEquals(listOf("[Detour:CONSENT] first", "[Detour:CONSENT] other"), warnings)
    }

    @Test
    fun `no warning in a release build`() {
        ConsentWarnings.isDevelopment = false
        ConsentWarnings.warnOnce("a", "first")
        assertEquals(emptyList<String>(), warnings)
    }

    @Test
    fun `a stored grant warns only for its own purpose`() {
        val stored = DetourConsent(purposes = mapOf(DetourPurpose.ATTRIBUTION to true))
        assertEquals(false, ConsentWarnings.warnIfStoredGrantWaits(DetourPurpose.ANALYTICS, stored))
        assertEquals(0, warnings.size)

        assertEquals(true, ConsentWarnings.warnIfStoredGrantWaits(DetourPurpose.ATTRIBUTION, stored))
        assertEquals(1, warnings.size)
    }

    @Test
    fun `the processed link is reported once`() {
        var reports = 0
        val report = LinkProcessedReport { reports++ }
        report.processed()
        report.processed()
        assertEquals(1, reports)
        LinkProcessedReport(null).processed()
    }

    @Test
    fun `status decisions map one to one`() {
        assertEquals(DetourStatus.Decision.SEND, DetourStatus.Decision.of(GateDecision.SEND))
        assertEquals(DetourStatus.Decision.WAIT, DetourStatus.Decision.of(GateDecision.WAIT))
        assertEquals(DetourStatus.Decision.DROP, DetourStatus.Decision.of(GateDecision.DROP))
    }
}
