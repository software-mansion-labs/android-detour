package com.swmansion.detour.models

import com.google.gson.annotations.SerializedName

/** `advertising_id` in Detour requests. */
data class AdvertisingId(
    @SerializedName("id")
    val id: String,

    @SerializedName("type")
    val type: String = "aaid",
)
