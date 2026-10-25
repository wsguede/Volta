package com.volta.app.domain.time

/** Wall-clock time, injected so time-based policies can be tested. */
fun interface Clock {
    fun nowMillis(): Long
}
