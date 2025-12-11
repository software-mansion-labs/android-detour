package com.detour.sdk.utils

import android.util.Log
import java.net.URL

/**
 * Utilities for URL parsing and route extraction.
 */
internal object UrlHelpers {

    private const val TAG = "UrlHelpers"

    /**
     * Parse link and extract route for navigation.
     * Removes the first path segment (app hash) from full URLs.
     *
     * Examples:
     * - "https://example.com/app-hash/product/123" -> "/product/123"
     * - "/app-hash/product/123" -> "/product/123"
     * - "/product/123" -> "/product/123"
     *
     * @param link The link to parse
     * @return Extracted route or null
     */
    internal fun parseRoute(link: String?): String? {
        if (link.isNullOrBlank()) {
            return null
        }

        return try {
            when {
                link.startsWith("http://") || link.startsWith("https://") || link.startsWith("//") -> {
                    val url = if (link.startsWith("//")) {
                        URL("https:$link")
                    } else {
                        URL(link)
                    }

                    val path = url.path
                    val query = url.query

                    // Remove first path segment (app hash)
                    val cleanedPath = removeFirstPathSegment(path)

                    if (query.isNullOrBlank()) {
                        cleanedPath
                    } else {
                        "$cleanedPath?$query"
                    }
                }
                else -> {
                    // Treat as pathname
                    if (link.startsWith("/")) link else "/$link"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[Detour:TYPE_ERROR] Error parsing URL", e)
            null
        }
    }

    private fun removeFirstPathSegment(path: String): String {
        if (path.isBlank() || path == "/") {
            return "/"
        }

        val segments = path.split("/").filter { it.isNotBlank() }

        return if (segments.size > 1) {
            "/" + segments.drop(1).joinToString("/")
        } else {
            "/"
        }
    }
}
