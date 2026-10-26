package com.volta.app.data.update

import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/**
 * `HttpURLConnection` follows redirects within the same protocol only, so it should never move an
 * https request to plain http. The final URL is checked anyway, since this is the app's only
 * network client.
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
            // check(), not IOException: callers treat IOException as offline, and a 404 or 403
            // is a real failure.
            check(status == HttpURLConnection.HTTP_OK) { "HTTP $status for $url" }
            check(connection.url.protocol == "https") { "$url was redirected to ${connection.url}" }
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
