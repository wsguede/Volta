package com.volta.app.data.update

import java.io.File

/** Stands in for PackageManager, which Robolectric can't use to parse real APKs (ADR 0019). */
class FakeApkInspector(
    override val installedPackage: ApkPackageInfo,
    private val archive: ApkPackageInfo?
) : ApkInspector {
    override fun inspectArchive(apk: File): ApkPackageInfo? = archive
}
