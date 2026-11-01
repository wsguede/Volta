package com.volta.app.domain.update

/**
 * Hands a verified APK to the platform installer. Implemented and bound in #59 (ADR 0018).
 *
 * The implementation deletes the APK after any terminal install status, and in a
 * `MY_PACKAGE_REPLACED` receiver because a successful install can end this process.
 */
interface UpdateInstaller {
    suspend fun install(apk: DownloadedApk): InstallResult
}
