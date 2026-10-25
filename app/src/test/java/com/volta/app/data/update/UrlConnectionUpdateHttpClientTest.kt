package com.volta.app.data.update

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import org.junit.Assert.assertThrows
import org.junit.Test

class UrlConnectionUpdateHttpClientTest {

    private val connection = mockk<HttpURLConnection>(relaxed = true)
    private val client = UrlConnectionUpdateHttpClient { url ->
        assertThat(url).isEqualTo(URL)
        connection
    }

    @Test
    fun `follows redirects with 10 second timeouts`() {
        every { connection.responseCode } returns HttpURLConnection.HTTP_OK

        client.open(URL)

        verify { connection.instanceFollowRedirects = true }
        verify { connection.connectTimeout = 10_000 }
        verify { connection.readTimeout = 10_000 }
    }

    @Test
    fun `returns the body and content length, and disconnects on close`() {
        val body = ByteArrayInputStream(byteArrayOf(1, 2, 3))
        every { connection.responseCode } returns HttpURLConnection.HTTP_OK
        every { connection.inputStream } returns body
        every { connection.contentLengthLong } returns 3L

        val response = client.open(URL)
        response.close()

        assertThat(response.body).isSameInstanceAs(body)
        assertThat(response.contentLength).isEqualTo(3L)
        verify { connection.disconnect() }
    }

    @Test
    fun `reports an unknown content length as null`() {
        every { connection.responseCode } returns HttpURLConnection.HTTP_OK
        every { connection.contentLengthLong } returns -1L

        assertThat(client.open(URL).contentLength).isNull()
    }

    @Test
    fun `throws IOException and disconnects on a non-200 status`() {
        every { connection.responseCode } returns HttpURLConnection.HTTP_NOT_FOUND

        assertThrows(IOException::class.java) { client.open(URL) }
        verify { connection.disconnect() }
    }

    @Test
    fun `disconnects when opening fails with a runtime exception`() {
        every { connection.responseCode } throws SecurityException("no INTERNET permission")

        assertThrows(SecurityException::class.java) { client.open(URL) }
        verify { connection.disconnect() }
    }

    private companion object {
        const val URL = "https://example.com/update.json"
    }
}
