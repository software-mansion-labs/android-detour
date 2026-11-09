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
 *
 * A stored value is checked with [userIdRejection] when it is loaded, because it may come from
 * an older app version or a backup that ran without the current validator.
 */
internal class UserIdStore(
    private val storage: DetourStorage,
    private val validator: ((String) -> Boolean)?,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    @Volatile
    private var loaded = false

    @Volatile
    private var cached: String? = null

    init {
        scope.launch { current() }
    }

    suspend fun current(): String? {
        if (loaded) return cached
        return mutex.withLock {
            if (!loaded) {
                val checked = loadChecked()
                // set() may have run while storage was read; its value wins.
                synchronized(this) {
                    if (!loaded) {
                        cached = checked
                        loaded = true
                    }
                }
            }
            cached
        }
    }

    fun set(userId: String?) {
        synchronized(this) {
            cached = userId
            loaded = true
        }
        scope.launch {
            mutex.withLock {
                runCatching {
                    if (userId != null) storage.setItem(StorageKeys.USER_ID, userId)
                    else storage.removeItem(StorageKeys.USER_ID)
                }.onFailure { Log.w(TAG, "[Detour:IDENTITY] Failed to save user ID.", it) }
            }
        }
    }

    private suspend fun loadChecked(): String? {
        val stored = runCatching { storage.getItem(StorageKeys.USER_ID) }
            .onFailure { Log.w(TAG, "[Detour:IDENTITY] Failed to read user ID.", it) }
            .getOrNull() ?: return null
        val rejection = userIdRejection(stored, validator) ?: return stored
        Log.w(TAG, "[Detour:IDENTITY] Stored user ID $rejection; cleared.")
        runCatching { storage.removeItem(StorageKeys.USER_ID) }
            .onFailure { Log.w(TAG, "[Detour:IDENTITY] Failed to clear user ID.", it) }
        return null
    }

    private companion object {
        const val TAG = "UserIdStore"
    }
}

/** The backend accepts at most this many characters (UTF-16 units, as [String.length] counts). */
internal const val USER_ID_MAX_LENGTH = 256

/**
 * Why [userId] must not be stored or sent, or `null` when it is fine. Shared by
 * [com.swmansion.detour.Detour.setUserId] and the check of the stored value.
 */
internal fun userIdRejection(userId: String, validator: ((String) -> Boolean)?): String? = when {
    // The backend answers 400 to other values, and the deferred link of that launch is lost.
    userId.isEmpty() || userId.length > USER_ID_MAX_LENGTH -> "has the wrong format (empty or longer than $USER_ID_MAX_LENGTH characters)"
    !UserIdPolicy.isAllowed(userId) || validator?.invoke(userId) == false -> "was rejected by the validator"
    else -> null
}

/** Built-in check for [com.swmansion.detour.Detour.setUserId] values, applied before [com.swmansion.detour.DetourConfig.userIdValidator]. */
internal object UserIdPolicy {
    // PROVISIONAL: accepts every value until it is decided whether the SDK itself rejects
    // values that look like e-mail addresses or phone numbers.
    @Suppress("UNUSED_PARAMETER")
    fun isAllowed(userId: String): Boolean = true
}
