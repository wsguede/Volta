package com.volta.app.data.update

import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.volta.app.domain.update.DownloadProgress
import com.volta.app.domain.update.DownloadedApk
import com.volta.app.domain.update.UpdateManifest
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executors
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import timber.log.Timber

// Robolectric supplies a real Context.cacheDir and real org.json (ADR 0019).
@RunWith(RobolectricTestRunner::class)
class GitHubUpdateRepositoryTest {

    private val context = RuntimeEnvironment.getApplication()
    private val updatesDir = File(context.cacheDir, "updates")
    private val ioThreadName = "fake-io"
    private val ioDispatcher: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { Thread(it, ioThreadName) }.asCoroutineDispatcher()
    private val httpClient = FakeUpdateHttpClient()

    private val apkBytes = ByteArray(200 * 1024) { (it % 251).toByte() }

    private fun repository(
        archive: ApkPackageInfo? = ApkPackageInfo(PACKAGE_NAME, NEW_VERSION_CODE),
        dispatcher: CoroutineDispatcher = ioDispatcher,
        onInspect: () -> Unit = {}
    ) = GitHubUpdateRepository(
        context = context,
        httpClient = httpClient,
        parser = UpdateManifestParser(),
        verifier = ApkVerifier(
            FakeApkInspector(
                installedPackage = ApkPackageInfo(PACKAGE_NAME, INSTALLED_VERSION_CODE),
                archive = archive,
                onInspect = onInspect
            )
        ),
        ioDispatcher = dispatcher
    )

    private val logs = RecordingTree()

    @Before
    fun setUp() {
        Timber.plant(logs)
    }

    @After
    fun tearDown() {
        Timber.uproot(logs)
        ioDispatcher.close()
        updatesDir.deleteRecursively()
    }

    // --- fetchLatestManifest ---

    @Test
    fun `fetches and parses the latest manifest from the release asset url`() = runTest {
        httpClient.respond(GitHubUpdateRepository.MANIFEST_URL, manifestJson().toByteArray())

        val result = repository().fetchLatestManifest()

        assertThat(result.getOrThrow()).isEqualTo(manifest())
        assertThat(httpClient.requestThreads).containsExactly(ioThreadName)
    }

    @Test
    fun `fetch failure is returned, not thrown, when offline`() = runTest {
        httpClient.fail(GitHubUpdateRepository.MANIFEST_URL, IOException("offline"))

        val result = repository().fetchLatestManifest()

        assertThat(result.exceptionOrNull()).isInstanceOf(IOException::class.java)
        assertThat(logs.priorities).containsExactly(Log.DEBUG)
    }

