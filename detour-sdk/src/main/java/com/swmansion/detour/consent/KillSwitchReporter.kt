package com.swmansion.detour.consent

import android.util.Log
import com.swmansion.detour.storage.InstallStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Reports the kill switch state to the server until it is confirmed (API contract, kill switch).
 * One request at a time, each given up to 60 s, so an older state never lands after a newer
 * one: the server keeps the state that arrived last. An unconfirmed state is sent again on the
 * next launch, because a lost "off" would leave the install in ad network postbacks.
 */
internal class KillSwitchReporter(
    private val store: InstallStore,
    private val scope: CoroutineScope,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val send: suspend (enabled: Boolean, atMs: Long) -> Outcome,
) {
    enum class Outcome {
        CONFIRMED,
        RETRY,

        /** The server rejected the request; retrying in this launch will not change that. */
        STOP,
    }

    // One consumer runs the requests one at a time. The channel keeps at most one pending
    // wake-up, so a change made while a run is in progress starts one more run after it.
    private val wakeUps = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (wakeUp in wakeUps) {
                // A failed run must not end the loop, or later changes on this launch go unreported.
                try {
                    run()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "[Detour:KILL_SWITCH] Reporting failed; tried again on the next change or launch.", e)
                }
            }
        }
    }

    fun sync() {
        wakeUps.trySend(Unit)
    }

    suspend fun run() {
        var retryDelayMs = FIRST_RETRY_DELAY_MS
        while (true) {
            val (enabled, atMs) = store.unreportedKillSwitch() ?: return
            when (send(enabled, atMs)) {
                // A change made while the request was out is still unreported, so the loop sends it next.
                Outcome.CONFIRMED -> {
                    store.markKillSwitchReported(enabled)
                    retryDelayMs = FIRST_RETRY_DELAY_MS
                }
                Outcome.RETRY -> {
                    sleep(retryDelayMs)
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                }
                Outcome.STOP -> return
            }
        }
    }

    companion object {
        private const val TAG = "KillSwitchReporter"
        const val FIRST_RETRY_DELAY_MS = 5_000L
        const val MAX_RETRY_DELAY_MS = 300_000L
    }
}
