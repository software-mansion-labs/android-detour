package com.swmansion.detour.links

import com.swmansion.detour.Detour
import com.swmansion.detour.DetourAttributionConfig
import com.swmansion.detour.DetourConfig
import com.swmansion.detour.api.HttpClient
import com.swmansion.detour.consent.DetourConsent
import com.swmansion.detour.consent.DetourPurpose
import com.swmansion.detour.models.LinkProcessingMode
import com.swmansion.detour.models.LinkResult
import java.util.Collections
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// runBlocking, not runTest: the SDK works on its own dispatchers in real time.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeferredLinkConsentTest {
    private val matchRequests: MutableList<String?> = Collections.synchronizedList(mutableListOf())
    private lateinit var originalClient: OkHttpClient

    // Answers every call locally; match-link finds a link.
    private val stub = Interceptor { chain ->
        val request = chain.request()
        val isMatch = request.url.encodedPath.endsWith("match-link")
        if (isMatch) matchRequests += request.header("X-SDK-Package")
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(if (isMatch) 200 else 404)
            .message("stub")
            .body((if (isMatch) """{"link":"https://app.godetour.link/product/1"}""" else "{}").toResponseBody(HttpClient.JSON))
            .build()
    }

    @Before
    fun setUp() {
        originalClient = HttpClient.okHttp
        HttpClient.okHttp = originalClient.newBuilder().addInterceptor(stub).build()
    }

    @After
    fun tearDown() {
        HttpClient.okHttp = originalClient
    }

    @Test
    fun `the first match waits for the attribution decision and survives a recreated caller`() = runBlocking {
        Detour.configure(
            RuntimeEnvironment.getApplication(),
            DetourConfig(
                apiKey = "key",
                appId = "app",
                linkProcessingMode = LinkProcessingMode.DEFERRED_ONLY,
                attribution = DetourAttributionConfig(),
            ),
        )
        var reports = 0
        var deliveredToFirstActivity: LinkResult? = null
        val firstActivity = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            deliveredToFirstActivity = Detour.getDeferredLink(onReadyForUI = { reports++ })
        }
        withTimeout(5_000) { while (reports == 0) delay(10) }
        delay(1_500)
        // The caller knows the link is processed, but nothing is matched before the decision.
        assertEquals(1, reports)
        assertTrue(matchRequests.isEmpty())

        // The Activity is recreated while the match waits for consent.
        firstActivity.cancel()
        val recreated = async(Dispatchers.Default) { Detour.getDeferredLink() }
        delay(100)
        Detour.setConsent(DetourConsent(purposes = mapOf(DetourPurpose.ATTRIBUTION to true)))

        val result = withTimeout(10_000) { recreated.await() }
        assertTrue(result is LinkResult.Success)
        assertEquals(listOf("mmp"), matchRequests.toList())
        // The result is delivered once in the session.
        assertEquals(LinkResult.NotFirstLaunch, Detour.getDeferredLink())
        // The cancelled caller gets no result, not even an error.
        assertNull(deliveredToFirstActivity)
    }
}
