package com.swmansion.detour.identifiers

import android.util.Log
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.swmansion.detour.api.HttpClient
import com.swmansion.detour.models.AdvertisingId
import com.swmansion.detour.storage.DetourStorage
import com.swmansion.detour.storage.StorageKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

/** The identifiers the SDK may send now, after the consent gate and the reader's own checks. */
internal data class DeviceIdentifiers(val advertisingId: AdvertisingId? = null) {

    /** Each identifier the SDK has, as its JSON value, by request field name. */
    fun encodedFields(): Map<String, String> = buildMap {
        advertisingId?.let { put("advertising_id", HttpClient.gson.toJson(it)) }
    }

    companion object {
        val NONE = DeviceIdentifiers()
    }
}

/**
 * Sends identifiers that appear, change or go away after the first deferred match to
 * `sdk/device-identifiers`. One request at a time; a field goes out only when it differs from what
 * the server last answered for, so most launches send nothing.
 */
internal class DeviceIdentifiersReporter(
    private val storage: DetourStorage,
    private val canSend: suspend () -> Boolean,
    private val current: suspend () -> DeviceIdentifiers,
    private val deviceId: suspend () -> String,
    /** `true` once the server answered for good; `false` tries again on the next sync. */
    private val send: suspend (JsonObject) -> Boolean,
) {
    // Holds hashes, not the identifiers, and the device ID they were sent for: a storage restored
    // from another install has a new device ID, so that install sends them again.
    private class Sent(
        @SerializedName("device_id") val deviceId: String,
        @SerializedName("hashes") val hashes: Map<String, String>,
    )

    private val mutex = Mutex()

    suspend fun sync() = mutex.withLock {
        try {
            if (!canSend()) return@withLock
            val deviceId = deviceId()
            val (fields, hashes) = changes(current().encodedFields(), loadSent(deviceId))
            if (fields.size() == 0) return@withLock
            fields.addProperty("device_id", deviceId)
            // Stored also after a rejection, so a value the server will not take is not sent on every launch.
            if (send(fields)) {
                storage.setItem(StorageKeys.SENT_DEVICE_IDENTIFIERS, HttpClient.gson.toJson(Sent(deviceId, hashes)))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "[Detour:ATTRIBUTION] Failed to send device identifiers", e)
        }
    }

    private suspend fun loadSent(deviceId: String): Map<String, String> {
        val raw = storage.getItem(StorageKeys.SENT_DEVICE_IDENTIFIERS)?.takeIf { it.isNotEmpty() } ?: return emptyMap()
        val sent = runCatching { HttpClient.gson.fromJson(raw, Sent::class.java) }.getOrNull()
        return sent?.takeIf { it.deviceId == deviceId }?.hashes.orEmpty()
    }

    companion object {
        private const val TAG = "Detour"

        /**
         * The fields to send (a value, or JSON null to clear one sent before) and the hashes to
         * store once the server answered.
         */
        internal fun changes(current: Map<String, String>, sentHashes: Map<String, String>): Pair<JsonObject, Map<String, String>> {
            val fields = JsonObject()
            val hashes = current.mapValues { (_, json) -> sha256(json) }
            for ((name, json) in current) {
                if (sentHashes[name] != hashes[name]) fields.add(name, JsonParser.parseString(json))
            }
            for (name in sentHashes.keys - current.keys) {
                fields.add(name, JsonNull.INSTANCE)
            }
            return fields to hashes
        }

        private fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
