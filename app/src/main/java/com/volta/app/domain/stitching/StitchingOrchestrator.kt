package com.volta.app.domain.stitching

import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Named

class StitchingOrchestrator
@Inject
constructor(
    @Named("Default") private val dispatcher: CoroutineDispatcher,
) {
    // TODO: Orchestrate on-device OpenCV stitching pipeline; emit progress
}
