package com.swmansion.detour.analytics

import com.swmansion.detour.api.AnalyticsApiClient
import com.swmansion.detour.api.RequestContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionManagerTest {

    private var clock = 1_730_000_000_000L
    private val events = mutableListOf<SessionManager.Event>()
    private val manager = SessionManager { clock }

    private val startedId get() = events.first().sessionId

    @Before
    fun setUp() {
        manager.start { events += it }
        manager.onForeground()
    }

    @Test
    fun `first foreground starts a session`() {
        assertEquals(listOf(SessionManager.Event.Start(startedId, clock)), events)
    }

    @Test
    fun `background reports the end with its duration`() {
        clock += 600_000
        manager.onBackground()

        assertEquals(SessionManager.Event.End(startedId, clock, 600_000), events.last())
        assertEquals(startedId, manager.currentSessionId)
    }

    @Test
    fun `short trip to the background keeps the session`() {
        manager.onBackground()
        clock += SessionManager.BACKGROUND_TIMEOUT_MS
        manager.onForeground()

        assertEquals(2, events.size)
        assertEquals(startedId, manager.currentSessionId)
    }

    @Test
    fun `long trip to the background starts a new session`() {
        manager.onBackground()
        clock += SessionManager.BACKGROUND_TIMEOUT_MS + 1
        manager.onForeground()

        val newStart = events.last()
        assertTrue(newStart is SessionManager.Event.Start)
        assertNotEquals(startedId, newStart.sessionId)
        assertEquals(newStart.sessionId, manager.currentSessionId)
    }

    @Test
    fun `session body follows the contract`() {
        val body = AnalyticsApiClient.body(
            AnalyticsRequest.Session(SessionManager.Event.End("8c1d2e3f-4a5b-4c6d-8e7f-901a2b3c4d5e", clock, 1_235)),
            RequestContext(appVersion = "4.2.0"),
            "3f2b8c1e-9d4a-4c55-8f0e-2a6b7c8d9e01",
            "u_12345",
        )

        assertEquals("end", body["type"])
        assertEquals("8c1d2e3f-4a5b-4c6d-8e7f-901a2b3c4d5e", body["session_id"])
        assertEquals(1_235L, body["duration_ms"])
        assertEquals("2024-10-27T03:33:20.000Z", body["timestamp"])
        assertEquals("4.2.0", body["app_version"])
        assertEquals("u_12345", body["user_id"])
    }
}
