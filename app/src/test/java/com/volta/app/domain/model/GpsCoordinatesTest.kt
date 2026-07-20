package com.volta.app.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GpsCoordinatesTest {

    @Test
    fun `holds altitude when provided`() {
        val coordinates = GpsCoordinates(
            latitude = 47.6062,
            longitude = -122.3321,
            altitude = 56.4,
            accuracyMeters = 10f
        )

        assertThat(coordinates.altitude).isEqualTo(56.4)
    }

    @Test
    fun `altitude may be absent`() {
        val coordinates = GpsCoordinates(
            latitude = 47.6062,
            longitude = -122.3321,
            altitude = null,
            accuracyMeters = 10f
        )

        assertThat(coordinates.altitude).isNull()
    }

    @Test
    fun `is accurate enough at exactly the threshold`() {
        val coordinates = GpsCoordinates(
            latitude = 0.0,
            longitude = 0.0,
            altitude = null,
            accuracyMeters = GpsCoordinates.MAX_ACCEPTABLE_HORIZONTAL_ACCURACY_METERS
        )

        assertThat(coordinates.isAccurateEnough()).isTrue()
    }

    @Test
    fun `is not accurate enough above the threshold`() {
        val coordinates = GpsCoordinates(
            latitude = 0.0,
            longitude = 0.0,
            altitude = null,
            accuracyMeters = GpsCoordinates.MAX_ACCEPTABLE_HORIZONTAL_ACCURACY_METERS + 0.1f
        )

        assertThat(coordinates.isAccurateEnough()).isFalse()
    }

    @Test
    fun `threshold constant is fifty meters`() {
        assertThat(GpsCoordinates.MAX_ACCEPTABLE_HORIZONTAL_ACCURACY_METERS).isEqualTo(50f)
    }
}
