package com.swmansion.detour.utils

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UrlHelpersTest {

    // --- parseRoute: full URLs ---

    @Test
    fun `parseRoute - full URL strips first path segment (app hash)`() {
        val result = UrlHelpers.parseRoute("https://example.com/app-hash/product/123")
        assertEquals("/product/123", result)
    }

    @Test
    fun `parseRoute - protocol-relative URL strips first segment`() {
        val result = UrlHelpers.parseRoute("//example.com/app-hash/product/123")
        assertEquals("/product/123", result)
    }

    @Test
    fun `parseRoute - full URL with query params`() {
        val result = UrlHelpers.parseRoute("https://example.com/app-hash/product/123?color=red&size=L")
        assertEquals("/product/123?color=red&size=L", result)
    }

    @Test
    fun `parseRoute - uppercase scheme is parsed as full URL`() {
        val result = UrlHelpers.parseRoute("HTTPS://example.com/app-hash/product/123?color=red")
        assertEquals("/product/123?color=red", result)
    }

    @Test
    fun `parseRoute - full URL drops fragment`() {
        val result = UrlHelpers.parseRoute("https://example.com/app-hash/product/123?color=red#section")
        assertEquals("/product/123?color=red", result)
    }

    @Test
    fun `parseRoute - single segment URL path returns root`() {
        val result = UrlHelpers.parseRoute("https://example.com/app-hash")
        assertEquals("/", result)
    }

    // --- parseRoute: path-only strings (also strip first segment) ---

    @Test
    fun `parseRoute - path with leading slash strips first segment`() {
        val result = UrlHelpers.parseRoute("/app-hash/product/123")
        assertEquals("/product/123", result)
    }

    @Test
    fun `parseRoute - path without leading slash strips first segment`() {
        val result = UrlHelpers.parseRoute("app-hash/product/123")
        assertEquals("/product/123", result)
    }

    @Test
    fun `parseRoute - path with query strips first segment and preserves query`() {
        val result = UrlHelpers.parseRoute("/app-hash/product/123?color=red")
        assertEquals("/product/123?color=red", result)
    }

    @Test
    fun `parseRoute - path drops fragment`() {
        assertEquals("/product/123?color=red", UrlHelpers.parseRoute("/app-hash/product/123?color=red#section"))
        assertEquals("/product/123", UrlHelpers.parseRoute("/app-hash/product/123#section"))
    }

    @Test
    fun `parseRoute - colon in path-only string stays in path or query`() {
        assertEquals("/p?redirect=https://x.com/y", UrlHelpers.parseRoute("/hash/p?redirect=https://x.com/y"))
        assertEquals("/product:1?x=1", UrlHelpers.parseRoute("/hash/product:1?x=1"))
        assertEquals("/time/12:30?x=1", UrlHelpers.parseRoute("hash/time/12:30?x=1"))
        assertEquals("/urn:isbn:123", UrlHelpers.parseRoute("/hash/urn:isbn:123"))
        assertEquals("/p?redirect=https://x.com/y", UrlHelpers.parseRoute("//example.com/hash/p?redirect=https://x.com/y"))
    }

    @Test
    fun `parseRoute - single segment path returns root`() {
        val result = UrlHelpers.parseRoute("/app-hash")
        assertEquals("/", result)
    }

    @Test
    fun `parseRoute - blank input returns null`() {
        assertNull(UrlHelpers.parseRoute(""))
        assertNull(UrlHelpers.parseRoute("   "))
        assertNull(UrlHelpers.parseRoute(null))
    }

    // --- isWebUrl ---

    @Test
    fun `isWebUrl - http scheme`() {
        assertTrue(UrlHelpers.isWebUrl("http://example.com"))
    }

    @Test
    fun `isWebUrl - https scheme`() {
        assertTrue(UrlHelpers.isWebUrl("https://example.com"))
    }

    @Test
    fun `isWebUrl - protocol relative`() {
        assertTrue(UrlHelpers.isWebUrl("//example.com/path"))
    }

    @Test
    fun `isWebUrl - custom scheme returns false`() {
        assertFalse(UrlHelpers.isWebUrl("myapp://product/123"))
    }

    @Test
    fun `isWebUrl - plain path returns false`() {
        assertFalse(UrlHelpers.isWebUrl("/product/123"))
    }

    // --- isSingleSegmentPath ---

    @Test
    fun `isSingleSegmentPath - single segment`() {
        val uri = Uri.parse("https://example.com/abc123")
        assertTrue(UrlHelpers.isSingleSegmentPath(uri))
    }

    @Test
    fun `isSingleSegmentPath - multi segment`() {
        val uri = Uri.parse("https://example.com/app-hash/product/123")
        assertFalse(UrlHelpers.isSingleSegmentPath(uri))
    }

    @Test
    fun `isSingleSegmentPath - empty path`() {
        val uri = Uri.parse("https://example.com")
        assertFalse(UrlHelpers.isSingleSegmentPath(uri))
    }

    // --- getRouteFromDeepLink ---

    @Test
    fun `getRouteFromDeepLink - extracts host and path`() {
        val uri = Uri.parse("myapp://product/123")
        assertEquals("/product/123", UrlHelpers.getRouteFromDeepLink(uri))
    }

    @Test
    fun `getRouteFromDeepLink - includes query params`() {
        val uri = Uri.parse("myapp://product/123?color=red")
        assertEquals("/product/123?color=red", UrlHelpers.getRouteFromDeepLink(uri))
    }

    @Test
    fun `getRouteFromDeepLink - host only`() {
        val uri = Uri.parse("myapp://home")
        assertEquals("/home", UrlHelpers.getRouteFromDeepLink(uri))
    }

    @Test
    fun `getRouteFromDeepLink - hostless URI keeps single leading slash`() {
        val uri = Uri.parse("myapp:///product/123?color=red")
        assertEquals("/product/123?color=red", UrlHelpers.getRouteFromDeepLink(uri))
    }

    @Test
    fun `getRouteFromDeepLink - keeps percent-encoded delimiters encoded`() {
        val uri = Uri.parse("myapp://product/a%3Fb?q=rock%26roll")
        val route = UrlHelpers.getRouteFromDeepLink(uri)
        assertEquals("/product/a%3Fb?q=rock%26roll", route)
        assertEquals("/product/a%3Fb", UrlHelpers.extractPathname(route))
    }

    @Test
    fun `getRouteFromDeepLink - opaque URI without slashes`() {
        val uri = Uri.parse("myapp:product/123?x=1#y")
        assertEquals("/product/123?x=1", UrlHelpers.getRouteFromDeepLink(uri))
    }

    @Test
    fun `getRouteFromDeepLink - keeps encoded host encoded and drops user info`() {
        val route = UrlHelpers.getRouteFromDeepLink(Uri.parse("myapp://a%3Fb/x?y=1"))
        assertEquals("/a%3Fb/x?y=1", route)
        assertEquals("/a%3Fb/x", UrlHelpers.extractPathname(route))
        assertEquals("/product/x", UrlHelpers.getRouteFromDeepLink(Uri.parse("myapp://user@product/x")))
    }

    @Test
    fun `getRouteFromDeepLink - drops fragment`() {
        val uri = Uri.parse("myapp://product/1?x=1#y")
        assertEquals("/product/1?x=1", UrlHelpers.getRouteFromDeepLink(uri))
    }

    // --- parseQueryParams ---

    @Test
    fun `parseQueryParams - extracts key-value pairs`() {
        val params = UrlHelpers.parseQueryParams("/products/123?color=red&size=L")
        assertEquals(mapOf("color" to "red", "size" to "L"), params)
    }

    @Test
    fun `parseQueryParams - returns empty map for no query`() {
        val params = UrlHelpers.parseQueryParams("/products/123")
        assertEquals(emptyMap<String, String>(), params)
    }

    @Test
    fun `parseQueryParams - handles encoded values`() {
        val params = UrlHelpers.parseQueryParams("/search?q=hello+world&lang=en")
        assertEquals("hello world", params["q"])
        assertEquals("en", params["lang"])
    }

    @Test
    fun `parseQueryParams - handles full URL`() {
        val params = UrlHelpers.parseQueryParams("https://example.com/hash/products/123?id=42")
        assertEquals(mapOf("id" to "42"), params)
    }

    @Test
    fun `parseQueryParams - ignores fragment after query`() {
        val params = UrlHelpers.parseQueryParams("https://acme.godetour.link/abc/promo?id=5#section")
        assertEquals(mapOf("id" to "5"), params)
    }

    @Test
    fun `parseQueryParams - keeps encoded hash in value`() {
        val params = UrlHelpers.parseQueryParams("https://example.com/abc/p?q=a%23b#frag")
        assertEquals(mapOf("q" to "a#b"), params)
    }

    @Test
    fun `parseQueryParams - custom scheme URL ignores fragment`() {
        val params = UrlHelpers.parseQueryParams("myapp://product/1?x=1#y")
        assertEquals(mapOf("x" to "1"), params)
    }

    @Test
    fun `parseQueryParams - URL with only a fragment returns empty map`() {
        val empty = emptyMap<String, String>()
        assertEquals(empty, UrlHelpers.parseQueryParams("https://acme.godetour.link/abc/promo#detour_open_app=true"))
        assertEquals(empty, UrlHelpers.parseQueryParams("https://acme.godetour.link/abc/promo#section?id=5"))
    }

    @Test
    fun `parseQueryParams - param with empty key maps value to empty key`() {
        val params = UrlHelpers.parseQueryParams("https://example.com/abc/p?=5&id=1")
        assertEquals(mapOf("" to "5", "id" to "1"), params)
    }

    @Test
    fun `parseQueryParams - colon in path-only string stays in path or query`() {
        assertEquals(mapOf("redirect" to "https://x.com/y"), UrlHelpers.parseQueryParams("/hash/p?redirect=https://x.com/y"))
        assertEquals(mapOf("x" to "1"), UrlHelpers.parseQueryParams("/hash/product:1?x=1"))
        assertEquals(mapOf("x" to "1"), UrlHelpers.parseQueryParams("hash/time/12:30?x=1"))
        assertEquals(emptyMap<String, String>(), UrlHelpers.parseQueryParams("/hash/urn:isbn:123"))
        assertEquals(mapOf("redirect" to "https://x.com/y"), UrlHelpers.parseQueryParams("//example.com/hash/p?redirect=https://x.com/y"))
    }

    @Test
    fun `parseQueryParams - opaque scheme URI without slashes`() {
        val params = UrlHelpers.parseQueryParams("myapp:product/123?x=1#section")
        assertEquals(mapOf("x" to "1"), params)
    }

    @Test
    fun `parseQueryParams - keeps raw value when percent-encoding is malformed`() {
        val params = UrlHelpers.parseQueryParams("https://acme.godetour.link/abc/promo?utm_content=50%off&id=5")
        assertEquals(mapOf("utm_content" to "50%off", "id" to "5"), params)
    }

    @Test
    fun `parseQueryParams - decodes the rest of a value with a stray percent sign`() {
        val params = UrlHelpers.parseQueryParams("https://example.com/abc/p?a=hello+50%off&b=100%25%off")
        assertEquals(mapOf("a" to "hello 50%off", "b" to "100%%off"), params)
    }

    // --- extractPathname ---

    @Test
    fun `extractPathname - removes query string`() {
        assertEquals("/products/123", UrlHelpers.extractPathname("/products/123?color=red"))
    }

    @Test
    fun `extractPathname - no query string returns as-is`() {
        assertEquals("/products/123", UrlHelpers.extractPathname("/products/123"))
    }

    @Test
    fun `extractPathname - blank input returns root`() {
        assertEquals("/", UrlHelpers.extractPathname(null))
        assertEquals("/", UrlHelpers.extractPathname(""))
    }
}
