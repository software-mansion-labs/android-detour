package com.swmansion.detour.analytics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RevenueWarningTest {

    // DetourAnalytics keeps its state for the whole test process, so each test uses its own event names.

    @Test
    fun `warns once per event name`() {
        val data = mapOf("revenue" to 0.01)

        assertTrue(DetourAnalytics.takeRevenueWarning("test_ad_impression", data))
        assertFalse(DetourAnalytics.takeRevenueWarning("test_ad_impression", data))
        assertTrue(DetourAnalytics.takeRevenueWarning("test_purchase", data))
    }

    @Test
    fun `does not warn without revenue`() {
        assertFalse(DetourAnalytics.takeRevenueWarning("test_no_revenue", mapOf("value" to 1)))
        assertFalse(DetourAnalytics.takeRevenueWarning("test_no_revenue", null))
        assertTrue(DetourAnalytics.takeRevenueWarning("test_no_revenue", mapOf("revenue" to 1)))
    }
}
