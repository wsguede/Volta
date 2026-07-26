package com.volta.app.data.ar

/**
 * Gates how often [ArSessionOrchestrator.tick] is invoked when the caller (a `GLSurfaceView` in
 * continuous render mode) runs at the display refresh rate rather than a sleep-controlled loop.
 * Without this, backing off for [ArSessionOrchestrator.UNAVAILABLE_RETRY_INTERVAL_MS] would still
 * re-invoke session creation on every `onDrawFrame` call (~60/sec) instead of respecting the
 * returned delay.
 */
internal class TickScheduler(private val nowNanos: () -> Long = System::nanoTime) {

    private var nextTickAtNanos = 0L

    fun shouldTick(): Boolean = nowNanos() >= nextTickAtNanos

    fun scheduleNextTick(delayMs: Long) {
        nextTickAtNanos = nowNanos() + delayMs * NANOS_PER_MILLI
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
