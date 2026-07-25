package com.volta.app.data.ar

/**
 * Pure retry/backoff/resume-pause state machine for driving an ARCore session, decoupled from
 * the real native `Session` (generic over [S]) so it can be unit tested without one.
 * [ArCameraRepository]'s background thread drives this by calling [tick] in a loop and sleeping
 * for the returned delay before calling it again.
 */
internal class ArSessionOrchestrator<S>(
    private val createSession: () -> S?,
    private val resumeSession: (S) -> Boolean,
    private val pauseSession: (S) -> Unit,
    private val pumpSession: (S) -> PumpResult,
    private val onAvailabilityChanged: (Boolean) -> Unit,
    private val onTrackingLost: () -> Unit
) {
    enum class PumpResult { PROCESSED, NO_NEW_FRAME, CAMERA_UNAVAILABLE }

    private var session: S? = null
    private var wasResumed = false
    private var isSessionResumed = false
    private var sessionCreationBackingOff = false

    /**
     * Advances the state machine by one tick. Returns the delay (ms) the caller should sleep
     * before the next tick — 0 when a frame was just processed or none was ready yet, since
     * ARCore paces [pumpSession] internally to the camera's actual frame rate.
     */
    fun tick(isResumed: Boolean): Long {
        if (!isResumed && wasResumed) stop()
        wasResumed = isResumed
        if (!isResumed) return PAUSED_POLL_INTERVAL_MS

        val activeSession = ensureResumed() ?: return retryDelay()
        return when (pumpSession(activeSession)) {
            PumpResult.PROCESSED, PumpResult.NO_NEW_FRAME -> 0L
            PumpResult.CAMERA_UNAVAILABLE -> {
                // ARCore doesn't self-heal a camera lost mid-stream by retrying update() alone —
                // it needs an explicit pause()/resume() cycle to reopen the device. stop() pauses
                // and clears isSessionResumed so the next tick's ensureResumed() re-resumes
                // rather than handing the same broken session straight back to pumpSession.
                stop()
                PAUSED_POLL_INTERVAL_MS
            }
        }
    }

    private fun ensureResumed(): S? {
        val activeSession = session ?: createSession()?.also {
            session = it
            sessionCreationBackingOff = false
            onAvailabilityChanged(true)
        } ?: run {
            sessionCreationBackingOff = true
            onAvailabilityChanged(false)
            return null
        }
        if (isSessionResumed) return activeSession
        if (!resumeSession(activeSession)) return null
        isSessionResumed = true
        sessionCreationBackingOff = false
        return activeSession
    }

    private fun stop() {
        session?.let(pauseSession)
        isSessionResumed = false
        onTrackingLost()
    }

    private fun retryDelay(): Long = if (sessionCreationBackingOff) {
        UNAVAILABLE_RETRY_INTERVAL_MS
    } else {
        PAUSED_POLL_INTERVAL_MS
    }

    companion object {
        const val PAUSED_POLL_INTERVAL_MS = 100L
        const val UNAVAILABLE_RETRY_INTERVAL_MS = 5_000L
    }
}
