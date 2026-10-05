package com.swmansion.detour.analytics

// In memory on purpose: a cold start without a link starts unattributed.
internal object SessionAttribution {
    @Volatile
    var clickId: String? = null
        private set

    fun setClickId(clickId: String) {
        this.clickId = clickId
    }
}
