package com.swmansion.detour.consent

import android.util.Log
import com.swmansion.detour.api.HttpClient
import com.swmansion.detour.storage.DetourStorage
import com.swmansion.detour.storage.StorageKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The consent the app passed: on this launch in memory, and the last one in [DetourStorage]. */
internal class ConsentStore(
    private val storage: DetourStorage,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val mutex = Mutex()

    /**
     * Also the `consent_snapshot` of every request. A stored consent is not sent: it is not
     * trusted to grant anything until the app passes it again.
     */
    @Volatile
    var passedThisLaunch: DetourConsent? = null
        private set

    @Volatile
    private var loaded = false

    @Volatile
    private var last: DetourConsent? = null

    suspend fun stored(): DetourConsent? {
        if (loaded) return last
        return mutex.withLock {
            if (!loaded) {
                last = runCatching {
                    storage.getItem(StorageKeys.CONSENT)
                        ?.let { HttpClient.gson.fromJson(it, Map::class.java) }
                        ?.let(DetourConsent::fromWire)
                }.getOrNull()
                loaded = true
            }
            last
        }
    }

    fun record(consent: DetourConsent) {
        passedThisLaunch = consent
        last = consent
        loaded = true
        scope.launch {
            mutex.withLock {
                runCatching { storage.setItem(StorageKeys.CONSENT, HttpClient.gson.toJson(consent.toWire())) }
                    .onFailure { Log.w(TAG, "[Detour:CONSENT] Failed to save consent.", it) }
            }
        }
    }

    private companion object {
        const val TAG = "ConsentStore"
    }
}
