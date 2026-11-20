package com.swmansion.detour.models

import com.google.gson.annotations.SerializedName

// PROVISIONAL: the field names until the contract defines meta_install_referrer.
/**
 * Meta Install Referrer data sent as `meta_install_referrer` in the match request. The referrer
 * stays encrypted; the backend decrypts it with the app's key.
 */
data class MetaInstallReferrer(
    @SerializedName("install_referrer")
    val installReferrer: String,

    /** True for a click, false for an ad view. */
    @SerializedName("is_ct")
    val isClickThrough: Boolean,

    /** Unix seconds of the click or view. */
    @SerializedName("actual_timestamp")
    val actualTimestamp: Long,

    /** `facebook`, `instagram` or `facebook_lite`. */
    @SerializedName("source_app")
    val sourceApp: String,
)
