package com.swmansion.detour.consent

import android.util.Log
import com.swmansion.detour.api.HttpClient
import com.swmansion.detour.storage.DetourStorage
import com.swmansion.detour.storage.StorageKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

/** The consent the app passed: on this launch in memory, and the last one in [DetourStorage]. */
internal class ConsentStore(
    private val storage: DetourStorage,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** [confirmedFor] is the device ID the server confirmed [consent] for, or `null`. */
    private class Saved(val consent: DetourConsent, val confirmedFor: String?) {
        /** The server already has [passed] for [deviceId]: the same choice, with no time of its own. */
        fun hasOnServer(passed: DetourConsent, deviceId: String) =
            confirmedFor == deviceId && passed.timestampMs == null && consent.copy(timestampMs = null) == passed
    }

    private val mutex = Mutex()

    private val passed = AtomicReference<DetourConsent?>(null)

    /**
     * Also the `consent_snapshot` of every request. A stored consent is not sent: it is not
     * trusted to grant anything until the app passes it again.
     */
    val passedThisLaunch: DetourConsent?
        get() = passed.get()

    @Volatile
    private var loaded = false

    @Volatile
    private var saved: Saved? = null

    suspend fun stored(): DetourConsent? {
        if (loaded) return saved?.consent
        return mutex.withLock { loadLocked() }?.consent
    }

    /**
     * Applies [consent] at once, then saves it and passes it to [send] with the device ID from
     * [deviceId]. [send] returns `true` when the server confirmed it. Nothing is sent when the
     * server already has the same choice for this device ID.
     */
    fun record(
        consent: DetourConsent,
        deviceId: suspend () -> String,
        send: suspend (DetourConsent, deviceId: String) -> Boolean,
    ) {
        val withTimestamp = consent.copy(timestampMs = consent.timestampMs ?: now())
        passed.set(withTimestamp)
        scope.launch {
            try {
                val currentDeviceId = deviceId()
                mutex.withLock {
                    // A newer call replaced this one before it ran.
                    if (passedThisLaunch !== withTimestamp) return@launch
                    val previous = loadLocked()
                    // Apps pass the same choice on every launch. Sent again, it would be stored on the
                    // server as a new choice made now. The device ID is compared because the stored
                    // consent comes back with a backup restore, but the device ID does not.
                    if (previous != null && previous.hasOnServer(consent, currentDeviceId)) {
                        passed.compareAndSet(withTimestamp, previous.consent)
                        return@launch
                    }
                    save(Saved(withTimestamp, confirmedFor = null))
                }
                if (!send(withTimestamp, currentDeviceId)) return@launch
                mutex.withLock {
                    if (saved?.consent === withTimestamp) save(Saved(withTimestamp, confirmedFor = currentDeviceId))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "[Detour:CONSENT] Failed to send consent; sent again on the next setConsent.", e)
            }
        }
    }

    private suspend fun loadLocked(): Saved? {
        if (!loaded) {
            saved = runCatching {
                storage.getItem(StorageKeys.CONSENT)
                    ?.let { HttpClient.gson.fromJson(it, Map::class.java) }
                    ?.let { wire -> DetourConsent.fromWire(wire)?.let { Saved(it, wire[CONFIRMED_FOR] as? String) } }
            }.getOrNull()
            loaded = true
        }
        return saved
    }

    private suspend fun save(value: Saved) {
        saved = value
        loaded = true
        runCatching {
            val wire = value.consent.toWire() + listOfNotNull(value.confirmedFor?.let { CONFIRMED_FOR to it })
            storage.setItem(StorageKeys.CONSENT, HttpClient.gson.toJson(wire))
        }.onFailure { Log.w(TAG, "[Detour:CONSENT] Failed to save consent.", it) }
    }

    private companion object {
        const val TAG = "ConsentStore"

        // Saved after a 2xx from the server, so a choice that failed to send is sent on the next launch.
        const val CONFIRMED_FOR = "confirmed_for"
    }
}
