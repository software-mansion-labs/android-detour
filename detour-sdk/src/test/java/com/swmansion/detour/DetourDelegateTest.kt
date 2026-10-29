package com.swmansion.detour

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DetourDelegateTest {

    @Test
    fun `opens the link on the first creation`() {
        val intent = linkIntent()

        delegate(TestOwner.created(savedState = null)).onCreate(intent)

        assertFalse(LinkOpens.isOpened(intent))
    }

    @Test
    fun `does not open the link again after process death`() {
        val before = TestOwner.created(savedState = null)
        delegate(before).onCreate(linkIntent())
        val savedState = before.saveState()

        // The new process gets the intent back from the system, without the SDK extras.
        val restoredIntent = linkIntent()
        delegate(TestOwner.created(savedState)).onCreate(restoredIntent)

        assertTrue(LinkOpens.isOpened(restoredIntent))
    }

    @Test
    fun `keeps a pending link when the activity is recreated in the same process`() = runTest {
        val intent = linkIntent()
        val before = TestOwner.created(savedState = null)
        delegate(before).onCreate(intent)
        val destroyedCaller = launch { LinkOpens().openOnce(intent) { awaitCancellation() } }
        runCurrent()
        destroyedCaller.cancel()

        delegate(TestOwner.created(before.saveState())).onCreate(intent)

        assertFalse(LinkOpens.isOpened(intent))
    }

    @Test
    fun `supports two delegates on one owner`() {
        val owner = TestOwner.created(savedState = null)
        val intent = linkIntent()

        delegate(owner).onCreate(intent)
        delegate(owner).onCreate(intent)

        assertFalse(LinkOpens.isOpened(intent))
    }

    private fun delegate(owner: TestOwner) = DetourDelegate(owner, CONFIG) {}

    private fun linkIntent() = Intent(Intent.ACTION_VIEW, Uri.parse(LINK))

    private class TestOwner private constructor() : SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

        fun saveState(): Bundle = Bundle().also { savedStateController.performSave(it) }

        companion object {
            fun created(savedState: Bundle?) = TestOwner().apply {
                savedStateController.performRestore(savedState)
                lifecycleRegistry.currentState = Lifecycle.State.CREATED
            }
        }
    }

    private companion object {
        const val LINK = "https://myapp.godetour.link/abc123/product/42"
        val CONFIG = DetourConfig(apiKey = "key", appId = "app")
    }
}
