package com.volta.app.domain.capture

import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Named

class FrameCaptureController
    @Inject
    constructor(
        @Named("Default") private val dispatcher: CoroutineDispatcher,
    ) {
        // TODO: Implement combined angular-spacing + blur-detection capture trigger
    }
