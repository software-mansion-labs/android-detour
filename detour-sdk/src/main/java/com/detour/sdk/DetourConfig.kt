package com.detour.sdk

/**
 * Configuration for Detour SDK.
 *
 * @property appId Your application ID from the Detour dashboard
 * @property apiKey Your API key from the Detour dashboard
 * @property shouldUseClipboard Whether to check clipboard for links on first launch (default: true)
 */
data class DetourConfig(
    val apiKey: String,
    val appId: String,
    val shouldUseClipboard: Boolean = true
) {
    init {
        require(apiKey.isNotBlank()) { "apiKey cannot be blank" }
        require(appId.isNotBlank()) { "appId cannot be blank" }
    }
}
