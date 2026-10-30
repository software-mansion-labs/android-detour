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
 * Per-install state that must disappear with the app: the device ID and the first-launch flag.
 *
 * Kept in a file in `noBackupFilesDir`, which Auto Backup skips, so a reinstall or a phone
 * restored from a backup starts with a new device ID and runs deferred matching again.
 * It does not use [DetourStorage]: a custom storage may be backed up.
 */
internal class InstallStore(
    noBackupDir: File,
    private val legacyStorage: DetourStorage,
) {
    private data class State(val deviceId: String, val firstLaunchHandled: Boolean)

    private val file = File(noBackupDir, FILE_NAME)
    private val mutex = Mutex()

    @Volatile
    private var state: State? = null

    suspend fun getDeviceId(): String = load().deviceId

    suspend fun isFirstLaunch(): Boolean = !load().firstLaunchHandled

    suspend fun markFirstLaunchHandled() {
        mutex.withLock {
            val current = loadLocked().copy(firstLaunchHandled = true)
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
            State(
                deviceId = normalizeDeviceId(json.getString(KEY_DEVICE_ID)) ?: error("not a UUID"),
                firstLaunchHandled = json.getBoolean(KEY_FIRST_LAUNCH_HANDLED),
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
    }
}
