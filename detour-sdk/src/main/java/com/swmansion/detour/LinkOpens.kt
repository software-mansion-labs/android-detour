package com.swmansion.detour

import android.content.Intent
import android.util.Log
import com.swmansion.detour.models.LinkResult
import java.util.WeakHashMap

/**
 * Makes sure the link from one intent opens in the app once.
 *
 * The same Intent object can be processed again: a recreated activity gets it back, and an app can
 * call [Detour.processLink] twice or use two [DetourDelegate]s. A copy of the intent, or a new intent
 * with the same link, opens again. The SDK cannot tell a repeat from an activity that passes the
 * link on, and losing a link is worse than opening it twice. Its click is still sent once.
 */
internal object LinkOpens {

    private const val TAG = "Detour"

    private enum class OpenState { PENDING, OPENED }

    // Intent does not override equals, so the state belongs to one Intent object. Weak keys let it
    // go away with the activity that holds the intent.
    private val openStates = WeakHashMap<Intent, OpenState>()

    /**
     * Runs [open] for a link the app has not got yet. Returns [LinkResult.NoLink] for a link
     * that was already opened, and for the slower of two calls that process the same intent at once.
     *
     * The intent is marked as pending before [open] suspends. This is how
     * [markOpenedInPreviousProcess] tells a recreated activity from one restored in a new process.
     */
    suspend fun openOnce(intent: Intent, open: suspend () -> LinkResult): LinkResult {
        if (isOpened(intent)) {
            Log.d(TAG, "Link from this intent was already opened")
            return LinkResult.NoLink
        }

        setStateIfMissing(intent, OpenState.PENDING)
        val result = open()
        return if (claimOpen(intent)) result else LinkResult.NoLink
    }

    /**
     * After process death the system restores the activity with a new Intent object, so this
     * process has no state for it. Its link was opened in the previous process and must not open
     * again. An intent that this process already saw keeps its state.
     */
    fun markOpenedInPreviousProcess(intent: Intent) {
        if (intent.data != null) setStateIfMissing(intent, OpenState.OPENED)
    }

    @Synchronized
    fun isOpened(intent: Intent): Boolean {
        // From Recents the system replays the intent that started the task. Its link was opened then.
        val launchedFromHistory = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        return launchedFromHistory || openStates[intent] == OpenState.OPENED
    }

    @Synchronized
    private fun claimOpen(intent: Intent): Boolean {
        if (openStates[intent] == OpenState.OPENED) return false
        openStates[intent] = OpenState.OPENED
        return true
    }

    @Synchronized
    private fun setStateIfMissing(intent: Intent, state: OpenState) {
        if (intent !in openStates) openStates[intent] = state
    }
}
