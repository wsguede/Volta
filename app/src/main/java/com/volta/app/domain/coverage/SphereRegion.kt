package com.volta.app.domain.coverage

// TODO(#22): Migrate FrameAnalyzer and CapturedFrame to DevicePose (radians) and remove this
// type. SphereRegion predates DevicePose and uses degrees; having two orientation types with
// different unit conventions is a misuse hazard.
data class SphereRegion(val azimuthDegrees: Float, val elevationDegrees: Float)
