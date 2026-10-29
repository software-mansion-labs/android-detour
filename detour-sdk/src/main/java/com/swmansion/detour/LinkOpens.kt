package com.swmansion.detour

import android.content.Intent
import android.util.Log
import com.swmansion.detour.models.LinkResult

/**
 * Makes sure the link from one intent opens in the app once.
 *
 * The same intent can be processed again: a recreated activity gets the same Intent object back,
 * an app can call [Detour.processLink] twice or use two [DetourDelegate]s, and a forwarded copy
 * of the intent keeps its extras. So the open state is stored in the intent extras.
 */
internal object LinkOpens {

    private const val TAG = "Detour"
    private const val EXTRA_OPEN_STATE = "com.swmansion.detour.extra.LINK_OPEN_STATE"
    private const val STATE_PENDING = "pending"
    private const val STATE_OPENED = "opened"

    /**
     * Runs [open] for a link the app has not got yet. Returns [LinkResult.NoLink] for a link
     * that was already opened, and for the slower of two calls that process the same intent at once.
     *
     * The intent is marked as pending before [open] suspends. This is how
     * [markOpenedBeforeProcessDeath] tells a recreated activity from one restored in a new process.
     */
    suspend fun openOnce(intent: Intent, open: suspend () -> LinkResult): LinkResult {
        if (isOpened(intent)) {
            Log.d(TAG, "Link from this intent was already opened")
            return LinkResult.NoLink
        }

        setStateIfMissing(intent, STATE_PENDING)
        val result = open()
        return if (claimOpen(intent)) result else LinkResult.NoLink
    }

    /**
     * After process death the system restores the activity with its original intent, without the
     * extras added in the old process. That intent was opened before, so it must not open again.
     * An intent that already has a state was seen in this process and keeps it.
     */
    fun markOpenedBeforeProcessDeath(intent: Intent) {
        if (intent.data != null) setStateIfMissing(intent, STATE_OPENED)
    }

    fun isOpened(intent: Intent): Boolean {
        // The system reopened the task from Recents with the intent that started it.
        val launchedFromHistory = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        return launchedFromHistory || readState(intent) == STATE_OPENED
    }

    @Synchronized
    private fun claimOpen(intent: Intent): Boolean {
        if (readState(intent) == STATE_OPENED) return false
        writeState(intent, STATE_OPENED)
        return true
    }

    @Synchronized
    private fun setStateIfMissing(intent: Intent, state: String) {
        if (readState(intent) == null) writeState(intent, state)
    }

    // Extras from another app can fail to unparcel. The link then opens as before this check.
    private fun readState(intent: Intent): String? =
        runCatching { intent.getStringExtra(EXTRA_OPEN_STATE) }.getOrNull()

    private fun writeState(intent: Intent, state: String) {
        runCatching { intent.putExtra(EXTRA_OPEN_STATE, state) }
    }
}
