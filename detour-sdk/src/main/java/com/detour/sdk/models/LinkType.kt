package com.detour.sdk.models

/**
 * Type of deep link processed by Detour SDK.
 */
enum class LinkType {
    /**
     * Deferred deep link - user clicked link before app was installed.
     * Matched via API call with device fingerprint.
     */
    DEFERRED,
    
    /**
     * Universal App Link - user clicked link with app already installed.
     * Handled directly by Android Intent system.
     */
    UNIVERSAL
}
