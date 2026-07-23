package com.volta.app.data.ar

import com.google.common.truth.Truth.assertThat
import com.volta.app.domain.model.TrackingState
import org.junit.Test
import com.google.ar.core.TrackingState as ArCoreTrackingState

class ArTrackingStateMapperTest {

    @Test
    fun `maps TRACKING to Tracking`() {
        assertThat(ArCoreTrackingState.TRACKING.toDomain()).isEqualTo(TrackingState.Tracking)
    }

    @Test
    fun `maps PAUSED to Paused`() {
        assertThat(ArCoreTrackingState.PAUSED.toDomain()).isEqualTo(TrackingState.Paused)
    }

    @Test
    fun `maps STOPPED to NotTracking`() {
        assertThat(ArCoreTrackingState.STOPPED.toDomain()).isEqualTo(TrackingState.NotTracking)
    }
}
