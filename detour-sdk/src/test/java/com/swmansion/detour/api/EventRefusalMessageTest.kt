package com.swmansion.detour.api

import com.swmansion.detour.api.AnalyticsApiClient.Companion.eventRefusalMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class EventRefusalMessageTest {

    private fun message(statusCode: Int, body: String? = "{}") =
        eventRefusalMessage(statusCode, body, "level_complete")

    @Test
    fun `uses the server message for a name refusal`() {
        val body = """{"error":{"formErrors":[],"fieldErrors":{"event_name":["the App already has 500 custom event names"]}}}"""
        assertEquals(
            "Event \"level_complete\" refused: the App already has 500 custom event names.",
            message(400, body)
        )
    }

    @Test
    fun `falls back to the status for a 400 without a name error`() {
        assertEquals("Event send failed: 400", message(400, """{"error":{"fieldErrors":{"data":["too big"]}}}"""))
        assertEquals("Event send failed: 400", message(400, """{"error":"Bad request"}"""))
        assertEquals("Event send failed: 400", message(400, "not json"))
        assertEquals("Event send failed: 400", message(400, null))
    }

    @Test
    fun `describes too large and rate limited`() {
        assertEquals("Event \"level_complete\" refused: the request body is too large.", message(413))
        assertEquals("Event \"level_complete\" refused: rate limit exceeded.", message(429))
    }

    @Test
    fun `falls back to the status code`() {
        assertEquals("Event send failed: 500", message(500))
    }
}
