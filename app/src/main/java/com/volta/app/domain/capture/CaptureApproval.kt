package com.volta.app.domain.capture

import com.volta.app.domain.model.DevicePose

/**
 * Capability token proving a pose passed [FrameCaptureTrigger.evaluate]'s angular-spacing and
 * sharpness checks. Only [FrameCaptureTrigger.evaluate] can construct one, so
 * [FrameCaptureTrigger.record] cannot be called with a frame the trigger never approved.
 */
class CaptureApproval internal constructor(internal val pose: DevicePose)
