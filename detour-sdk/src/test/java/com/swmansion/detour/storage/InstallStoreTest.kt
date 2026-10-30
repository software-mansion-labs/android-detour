package com.swmansion.detour.storage

import com.swmansion.detour.test.InMemoryStorage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class InstallStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val legacy = InMemoryStorage()

    private fun store(dir: File = folder.root) = InstallStore(dir, legacy)

    @Test
    fun `device id survives relaunch but not reinstall`() = runTest {
        val first = store().getDeviceId()
        assertTrue(first.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))

        assertEquals(first, store().getDeviceId())

        // A reinstall or a restored phone has an empty noBackupFilesDir.
        assertNotEquals(first, store(folder.newFolder()).getDeviceId())
    }

    @Test
    fun `first launch flag lives with the device id`() = runTest {
        assertTrue(store().isFirstLaunch())

        store().markFirstLaunchHandled()

        assertFalse(store().isFirstLaunch())
    }

    @Test
    fun `update from DetourStorage keeps device id and flag`() = runTest {
        legacy.setItem(StorageKeys.DEVICE_ID, " 3F2B8C1E-9D4A-4C55-8F0E-2A6B7C8D9E01 ")
        legacy.setItem(StorageKeys.FIRST_ENTRANCE_FLAG, "true")

        val store = store()

        assertEquals("3f2b8c1e-9d4a-4c55-8f0e-2a6b7c8d9e01", store.getDeviceId())
        assertFalse(store.isFirstLaunch())
        assertNull(legacy.getItem(StorageKeys.DEVICE_ID))
        assertNull(legacy.getItem(StorageKeys.FIRST_ENTRANCE_FLAG))
        assertEquals(store.getDeviceId(), store().getDeviceId())
    }

    @Test
    fun `unreadable file starts a new install`() = runTest {
        File(folder.root, "detour_install.json").writeText("not json")

        val store = store()

        assertTrue(store.isFirstLaunch())
        assertEquals(store.getDeviceId(), store().getDeviceId())
    }
}
