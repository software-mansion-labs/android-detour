package com.swmansion.detour.referrer

import android.util.Log
import com.google.gson.annotations.SerializedName
import com.swmansion.detour.api.HttpClient
import com.swmansion.detour.consent.GateDecision
import com.swmansion.detour.models.MetaInstallReferrer
import com.swmansion.detour.storage.DetourStorage
import com.swmansion.detour.storage.StorageKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps a Meta Install Referrer that was read after the limit for the deferred link request, and
 * sends it until the server takes it, on later launches too.
 */
internal class LateMetaReferrerSender(
    private val storage: DetourStorage,
    private val deviceId: suspend () -> String,
    private val attributionDecision: suspend () -> GateDecision,
    /** `true` once the referrer is settled; `false` keeps it for the next launch. */
    private val send: suspend (deviceId: String, referrer: MetaInstallReferrer) -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Kept(
        @SerializedName("device_id") val deviceId: String,
        @SerializedName("meta_install_referrer") val referrer: MetaInstallReferrer,
        @SerializedName("kept_at_ms") val keptAtMs: Long,
    )

    // One send at a time, so a referrer is not sent twice on one launch.
    private val mutex = Mutex()

    // Sent from memory once when the storage could not keep it.
    @Volatile
    private var unsaved: Kept? = null

    /** Stores the referrer; [sendKept] sends it. */
    suspend fun keep(deviceId: String, referrer: MetaInstallReferrer) {
        val kept = Kept(deviceId, referrer, now())
        try {
            storage.setItem(StorageKeys.LATE_META_REFERRER, HttpClient.gson.toJson(kept))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "[Detour:ATTRIBUTION] Failed to keep the late Meta Install Referrer", e)
            unsaved = kept
        }
    }

    /** Sends the kept referrer, if any. */
    suspend fun sendKept() = mutex.withLock {
        try {
            unsaved?.let { kept ->
                if (trySend(kept)) unsaved = null
                return@withLock
            }
            val raw = storage.getItem(StorageKeys.LATE_META_REFERRER)?.takeIf { it.isNotEmpty() } ?: return@withLock
            // An unreadable value is dropped too, so it is not read again on every launch.
            val kept = runCatching { HttpClient.gson.fromJson(raw, Kept::class.java) }.getOrNull()
            // An empty value, not removeItem, which a custom storage may leave out.
            if (kept == null || trySend(kept)) storage.setItem(StorageKeys.LATE_META_REFERRER, "")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "[Detour:ATTRIBUTION] Failed to send the late Meta Install Referrer", e)
        }
    }

    /** `true` once the referrer is settled: sent and taken, or dropped. */
    private suspend fun trySend(kept: Kept): Boolean {
        if (now() - kept.keptAtMs > MAX_KEPT_MS) return true
        // A storage restored from another install has a new device ID; the referrer is not its own.
        if (deviceId() != kept.deviceId) return true
        return when (attributionDecision()) {
            GateDecision.SEND -> send(kept.deviceId, kept.referrer)
            GateDecision.DROP -> true
            GateDecision.WAIT -> false
        }
    }

    private companion object {
        const val TAG = "Detour"

        // Meta takes an install event up to 7 days old, so a referrer the server never took is dropped then.
        const val MAX_KEPT_MS = 7L * 24 * 60 * 60 * 1000
    }
}
