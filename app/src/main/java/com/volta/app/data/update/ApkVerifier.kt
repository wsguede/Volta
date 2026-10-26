package com.volta.app.data.update

import com.volta.app.domain.update.UpdateManifest
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

/**
 * Checks a downloaded APK before it is handed to the installer (ADR 0018): the SHA-256 must match
 * the manifest, and the APK must be the same package, with the manifest's versionCode, which must
 * be higher than the installed app's. Android's own signature check at install time rejects an
 * APK not signed with the release key.
 */
class ApkVerifier @Inject constructor(private val inspector: ApkInspector) {

    fun verify(apk: File, manifest: UpdateManifest): Result<Unit> = runCatching {
        val actualSha256 = sha256Hex(apk)
        check(actualSha256.equals(manifest.sha256, ignoreCase = true)) {
            "SHA-256 mismatch: expected ${manifest.sha256}, got $actualSha256"
        }

        val archive =
            checkNotNull(inspector.inspectArchive(apk)) { "Downloaded file is not a parsable APK" }
        val installed = inspector.installedPackage
        check(archive.packageName == installed.packageName) {
            "APK package ${archive.packageName} does not match ${installed.packageName}"
        }
        // Catches a release whose manifest disagrees with its APK; the update offer is based on
        // the manifest's versionCode.
        check(archive.versionCode == manifest.versionCode.toLong()) {
            "APK versionCode ${archive.versionCode} does not match manifest ${manifest.versionCode}"
        }
        check(archive.versionCode > installed.versionCode) {
            "APK versionCode ${archive.versionCode} is not higher than installed ${installed.versionCode}"
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
