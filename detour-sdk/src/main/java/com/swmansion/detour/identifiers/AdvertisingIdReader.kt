package com.swmansion.detour.identifiers

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.identifier.AdvertisingIdClient

/**
 * Reads the AAID with `play-services-ads-identifier`, a library the app adds itself: with it comes
 * the `AD_ID` permission and the Data safety declaration, so the SDK does not add it for every app.
 */
internal class AdvertisingIdReader(private val context: Context) {

    /** The AAID, or null without the library, after the user opted out of ads personalization, or on an error. Blocks. */
    fun read(): String? {
        if (!isLibraryPresent) return null
        return try {
            val info = AdvertisingIdClient.getAdvertisingIdInfo(context)
            if (info.isLimitAdTrackingEnabled) null else normalized(info.id)
        } catch (e: Exception) {
            Log.w(TAG, "[Detour:ATTRIBUTION] Advertising ID unavailable", e)
            null
        } catch (e: LinkageError) {
            Log.w(TAG, "[Detour:ATTRIBUTION] Advertising ID unavailable", e)
            null
        }
    }

    companion object {
        // PROVISIONAL: the deferred link waits at most 2 s for the advertising ID, the same limit
        // as for the Meta Install Referrer; an ID read later goes to sdk/device-identifiers.
        const val TIMEOUT_MS = 2_000L
        private const val TAG = "Detour"
        private const val ZERO_ID = "00000000-0000-0000-0000-000000000000"

        private val isLibraryPresent: Boolean by lazy {
            runCatching { Class.forName("com.google.android.gms.ads.identifier.AdvertisingIdClient") }.isSuccess
        }

        // Android 12+ returns zeros after an opt-out.
        internal fun normalized(id: String?): String? = id?.lowercase()?.takeIf { it.isNotBlank() && it != ZERO_ID }
    }
}
