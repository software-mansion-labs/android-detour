package com.swmansion.detour.analytics

import android.util.Log
import com.swmansion.detour.storage.DetourStorage
import com.swmansion.detour.storage.StorageKeys

// The opening link lives in memory, for this run only; the install link is
// stored for good.
internal object LinkAttribution {
    private const val TAG = "LinkAttribution"

    private var storage: DetourStorage? = null
    private var installClickId: String? = null
    private var installClickIdLoaded = false
    private var openClickId: String? = null
    private var openType = "organic"

    fun initialize(storage: DetourStorage) {
        this.storage = storage
    }

    suspend fun recordDeferredOpen(clickId: String) {
        synchronized(this) {
            installClickId = clickId
            installClickIdLoaded = true
            openClickId = clickId
            openType = "deferred"
        }
        try {
            storage?.setItem(StorageKeys.INSTALL_CLICK_ID, clickId)
        } catch (e: Exception) {
            Log.w(TAG, "[Detour:STORAGE_ERROR] Failed to save the install link", e)
        }
    }

    @Synchronized
    fun recordLinkOpen(clickId: String) {
        openClickId = clickId
        openType = "verified"
    }

    @Synchronized
    fun recordBlockedLinkOpen() {
        openClickId = null
        openType = "verified"
    }

    // Custom schemes also carry OAuth returns, so they never replace a Detour link.
    @Synchronized
    fun recordSchemeOpen() {
        if (openClickId == null) openType = "scheme"
    }

    suspend fun payload(): Map<String, String?> {
        if (!installClickIdLoaded) {
            val stored = try {
                storage?.getItem(StorageKeys.INSTALL_CLICK_ID)
            } catch (e: Exception) {
                null
            }
            synchronized(this) {
                if (!installClickIdLoaded) {
                    installClickId = stored
                    installClickIdLoaded = true
                }
            }
        }
        return synchronized(this) {
            mapOf(
                "install_click_id" to installClickId,
                "open_click_id" to openClickId,
                "open_type" to openType
            )
        }
    }
}
