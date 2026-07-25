package com.volta.app.data.ar

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TickSchedulerTest {

    private var nowNanos = 0L
    private val scheduler = TickScheduler(nowNanos = { nowNanos })

    @Test
    fun `ticks immediately before any delay has been scheduled`() {
        assertThat(scheduler.shouldTick()).isTrue()
    }

    @Test
    fun `does not tick again before the scheduled delay elapses`() {
        scheduler.scheduleNextTick(delayMs = 100)
        nowNanos += 50 * NANOS_PER_MILLI

        assertThat(scheduler.shouldTick()).isFalse()
    }

    @Test
    fun `ticks again once the scheduled delay fully elapses`() {
        scheduler.scheduleNextTick(delayMs = 100)
        nowNanos += 100 * NANOS_PER_MILLI

        assertThat(scheduler.shouldTick()).isTrue()
    }

    @Test
    fun `a zero delay allows ticking again on the very next check`() {
        scheduler.scheduleNextTick(delayMs = 0)

        assertThat(scheduler.shouldTick()).isTrue()
    }

    @Test
    fun `re-scheduling after each tick keeps gating subsequent calls`() {
        scheduler.scheduleNextTick(delayMs = 100)
        nowNanos += 100 * NANOS_PER_MILLI
        assertThat(scheduler.shouldTick()).isTrue()

        scheduler.scheduleNextTick(delayMs = 100)
        nowNanos += 10 * NANOS_PER_MILLI

        assertThat(scheduler.shouldTick()).isFalse()
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
