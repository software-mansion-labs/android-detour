package com.swmansion.detour.consent

/**
 * Purpose IDs for [DetourConsent.purposes]. Strings, not an enum, so a purpose added on the
 * Detour server can be passed before the SDK knows it.
 */
object DetourPurpose {
    /** Events, sessions and conversions stored for your statistics. */
    const val ANALYTICS = "analytics"

    /** Assigning an install to a campaign. */
    const val ATTRIBUTION = "attribution"

    /** Sending install and conversion data to ad networks. */
    const val AD_NETWORK_SHARING = "ad_network_sharing"

    /** Ad personalization, for example Google's `ad_personalization`. */
    const val AD_PERSONALIZATION = "ad_personalization"
}
