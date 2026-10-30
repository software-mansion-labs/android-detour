package com.swmansion.detour

import com.swmansion.detour.models.LinkProcessingMode
import com.swmansion.detour.storage.DetourStorage

/**
 * Configuration for Detour SDK.
 *
 * @property apiKey Your API key from the Detour dashboard
 * @property appId Your application ID from the Detour dashboard
 * @property linkProcessingMode Controls which link sources are handled (default: [LinkProcessingMode.ALL])
 * @property storage Custom storage implementation for persisting SDK data.
 *                   Defaults to SharedPreferences-based storage.
 * @property baseUrl Detour API host for tests and non-production environments (https only).
 *                   `null` uses the production Detour API.
 * @property userIdValidator Called on [Detour.setUserId]; returning `false` drops the value and clears
 *                   the stored one. Use it to reject values that must not leave the device, such as
 *                   e-mail addresses or phone numbers.
 */
data class DetourConfig(
    val apiKey: String,
    val appId: String,
    val linkProcessingMode: LinkProcessingMode = LinkProcessingMode.ALL,
    val storage: DetourStorage? = null,
    val baseUrl: String? = null,
    val userIdValidator: ((String) -> Boolean)? = null
) {
    /**
     * Builder for Java callers (data class defaults don't work from Java).
     *
     * ```java
     * DetourConfig config = new DetourConfig.Builder("apiKey", "appId")
     *     .linkProcessingMode(LinkProcessingMode.WEB_ONLY)
     *     .build();
     * ```
     */
    class Builder(private val apiKey: String, private val appId: String) {
        private var linkProcessingMode: LinkProcessingMode = LinkProcessingMode.ALL
        private var storage: DetourStorage? = null
        private var baseUrl: String? = null
        private var userIdValidator: ((String) -> Boolean)? = null

        fun linkProcessingMode(value: LinkProcessingMode) = apply { linkProcessingMode = value }
        fun storage(value: DetourStorage) = apply { storage = value }
        fun baseUrl(value: String) = apply { baseUrl = value }
        fun userIdValidator(value: (String) -> Boolean) = apply { userIdValidator = value }

        fun build(): DetourConfig = DetourConfig(
            apiKey = apiKey,
            appId = appId,
            linkProcessingMode = linkProcessingMode,
            storage = storage,
            baseUrl = baseUrl,
            userIdValidator = userIdValidator
        )
    }
}
