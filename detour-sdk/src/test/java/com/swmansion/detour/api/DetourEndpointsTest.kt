package com.swmansion.detour.api

import com.swmansion.detour.DetourAttributionConfig
import com.swmansion.detour.DetourConfig
import com.swmansion.detour.analytics.AnalyticsRequest
import com.swmansion.detour.analytics.DetourConversion
import com.swmansion.detour.analytics.RevenueType
import com.swmansion.detour.analytics.SessionManager
import com.swmansion.detour.consent.DetourConsent
import com.swmansion.detour.consent.DetourPurpose
import com.swmansion.detour.consent.GateDecision
import com.swmansion.detour.models.DirectMatchRequest
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
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
import java.math.BigDecimal
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
            DetourEndpoint.ANALYTICS_SESSION to "https://godetour.dev/api/analytics/session",
            DetourEndpoint.ANALYTICS_CONVERSION to "https://godetour.dev/api/analytics/conversion",
            DetourEndpoint.SDK_CONFIG to "https://godetour.dev/api/sdk/config",
            DetourEndpoint.SDK_CONSENT to "https://godetour.dev/api/sdk/consent",
            DetourEndpoint.SDK_DISABLE to "https://godetour.dev/api/sdk/disable",
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
    fun `requests report mmp only with attribution set and the attribution gate open`() = runTest {
        suspend fun packageHeader(config: DetourConfig, decision: suspend () -> GateDecision): String? {
            HttpClient.attributionDecision = decision
            return Request.Builder().url("https://api.example.com").detourHeaders(config).build().header("X-SDK-Package")
        }
        val attribution = DetourConfig.Builder("key", "app").attribution(DetourAttributionConfig()).build()

        try {
            assertEquals("core", packageHeader(config(null)) { GateDecision.SEND })
            assertEquals("mmp", packageHeader(attribution) { GateDecision.SEND })
            assertEquals("core", packageHeader(attribution) { GateDecision.WAIT })
            assertEquals("core", packageHeader(attribution) { error("storage failed") })
        } finally {
            HttpClient.attributionDecision = { GateDecision.DROP }
        }
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
        val app = RuntimeEnvironment.getApplication()
        val requestContext = RequestContextProvider(app)
        val apiClient = DetourApiClient(config, app, requestContext)
        val analyticsClient = AnalyticsApiClient(config, requestContext)

        apiClient.matchLink(DirectMatchRequest(clickId = "click"))
        apiClient.resolveShortLink("https://app.godetour.link/abc")
        apiClient.sendUniversalLinkClick("https://app.godetour.link/abc")
        SdkApiClient(config).fetchConfig(etag = null)
        SdkApiClient(config).sendConsent(DetourConsent(mapOf(DetourPurpose.ANALYTICS to true)), null, "device", null)
        SdkApiClient(config).sendKillSwitch(enabled = false, atMs = 0, deviceId = "device")
        listOf(
            AnalyticsRequest.Event("test", null, 0),
            AnalyticsRequest.Retention("app_open", 0),
            AnalyticsRequest.Session(SessionManager.Event.Start("8c1d2e3f-4a5b-4c6d-8e7f-901a2b3c4d5e", 0)),
            AnalyticsRequest.Conversion(DetourConversion("purchase", BigDecimal("4.99"), "USD", RevenueType.IAP), 0),
        ).forEach { analyticsClient.send(it, "device", null) }
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
