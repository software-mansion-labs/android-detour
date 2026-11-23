package com.swmansion.detour.referrer

import com.swmansion.detour.consent.GateDecision
import com.swmansion.detour.models.MetaInstallReferrer
import com.swmansion.detour.storage.DetourStorage
import com.swmansion.detour.storage.StorageKeys
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LateMetaReferrerSenderTest {

    private val referrer = MetaInstallReferrer("encrypted", true, 1_700_000_000, "facebook")

    private class MemoryStorage : DetourStorage {
        val items = mutableMapOf<String, String>()
        override suspend fun getItem(key: String) = items[key]
        override suspend fun setItem(key: String, value: String) {
            items[key] = value
        }
    }

    private fun sender(
        storage: DetourStorage,
        sent: MutableList<String>,
        answers: MutableList<Boolean> = mutableListOf(true),
        decision: GateDecision = GateDecision.SEND,
        now: () -> Long = { 0L },
    ) = LateMetaReferrerSender(
        storage,
        deviceId = { "device" },
        attributionDecision = { decision },
        send = { deviceId, _ -> sent += deviceId; answers.removeAt(0) },
        now = now,
    )

    @Test
    fun `sends once and forgets the referrer the server took`() = runTest {
        val storage = MemoryStorage()
        val sent = mutableListOf<String>()
        sender(storage, sent).run { keep("device", referrer); sendKept() }
        sender(storage, sent).sendKept()

        assertEquals(listOf("device"), sent)
        assertEquals("", storage.items[StorageKeys.LATE_META_REFERRER])
    }

    @Test
    fun `keeps it for the next launch until the server takes it`() = runTest {
        val storage = MemoryStorage()
        val sent = mutableListOf<String>()
        sender(storage, sent, mutableListOf(false)).run { keep("device", referrer); sendKept() }
        sender(storage, sent).sendKept()

        assertEquals(2, sent.size)
        assertEquals("", storage.items[StorageKeys.LATE_META_REFERRER])
    }

    @Test
    fun `drops it after a refusal, for another device ID and after 7 days`() = runTest {
        val sent = mutableListOf<String>()
        val refused = MemoryStorage()
        sender(refused, sent, decision = GateDecision.DROP).run { keep("device", referrer); sendKept() }
        sender(MemoryStorage(), sent).run { keep("restored-device", referrer); sendKept() }
        val old = MemoryStorage()
        sender(old, sent, decision = GateDecision.WAIT).run { keep("device", referrer); sendKept() }
        sender(old, sent, now = { 8L * 24 * 60 * 60 * 1000 }).sendKept()

        assertEquals(emptyList<String>(), sent)
        assertEquals("", refused.items[StorageKeys.LATE_META_REFERRER])
        assertEquals("", old.items[StorageKeys.LATE_META_REFERRER])
    }

    @Test
    fun `drops a kept value it cannot read`() = runTest {
        val storage = MemoryStorage().apply { items[StorageKeys.LATE_META_REFERRER] = "{not json" }
        val sent = mutableListOf<String>()
        sender(storage, sent).sendKept()

        assertEquals(emptyList<String>(), sent)
        assertEquals("", storage.items[StorageKeys.LATE_META_REFERRER])
    }
}
