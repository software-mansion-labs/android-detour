package com.swmansion.detour.referrer

import com.swmansion.detour.models.MetaInstallReferrer
import com.swmansion.detour.referrer.MetaInstallReferrerReader.Provider
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

// runBlocking, not runTest: the read waits in real time, and virtual time would end the wait at once.
@RunWith(RobolectricTestRunner::class)
class MetaInstallReferrerReaderTest {

    private fun referrer(provider: Provider, timestamp: Long) =
        MetaInstallReferrer("{\"utm_source\":\"apps.facebook.com\"}", true, timestamp, provider.sourceApp)

    @Test
    fun `the latest touch across Meta apps wins`() = runBlocking {
        val asked = mutableListOf<Pair<Provider, String>>()
        val reader = MetaInstallReferrerReader { provider, appId ->
            asked += provider to appId
            when (provider) {
                Provider.FACEBOOK -> referrer(provider, 100)
                Provider.INSTAGRAM -> referrer(provider, 200)
                Provider.FACEBOOK_LITE -> null
            }
        }

        assertEquals("instagram", reader.read("123")?.sourceApp)
        assertEquals(Provider.entries.map { it to "123" }, asked)
    }

    @Test
    fun `stops waiting for a Meta app that does not answer`() = runBlocking {
        val release = CountDownLatch(1)
        val reader = MetaInstallReferrerReader { provider, _ ->
            release.await()
            referrer(provider, 100)
        }

        try {
            assertNull(reader.read("123"))
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `nothing to read without a Meta app`() = runBlocking {
        assertNull(MetaInstallReferrerReader(RuntimeEnvironment.getApplication()).read("123"))
    }
}
