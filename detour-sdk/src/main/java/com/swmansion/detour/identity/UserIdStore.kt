package com.swmansion.detour.identity

import android.util.Log
import com.swmansion.detour.storage.DetourStorage
import com.swmansion.detour.storage.StorageKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The app's user identifier from [com.swmansion.detour.Detour.setUserId], kept in [DetourStorage].
 * Unlike the device ID it may come back from a backup: on a new install it lets the backend
 * recognise a returning user.
 */
internal class UserIdStore(private val storage: DetourStorage) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    @Volatile
    private var loaded = false

    @Volatile
    private var cached: String? = null

    suspend fun current(): String? {
        if (loaded) return cached
        return mutex.withLock {
            if (!loaded) {
                cached = storage.getItem(StorageKeys.USER_ID)
                loaded = true
            }
            cached
        }
    }

    fun set(userId: String?) {
        cached = userId
        loaded = true
        scope.launch {
            mutex.withLock {
                runCatching {
                    if (userId != null) storage.setItem(StorageKeys.USER_ID, userId)
                    else storage.removeItem(StorageKeys.USER_ID)
                }.onFailure { Log.w(TAG, "[Detour:IDENTITY] Failed to save user ID.", it) }
            }
        }
    }

    private companion object {
        const val TAG = "UserIdStore"
    }
}

/** Built-in check for [com.swmansion.detour.Detour.setUserId] values, applied before [com.swmansion.detour.DetourConfig.userIdValidator]. */
internal object UserIdPolicy {
    // PROVISIONAL: accepts every value until it is decided whether the SDK itself rejects
    // values that look like e-mail addresses or phone numbers.
    @Suppress("UNUSED_PARAMETER")
    fun isAllowed(userId: String): Boolean = true
}
