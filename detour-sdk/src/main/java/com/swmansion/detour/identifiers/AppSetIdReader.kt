package com.swmansion.detour.identifiers

import android.content.Context
import android.util.Log
import com.google.android.gms.appset.AppSet
import com.google.android.gms.appset.AppSetIdInfo
import com.google.android.gms.tasks.Tasks
import com.swmansion.detour.models.AppSetId
import java.util.concurrent.TimeUnit

/**
 * Reads the App Set ID with `play-services-appset`, a library the app adds itself, so an app
 * without Google Play services or without the library gets nothing.
 */
internal class AppSetIdReader(private val context: Context) {

    /** The App Set ID, or null without the library or on an error. Blocks. */
    fun read(): AppSetId? {
        if (!isLibraryPresent) return null
        return try {
            // With a limit: a Play services that never answers would block an IO thread for good.
            val info = Tasks.await(AppSet.getClient(context).appSetIdInfo, AdvertisingIdReader.TIMEOUT_MS, TimeUnit.MILLISECONDS)
            info.id.takeIf { it.isNotBlank() }?.let { AppSetId(it.lowercase(), scopeName(info.scope)) }
        } catch (e: Exception) {
            Log.w(TAG, "[Detour:ATTRIBUTION] App Set ID unavailable", e)
            null
        } catch (e: LinkageError) {
            Log.w(TAG, "[Detour:ATTRIBUTION] App Set ID unavailable", e)
            null
        }
    }

    companion object {
        private const val TAG = "Detour"

        private val isLibraryPresent: Boolean by lazy {
            runCatching { Class.forName("com.google.android.gms.appset.AppSet") }.isSuccess
        }

        internal fun scopeName(scope: Int): String =
            if (scope == AppSetIdInfo.SCOPE_DEVELOPER) "developer" else "app"
    }
}
