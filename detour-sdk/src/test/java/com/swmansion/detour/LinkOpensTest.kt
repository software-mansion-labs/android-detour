package com.swmansion.detour

import android.content.Intent
import android.net.Uri
import com.swmansion.detour.models.LinkResult
import com.swmansion.detour.models.LinkType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LinkOpensTest {

    private var clock = 0L
    private val linkOpens = LinkOpens(now = { clock })
    private var opens = 0
    private val open: suspend () -> LinkResult = {
        opens++
        SUCCESS
    }

    @Test
    fun `opens the link from a new intent`() = runTest {
        assertEquals(SUCCESS, linkOpens.openOnce(linkIntent(), open))
        assertEquals(1, opens)
    }

    @Test
    fun `does not open the same intent again`() = runTest {
        // A recreated activity, a second DetourDelegate or a second processLink call.
        val intent = linkIntent()

        linkOpens.openOnce(intent, open)
        val repeat = linkOpens.openOnce(intent, open)

        assertEquals(LinkResult.NoLink, repeat)
        assertEquals(1, opens)
    }

    @Test
    fun `does not open a copy of an opened intent`() = runTest {
        val intent = linkIntent()

        linkOpens.openOnce(intent, open)
        val forwarded = linkOpens.openOnce(Intent(intent), open)

        assertEquals(LinkResult.NoLink, forwarded)
        assertEquals(1, opens)
    }

    @Test
    fun `opens the link again when the user taps it again`() = runTest {
        linkOpens.openOnce(linkIntent(), open)
        clock += LinkOpens.SAME_TAP_WINDOW_MS
        val tappedAgain = linkOpens.openOnce(linkIntent(), open)

        assertEquals(SUCCESS, tappedAgain)
        assertEquals(2, opens)
    }

    @Test
    fun `does not open the same link from a new intent right after an open`() = runTest {
        // For example, a splash activity passes the link on to the main activity.
        linkOpens.openOnce(linkIntent(), open)
        clock += LinkOpens.SAME_TAP_WINDOW_MS - 1

        assertEquals(LinkResult.NoLink, linkOpens.openOnce(linkIntent(), open))
    }

    @Test
    fun `opens another link right after an open`() = runTest {
        linkOpens.openOnce(linkIntent(), open)

        assertEquals(SUCCESS, linkOpens.openOnce(linkIntent(OTHER_LINK), open))
    }

    @Test
    fun `delivers the link once when two intents with it are processed at once`() = runTest {
        val clickSent = CompletableDeferred<Unit>()
        val slowOpen: suspend () -> LinkResult = {
            clickSent.await()
            SUCCESS
        }

        val calls = listOf(
            async { linkOpens.openOnce(linkIntent(), slowOpen) },
            async { linkOpens.openOnce(linkIntent(), slowOpen) }
        )
        runCurrent()
        clickSent.complete(Unit)

        assertEquals(listOf(SUCCESS, LinkResult.NoLink), calls.awaitAll())
    }

    @Test
    fun `delivers the link from a new intent when the first caller was cancelled`() = runTest {
        val destroyedCaller = launch { linkOpens.openOnce(linkIntent()) { awaitCancellation() } }
        runCurrent()
        destroyedCaller.cancel()

        assertEquals(SUCCESS, linkOpens.openOnce(linkIntent(), open))
    }

    @Test
    fun `delivers the link once when two callers process the intent at once`() = runTest {
        val intent = linkIntent()
        val clickSent = CompletableDeferred<Unit>()
        val slowOpen: suspend () -> LinkResult = {
            clickSent.await()
            SUCCESS
        }

        val calls = listOf(
            async { linkOpens.openOnce(intent, slowOpen) },
            async { linkOpens.openOnce(intent, slowOpen) }
        )
        runCurrent()
        clickSent.complete(Unit)

        assertEquals(listOf(SUCCESS, LinkResult.NoLink), calls.awaitAll())
    }

    @Test
    fun `delivers the link to the next caller when the first one was cancelled`() = runTest {
        val intent = linkIntent()

        // The activity is destroyed before the link is delivered.
        val destroyedCaller = launch { linkOpens.openOnce(intent) { awaitCancellation() } }
        runCurrent()
        destroyedCaller.cancel()

        assertEquals(SUCCESS, linkOpens.openOnce(intent, open))
    }

    @Test
    fun `does not open a link reopened from Recents`() = runTest {
        val intent = linkIntent().addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        assertEquals(LinkResult.NoLink, linkOpens.openOnce(intent, open))
        assertEquals(0, opens)
    }

    @Test
    fun `does not open a link restored after process death`() = runTest {
        val restoredIntent = linkIntent()

        LinkOpens.markOpenedBeforeProcessDeath(restoredIntent)

        assertEquals(LinkResult.NoLink, linkOpens.openOnce(restoredIntent, open))
        assertEquals(0, opens)
    }

    @Test
    fun `opens a pending link when the activity is recreated in the same process`() = runTest {
        val intent = linkIntent()
        val destroyedCaller = launch { linkOpens.openOnce(intent) { awaitCancellation() } }
        runCurrent()
        destroyedCaller.cancel()

        LinkOpens.markOpenedBeforeProcessDeath(intent)

        assertEquals(SUCCESS, linkOpens.openOnce(intent, open))
    }

    private fun linkIntent(link: String = LINK) = Intent(Intent.ACTION_VIEW, Uri.parse(link))

    private companion object {
        const val LINK = "https://myapp.godetour.link/abc123/product/42"
        const val OTHER_LINK = "https://myapp.godetour.link/abc123/product/7"
        val SUCCESS = LinkResult.Success(
            url = LINK,
            route = "/product/42",
            pathname = "/product/42",
            type = LinkType.VERIFIED
        )
    }
}
