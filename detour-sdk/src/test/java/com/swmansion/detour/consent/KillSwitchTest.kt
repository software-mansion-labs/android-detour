package com.swmansion.detour.consent

import com.google.gson.JsonParser
import com.swmansion.detour.api.HttpClient
import com.swmansion.detour.consent.KillSwitchReporter.Outcome
import com.swmansion.detour.models.DirectMatchRequest
import com.swmansion.detour.storage.InstallStore
import com.swmansion.detour.test.InMemoryStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
class KillSwitchTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val at = 1_760_445_000_000L

    private fun store() = InstallStore(folder.root, InMemoryStorage()) { true }

    @Test
    fun `disabled state survives relaunch and keeps the device id`() = runTest {
        val first = store()
        val deviceId = first.getDeviceId()
        assertTrue(first.setEnabled(false, at))

        val relaunched = store()
        assertFalse(relaunched.isEnabled())
        assertEquals(deviceId, relaunched.getDeviceId())
    }

    @Test
    fun `repeated call with the same state is not a change`() = runTest {
        val store = store()
        assertFalse(store.setEnabled(true, at))
        assertNull(store.unreportedKillSwitch())

        assertTrue(store.setEnabled(false, at))
        assertFalse(store.setEnabled(false, at + 60_000))
        assertEquals(false to at, store.unreportedKillSwitch())
    }

    @Test
    fun `file from an sdk without the kill switch keeps its device id`() = runTest {
        val deviceId = "3f2b8c1e-9d4a-4c55-8f0e-2a6b7c8d9e01"
        File(folder.root, "detour_install.json").writeText("""{"deviceId":"$deviceId","firstLaunchHandled":true}""")

        val store = store()
        assertEquals(deviceId, store.getDeviceId())
        assertTrue(store.isEnabled())
        assertFalse(store.isFirstLaunch())
    }

    @Test
    fun `reporter sends the state until confirmed`() = runTest {
        val store = store()
        store.setEnabled(false, at)
        val sent = mutableListOf<Boolean>()
        val outcomes = ArrayDeque(listOf(Outcome.RETRY, Outcome.CONFIRMED))
        val delays = mutableListOf<Long>()
        val reporter = KillSwitchReporter(store, backgroundScope, sleep = { delays.add(it) }) { enabled, atMs ->
            assertEquals(at, atMs)
            sent.add(enabled)
            outcomes.removeFirst()
        }

        reporter.run()

        assertEquals(listOf(false, false), sent)
        assertEquals(listOf(KillSwitchReporter.FIRST_RETRY_DELAY_MS), delays)
        assertNull(store().unreportedKillSwitch())
    }

    @Test
    fun `change during a request is sent after it`() = runTest {
        val store = store()
        store.setEnabled(false, at)
        val sent = mutableListOf<Boolean>()
        val reporter = KillSwitchReporter(store, backgroundScope) { enabled, _ ->
            sent.add(enabled)
            if (sent.size == 1) store.setEnabled(true, at + 10_000)
            Outcome.CONFIRMED
        }

        reporter.run()

        assertEquals(listOf(false, true), sent)
        assertNull(store.unreportedKillSwitch())
    }

    @Test
    fun `sync during a request sends the newer state after it`() = runTest {
        val store = store()
        store.setEnabled(false, at)
        val sent = Collections.synchronizedList(mutableListOf<Boolean>())
        val bothSent = CompletableDeferred<Unit>()
        lateinit var reporter: KillSwitchReporter
        reporter = KillSwitchReporter(store, backgroundScope) { enabled, _ ->
            sent.add(enabled)
            if (sent.size == 1) {
                store.setEnabled(true, at + 10_000)
                reporter.sync()
            } else {
                bothSent.complete(Unit)
            }
            Outcome.CONFIRMED
        }

        reporter.sync()
        reporter.sync()
        // The store reads its file on Dispatchers.IO, so wait in real time.
        withContext(Dispatchers.Default) { withTimeout(5_000) { bothSent.await() } }

        assertEquals(listOf(false, true), sent.toList())
    }

    @Test
    fun `rejected state is kept for the next launch`() = runTest {
        val store = store()
        store.setEnabled(false, at)

        KillSwitchReporter(store, backgroundScope) { _, _ -> Outcome.STOP }.run()

        assertEquals(false, store().unreportedKillSwitch()?.first)
    }

    @Test
    fun `click id match without device data is the v1 body`() {
        val json = JsonParser.parseString(HttpClient.gson.toJson(DirectMatchRequest("click"))).asJsonObject
        assertEquals(setOf("clickId"), json.keySet())
    }
}
