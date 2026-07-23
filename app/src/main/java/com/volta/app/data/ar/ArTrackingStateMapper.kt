package com.volta.app.data.ar

import com.volta.app.domain.model.TrackingState
import com.google.ar.core.TrackingState as ArCoreTrackingState

internal fun ArCoreTrackingState.toDomain(): TrackingState = when (this) {
    ArCoreTrackingState.TRACKING -> TrackingState.Tracking
    ArCoreTrackingState.PAUSED -> TrackingState.Paused
    ArCoreTrackingState.STOPPED -> TrackingState.NotTracking
}
