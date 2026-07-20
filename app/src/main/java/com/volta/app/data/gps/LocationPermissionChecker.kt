package com.volta.app.data.gps

fun interface LocationPermissionChecker {
    fun hasFineLocationPermission(): Boolean
}
