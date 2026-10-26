package com.volta.app.data.update

import com.google.common.truth.Truth.assertThat
import com.volta.app.domain.update.UpdateManifest
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkVerifierTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val apkBytes = ByteArray(10_000) { (it % 251).toByte() }

    private fun apk(): File = tempFolder.newFile().apply { writeBytes(apkBytes) }

    private fun verifier(
        archive: ApkPackageInfo? = ApkPackageInfo(PACKAGE_NAME, NEW_VERSION_CODE)
    ) = ApkVerifier(
        FakeApkInspector(
            installedPackage = ApkPackageInfo(PACKAGE_NAME, INSTALLED_VERSION_CODE),
            archive = archive
        )
    )

    private fun manifest(
        sha256: String = sha256Of(apkBytes),
        versionCode: Long = NEW_VERSION_CODE
    ) = UpdateManifest(
        schemaVersion = 1,
        versionCode = versionCode.toInt(),
        versionName = "2026.10.2",
        minSdk = 30,
        sha256 = sha256,
        apkUrl = "https://example.com/app-release.apk",
        notes = "notes"
    )

    @Test
    fun `accepts an apk with a matching checksum, package and higher versionCode`() {
        val result = verifier().verify(apk(), manifest())

        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `compares the checksum case-insensitively`() {
        val result = verifier().verify(apk(), manifest(sha256 = sha256Of(apkBytes).uppercase()))

        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `rejects a checksum mismatch`() {
        val result = verifier().verify(apk(), manifest(sha256 = "00".repeat(32)))

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `rejects an apk for a different package`() {
        val result = verifier(ApkPackageInfo("com.example.other", NEW_VERSION_CODE))
            .verify(apk(), manifest())

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `rejects an apk whose versionCode is not higher than the installed one`() {
        // The manifest agrees with each APK, so only the installed-version check can reject them.
        val same = verifier(ApkPackageInfo(PACKAGE_NAME, INSTALLED_VERSION_CODE))
            .verify(apk(), manifest(versionCode = INSTALLED_VERSION_CODE))
        val older = verifier(ApkPackageInfo(PACKAGE_NAME, INSTALLED_VERSION_CODE - 1))
            .verify(apk(), manifest(versionCode = INSTALLED_VERSION_CODE - 1))

        assertThat(same.isFailure).isTrue()
        assertThat(older.isFailure).isTrue()
    }

    @Test
    fun `rejects an apk whose versionCode differs from the manifest`() {
        val result = verifier(ApkPackageInfo(PACKAGE_NAME, NEW_VERSION_CODE + 1))
            .verify(apk(), manifest(versionCode = NEW_VERSION_CODE))

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `rejects a file the package manager cannot parse`() {
        val result = verifier(archive = null).verify(apk(), manifest())

        assertThat(result.isFailure).isTrue()
    }

    private companion object {
        const val PACKAGE_NAME = "com.volta.app"
        const val INSTALLED_VERSION_CODE = 202610001L
        const val NEW_VERSION_CODE = 202610002L
    }
}
