package com.swmansion.detour

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.swmansion.detour.models.LinkResult

/**
 * Makes sure one tap on a link opens it in the app once.
 *
 * The same intent can be processed again: a recreated activity gets the same Intent object back,
 * an app can call [Detour.processLink] twice or use two [DetourDelegate]s, and a forwarded copy
 * of the intent keeps its extras. So the open state is stored in the intent extras.
 *
 * An app can also pass the link on in a new intent, for example from a splash activity.
 * So the same link from another intent within [sameTapWindowMs] of an open does not open again.
 */
internal class LinkOpens(
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val sameTapWindowMs: Long = SAME_TAP_WINDOW_MS
) {

    private val openedAt = mutableMapOf<String, Long>()

    /**
     * Runs [open] for a link the app has not got yet. Returns [LinkResult.NoLink] for a link
     * that was already opened, and for the slower of two calls that open the same link at once.
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

    private fun claimOpen(intent: Intent): Boolean {
        synchronized(lock) {
            if (readState(intent) == STATE_OPENED) return false
            writeState(intent, STATE_OPENED)

            val link = intent.dataString ?: return true
            val claimedAt = now()
            openedAt.values.removeAll { claimedAt - it >= sameTapWindowMs }
            if (link in openedAt) {
                Log.d(TAG, "Link was opened from another intent a moment ago")
                return false
            }
            openedAt[link] = claimedAt
            return true
        }
    }

    companion object {
        private const val TAG = "Detour"
        private const val EXTRA_OPEN_STATE = "com.swmansion.detour.extra.LINK_OPEN_STATE"
        private const val STATE_PENDING = "pending"
        private const val STATE_OPENED = "opened"

        // A link passed on in a new intent reaches the SDK again within milliseconds.
        // The React Native and iOS SDKs also treat a repeat within 1 s as the same tap.
        const val SAME_TAP_WINDOW_MS = 1_000L

        private val lock = Any()

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

        private fun setStateIfMissing(intent: Intent, state: String) {
            synchronized(lock) {
                if (readState(intent) == null) writeState(intent, state)
            }
        }

        // Extras from another app can fail to unparcel. The link then opens as before this check.
        private fun readState(intent: Intent): String? =
            runCatching { intent.getStringExtra(EXTRA_OPEN_STATE) }.getOrNull()

        private fun writeState(intent: Intent, state: String) {
            runCatching { intent.putExtra(EXTRA_OPEN_STATE, state) }
        }
    }
}
