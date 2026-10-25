package com.volta.app.data.update

import com.google.common.truth.Truth.assertThat
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

    @Test
    fun `accepts an apk with a matching checksum, package and higher versionCode`() {
        val result = verifier().verify(apk(), sha256Of(apkBytes))

        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `compares the checksum case-insensitively`() {
        val result = verifier().verify(apk(), sha256Of(apkBytes).uppercase())

        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `rejects a checksum mismatch`() {
        val result = verifier().verify(apk(), "00".repeat(32))

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `rejects an apk for a different package`() {
        val result = verifier(ApkPackageInfo("com.example.other", NEW_VERSION_CODE))
            .verify(apk(), sha256Of(apkBytes))

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `rejects an apk whose versionCode is not higher than the installed one`() {
        val same = verifier(ApkPackageInfo(PACKAGE_NAME, INSTALLED_VERSION_CODE))
        val older = verifier(ApkPackageInfo(PACKAGE_NAME, INSTALLED_VERSION_CODE - 1))

        assertThat(same.verify(apk(), sha256Of(apkBytes)).isFailure).isTrue()
        assertThat(older.verify(apk(), sha256Of(apkBytes)).isFailure).isTrue()
    }

    @Test
    fun `rejects a file the package manager cannot parse`() {
        val result = verifier(archive = null).verify(apk(), sha256Of(apkBytes))

        assertThat(result.isFailure).isTrue()
    }

    private companion object {
        const val PACKAGE_NAME = "com.volta.app"
        const val INSTALLED_VERSION_CODE = 202610001L
        const val NEW_VERSION_CODE = 202610002L
    }
}
