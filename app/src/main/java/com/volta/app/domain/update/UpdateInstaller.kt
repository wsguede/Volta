package com.volta.app.domain.update

/** Hands a verified APK to the platform installer. Implemented and bound in #59 (ADR 0018). */
interface UpdateInstaller {
    suspend fun install(apk: DownloadedApk): InstallResult
}
