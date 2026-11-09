package com.swmansion.detour.models

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.swmansion.detour.api.DetourApiClient
import com.swmansion.detour.api.RequestContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MatchRequestTest {

    private fun json(request: MatchRequest): JsonObject =
        Gson().toJsonTree(request).asJsonObject

    @Test
    fun `direct request sends device_id and user_id in snake case`() {
        val body = json(DirectMatchRequest("c_1", "3f2b8c1e-9d4a-4c55-8f0e-2a6b7c8d9e01", "u_12345"))

        assertEquals("c_1", body["clickId"].asString)
        assertEquals("3f2b8c1e-9d4a-4c55-8f0e-2a6b7c8d9e01", body["device_id"].asString)
        assertEquals("u_12345", body["user_id"].asString)
    }

    @Test
    fun `user_id is omitted when not set`() {
        assertFalse(json(DirectMatchRequest("c_1", "3f2b8c1e-9d4a-4c55-8f0e-2a6b7c8d9e01")).has("user_id"))
    }

    @Test
    fun `install_referrer uses contract names and leaves out clickId`() {
        val referrer = InstallReferrerDetails(
            raw = "gclid=AbC.123&click_id=c_1",
            utm = null,
            gclid = "AbC.123",
            clickTs = 1,
            installBeginTs = 2,
            clickTsServer = 3,
            installBeginTsServer = 4,
            googlePlayInstant = false,
            clickId = "c_1",
        )

        val body = json(DirectMatchRequest("c_1", installReferrer = referrer))["install_referrer"].asJsonObject

        assertEquals(
            setOf(
                "raw", "gclid", "click_ts", "install_begin_ts",
                "click_ts_server", "install_begin_ts_server", "google_play_instant",
            ),
            body.keySet(),
        )
    }

    @Test
    fun `match-link body is flat and keeps the locale signals`() {
        val request = SignalMatchRequest(
            platform = "android", model = "Pixel 8", manufacturer = "Google", systemVersion = "14",
            screenWidth = 1080, screenHeight = 2400, scale = 2.6f,
            locale = listOf(LocaleInfo("pl-PL")), timezone = "Europe/Warsaw", userAgent = "ua", timestamp = 0,
        )
        val context = RequestContext(appVersion = "4.2.0", networkType = "wifi", locale = "pl-PL")

        val body = JsonParser.parseString(DetourApiClient.matchLinkJson(request, context)).asJsonObject

        assertEquals("4.2.0", body["app_version"].asString)
        assertEquals("wifi", body["network_type"].asString)
        assertEquals("pl-PL", body["locale"].asJsonArray[0].asJsonObject["languageTag"].asString)
    }
}
