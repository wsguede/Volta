package com.volta.app.domain.update

import kotlinx.coroutines.flow.Flow

/**
 * The updater's only network boundary, implemented in `data/update/`. Never call it from the
 * capture, stitching, or export paths, which must stay offline. Installing is a separate
 * boundary, [UpdateInstaller].
 */
interface UpdateRepository {
    /**
     * Fetches the latest release manifest.
     *
     * I/O and parse errors are returned as [Result.failure], never thrown. Cancellation is the
     * exception: implementations must rethrow [kotlinx.coroutines.CancellationException] rather
     * than wrap it, so don't build this on a bare `runCatching`. Report timeouts as
     * [Result.failure] too: a `withTimeout` here would throw `TimeoutCancellationException`, which
     * looks like the caller being cancelled, so use the connection's own timeouts instead.
     */
    suspend fun fetchLatestManifest(): Result<UpdateManifest>

    /**
     * Downloads and verifies the APK for [manifest]. Errors are emitted as
     * [DownloadProgress.Failed], which ends the flow; the flow itself throws only on cancellation.
     * A cancelled or failed download leaves no partial or unverified file behind. Downloads run
     * one at a time.
     *
     * The verified APK in [DownloadProgress.Completed] stays only until the next download starts,
     * which replaces it. Callers must finish [UpdateInstaller.install] before starting another
     * download; the installer deletes the file when it is done (#59).
     *
     * The flow is cold. A ViewModel that needs current progress converts it to a `StateFlow`.
     */
    fun download(manifest: UpdateManifest): Flow<DownloadProgress>
}
