package com.syncrobotic.webrtc.e2e

import com.syncrobotic.webrtc.session.SessionState
import com.syncrobotic.webrtc.session.WebRTCSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Wait for the session to reach a settled state (Connected, Error, or Closed).
 * Returns the settled state for further assertions.
 *
 * Uses real-time polling (Thread.sleep) to work correctly with `runTest` (virtual time),
 * since WebRTCSession runs connect() on `Dispatchers.Default` (real thread).
 */
fun WebRTCSession.awaitSettled(
    timeoutMs: Long = 10_000
): SessionState {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        val current = state.value
        if (current is SessionState.Connected ||
            current is SessionState.Error ||
            current is SessionState.Closed
        ) {
            return current
        }
        Thread.sleep(50)
    }
    return state.value
}

/**
 * Launch connect() on a real dispatcher (not TestDispatcher).
 * Returns the Job for cancellation.
 */
fun WebRTCSession.launchConnect(): Job {
    return CoroutineScope(Dispatchers.Default).launch { connect() }
}
