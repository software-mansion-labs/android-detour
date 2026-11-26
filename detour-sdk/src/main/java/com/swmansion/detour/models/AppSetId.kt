package com.swmansion.detour.models

import com.google.gson.annotations.SerializedName

/** `app_set_id` in Detour requests. */
data class AppSetId(
    @SerializedName("id")
    val id: String,

    /** `app` or `developer`. */
    @SerializedName("scope")
    val scope: String,
)
