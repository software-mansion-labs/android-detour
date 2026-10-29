package com.swmansion.detour.api

import com.swmansion.detour.api.DetourApiClient.UniversalLinkClickResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UniversalLinkClickRecorderTest {

    private var now = 1_000_000L
    private val sentUrls = mutableListOf<String>()
    private var response: UniversalLinkClickResult? = ALLOWED

    private fun TestScope.recorder(
        sendClick: suspend (String) -> UniversalLinkClickResult? = { url ->
            sentUrls += url
            response
        }
    ) = UniversalLinkClickRecorder(sendClick = sendClick, scope = backgroundScope, now = { now })

    @Test
    fun `sends one request when the same open reaches the SDK twice`() = runTest {
        val recorder = recorder()

        val first = recorder.record(LINK)
        now += 1_500
        val second = recorder.record(LINK)

        assertEquals(ALLOWED, first)
        assertEquals(ALLOWED, second)
        assertEquals(1, sentUrls.size)
    }

    @Test
    fun `shares a request that is still in flight`() = runTest {
        val pendingResponse = CompletableDeferred<UniversalLinkClickResult?>()
        val recorder = recorder { url ->
            sentUrls += url
            pendingResponse.await()
        }

        val calls = listOf(async { recorder.record(LINK) }, async { recorder.record(LINK) })
        runCurrent()
        pendingResponse.complete(ALLOWED)

        assertEquals(listOf(ALLOWED, ALLOWED), calls.awaitAll())
        assertEquals(1, sentUrls.size)
    }

    @Test
    fun `keeps the request going when the caller is cancelled`() = runTest {
        val pendingResponse = CompletableDeferred<UniversalLinkClickResult?>()
        val recorder = recorder { url ->
            sentUrls += url
            pendingResponse.await()
        }

        // The activity is destroyed while the click is being sent, and the new one asks again.
        val destroyedCaller = launch { recorder.record(LINK) }
        runCurrent()
        destroyedCaller.cancel()
        pendingResponse.complete(ALLOWED)

        assertEquals(ALLOWED, recorder.record(LINK))
        assertEquals(1, sentUrls.size)
    }

    @Test
    fun `sends again once the repeat window has passed`() = runTest {
        val recorder = recorder()

        recorder.record(LINK)
        now += UniversalLinkClickRecorder.REPEAT_WINDOW_MS
        recorder.record(LINK)

        assertEquals(2, sentUrls.size)
    }

    @Test
    fun `sends a separate request for a different link`() = runTest {
        val recorder = recorder()

        recorder.record(LINK)
        recorder.record("https://myapp.godetour.link/abc123/product/43")

        assertEquals(2, sentUrls.size)
    }

    @Test
    fun `reuses a denied result for the repeat`() = runTest {
        val denied = UniversalLinkClickResult(allowed = false, error = "Click limit exceeded", effectiveLimit = 100)
        response = denied
        val recorder = recorder()

        recorder.record(LINK)
        val repeat = recorder.record(LINK)

        assertEquals(denied, repeat)
        assertEquals(1, sentUrls.size)
    }

    @Test
    fun `lets the link open and sends the click again when it was not saved`() = runTest {
        response = null
        val recorder = recorder()

        val failed = recorder.record(LINK)
        response = ALLOWED
        val retried = recorder.record(LINK)

        assertEquals(UniversalLinkClickResult(allowed = true), failed)
        assertEquals(ALLOWED, retried)
        assertEquals(2, sentUrls.size)
    }

    @Test
    fun `lets the link open and sends the click again when sending throws`() = runTest {
        var attempts = 0
        val recorder = recorder { url ->
            sentUrls += url
            if (++attempts == 1) throw IllegalStateException("offline")
            ALLOWED
        }

        val failed = recorder.record(LINK)
        val retried = recorder.record(LINK)

        assertEquals(UniversalLinkClickResult(allowed = true), failed)
        assertEquals(ALLOWED, retried)
        assertEquals(2, sentUrls.size)
    }

    private companion object {
        const val LINK = "https://myapp.godetour.link/abc123/product/42?ref=x"
        val ALLOWED = UniversalLinkClickResult(allowed = true, clickId = "click-1")
    }
}
