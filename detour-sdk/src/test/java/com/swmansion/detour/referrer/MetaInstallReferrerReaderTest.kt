package com.swmansion.detour.referrer

import com.swmansion.detour.models.MetaInstallReferrer
import com.swmansion.detour.referrer.MetaInstallReferrerReader.Provider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
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
        // Asked at once from several threads.
        val asked = java.util.concurrent.ConcurrentLinkedQueue<Pair<Provider, String>>()
        val reader = MetaInstallReferrerReader { provider, appId ->
            asked += provider to appId
            when (provider) {
                Provider.FACEBOOK -> referrer(provider, 100)
                Provider.INSTAGRAM -> referrer(provider, 200)
                Provider.FACEBOOK_LITE -> null
            }
        }

        assertEquals("instagram", reader.read("123") {}?.sourceApp)
        assertEquals(Provider.entries.map { it to "123" }.toSet(), asked.toSet())
    }

    @Test
    fun `stops waiting for a Meta app that does not answer`() = runBlocking {
        val release = CountDownLatch(1)
        val reader = MetaInstallReferrerReader { provider, _ ->
            release.await()
            referrer(provider, 100)
        }

        val late = CompletableDeferred<MetaInstallReferrer>()
        try {
            assertNull(reader.read("123") { late.complete(it) })
        } finally {
            release.countDown()
        }
        assertEquals("facebook", late.await().sourceApp)
    }

    @Test
    fun `nothing to read without a Meta app`() = runBlocking {
        assertNull(MetaInstallReferrerReader(RuntimeEnvironment.getApplication()).read("123") {})
    }

    @Test
    fun `asks the Meta apps at once`() = runBlocking {
        // Each answer waits until both apps were asked, so a read one by one gets nothing from Facebook.
        val bothAsked = CountDownLatch(2)
        val reader = MetaInstallReferrerReader { provider, _ ->
            when (provider) {
                Provider.FACEBOOK_LITE -> null
                else -> {
                    bothAsked.countDown()
                    if (!bothAsked.await(1, TimeUnit.SECONDS)) return@MetaInstallReferrerReader null
                    referrer(provider, if (provider == Provider.FACEBOOK) 200 else 100)
                }
            }
        }

        assertEquals("facebook", reader.read("123") {}?.sourceApp)
    }
}
