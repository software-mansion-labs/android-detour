package com.swmansion.detour.api

import com.swmansion.detour.DetourConfig
import com.swmansion.detour.models.DeterministicFingerprint
import kotlinx.coroutines.test.runTest
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
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
class DetourEndpointsTest {

    private fun config(baseUrl: String?) = DetourConfig(apiKey = "key", appId = "app", baseUrl = baseUrl)

    @Test
    fun `default base keeps production addresses`() {
        val expected = mapOf(
            DetourEndpoint.MATCH_LINK to "https://godetour.dev/api/link/match-link",
            DetourEndpoint.RESOLVE_SHORT to "https://godetour.dev/api/link/resolve-short",
            DetourEndpoint.UNIVERSAL_LINK_CLICK to "https://godetour.dev/api/link/universal-link-click",
            DetourEndpoint.ANALYTICS_EVENT to "https://godetour.dev/api/analytics/event",
            DetourEndpoint.ANALYTICS_RETENTION to "https://godetour.dev/api/analytics/retention",
        )
        assertEquals(DetourEndpoint.entries.toSet(), expected.keys)

        for ((endpoint, url) in expected) {
            assertEquals(url, DetourEndpoints.url(endpoint, config(null)))
        }
    }

    @Test
    fun `custom base is used for every endpoint`() {
        for (endpoint in DetourEndpoint.entries) {
            assertEquals(
                "https://api.example.com/${endpoint.path}",
                DetourEndpoints.url(endpoint, config("https://api.example.com"))
            )
        }
    }

    @Test
    fun `custom base keeps path and ignores trailing slash`() {
        assertEquals(
            "https://example.com/detour/api/analytics/event",
            DetourEndpoints.url(DetourEndpoint.ANALYTICS_EVENT, config("https://example.com/detour/"))
        )
    }

    @Test
    fun `non https base is rejected`() {
        assertNull(DetourEndpoints.url(DetourEndpoint.MATCH_LINK, config("http://localhost:3000")))
        assertNull(DetourEndpoints.url(DetourEndpoint.MATCH_LINK, config("ftp://example.com")))
        assertNull(DetourEndpoints.url(DetourEndpoint.MATCH_LINK, config("not a url")))
    }

    @Test
    fun `builder sets base url for java callers`() {
        val built = DetourConfig.Builder("key", "app").baseUrl("https://api.example.com").build()
        assertEquals("https://api.example.com", built.baseUrl)
    }
}

@RunWith(RobolectricTestRunner::class)
class ApiClientsBaseUrlTest {

    private val recordedUrls: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private lateinit var originalClient: OkHttpClient
    private lateinit var originalShortClient: OkHttpClient

    // Answers every call locally with 404, so no request reaches the network.
    private val recorder = Interceptor { chain ->
        recordedUrls.add(chain.request().url.toString())
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(404)
            .message("Not Found")
            .body("{}".toResponseBody(HttpClient.JSON))
            .build()
    }

    @Before
    fun setUp() {
        originalClient = HttpClient.okHttp
        originalShortClient = HttpClient.shortTimeoutOkHttp
        HttpClient.okHttp = originalClient.newBuilder().addInterceptor(recorder).build()
        HttpClient.shortTimeoutOkHttp = originalShortClient.newBuilder().addInterceptor(recorder).build()
    }

    @After
    fun tearDown() {
        HttpClient.okHttp = originalClient
        HttpClient.shortTimeoutOkHttp = originalShortClient
    }

    private suspend fun callEveryEndpoint(config: DetourConfig) {
        val apiClient = DetourApiClient(config, RuntimeEnvironment.getApplication())
        val analyticsClient = AnalyticsApiClient(config)

        apiClient.matchLink(DeterministicFingerprint(clickId = "click"))
        apiClient.resolveShortLink("https://app.godetour.link/abc")
        apiClient.sendUniversalLinkClick("https://app.godetour.link/abc")
        analyticsClient.sendEvent("test", null, "device")
        analyticsClient.sendRetentionEvent("app_open", "device")
    }

    @Test
    fun `all requests go to configured host`() = runTest {
        callEveryEndpoint(DetourConfig(apiKey = "key", appId = "app", baseUrl = "https://api.example.com"))

        assertEquals(
            DetourEndpoint.entries.map { "https://api.example.com/${it.path}" }.toSet(),
            recordedUrls.toSet()
        )
    }

    @Test
    fun `invalid base sends no requests`() = runTest {
        callEveryEndpoint(DetourConfig(apiKey = "key", appId = "app", baseUrl = "http://api.example.com"))

        assertTrue(recordedUrls.isEmpty())
    }
}
