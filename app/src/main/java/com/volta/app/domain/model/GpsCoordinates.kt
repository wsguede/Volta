package com.volta.app.domain.model

data class GpsCoordinates(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val accuracyMeters: Float
) {
    fun isAccurateEnough(): Boolean = accuracyMeters <= MAX_ACCEPTABLE_HORIZONTAL_ACCURACY_METERS

    companion object {
        const val MAX_ACCEPTABLE_HORIZONTAL_ACCURACY_METERS = 50f
    }
}
