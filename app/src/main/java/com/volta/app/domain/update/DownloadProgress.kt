package com.volta.app.domain.update

/** A verified APK on local storage, ready to install. */
data class DownloadedApk(val path: String)

sealed interface DownloadProgress {
    /** [totalBytes] is null when the server does not report a content length. */
    data class InProgress(val bytesDownloaded: Long, val totalBytes: Long?) : DownloadProgress

    data class Completed(val apk: DownloadedApk) : DownloadProgress

    data class Failed(val cause: Throwable) : DownloadProgress
}
