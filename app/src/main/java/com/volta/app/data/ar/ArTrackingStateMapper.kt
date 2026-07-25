package com.volta.app.data.ar

import com.google.ar.core.TrackingState as ArCoreTrackingState
import com.volta.app.domain.model.TrackingState

internal fun ArCoreTrackingState.toDomain(): TrackingState = when (this) {
    ArCoreTrackingState.TRACKING -> TrackingState.Tracking
    ArCoreTrackingState.PAUSED -> TrackingState.Paused
    ArCoreTrackingState.STOPPED -> TrackingState.NotTracking
}
