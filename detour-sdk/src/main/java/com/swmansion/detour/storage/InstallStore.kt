package com.swmansion.detour.storage

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Per-install state that must disappear with the app: the device ID, the first-launch flag and
 * the kill switch, which a reinstall keeps or loses together with the device ID.
 *
 * Kept in a file in `noBackupFilesDir`, which Auto Backup skips, so a reinstall or a phone
 * restored from a backup starts with a new device ID and runs deferred matching again.
 * It does not use [DetourStorage]: a custom storage may be backed up.
 */
internal class InstallStore(
    noBackupDir: File,
    private val legacyStorage: DetourStorage,
) {
    private data class State(
        val deviceId: String,
        val firstLaunchHandled: Boolean,
        val enabled: Boolean = true,
        /** The last kill switch state the server confirmed. A new install is enabled there. */
        val reportedEnabled: Boolean = true,
        val enabledChangedAtMs: Long? = null,
    )

    private val file = File(noBackupDir, FILE_NAME)
    private val mutex = Mutex()

    @Volatile
    private var state: State? = null

    suspend fun getDeviceId(): String = load().deviceId

    suspend fun isFirstLaunch(): Boolean = !load().firstLaunchHandled

    suspend fun markFirstLaunchHandled() = update { it.copy(firstLaunchHandled = true) }

    suspend fun isEnabled(): Boolean = load().enabled

    /** Returns false when the SDK was already in this state. */
    suspend fun setEnabled(enabled: Boolean, atMs: Long): Boolean = mutex.withLock {
        val current = loadLocked()
        if (current.enabled == enabled) return@withLock false
        val changed = current.copy(enabled = enabled, enabledChangedAtMs = atMs)
        state = changed
        save(changed)
        true
    }

    /** The kill switch state the server has not confirmed yet, with the time it was set. */
    suspend fun unreportedKillSwitch(): Pair<Boolean, Long>? {
        val current = load()
        if (current.enabled == current.reportedEnabled) return null
        return current.enabled to (current.enabledChangedAtMs ?: System.currentTimeMillis())
    }

    suspend fun markKillSwitchReported(enabled: Boolean) = update { it.copy(reportedEnabled = enabled) }

    private suspend fun update(change: (State) -> State) {
        mutex.withLock {
            val current = change(loadLocked())
            state = current
            save(current)
        }
    }

    private suspend fun load(): State = state ?: mutex.withLock { loadLocked() }

    private suspend fun loadLocked(): State {
        state?.let { return it }

        readFile()?.let {
            state = it
            return it
        }

        // PROVISIONAL: an app updated from an SDK that kept these values in DetourStorage keeps
        // its device ID and first-launch flag, so the update is not counted as a new install
        // and the deferred link is not opened again. The old keys are removed after the move.
        val legacy = readLegacyState()
        val created = legacy ?: State(newDeviceId(), firstLaunchHandled = false)
        state = created
        if (save(created) && legacy != null) {
            legacyStorage.removeItem(StorageKeys.DEVICE_ID)
            legacyStorage.removeItem(StorageKeys.FIRST_ENTRANCE_FLAG)
        }
        return created
    }

    private suspend fun readFile(): State? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        val saved = runCatching {
            val json = JSONObject(file.readText())
            // A file saved by an SDK without the kill switch must still load, or the device ID is lost.
            State(
                deviceId = normalizeDeviceId(json.getString(KEY_DEVICE_ID)) ?: error("not a UUID"),
                firstLaunchHandled = json.getBoolean(KEY_FIRST_LAUNCH_HANDLED),
                enabled = json.optBoolean(KEY_ENABLED, true),
                reportedEnabled = json.optBoolean(KEY_REPORTED_ENABLED, true),
                enabledChangedAtMs = json.optLong(KEY_ENABLED_CHANGED_AT, -1).takeIf { it >= 0 },
            )
        }
        saved.exceptionOrNull()?.let {
            Log.w(TAG, "[Detour:STORAGE] Install state file is unreadable; starting a new install.", it)
        }
        saved.getOrNull()
    }

    private suspend fun readLegacyState(): State? {
        val legacyId = legacyStorage.getItem(StorageKeys.DEVICE_ID)?.let(::normalizeDeviceId)
        val legacyHandled = legacyStorage.getItem(StorageKeys.FIRST_ENTRANCE_FLAG) == "true"
        if (legacyId == null && !legacyHandled) return null
        return State(legacyId ?: newDeviceId(), legacyHandled)
    }

    private suspend fun save(state: State): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject()
                .put(KEY_DEVICE_ID, state.deviceId)
                .put(KEY_FIRST_LAUNCH_HANDLED, state.firstLaunchHandled)
                .put(KEY_ENABLED, state.enabled)
                .put(KEY_REPORTED_ENABLED, state.reportedEnabled)
                .apply { state.enabledChangedAtMs?.let { put(KEY_ENABLED_CHANGED_AT, it) } }
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(json.toString())
            check(tmp.renameTo(file)) { "rename failed" }
        }.onFailure {
            Log.e(TAG, "[Detour:STORAGE] Failed to save install state.", it)
        }.isSuccess
    }

    private fun newDeviceId(): String = UUID.randomUUID().toString()

    private fun normalizeDeviceId(value: String): String? {
        val trimmed = value.trim().lowercase()
        return trimmed.takeIf { runCatching { UUID.fromString(it).toString() == it }.getOrDefault(false) }
    }

    companion object {
        private const val TAG = "InstallStore"
        private const val FILE_NAME = "detour_install.json"
        private const val KEY_DEVICE_ID = "deviceId"
        private const val KEY_FIRST_LAUNCH_HANDLED = "firstLaunchHandled"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_REPORTED_ENABLED = "reportedEnabled"
        private const val KEY_ENABLED_CHANGED_AT = "enabledChangedAtMs"
    }
}
