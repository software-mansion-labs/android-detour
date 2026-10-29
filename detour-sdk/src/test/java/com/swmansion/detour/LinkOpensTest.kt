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

    private var opens = 0
    private val open: suspend () -> LinkResult = {
        opens++
        SUCCESS
    }

    @Test
    fun `opens the link from a new intent`() = runTest {
        assertEquals(SUCCESS, LinkOpens.openOnce(linkIntent(), open))
        assertEquals(1, opens)
    }

    @Test
    fun `does not open the same intent again`() = runTest {
        // A recreated activity, a second DetourDelegate or a second processLink call.
        val intent = linkIntent()

        LinkOpens.openOnce(intent, open)
        val repeat = LinkOpens.openOnce(intent, open)

        assertEquals(LinkResult.NoLink, repeat)
        assertEquals(1, opens)
    }

    @Test
    fun `does not open a copy of an opened intent`() = runTest {
        val intent = linkIntent()

        LinkOpens.openOnce(intent, open)
        val forwarded = LinkOpens.openOnce(Intent(intent), open)

        assertEquals(LinkResult.NoLink, forwarded)
        assertEquals(1, opens)
    }

    @Test
    fun `opens the link again when the user taps it again`() = runTest {
        LinkOpens.openOnce(linkIntent(), open)
        val tappedAgain = LinkOpens.openOnce(linkIntent(), open)

        assertEquals(SUCCESS, tappedAgain)
        assertEquals(2, opens)
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
            async { LinkOpens.openOnce(intent, slowOpen) },
            async { LinkOpens.openOnce(intent, slowOpen) }
        )
        runCurrent()
        clickSent.complete(Unit)

        assertEquals(listOf(SUCCESS, LinkResult.NoLink), calls.awaitAll())
    }

    @Test
    fun `delivers the link to the next caller when the first one was cancelled`() = runTest {
        val intent = linkIntent()

        // The activity is destroyed before the link is delivered.
        val destroyedCaller = launch { LinkOpens.openOnce(intent) { awaitCancellation() } }
        runCurrent()
        destroyedCaller.cancel()

        assertEquals(SUCCESS, LinkOpens.openOnce(intent, open))
    }

    @Test
    fun `does not open a link reopened from Recents`() = runTest {
        val intent = linkIntent().addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        assertEquals(LinkResult.NoLink, LinkOpens.openOnce(intent, open))
        assertEquals(0, opens)
    }

    @Test
    fun `does not open a link restored after process death`() = runTest {
        val restoredIntent = linkIntent()

        LinkOpens.markOpenedBeforeProcessDeath(restoredIntent)

        assertEquals(LinkResult.NoLink, LinkOpens.openOnce(restoredIntent, open))
        assertEquals(0, opens)
    }

    @Test
    fun `opens a pending link when the activity is recreated in the same process`() = runTest {
        val intent = linkIntent()
        val destroyedCaller = launch { LinkOpens.openOnce(intent) { awaitCancellation() } }
        runCurrent()
        destroyedCaller.cancel()

        LinkOpens.markOpenedBeforeProcessDeath(intent)

        assertEquals(SUCCESS, LinkOpens.openOnce(intent, open))
    }

    private fun linkIntent() = Intent(Intent.ACTION_VIEW, Uri.parse(LINK))

    private companion object {
        const val LINK = "https://myapp.godetour.link/abc123/product/42"
        val SUCCESS = LinkResult.Success(
            url = LINK,
            route = "/product/42",
            pathname = "/product/42",
            type = LinkType.VERIFIED
        )
    }
}
