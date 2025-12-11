package com.detour.sdk.models

/**
 * Result of link processing (deferred or universal).
 */
sealed class LinkResult {
    /**
     * Link successfully processed.
     * 
     * @property link Full URL that was matched
     * @property route Custom data extracted for navigation (path + query after hash)
     * @property type Source of the link (DEFERRED or UNIVERSAL)
     */
    data class Success(
        val link: String,
        val route: String?,
        val type: LinkType
    ) : LinkResult()
    
    /**
     * No deferred link available - not first launch.
     * This is returned when getDeferredLink() is called on subsequent app launches.
     */
    data object NotFirstLaunch : LinkResult()
    
    /**
     * No link found - normal app launch without any deep link.
     */
    data object NoLink : LinkResult()
    
    /**
     * Error occurred during link processing.
     * 
     * @property exception The exception that occurred
     */
    data class Error(val exception: Exception) : LinkResult()
}
