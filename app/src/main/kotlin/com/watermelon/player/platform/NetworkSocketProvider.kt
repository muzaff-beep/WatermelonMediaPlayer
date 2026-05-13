// app/src/main/kotlin/com/watermelon/player/platform/NetworkSocketProvider.kt
// Provides network sockets to the Rust engine for HTTP/HLS streaming.
// Manifesto §1.2: Rust owns the buffer; Kotlin provides the socket via JNI.

package com.watermelon.player.platform

import android.util.Log
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Creates and manages HTTP connections on behalf of the Rust streaming client.
 * The Rust core calls back through JNI to request a socket for a given URI.
 * Returns an InputStream that Rust reads directly.
 */
class NetworkSocketProvider {

    private val TAG = "NetworkSocketProvider"
    private val executor = Executors.newCachedThreadPool()
    private var activeConnection: HttpURLConnection? = null

    /**
     * Open a connection to the given URI and return an InputStream.
     * Called from Rust JNI callback. Runs blocking on the calling thread.
     *
     * @param uri the media URI to connect to
     * @param requestHeaders optional HTTP headers (e.g. Range, User-Agent)
     * @return InputStream for reading response body, or null on failure
     */
    fun openStream(uri: String, requestHeaders: Map<String, String>?): InputStream? {
        return try {
            val url = URL(uri)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                requestHeaders?.forEach { (key, value) ->
                    setRequestProperty(key, value)
                }
            }
            connection.connect()
            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                activeConnection = connection
                Log.d(TAG, "Connected to $uri (HTTP $responseCode)")
                connection.inputStream
            } else {
                Log.w(TAG, "HTTP error $responseCode for $uri")
                connection.disconnect()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open stream for $uri: ${e.message}")
            null
        }
    }

    /**
     * Get the content length reported by the server, or -1 if unknown.
     */
    fun getContentLength(): Long {
        return activeConnection?.contentLength?.toLong() ?: -1L
    }

    /**
     * Get the MIME type reported by the server, or null if unknown.
     */
    fun getContentType(): String? {
        return activeConnection?.contentType
    }

    /**
     * Close the active connection and release resources.
     */
    fun close() {
        try {
            activeConnection?.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing connection: ${e.message}")
        }
        activeConnection = null
    }

    /**
     * Shutdown the executor and release all resources.
     */
    fun shutdown() {
        close()
        executor.shutdown()
    }
}