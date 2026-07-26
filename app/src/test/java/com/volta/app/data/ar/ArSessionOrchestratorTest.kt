package com.volta.app.data.ar

import com.google.common.truth.Truth.assertThat
import org.junit.Test

private const val SESSION = "session"

class ArSessionOrchestratorTest {

    private var createSessionCalls = 0
    private var createSessionResult: String? = SESSION
    private var resumeSessionCalls = 0
    private var resumeSessionResult = true
    private var pauseSessionCalls = 0
    private var pausedSession: String? = null
    private var pumpSessionResult = ArSessionOrchestrator.PumpResult.PROCESSED
    private val availabilityEvents = mutableListOf<Boolean>()
    private var sessionStoppedCalls = 0

    private fun orchestrator() = ArSessionOrchestrator(
        createSession = {
            createSessionCalls++
            createSessionResult
        },
        resumeSession = {
            resumeSessionCalls++
            resumeSessionResult
        },
        pauseSession = {
            pauseSessionCalls++
            pausedSession = it
        },
        pumpSession = { pumpSessionResult },
        onAvailabilityChanged = { availabilityEvents.add(it) },
        onSessionStopped = { sessionStoppedCalls++ }
    )

    @Test
    fun `does not create a session while not resumed`() {
        val delay = orchestrator().tick(isResumed = false)

        assertThat(delay).isEqualTo(ArSessionOrchestrator.PAUSED_POLL_INTERVAL_MS)
        assertThat(createSessionCalls).isEqualTo(0)
    }

    @Test
    fun `backs off with the long interval when session creation permanently fails`() {
        createSessionResult = null

        val delay = orchestrator().tick(isResumed = true)

        assertThat(delay).isEqualTo(ArSessionOrchestrator.UNAVAILABLE_RETRY_INTERVAL_MS)
        assertThat(availabilityEvents).containsExactly(false)
    }

    @Test
    fun `backs off with the short interval on transient camera unavailability during resume`() {
        resumeSessionResult = false

        val delay = orchestrator().tick(isResumed = true)

        assertThat(delay).isEqualTo(ArSessionOrchestrator.PAUSED_POLL_INTERVAL_MS)
        assertThat(availabilityEvents).containsExactly(true)
    }

    @Test
    fun `returns zero delay after successfully processing a frame`() {
        val delay = orchestrator().tick(isResumed = true)

        assertThat(delay).isEqualTo(0L)
    }

    @Test
    fun `does not recreate the session on a later tick once resume keeps failing`() {
        resumeSessionResult = false
        val loop = orchestrator()

        loop.tick(isResumed = true)
        loop.tick(isResumed = true)

        assertThat(createSessionCalls).isEqualTo(1)
        assertThat(resumeSessionCalls).isEqualTo(2)
    }

    @Test
    fun `loses tracking and backs off when pumping reports camera unavailable`() {
        pumpSessionResult = ArSessionOrchestrator.PumpResult.CAMERA_UNAVAILABLE

        val delay = orchestrator().tick(isResumed = true)

        assertThat(delay).isEqualTo(ArSessionOrchestrator.PAUSED_POLL_INTERVAL_MS)
        assertThat(sessionStoppedCalls).isEqualTo(1)
        assertThat(pauseSessionCalls).isEqualTo(1)
    }

    @Test
    fun `re-resumes instead of repumping a broken session after camera loss mid-stream`() {
        val loop = orchestrator()
        loop.tick(isResumed = true)

        pumpSessionResult = ArSessionOrchestrator.PumpResult.CAMERA_UNAVAILABLE
        loop.tick(isResumed = true)

        pumpSessionResult = ArSessionOrchestrator.PumpResult.PROCESSED
        val delay = loop.tick(isResumed = true)

        assertThat(createSessionCalls).isEqualTo(1)
        assertThat(resumeSessionCalls).isEqualTo(2)
        assertThat(pauseSessionCalls).isEqualTo(1)
        assertThat(delay).isEqualTo(0L)
    }

    @Test
    fun `pauses the session and reports session stopped when transitioning to not resumed`() {
        val loop = orchestrator()
        loop.tick(isResumed = true)

        loop.tick(isResumed = false)

        assertThat(pauseSessionCalls).isEqualTo(1)
        assertThat(pausedSession).isEqualTo(SESSION)
        assertThat(sessionStoppedCalls).isEqualTo(1)
    }

    @Test
    fun `resumes the same already-created session again after a pause-resume cycle`() {
        val loop = orchestrator()
        loop.tick(isResumed = true)
        loop.tick(isResumed = false)

        loop.tick(isResumed = true)

        assertThat(createSessionCalls).isEqualTo(1)
        assertThat(resumeSessionCalls).isEqualTo(2)
    }

    @Test
    fun `recovers once a session becomes available on a later tick`() {
        createSessionResult = null
        val loop = orchestrator()
        loop.tick(isResumed = true)

        createSessionResult = SESSION
        val delay = loop.tick(isResumed = true)

        assertThat(createSessionCalls).isEqualTo(2)
        assertThat(delay).isEqualTo(0L)
        assertThat(availabilityEvents).containsExactly(false, true).inOrder()
    }
}
