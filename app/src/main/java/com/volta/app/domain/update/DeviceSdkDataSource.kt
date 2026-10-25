package com.volta.app.domain.update

/** The Android API level of the device, kept behind an interface so `domain/` stays pure Kotlin. */
interface DeviceSdkDataSource {
    val sdkInt: Int
}
