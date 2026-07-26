package com.volta.app.data.ar

import com.google.common.truth.Truth.assertThat
import org.junit.Test

private const val SESSION = "session"

class GatedSessionTickerTest {

    private var pumpCalls = 0
    private var pumpThrows = false
    private var nowNanos = 0L
    private val failures = mutableListOf<Throwable>()

    private val orchestrator = ArSessionOrchestrator(
        createSession = { SESSION },
        resumeSession = { true },
        pauseSession = {},
        pumpSession = {
            pumpCalls++
            if (pumpThrows) error("boom")
            ArSessionOrchestrator.PumpResult.PROCESSED
        },
        onAvailabilityChanged = {},
        onSessionStopped = {}
    )
    private val tickScheduler = TickScheduler(nowNanos = { nowNanos })
    private val ticker = GatedSessionTicker(orchestrator, tickScheduler)

    private fun onFailure(unexpected: Throwable) {
        failures.add(unexpected)
    }

    @Test
    fun `tickIfScheduled ticks when the scheduler allows it`() {
        ticker.tickIfScheduled(isResumed = true, onTickFailure = ::onFailure)

        assertThat(pumpCalls).isEqualTo(1)
    }

    @Test
    fun `tickIfScheduled reports a failure and gates further ticks by the fallback delay`() {
        pumpThrows = true

        ticker.tickIfScheduled(isResumed = true, onTickFailure = ::onFailure)
        // Clock unchanged: a persistent failure must still back off rather than retry every frame.
        ticker.tickIfScheduled(isResumed = true, onTickFailure = ::onFailure)

        assertThat(failures).hasSize(1)
        assertThat(pumpCalls).isEqualTo(1)
    }

    @Test
    fun `tickIfScheduled ticks again once the fallback delay elapses`() {
        pumpThrows = true
        ticker.tickIfScheduled(isResumed = true, onTickFailure = ::onFailure)
        nowNanos += 200_000_000L // 200ms

        ticker.tickIfScheduled(isResumed = true, onTickFailure = ::onFailure)

        assertThat(pumpCalls).isEqualTo(2)
    }

    @Test
    fun `forceTick bypasses the scheduler even when it would gate a normal tick`() {
        pumpThrows = true
        ticker.tickIfScheduled(isResumed = true, onTickFailure = ::onFailure)
        check(pumpCalls == 1) { "test setup: expected exactly one gating tick before this point" }

        ticker.forceTick(isResumed = true, onTickFailure = ::onFailure)

        assertThat(pumpCalls).isEqualTo(2)
    }

    @Test
    fun `forceTick leaves the scheduler's existing gate untouched`() {
        pumpThrows = true
        ticker.tickIfScheduled(isResumed = true, onTickFailure = ::onFailure)

        ticker.forceTick(isResumed = true, onTickFailure = ::onFailure)

        // Still within the delay the first tickIfScheduled call scheduled (the clock hasn't
        // advanced): if forceTick had called scheduleNextTick, this would incorrectly un-gate.
        ticker.tickIfScheduled(isResumed = true, onTickFailure = ::onFailure)
        assertThat(pumpCalls).isEqualTo(2)
    }

    @Test
    fun `forceTick reports failures without throwing`() {
        pumpThrows = true

        ticker.forceTick(isResumed = true, onTickFailure = ::onFailure)

        assertThat(failures).hasSize(1)
    }
}
