package com.volta.app.data.update

import java.io.File
import java.security.MessageDigest

/** Stands in for PackageManager, which Robolectric can't use to parse real APKs (ADR 0019). */
class FakeApkInspector(
    override val installedPackage: ApkPackageInfo,
    private val archive: ApkPackageInfo?
) : ApkInspector {
    override fun inspectArchive(apk: File): ApkPackageInfo? = archive
}

fun sha256Of(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
