package com.detour.sdk.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.content.edit

/**
 * Detects if this is the first app launch.
 */
internal class FirstLaunchDetector(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Check if this is the first app launch.
     */
    suspend fun isFirstLaunch(): Boolean = withContext(Dispatchers.IO) {
        !prefs.getBoolean(FIRST_ENTRANCE_FLAG, false)
    }

    /**
     * Mark that the app has been launched.
     */
    suspend fun markAsLaunched() = withContext(Dispatchers.IO) {
        prefs.edit { putBoolean(FIRST_ENTRANCE_FLAG, true) }
    }

    companion object {
        private const val PREFS_NAME = "Detour"
        private const val FIRST_ENTRANCE_FLAG = "firstEntranceFlag"
    }
}
