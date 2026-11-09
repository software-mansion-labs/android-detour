package com.swmansion.detour.api

import android.os.SystemClock
import android.util.Log
import com.swmansion.detour.api.DetourApiClient.UniversalLinkClickOutcome
import com.swmansion.detour.api.DetourApiClient.UniversalLinkClickResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Saves one click per link open. Calls for the same link within [repeatWindowMs] make no new
 * request and get the result of the first one.
 *
 * One open can reach the SDK twice, for example when the activity is recreated while the click
 * is being sent, or when an activity passes the link on to another one.
 *
 * @param scope Runs the requests, so a caller that is cancelled does not cancel the shared request
 */
internal class UniversalLinkClickRecorder(
    private val sendClick: suspend (url: String) -> UniversalLinkClickOutcome,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val repeatWindowMs: Long = REPEAT_WINDOW_MS
) {

    private class RecordedClick(
        val outcome: CompletableDeferred<UniversalLinkClickOutcome>,
        val sentAt: Long
    )

    private val recordedClicks = mutableMapOf<String, RecordedClick>()

    suspend fun record(url: String): UniversalLinkClickResult {
        val click = synchronized(recordedClicks) {
            val sentAt = now()
            recordedClicks.values.removeAll { sentAt - it.sentAt >= repeatWindowMs }
            recordedClicks[url] ?: send(url, sentAt)
        }
        return when (val outcome = click.outcome.await()) {
            is UniversalLinkClickOutcome.Saved -> outcome.result
            UniversalLinkClickOutcome.NotSaved -> FAIL_OPEN_RESULT
        }
    }

    private fun send(url: String, sentAt: Long): RecordedClick {
        val click = RecordedClick(CompletableDeferred(), sentAt)
        recordedClicks[url] = click
        scope.launch {
            val outcome = try {
                sendClick(url)
            } catch (e: Exception) {
                Log.w(TAG, "[Detour:NETWORK_ERROR] Universal link click not sent", e)
                UniversalLinkClickOutcome.NotSaved
            }
            if (outcome == UniversalLinkClickOutcome.NotSaved) {
                // The next call for this link sends the click again.
                synchronized(recordedClicks) {
                    if (recordedClicks[url] === click) recordedClicks.remove(url)
                }
            }
            click.outcome.complete(outcome)
        }
        return click
    }

    companion object {
        private const val TAG = "Detour"

        // Matches the server, which also ignores a repeated click on the same link within 10 s.
        const val REPEAT_WINDOW_MS = 10_000L

        // Apps keep working when the click cannot be saved. Limits apply only on an explicit deny.
        private val FAIL_OPEN_RESULT = UniversalLinkClickResult(allowed = true)
    }
}
