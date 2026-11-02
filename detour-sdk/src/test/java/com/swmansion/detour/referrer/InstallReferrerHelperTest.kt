package com.swmansion.detour.referrer

import android.content.Context
import com.swmansion.detour.models.ReferrerUtm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class InstallReferrerHelperTest {

    private lateinit var helper: InstallReferrerHelper

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        helper = InstallReferrerHelper(context)
    }

    @Test
    fun `extractClickId - parses click_id from referrer string`() {
        val referrer = "utm_source=google&click_id=abc123&utm_medium=cpc"
        val result = helper.extractClickId(referrer)
        assertEquals("abc123", result)
    }

    @Test
    fun `extractClickId - handles URL-encoded referrer`() {
        val referrer = "utm_source%3Dgoogle%26click_id%3Dxyz789%26utm_medium%3Dcpc"
        val result = helper.extractClickId(referrer)
        assertEquals("xyz789", result)
    }

    @Test
    fun `extractClickId - returns null when click_id is missing`() {
        val referrer = "utm_source=google&utm_medium=cpc"
        val result = helper.extractClickId(referrer)
        assertNull(result)
    }

    @Test
    fun `extractClickId - click_id at start of string`() {
        val referrer = "click_id=first123&utm_source=google"
        val result = helper.extractClickId(referrer)
        assertEquals("first123", result)
    }

    @Test
    fun `extractClickId - click_id at end of string`() {
        val referrer = "utm_source=google&click_id=last456"
        val result = helper.extractClickId(referrer)
        assertEquals("last456", result)
    }

    @Test
    fun `extractClickId - click_id is only parameter`() {
        val referrer = "click_id=solo789"
        val result = helper.extractClickId(referrer)
        assertEquals("solo789", result)
    }

    @Test
    fun `buildDetails - reads every field and keeps the gclid case`() {
        val raw = "utm_source=google&utm_medium=cpc&gclid=AbC.123&click_id=x"

        val details = helper.buildDetails(
            raw = raw,
            clickTs = 1_730_000_000,
            installBeginTs = 1_730_000_030,
            clickTsServer = 1_730_000_001,
            installBeginTsServer = 1_730_000_031,
            googlePlayInstant = true,
        )

        assertEquals(raw, details.raw)
        assertEquals(ReferrerUtm("google", "cpc", null, null, null), details.utm)
        assertEquals("AbC.123", details.gclid)
        assertEquals("x", details.clickId)
        assertEquals(1_730_000_000, details.clickTs)
        assertEquals(1_730_000_030, details.installBeginTs)
        assertEquals(1_730_000_001, details.clickTsServer)
        assertEquals(1_730_000_031, details.installBeginTsServer)
        assertEquals(true, details.googlePlayInstant)
    }

    @Test
    fun `buildDetails - no utm parameters leaves utm out`() {
        assertNull(helper.buildDetails(
            raw = "click_id=x",
            clickTs = 0,
            installBeginTs = 0,
            clickTsServer = 0,
            installBeginTsServer = 0,
            googlePlayInstant = false,
        ).utm)
    }
}
