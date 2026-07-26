package com.volta.app.data.ar

/**
 * Pairs an [ArSessionOrchestrator] with the [TickScheduler] gate that gives it a sane rate when
 * driven from a continuously-rendering `GLSurfaceView.onDrawFrame` (~60 Hz) — while keeping a
 * second, ungated path available for callers that must guarantee a tick actually runs right now,
 * not whenever the schedule next allows one. [ArSessionManager.flushPendingPause] is exactly such
 * a caller: flushing a pending pause through [tickIfScheduled] instead of [forceTick] would let
 * it silently no-op during a scheduled backoff, defeating the guarantee it exists to provide.
 */
internal class GatedSessionTicker<S>(
    private val orchestrator: ArSessionOrchestrator<S>,
    private val tickScheduler: TickScheduler
) {

    /** For per-frame driving: ticks only if [TickScheduler.shouldTick] currently allows it. */
    @Suppress("TooGenericExceptionCaught")
    fun tickIfScheduled(isResumed: Boolean, onTickFailure: (Throwable) -> Unit) {
        if (!tickScheduler.shouldTick()) return
        val delayMs = runCatching { orchestrator.tick(isResumed) }
            .onFailure(onTickFailure)
            .getOrDefault(ArSessionOrchestrator.PAUSED_POLL_INTERVAL_MS)
        tickScheduler.scheduleNextTick(delayMs)
    }

    /** Ticks unconditionally, bypassing [TickScheduler] entirely — for one-shot flushes that must
     * not be skipped by rate limiting meant only to pace normal per-frame ticking. Does not
     * schedule anything, so it has no effect on [tickIfScheduled]'s own gating. */
    @Suppress("TooGenericExceptionCaught")
    fun forceTick(isResumed: Boolean, onTickFailure: (Throwable) -> Unit) {
        runCatching { orchestrator.tick(isResumed) }.onFailure(onTickFailure)
    }
}
