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

        // A leading slash keeps a path-only string from being read as "scheme:..." by Uri.
        val uri = Uri.parse(if (isWebUrl(link) || link.startsWith("/")) link else "/$link")
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

        val host = uri.host.orEmpty()
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
        val uri = Uri.parse(url)
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

    // URLDecoder throws on a stray '%' (e.g. "50%off"). Keep the raw text, as URLSearchParams
    // does, so one bad param doesn't fail the whole link.
    private fun decodeQueryComponent(value: String): String =
        try {
            URLDecoder.decode(value, "UTF-8")
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
