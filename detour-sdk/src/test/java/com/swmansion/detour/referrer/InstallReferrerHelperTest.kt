package com.swmansion.detour.referrer

import android.content.Context
import com.swmansion.detour.models.ReferrerUtm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `parseReferrerParams - encoded ampersand and equals stay in the value`() {
        val params = helper.parseReferrerParams("click_id=x&utm_campaign=Q4%20%26%20sale&utm_term=a%3Db")
        assertEquals("Q4 & sale", params["utm_campaign"])
        assertEquals("a=b", params["utm_term"])
        assertEquals("x", params["click_id"])
    }

    @Test
    fun `parseReferrerParams - plus becomes a space and encoded plus stays a plus`() {
        val params = helper.parseReferrerParams("utm_campaign=summer+sale&utm_term=c%2B%2B")
        assertEquals("summer sale", params["utm_campaign"])
        assertEquals("c++", params["utm_term"])
    }

    @Test
    fun `parseReferrerParams - double-encoded referrer keeps encoded characters in values`() {
        val params = helper.parseReferrerParams("utm_source%3Dgoogle%26utm_campaign%3DQ4%2520%2526%2520sale")
        assertEquals("google", params["utm_source"])
        assertEquals("Q4 & sale", params["utm_campaign"])
    }

    @Test
    fun `parseReferrerParams - ignores a leading question mark`() {
        assertEquals(mapOf("utm_source" to "x"), helper.parseReferrerParams("?utm_source=x"))
    }

    @Test
    fun `parseReferrerParams - first repeated key wins and empty parts are dropped`() {
        val params = helper.parseReferrerParams("utm_source=a&utm_source=b&utm_medium=&=c&&gclid=AbC")
        assertEquals(mapOf("utm_source" to "a", "gclid" to "AbC"), params)
    }

    @Test
    fun `parseReferrerParams - keeps a badly encoded part as it is`() {
        val params = helper.parseReferrerParams("utm_source=100%&utm_medium=cpc")
        assertEquals("100%", params["utm_source"])
        assertEquals("cpc", params["utm_medium"])
    }

    @Test
    fun `isDirectMatchClickId - accepts a UUID in any case`() {
        assertTrue(InstallReferrerHelper.isDirectMatchClickId("3f2b8c1e-9a4d-4e7b-8c2a-1d5e6f7a8b9c"))
        assertTrue(InstallReferrerHelper.isDirectMatchClickId("3F2B8C1E-9A4D-4E7B-8C2A-1D5E6F7A8B9C"))
    }

    @Test
    fun `isDirectMatchClickId - rejects values that are not a UUID`() {
        assertFalse(InstallReferrerHelper.isDirectMatchClickId("null"))
        assertFalse(InstallReferrerHelper.isDirectMatchClickId("abc"))
        assertFalse(InstallReferrerHelper.isDirectMatchClickId("3f2b8c1e9a4d4e7b8c2a1d5e6f7a8b9c"))
        assertFalse(InstallReferrerHelper.isDirectMatchClickId("3f2b8c1e-9a4d-4e7b-8c2a-1d5e6f7a8b9c-x"))
    }
}
