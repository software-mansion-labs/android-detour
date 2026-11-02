package com.swmansion.detour.models

import com.google.gson.Gson
import com.google.gson.JsonObject
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
}
