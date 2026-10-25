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
     * A cancelled or failed download leaves no partial or unverified file behind; a cancelled
     * download stops after the current blocking read, so the delete can lag by up to the
     * connection's read timeout. A verified APK stays until the next download or until the
     * installer cleans it up.
     */
    fun download(manifest: UpdateManifest): Flow<DownloadProgress>
}
