package com.swmansion.detour.referrer

import android.content.Context
import android.net.Uri
import android.util.Log
import com.swmansion.detour.models.MetaInstallReferrer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reads the Meta Install Referrer from the Facebook, Instagram and Facebook Lite apps, as Meta
 * describes it: https://developers.facebook.com/documentation/app-ads/meta-install-referrer
 * (read 19.11.2026). Without the app, a supported app version or the app's `<queries>` entries
 * there is nothing to read, and that is not an error.
 */
internal class MetaInstallReferrerReader(
    private val query: (provider: Provider, facebookAppId: String) -> MetaInstallReferrer?,
) {
    constructor(context: Context) : this({ provider, appId -> queryProvider(context, provider, appId) })

    internal enum class Provider(val authority: String, val sourceApp: String) {
        FACEBOOK("com.facebook.katana.provider.InstallReferrerProvider", "facebook"),
        INSTAGRAM("com.instagram.contentprovider.InstallReferrerProvider", "instagram"),
        FACEBOOK_LITE("com.facebook.lite.provider.InstallReferrerProvider", "facebook_lite"),
    }

    // A query starts the Meta app if it is not running, which can take seconds on a slow phone.
    // The read runs outside the caller, so the caller stops waiting at the timeout even though a
    // blocking query cannot be cancelled.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The referrer read within [TIMEOUT_MS], or null; one read later goes to [onLate]. */
    suspend fun read(facebookAppId: String, onLate: suspend (MetaInstallReferrer) -> Unit): MetaInstallReferrer? {
        // PROVISIONAL: Meta's sample reads only the first app it finds. Each app keeps its own last
        // touch, so all are read and the latest one wins, as the last touch before the install.
        val reading = scope.async {
            Provider.entries.mapNotNull { query(it, facebookAppId) }.maxByOrNull { it.actualTimestamp }
        }
        return withTimeoutOrNull(TIMEOUT_MS) { reading.await() } ?: run {
            scope.launch { reading.await()?.let { onLate(it) } }
            null
        }
    }

    internal companion object {
        // PROVISIONAL: the deferred link waits at most 2 s for the referrer, then goes without it.
        // A Meta app that is not running took up to 0.9 s to answer on a fast phone; a referrer
        // read later is sent on its own.
        const val TIMEOUT_MS = 2_000L
        private const val TAG = "Detour"

        private fun queryProvider(context: Context, provider: Provider, facebookAppId: String): MetaInstallReferrer? = try {
            val uri = Uri.parse("content://${provider.authority}/$facebookAppId")
            val projection = arrayOf("install_referrer", "is_ct", "actual_timestamp")
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val referrer = cursor.getString(cursor.getColumnIndexOrThrow("install_referrer"))
                    ?.takeIf { it.isNotBlank() } ?: return@use null
                MetaInstallReferrer(
                    installReferrer = referrer,
                    isClickThrough = cursor.getInt(cursor.getColumnIndexOrThrow("is_ct")) == 1,
                    actualTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow("actual_timestamp")),
                    sourceApp = provider.sourceApp,
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "[Detour:ATTRIBUTION] No Meta Install Referrer from ${provider.sourceApp}: ${e.message}")
            null
        }
    }
}
