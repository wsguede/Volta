package com.volta.app.data.update

import java.io.File

/** Stands in for PackageManager, which Robolectric can't use to parse real APKs (ADR 0019). */
class FakeApkInspector(
    override val installedPackage: ApkPackageInfo,
    private val archive: ApkPackageInfo?,
    /** Runs while the APK is being inspected, so tests can act mid-verification. */
    private val onInspect: () -> Unit = {}
) : ApkInspector {
    override fun inspectArchive(apk: File): ApkPackageInfo? {
        onInspect()
        return archive
    }
}
