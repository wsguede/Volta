package com.volta.app.domain.model

sealed interface TrackingState {
    data object Tracking : TrackingState
    data object Paused : TrackingState
    data object NotTracking : TrackingState
}
