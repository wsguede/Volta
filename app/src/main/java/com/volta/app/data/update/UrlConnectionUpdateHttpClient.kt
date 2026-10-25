package com.volta.app.data.update

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/**
 * `HttpURLConnection` follows redirects within the same protocol only, so an https request is
 * never redirected to plain http.
 */
class UrlConnectionUpdateHttpClient @Inject constructor() : UpdateHttpClient {

    override fun open(url: String): UpdateHttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) throw IOException("HTTP $status for $url")
            return UpdateHttpResponse(
                body = connection.inputStream,
                contentLength = connection.contentLengthLong.takeIf { it >= 0 },
                onClose = connection::disconnect
            )
        } catch (e: IOException) {
            connection.disconnect()
            throw e
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000
    }
}
