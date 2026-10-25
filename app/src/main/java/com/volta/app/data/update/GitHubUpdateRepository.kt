package com.volta.app.data.update

import android.content.Context
import com.volta.app.di.IoDispatcher
import com.volta.app.domain.update.DownloadProgress
import com.volta.app.domain.update.DownloadedApk
import com.volta.app.domain.update.UpdateManifest
import com.volta.app.domain.update.UpdateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Fetches `update.json` from the latest GitHub release and downloads the APK it points to
 * (ADR 0018). This is the app's only network caller; never call it from capture, stitching, or
 * export.
 */
class GitHubUpdateRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val httpClient: UpdateHttpClient,
    private val parser: UpdateManifestParser,
    private val verifier: ApkVerifier,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : UpdateRepository {

    private val updatesDir = File(context.cacheDir, UPDATES_DIR_NAME)
    private val downloadMutex = Mutex()

    @Suppress("TooGenericExceptionCaught")
    override suspend fun fetchLatestManifest(): Result<UpdateManifest> = withContext(ioDispatcher) {
        try {
            val json = httpClient.open(MANIFEST_URL).use { readManifest(it.body) }
            parser.parse(json).onFailure { Timber.w(it, "Update manifest is invalid") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // Offline is the common case; launch checks fail quietly (ADR 0018).
            Timber.d(e, "Update manifest fetch failed")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.w(e, "Update manifest fetch failed")
            Result.failure(e)
        }
    }

    override fun download(manifest: UpdateManifest): Flow<DownloadProgress> = flow {
        // One download at a time: each one empties updatesDir, which would delete a running
        // download's file. Completed is emitted under the lock so the next download can't delete
        // the file before the collector has its path.
        downloadMutex.withLock {
            val apk = downloadAndVerify(manifest)
            // Emitted outside downloadAndVerify: a collector that stops at Completed must not
            // delete the verified file.
            emit(DownloadProgress.Completed(DownloadedApk(apk.absolutePath)))
        }
    }.catch { error ->
        if (error is IOException) {
            // Usually offline or a dropped connection; the UI shows its own retryable message.
            Timber.d(error, "Update download failed")
        } else {
            Timber.w(error, "Update download failed")
        }
        emit(DownloadProgress.Failed(error))
    }
        // Rendezvous: the download waits for the collector at each progress step, so a collector
        // that stops early cancels it there instead of racing ahead to finish the file.
        .buffer(Channel.RENDEZVOUS)
        .flowOn(ioDispatcher)

    private suspend fun FlowCollector<DownloadProgress>.downloadAndVerify(
        manifest: UpdateManifest
    ): File {
        require(UpdateManifestParser.isHttpsUrl(manifest.apkUrl)) { "apkUrl must use https" }

        // Emptied first, so a leftover file is never mistaken for the new APK.
        updatesDir.deleteRecursively()
        if (!updatesDir.mkdirs()) throw IOException("Could not create $updatesDir")
        val apk = File(updatesDir, "volta-${manifest.versionCode}.apk")

        var verified = false
        try {
            httpClient.open(manifest.apkUrl).use { response ->
                copyWithProgress(response.body, apk, response.contentLength)
            }
            verifier.verify(apk, manifest).getOrThrow()
            verified = true
        } finally {
            // Covers failures and cancellation: no partial or unverified file is left behind.
            if (!verified) deleteQuietly(apk)
        }
        return apk
    }

    /**
     * Copies [input] to [target], emitting progress every [PROGRESS_STEP_BYTES]. Fails once more
     * bytes arrive than the server announced, or than [MAX_APK_BYTES] when it announced none, so
     * a bad response can't fill the disk.
     */
    private suspend fun FlowCollector<DownloadProgress>.copyWithProgress(
        input: InputStream,
        target: File,
        totalBytes: Long?
    ) {
        val maxBytes = totalBytes ?: MAX_APK_BYTES
        var bytesDownloaded = 0L
        var lastReported = 0L
        emit(DownloadProgress.InProgress(bytesDownloaded, totalBytes))
        target.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                // Blocking reads can't be interrupted, so cancellation takes effect here, after
                // the current read returns (at most the connection's read timeout).
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                bytesDownloaded += read
                if (bytesDownloaded >
                    maxBytes
                ) {
                    throw IOException("Update download exceeds $maxBytes bytes")
                }
                output.write(buffer, 0, read)
                if (bytesDownloaded - lastReported >= PROGRESS_STEP_BYTES) {
                    lastReported = bytesDownloaded
                    emit(DownloadProgress.InProgress(bytesDownloaded, totalBytes))
                }
            }
        }
        val hasUnreportedBytes = lastReported != bytesDownloaded
        if (hasUnreportedBytes) emit(DownloadProgress.InProgress(bytesDownloaded, totalBytes))
    }

    private fun deleteQuietly(file: File) {
        if (file.exists() && !file.delete()) Timber.w("Failed to delete update file %s", file)
    }

    /** Reads at most [MAX_MANIFEST_BYTES], so a bad response can't exhaust memory. */
    private fun readManifest(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            if (output.size() >
                MAX_MANIFEST_BYTES
            ) {
                throw IOException("Update manifest is too large")
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    companion object {
        const val MANIFEST_URL =
            "https://github.com/wsguede/Volta/releases/latest/download/update.json"
        const val MAX_MANIFEST_BYTES = 64 * 1024
        private const val UPDATES_DIR_NAME = "updates"
        const val PROGRESS_STEP_BYTES = 64 * 1024

        /** Far above any real Volta APK; only applies when the server sends no length. */
        private const val MAX_APK_BYTES = 512L * 1024 * 1024
    }
}
