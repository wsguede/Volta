package com.volta.app.domain.update

import kotlinx.coroutines.flow.Flow

/**
 * The updater's only network and package-install boundary, implemented in `data/update/`.
 * Never call it from the capture, stitching, or export paths, which must stay offline.
 */
interface UpdateRepository {
    /** Fetches the latest release manifest. Offline or malformed responses are a failure. */
    suspend fun fetchLatestManifest(): Result<UpdateManifest>

    fun download(manifest: UpdateManifest): Flow<DownloadProgress>

    suspend fun install(apk: DownloadedApk): InstallResult
}
