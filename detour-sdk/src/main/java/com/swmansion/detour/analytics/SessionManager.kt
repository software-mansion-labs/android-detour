package com.swmansion.detour.analytics

import java.util.UUID

/**
 * Analytics sessions: one `session_id` per continuous use of the app, kept in memory only.
 * [onForeground] and [onBackground] come from the process lifecycle.
 */
internal class SessionManager(private val now: () -> Long = System::currentTimeMillis) {

    sealed interface Event {
        val sessionId: String

        data class Start(override val sessionId: String, val atMs: Long) : Event

        /**
         * Duration is wall time since the start, so the backend's `start = end - duration`
         * still holds when the start request was lost.
         */
        data class End(override val sessionId: String, val atMs: Long, val durationMs: Long) : Event
    }

    private var send: ((Event) -> Unit)? = null
    private var startedAtMs = 0L
    private var backgroundedAtMs: Long? = null

    @Volatile
    var currentSessionId: String? = null
        private set

    fun start(send: (Event) -> Unit) = synchronized(this) {
        if (this.send == null) this.send = send
    }

    /** The first foreground starts a session; a return after [BACKGROUND_TIMEOUT_MS] starts a new one. */
    fun onForeground() {
        val (send, event) = synchronized(this) {
            val send = send ?: return
            val backgroundedAt = backgroundedAtMs
            backgroundedAtMs = null
            val expired = backgroundedAt != null && now() - backgroundedAt > BACKGROUND_TIMEOUT_MS
            if (currentSessionId != null && !expired) return
            send to beginSessionLocked()
        }
        send(event)
    }

    // The process can be killed in the background without notice, so every trip to the background
    // reports the end. If the user returns in time, a later end of the same session replaces it.
    fun onBackground() {
        val (send, event) = synchronized(this) {
            val send = send ?: return
            val sessionId = currentSessionId ?: return
            val at = now()
            backgroundedAtMs = at
            send to Event.End(sessionId, at, at - startedAtMs)
        }
        send(event)
    }

    private fun beginSessionLocked(): Event.Start {
        val id = UUID.randomUUID().toString()
        startedAtMs = now()
        currentSessionId = id
        return Event.Start(id, startedAtMs)
    }

    companion object {
        // PROVISIONAL: more than 30 min in the background starts a new session, the threshold Google
        // Analytics and Adjust use, until the API contract settles on an SDK constant or a server value.
        const val BACKGROUND_TIMEOUT_MS = 30 * 60 * 1000L
    }
}
