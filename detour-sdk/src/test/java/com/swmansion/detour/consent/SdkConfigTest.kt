package com.swmansion.detour.consent

import com.swmansion.detour.consent.SdkConfigStore.FetchResult
import com.swmansion.detour.test.InMemoryStorage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SdkConfigTest {

    private val storage = InMemoryStorage()

    private val body = """
        {
          "schema_version": 1,
          "purposes": ["analytics", "attribution", "ad_network_sharing", "ad_personalization"],
          "consent_gate_default": {
            "analytics": "closed",
            "attribution": "closed",
            "ad_network_sharing": "closed",
            "ad_personalization": "closed"
          },
          "attribution_window_days_default": 7,
          "retention": { "gclid_days": 180, "raw_events_days": null },
          "features": { "postbacks_enabled": false, "attribution_enabled": false },
          "feature_flags": { "store_gclid_on_device": false, "read_app_set_id": false }
        }
    """.trimIndent()

    @Test
    fun `fallback opens only analytics`() = runTest {
        val config = SdkConfigStore(storage, "app").current()
        assertEquals(ConsentGateDefault.OPEN, config.gateDefault(DetourPurpose.ANALYTICS))
        assertEquals(ConsentGateDefault.CLOSED, config.gateDefault(DetourPurpose.ATTRIBUTION))
    }

    @Test
    fun `server values replace the fallback and survive relaunch`() = runTest {
        assertTrue(SdkConfigStore(storage, "app").apply(FetchResult.Updated(body, "\"v1\"")))

        val relaunched = SdkConfigStore(storage, "app")
        assertEquals(ConsentGateDefault.CLOSED, relaunched.current().gateDefault(DetourPurpose.ANALYTICS))
        assertEquals("\"v1\"", relaunched.etag())
    }

    @Test
    fun `cache of another app is ignored`() = runTest {
        SdkConfigStore(storage, "other").apply(FetchResult.Updated(body, "\"v1\""))

        val store = SdkConfigStore(storage, "app")
        assertEquals(SdkConfig.FALLBACK, store.current())
        assertNull(store.etag())
    }

    @Test
    fun `not modified and failure keep the last config`() = runTest {
        val store = SdkConfigStore(storage, "app")
        store.apply(FetchResult.Updated(body, "\"v1\""))

        assertFalse(store.apply(FetchResult.NotModified))
        assertFalse(store.apply(FetchResult.Failed))
        assertEquals(ConsentGateDefault.CLOSED, store.current().gateDefault(DetourPurpose.ANALYTICS))
        assertEquals("\"v1\"", store.etag())
    }

    @Test
    fun `body in an unexpected shape is ignored`() = runTest {
        val store = SdkConfigStore(storage, "app")
        assertFalse(store.apply(FetchResult.Updated("""{"schema_version": 1, "consent_gate_default": "open"}""", null)))
        assertEquals(SdkConfig.FALLBACK, store.current())
    }

    @Test
    fun `unknown gate value waits for consent`() {
        val config = SdkConfig.fromJson("""{"schema_version": 2, "consent_gate_default": {"analytics": "grandfather"}}""")!!
        assertEquals(ConsentGateDefault.CLOSED, config.gateDefault(DetourPurpose.ANALYTICS))
        assertEquals(2, config.schemaVersion)
    }
}