    @Test
    fun `fetch fails on an invalid manifest`() = runTest {
        httpClient.respond(GitHubUpdateRepository.MANIFEST_URL, "{ not json".toByteArray())

        val result = repository().fetchLatestManifest()

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `fetch fails on an oversized manifest instead of reading it all`() = runTest {
        val padding = " ".repeat(GitHubUpdateRepository.MAX_MANIFEST_BYTES)
        httpClient.respond(
            GitHubUpdateRepository.MANIFEST_URL,
            (manifestJson() + padding).toByteArray()
        )

        val result = repository().fetchLatestManifest()

        assertThat(result.isFailure).isTrue()
        // A broken response is not an offline failure, so it is logged loudly.
        assertThat(logs.priorities).containsExactly(Log.WARN)
    }

    @Test
    fun `fetch logs an HTTP error status loudly`() = runTest {
        // The real client reports a non-200 status this way (see UrlConnectionUpdateHttpClient).
        httpClient.fail(GitHubUpdateRepository.MANIFEST_URL, IllegalStateException("HTTP 404"))

        val result = repository().fetchLatestManifest()

        assertThat(result.isFailure).isTrue()
        assertThat(logs.priorities).containsExactly(Log.WARN)
    }

    @Test
    fun `fetch logs a TLS failure loudly, though it is an IOException`() = runTest {
        httpClient.fail(
            GitHubUpdateRepository.MANIFEST_URL,
            SSLHandshakeException("bad certificate")
        )

        val result = repository().fetchLatestManifest()

        assertThat(result.exceptionOrNull()).isInstanceOf(SSLHandshakeException::class.java)
        assertThat(logs.priorities).containsExactly(Log.WARN)
    }

    @Test
    fun `fetch rethrows cancellation instead of returning it as a failure`() = runTest {
        httpClient.fail(GitHubUpdateRepository.MANIFEST_URL, CancellationException("cancelled"))

        val thrown = runCatching { repository().fetchLatestManifest() }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(CancellationException::class.java)
    }

    // --- download ---

    @Test
    fun `downloads into the updates cache dir named after the versionCode`() = runTest {
        httpClient.respond(APK_URL, apkBytes)

        val events = repository().download(manifest()).toList()

        val expected = File(updatesDir, "volta-$NEW_VERSION_CODE.apk")
        assertThat(
            events.last()
        ).isEqualTo(DownloadProgress.Completed(DownloadedApk(expected.absolutePath)))
        assertThat(expected.readBytes()).isEqualTo(apkBytes)
        assertThat(httpClient.requestThreads).containsExactly(ioThreadName)
    }

    @Test
    fun `emits progress with the reported total`() = runTest {
        httpClient.respond(APK_URL, apkBytes)

        val progress = repository().download(
            manifest()
        ).toList().filterIsInstance<DownloadProgress.InProgress>()

        assertThat(
            progress.first()
        ).isEqualTo(DownloadProgress.InProgress(0, apkBytes.size.toLong()))
        assertThat(
            progress.last()
        ).isEqualTo(DownloadProgress.InProgress(apkBytes.size.toLong(), apkBytes.size.toLong()))
        assertThat(progress.map { it.bytesDownloaded }).isInStrictOrder()
    }

    @Test
    fun `empties the updates cache dir before downloading`() = runTest {
        updatesDir.mkdirs()
        val leftover = File(updatesDir, "volta-1.apk").apply { writeText("stale") }
        httpClient.respond(APK_URL, apkBytes)

        repository().download(manifest()).toList()

        assertThat(leftover.exists()).isFalse()
        assertThat(updatesDir.list()).asList().containsExactly("volta-$NEW_VERSION_CODE.apk")
    }

    @Test
    fun `checksum mismatch fails and deletes the file`() = runTest {
        httpClient.respond(APK_URL, apkBytes)

        val events = repository().download(manifest(sha256 = "00".repeat(32))).toList()

        assertThat(events.last()).isInstanceOf(DownloadProgress.Failed::class.java)
        assertThat(updatesDir.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `package verification failure fails and deletes the file`() = runTest {
        httpClient.respond(APK_URL, apkBytes)

        val events = repository(archive = ApkPackageInfo("com.example.other", NEW_VERSION_CODE))
            .download(manifest())
            .toList()

        assertThat(events.last()).isInstanceOf(DownloadProgress.Failed::class.java)
        assertThat(updatesDir.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `a broken connection mid-download fails and deletes the partial file`() = runTest {
        httpClient.respond(
            APK_URL,
            FailingInputStream(apkBytes, failAfter = 100 * 1024),
            apkBytes.size.toLong()
        )

        val events = repository().download(manifest()).toList()

        assertThat(
            (events.last() as DownloadProgress.Failed).cause
        ).isInstanceOf(IOException::class.java)
        assertThat(updatesDir.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `a failure to connect is emitted as Failed and logged quietly`() = runTest {
        httpClient.fail(APK_URL, IOException("offline"))

        val events = repository().download(manifest()).toList()

        assertThat(events.single()).isInstanceOf(DownloadProgress.Failed::class.java)
        assertThat(logs.priorities).containsExactly(Log.DEBUG)
    }

    @Test
    fun `a TLS failure during download is logged loudly`() = runTest {
        httpClient.fail(APK_URL, SSLPeerUnverifiedException("unverified peer"))

        val events = repository().download(manifest()).toList()

        assertThat(events.single()).isInstanceOf(DownloadProgress.Failed::class.java)
        assertThat(logs.priorities).containsExactly(Log.WARN)
    }

    @Test
    fun `a collector that stops at Completed keeps the verified file`() = runTest {
        httpClient.respond(APK_URL, apkBytes)

        val completed = repository().download(manifest())
            .first { it is DownloadProgress.Completed } as DownloadProgress.Completed

        assertThat(File(completed.apk.path).readBytes()).isEqualTo(apkBytes)
    }

    @Test
    fun `cancellation during verification deletes the file`() = runTest {
        httpClient.respond(APK_URL, apkBytes)
        lateinit var download: Job
        // The inspector runs inside verification; cancelling there exercises that window.
        val repository = repository(onInspect = { download.cancel() })

        download = launch { repository.download(manifest()).collect() }
        download.join()

        assertThat(updatesDir.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `a body longer than the reported length fails and deletes the file`() = runTest {
        httpClient.respond(APK_URL, ByteArrayInputStream(apkBytes), contentLength = 100 * 1024L)

        val events = repository().download(manifest()).toList()

        assertThat(events.last()).isInstanceOf(DownloadProgress.Failed::class.java)
        assertThat(updatesDir.listFiles().orEmpty()).isEmpty()
        // A bad response is not an offline failure, so it is logged loudly.
        assertThat(logs.priorities).containsExactly(Log.WARN)
    }

    @Test
    fun `an announced length above the size cap fails before writing anything`() = runTest {
        httpClient.respond(
            APK_URL,
            ByteArrayInputStream(apkBytes),
            contentLength = GitHubUpdateRepository.MAX_APK_BYTES + 1
        )

        val events = repository().download(manifest()).toList()

        assertThat(events.single()).isInstanceOf(DownloadProgress.Failed::class.java)
        assertThat(updatesDir.listFiles().orEmpty()).isEmpty()
        assertThat(logs.priorities).containsExactly(Log.WARN)
    }

    @Test
    fun `a second download waits for the first instead of replacing its file`() = runTest {
        httpClient.respond(APK_URL, apkBytes)
        // The test scheduler controls both downloads, so the interleaving below is exact.
        val repository = repository(dispatcher = StandardTestDispatcher(testScheduler))
        val step = GitHubUpdateRepository.PROGRESS_STEP_BYTES
        val lastStep = (apkBytes.size - apkBytes.size % step).toLong()
        val releaseFirst = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()

        // Paused here, the first download has written its whole file but not verified it yet.
        val first = async {
            repository.download(manifest()).pauseAt(releaseFirst) { it == lastStep }.toList().last()
        }
        runCurrent()
        // Paused here, the second download would leave a partial file at the same path.
        val second = async {
            repository.download(manifest()).pauseAt(releaseSecond) { it > 0 }.toList().last()
        }
        runCurrent()
        releaseFirst.complete(Unit)
        runCurrent()
        releaseSecond.complete(Unit)

        val expected = DownloadProgress.Completed(
            DownloadedApk(File(updatesDir, "volta-$NEW_VERSION_CODE.apk").absolutePath)
        )
        assertThat(first.await()).isEqualTo(expected)
        assertThat(second.await()).isEqualTo(expected)
        assertThat(File(expected.apk.path).readBytes()).isEqualTo(apkBytes)
    }

    /** Suspends collection at matching progress events until [gate] completes. */
    private fun Flow<DownloadProgress>.pauseAt(
        gate: CompletableDeferred<Unit>,
        bytes: (Long) -> Boolean
    ) = onEach { if (it is DownloadProgress.InProgress && bytes(it.bytesDownloaded)) gate.await() }

    @Test
    fun `refuses a non-https apkUrl without connecting`() = runTest {
        val events = repository().download(manifest(apkUrl = "http://example.com/app.apk")).toList()

        assertThat(events.single()).isInstanceOf(DownloadProgress.Failed::class.java)
        assertThat(httpClient.requestThreads).isEmpty()
    }

    @Test
    fun `cancellation deletes the partial file`() = runTest {
        httpClient.respond(APK_URL, apkBytes)

        // first() cancels the download once it sees bytes on disk.
        repository().download(manifest()).first {
            it is DownloadProgress.InProgress &&
                it.bytesDownloaded > 0
        }

        assertThat(updatesDir.listFiles().orEmpty()).isEmpty()
    }

    private fun manifest(sha256: String = sha256Of(apkBytes), apkUrl: String = APK_URL) =
        UpdateManifest(
            schemaVersion = 1,
            versionCode = NEW_VERSION_CODE.toInt(),
            versionName = "2026.10.2",
            minSdk = 30,
            sha256 = sha256,
            apkUrl = apkUrl,
            notes = "notes"
        )

    private fun manifestJson(): String = """
        {
          "schemaVersion": 1,
          "versionCode": $NEW_VERSION_CODE,
          "versionName": "2026.10.2",
          "minSdk": 30,
          "sha256": "${sha256Of(apkBytes)}",
          "apkUrl": "$APK_URL",
          "notes": "notes"
        }
    """.trimIndent()

    private class FakeUpdateHttpClient : UpdateHttpClient {
        private val responses = mutableMapOf<String, () -> UpdateHttpResponse>()
        val requestThreads = mutableListOf<String>()

        fun respond(url: String, body: ByteArray) {
            responses[url] = { UpdateHttpResponse(ByteArrayInputStream(body), body.size.toLong()) }
        }

        fun respond(url: String, body: InputStream, contentLength: Long?) {
            responses[url] = { UpdateHttpResponse(body, contentLength) }
        }

        fun fail(url: String, error: Throwable) {
            responses[url] = { throw error }
        }

        override fun open(url: String): UpdateHttpResponse {
            // Coroutine debug mode appends " @coroutine#N" to the thread name.
            requestThreads += Thread.currentThread().name.substringBefore(" @")
            return checkNotNull(responses[url]) { "Unexpected request to $url" }()
        }
    }

    private class RecordingTree : Timber.Tree() {
        val priorities = mutableListOf<Int>()

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            priorities += priority
        }
    }

    /** Serves [bytes] but throws once [failAfter] bytes have been read, like a dropped connection. */
    private class FailingInputStream(bytes: ByteArray, private val failAfter: Int) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)
        private var read = 0

        override fun read(): Int {
            if (read >= failAfter) throw IOException("connection reset")
            return delegate.read().also { if (it >= 0) read++ }
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (read >= failAfter) throw IOException("connection reset")
            return delegate.read(b, off, minOf(len, failAfter - read)).also {
                if (it >
                    0
                ) {
                    read += it
                }
            }
        }
    }

    private companion object {
        const val PACKAGE_NAME = "com.volta.app"
        const val INSTALLED_VERSION_CODE = 202610001L
        const val NEW_VERSION_CODE = 202610002L
        const val APK_URL =
            "https://github.com/wsguede/Volta/releases/download/v2026.10.2/app-release.apk"
    }
}
