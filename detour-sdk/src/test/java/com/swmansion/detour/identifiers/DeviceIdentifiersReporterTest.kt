package com.swmansion.detour.identifiers

import com.google.gson.JsonObject
import com.swmansion.detour.api.HttpClient
import com.swmansion.detour.models.AdvertisingId
import com.swmansion.detour.models.DirectMatchRequest
import com.swmansion.detour.test.InMemoryStorage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdentifiersReporterTest {

    private val aaid = AdvertisingId("38400000-8cf0-11bd-b23e-10b96e40000d")

    @Test
    fun `only changed fields go out and a gone value is cleared`() {
        val first = DeviceIdentifiersReporter.changes(DeviceIdentifiers(aaid).encodedFields(), emptyMap())
        assertEquals(aaid.id, first.first.getAsJsonObject("advertising_id")["id"].asString)
        assertEquals("aaid", first.first.getAsJsonObject("advertising_id")["type"].asString)

        val unchanged = DeviceIdentifiersReporter.changes(DeviceIdentifiers(aaid).encodedFields(), first.second)
        assertEquals(0, unchanged.first.size())

        val withdrawn = DeviceIdentifiersReporter.changes(DeviceIdentifiers.NONE.encodedFields(), first.second)
        assertTrue(withdrawn.first["advertising_id"].isJsonNull)
        assertTrue(withdrawn.second.isEmpty())
    }

    @Test
    fun `an answered state is not sent again but a failed one is`() = runTest {
        val bodies = mutableListOf<JsonObject>()
        var answer = false
        val reporter = DeviceIdentifiersReporter(
            InMemoryStorage(),
            canSend = { true },
            current = { DeviceIdentifiers(aaid) },
            deviceId = { "3f2b8c1e-9d4a-4c55-8f0e-2a6b7c8d9e01" },
            send = { bodies += it; answer },
        )

        reporter.sync()
        reporter.sync()
        assertEquals(2, bodies.size)
        assertEquals("3f2b8c1e-9d4a-4c55-8f0e-2a6b7c8d9e01", bodies.first()["device_id"].asString)

        answer = true
        reporter.sync()
        reporter.sync()
        assertEquals(3, bodies.size)
    }

    @Test
    fun `what the first match carried is not sent again`() = runTest {
        val bodies = mutableListOf<JsonObject>()
        var current = DeviceIdentifiers(aaid)
        val reporter = DeviceIdentifiersReporter(
            InMemoryStorage(),
            canSend = { true },
            current = { current },
            deviceId = { "device" },
            send = { bodies += it; true },
        )

        reporter.markSent(current, "device")
        reporter.sync()
        assertTrue(bodies.isEmpty())

        current = DeviceIdentifiers.NONE
        reporter.sync()
        assertTrue(bodies.single()["advertising_id"].isJsonNull)
    }

    @Test
    fun `nothing goes out before the reporter may send`() = runTest {
        val bodies = mutableListOf<JsonObject>()
        DeviceIdentifiersReporter(
            InMemoryStorage(),
            canSend = { false },
            current = { DeviceIdentifiers(aaid) },
            deviceId = { "device" },
            send = { bodies += it; true },
        ).sync()

        assertTrue(bodies.isEmpty())
    }

    @Test
    fun `a zero or blank advertising ID is no ID, and it goes lowercase`() {
        assertNull(AdvertisingIdReader.normalized("00000000-0000-0000-0000-000000000000"))
        assertNull(AdvertisingIdReader.normalized(""))
        assertEquals(aaid.id, AdvertisingIdReader.normalized(aaid.id.uppercase()))
    }

    @Test
    fun `the match request carries the advertising ID only when set`() {
        val without = HttpClient.gson.toJsonTree(DirectMatchRequest("c_1")).asJsonObject
        assertTrue(!without.has("advertising_id"))

        val with = HttpClient.gson.toJsonTree(DirectMatchRequest("c_1", advertisingId = aaid)).asJsonObject
        assertEquals(aaid.id, with.getAsJsonObject("advertising_id")["id"].asString)
    }
}
