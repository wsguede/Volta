package com.volta.app.data.update

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/**
 * `HttpURLConnection` follows redirects within the same protocol only, so an https request is
 * never redirected to plain http.
 */
class UrlConnectionUpdateHttpClient internal constructor(
    private val openConnection: (String) -> HttpURLConnection
) : UpdateHttpClient {

    @Inject
    constructor() : this({ url -> URL(url).openConnection() as HttpURLConnection })

    override fun open(url: String): UpdateHttpResponse {
        val connection = openConnection(url)
        var opened = false
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) throw IOException("HTTP $status for $url")
            val response = UpdateHttpResponse(
                body = connection.inputStream,
                contentLength = connection.contentLengthLong.takeIf { it >= 0 },
                onClose = connection::disconnect
            )
            opened = true
            return response
        } finally {
            // Any failure, not only IOException, must release the connection.
            if (!opened) connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000
    }
}
