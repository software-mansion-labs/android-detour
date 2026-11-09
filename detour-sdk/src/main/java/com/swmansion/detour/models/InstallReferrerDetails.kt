package com.swmansion.detour.models

import com.google.gson.annotations.SerializedName

/**
 * Google Play Install Referrer data sent as `install_referrer` in the match request.
 * Timestamps are Unix seconds as the Play API returns them; `0` means not available.
 */
data class InstallReferrerDetails(
    @SerializedName("raw")
    val raw: String,

    @SerializedName("utm")
    val utm: ReferrerUtm?,

    // PROVISIONAL: the gclid is only sent; the backend keeps it, so the SDK never stores it.
    @SerializedName("gclid")
    val gclid: String?,

    @SerializedName("click_ts")
    val clickTs: Long,

    @SerializedName("install_begin_ts")
    val installBeginTs: Long,

    @SerializedName("click_ts_server")
    val clickTsServer: Long,

    @SerializedName("install_begin_ts_server")
    val installBeginTsServer: Long,

    @SerializedName("google_play_instant")
    val googlePlayInstant: Boolean,

    // Sent as the top-level clickId, not inside install_referrer.
    @Transient
    val clickId: String? = null
)

data class ReferrerUtm(
    @SerializedName("source")
    val source: String?,

    @SerializedName("medium")
    val medium: String?,

    @SerializedName("campaign")
    val campaign: String?,

    @SerializedName("content")
    val content: String?,

    @SerializedName("term")
    val term: String?
)
