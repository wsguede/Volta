package com.volta.app.data.update

import java.io.Closeable
import java.io.InputStream

/** The body of a successful response. Closing it closes the stream and the connection. */
class UpdateHttpResponse(
    val body: InputStream,
    /** Null when the server does not report a content length. */
    val contentLength: Long?,
    private val onClose: () -> Unit = {}
) : Closeable {
    override fun close() {
        try {
            body.close()
        } finally {
            onClose()
        }
    }
}

/** Seam over `HttpURLConnection`, so the repository can be tested without a network. */
interface UpdateHttpClient {
    /**
     * Sends a blocking GET to [url], following redirects. Throws `IOException` on network errors
     * and timeouts. Throws `IllegalStateException` for any status other than 200, or when a
     * redirect leaves https. Call it on the IO dispatcher.
     */
    fun open(url: String): UpdateHttpResponse
}
