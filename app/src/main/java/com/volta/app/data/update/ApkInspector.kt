package com.volta.app.data.update

import java.io.File

/** A package name and version code, read from the installed app or from an APK file. */
data class ApkPackageInfo(val packageName: String, val versionCode: Long)

/**
 * Seam over `PackageManager`, so [ApkVerifier] can be tested with fakes. Robolectric's
 * `getPackageArchiveInfo` does not parse real APKs (ADR 0019).
 */
interface ApkInspector {
    val installedPackage: ApkPackageInfo

    /** Returns null when the file is not an APK the package manager can parse. */
    fun inspectArchive(apk: File): ApkPackageInfo?
}
