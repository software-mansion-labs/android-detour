package com.swmansion.detour.analytics

import com.google.gson.JsonParser
import com.swmansion.detour.api.AnalyticsApiClient
import com.swmansion.detour.api.HttpClient
import com.swmansion.detour.api.RequestContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.math.BigDecimal

class ConversionBodyTest {

    @Test
    fun `sends the contract fields with exact revenue`() {
        val conversion = DetourConversion(
            name = "ad_impression",
            revenue = BigDecimal("0.001234"),
            currency = "USD",
            revenueType = RevenueType.IN_APP_ADS,
            transactionId = "t_1",
        )
        val body = AnalyticsApiClient.body(
            AnalyticsRequest.Conversion(conversion, 1_730_000_000_000L),
            RequestContext(),
            "3f2b8c1e-9d4a-4c55-8f0e-2a6b7c8d9e01",
            null,
        )

        val json = HttpClient.gson.toJson(body)
        val parsed = JsonParser.parseString(json).asJsonObject

        assertEquals("in_app_ads", parsed["revenue_type"].asString)
        assertEquals(true, json.contains("\"revenue\":0.001234"))
        assertEquals("t_1", parsed["transaction_id"].asString)
        assertEquals("2024-10-27T03:33:20.000Z", parsed["timestamp"].asString)
        assertFalse(parsed.has("product_id"))
        assertFalse(parsed.has("quantity"))
    }
}
