package com.swmansion.detour.utils

import android.net.Uri
import java.net.URLDecoder

/**
 * Utilities for URL parsing and route extraction.
 *
 * The "route" concept: Detour links include an app-hash as the first path segment
 * (e.g. `https://link.example.com/nkeFLNfFBf/products/123`). All parse functions
 * strip that first segment so the consumer receives `/products/123`.
 */
internal object UrlHelpers {

    private val SCHEME_PREFIX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    private val STRAY_PERCENT = Regex("%(?![0-9A-Fa-f]{2})")

    /**
     * Parse a link and extract the route for navigation.
     * Strips the first path segment (app hash) from both full URLs and path-only strings.
     *
     * Mirrors the React Native SDK's `resolveLink` + `getRestOfPath` behavior.
     *
     * Examples:
     * - `"https://example.com/hash/product/123?c=red"` → `"/product/123?c=red"`
     * - `"//example.com/hash/product/123"`               → `"/product/123"`
     * - `"/hash/product/123?c=red"`                     → `"/product/123?c=red"`
     * - `"/hash/product/123?c=red#top"`                 → `"/product/123?c=red"`
     * - `"hash/product/123"`                            → `"/product/123"`
     * - `"/hash"`                                       → `"/"`
     *
     * @param link The link to parse (full URL or path)
     * @return Extracted route, or null on blank input
     */
    internal fun parseRoute(link: String?): String? {
        if (link.isNullOrBlank()) return null

        val uri = if (isWebUrl(link)) parseLink(link) else parsePath(link)
        val cleanedPath = removeFirstPathSegment(uri.encodedPath.orEmpty())
        val query = uri.encodedQuery
        return if (query.isNullOrBlank()) cleanedPath else "$cleanedPath?$query"
    }

    /**
     * Check whether a URL uses http or https scheme.
     */
    internal fun isWebUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("//")
    }

    /**
     * Extract the navigation route from a custom-scheme deep link.
     * Mirrors the RN SDK's `getRouteFromDeepLink()`.
     *
     * Examples:
     * - `myapp://product/123?color=red` → `"/product/123?color=red"`
     * - `myapp:product/123?color=red`   → `"/product/123?color=red"`
     */
    internal fun getRouteFromDeepLink(uri: Uri): String {
        // Without "//" Uri is opaque and has no host, path or query, so read the raw part instead.
        if (uri.isOpaque) return "/${uri.encodedSchemeSpecificPart}"

        // Uri.getHost() decodes the host despite its docs. Use the raw authority without
        // user info instead, like RN's URL.host.
        val host = uri.encodedAuthority.orEmpty().substringAfterLast('@')
        val path = uri.encodedPath.orEmpty()
        val query = uri.encodedQuery

        val hostPart = if (host.isBlank()) "" else "/$host"
        val pathPart = when {
            path.isBlank() -> ""
            path.startsWith("/") -> path
            else -> "/$path"
        }
        val baseRoute = if (hostPart.isBlank() && pathPart.isBlank()) "/" else "$hostPart$pathPart"

        return if (query.isNullOrBlank()) {
            baseRoute
        } else {
            "$baseRoute?$query"
        }
    }

    /**
     * Check whether a URI has exactly one path segment (indicating a short link).
     */
    internal fun isSingleSegmentPath(uri: Uri): Boolean {
        return uri.pathSegments.size == 1
    }

    /**
     * Parse query parameters from a URL or route string into a map.
     *
     * Example: `"/products/123?color=red&size=L#top"` → `{color=red, size=L}`
     */
    internal fun parseQueryParams(url: String): Map<String, String> {
        val uri = parseLink(url)
        // Uri has no query for opaque URIs like "myapp:product?x=1", but RN reads one there.
        val query = if (uri.isOpaque) {
            uri.encodedSchemeSpecificPart.substringAfter('?', "")
        } else {
            uri.encodedQuery.orEmpty()
        }
        if (query.isBlank()) return emptyMap()

        return query.split('&').mapNotNull { param ->
            val eqIndex = param.indexOf('=')
            if (eqIndex >= 0) {
                val key = decodeQueryComponent(param.substring(0, eqIndex))
                val value = decodeQueryComponent(param.substring(eqIndex + 1))
                key to value
            } else if (param.isNotBlank()) {
                decodeQueryComponent(param) to ""
            } else {
                null
            }
        }.toMap()
    }

    // URLDecoder throws on a '%' that doesn't start an escape (e.g. "50%off"). URLSearchParams
    // keeps such a '%' as text, so escape it first. The catch is a fallback for anything else.
    private fun decodeQueryComponent(value: String): String =
        try {
            URLDecoder.decode(value.replace(STRAY_PERCENT, "%25"), "UTF-8")
        } catch (e: IllegalArgumentException) {
            value
        }

    /**
     * Extract the pathname (path without query string) from a route.
     *
     * Example: `"/products/123?color=red"` → `"/products/123"`
     */
    internal fun extractPathname(route: String?): String {
        if (route.isNullOrBlank()) return "/"
        val queryIndex = route.indexOf('?')
        return if (queryIndex >= 0) route.substring(0, queryIndex) else route
    }

    // Accepts a full URL (any scheme), a "//host" link or a path-only string.
    private fun parseLink(link: String): Uri = when {
        link.startsWith("//") -> Uri.parse("https:$link")
        SCHEME_PREFIX.containsMatchIn(link) -> Uri.parse(link)
        else -> parsePath(link)
    }

    // Uri.parse reads everything before the first ':' as the scheme, even in
    // "/hash/p?redirect=https://x". A placeholder origin keeps that ':' in the path or query.
    private fun parsePath(path: String): Uri =
        Uri.parse("https://x" + if (path.startsWith("/")) path else "/$path")

    /**
     * Remove the first path segment (app hash) from a pathname.
     * Mirrors the RN SDK's `getRestOfPath()`.
     *
     * Examples:
     * - `"/hash/product/123"` → `"/product/123"`
     * - `"/hash"`             → `"/"`
     * - `"/"`                 → `"/"`
     */
    private fun removeFirstPathSegment(path: String): String {
        if (path.isBlank() || path == "/") return "/"

        val secondSlashIndex = path.indexOf('/', 1)
        return if (secondSlashIndex == -1) "/" else path.substring(secondSlashIndex)
    }
}
