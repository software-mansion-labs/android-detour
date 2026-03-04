package com.detour.sdk

import com.detour.sdk.models.LinkProcessingMode
import com.detour.sdk.storage.DetourStorage

/**
 * Configuration for Detour SDK.
 *
 * @property apiKey Your API key from the Detour dashboard
 * @property appId Your application ID from the Detour dashboard
 * @property shouldUseClipboard Whether to check clipboard for links on first launch (default: true)
 * @property linkProcessingMode Controls which link sources are handled (default: [LinkProcessingMode.ALL])
 * @property storage Custom storage implementation for persisting SDK data.
 *                   Defaults to SharedPreferences-based storage.
 */
data class DetourConfig(
    val apiKey: String,
    val appId: String,
    val shouldUseClipboard: Boolean = true,
    val linkProcessingMode: LinkProcessingMode = LinkProcessingMode.ALL,
    val storage: DetourStorage? = null
) {
    init {
        require(apiKey.isNotBlank()) { "apiKey cannot be blank" }
        require(appId.isNotBlank()) { "appId cannot be blank" }
    }

    /**
     * Builder for Java callers (data class defaults don't work from Java).
     *
     * ```java
     * DetourConfig config = new DetourConfig.Builder("apiKey", "appId")
     *     .shouldUseClipboard(false)
     *     .linkProcessingMode(LinkProcessingMode.WEB_ONLY)
     *     .build();
     * ```
     */
    class Builder(private val apiKey: String, private val appId: String) {
        private var shouldUseClipboard: Boolean = true
        private var linkProcessingMode: LinkProcessingMode = LinkProcessingMode.ALL
        private var storage: DetourStorage? = null

        fun shouldUseClipboard(value: Boolean) = apply { shouldUseClipboard = value }
        fun linkProcessingMode(value: LinkProcessingMode) = apply { linkProcessingMode = value }
        fun storage(value: DetourStorage) = apply { storage = value }

        fun build(): DetourConfig = DetourConfig(
            apiKey = apiKey,
            appId = appId,
            shouldUseClipboard = shouldUseClipboard,
            linkProcessingMode = linkProcessingMode,
            storage = storage
        )
    }
}
