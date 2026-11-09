package com.swmansion.detour.identity

import com.swmansion.detour.storage.StorageKeys
import com.swmansion.detour.test.InMemoryStorage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserIdStoreTest {

    private val storage = InMemoryStorage()

    @Test
    fun `user id must have 1 to 256 characters`() {
        assertNull(userIdRejection("u", null))
        assertNull(userIdRejection("a".repeat(USER_ID_MAX_LENGTH), null))
        assertNotNull(userIdRejection("", null))
        assertNotNull(userIdRejection("a".repeat(USER_ID_MAX_LENGTH + 1), null))
        // Length counts UTF-16 units: each emoji takes two.
        assertNotNull(userIdRejection("😀".repeat(129), null))
    }

    @Test
    fun `wrong format is rejected before the validator runs`() {
        var calls = 0
        assertNotNull(userIdRejection("", { calls++; true }))
        assertEquals(0, calls)
    }

    @Test
    fun `validator rejection is reported`() {
        assertNotNull(userIdRejection("u_1", { false }))
        assertNull(userIdRejection("u_1", { true }))
    }

    @Test
    fun `stored value is kept when it passes the checks`() = runTest {
        storage.setItem(StorageKeys.USER_ID, "u_1")

        assertEquals("u_1", UserIdStore(storage) { true }.current())
        assertEquals("u_1", storage.getItem(StorageKeys.USER_ID))
    }

    @Test
    fun `stored value rejected by a new validator is cleared and not returned`() = runTest {
        storage.setItem(StorageKeys.USER_ID, "jane@example.com")

        assertNull(UserIdStore(storage) { !it.contains('@') }.current())
        assertNull(storage.getItem(StorageKeys.USER_ID))
    }

    @Test
    fun `stored value with the wrong format is cleared and not returned`() = runTest {
        storage.setItem(StorageKeys.USER_ID, "a".repeat(USER_ID_MAX_LENGTH + 1))

        assertNull(UserIdStore(storage, null).current())
        assertNull(storage.getItem(StorageKeys.USER_ID))
    }
}
