package com.onetouch.remote

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * Minimal placeholder for a local secure WebSocket control server.
 * For production, use a proper embedded server or reuse existing HTTP server with TLS.
 */
class LocalControlServer(
    private val scope: CoroutineScope,
    private val onMessage: (String) -> Unit
) {
    private val client = OkHttpClient()

    fun connect(url: String) {
        val wsReq = Request.Builder().url(url).build()
        client.newWebSocket(wsReq, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) { onMessage(text) }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) { onMessage(bytes.utf8()) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                Log.e(TAG, "WSS failure", t)
            }
        })
    }

    companion object { private const val TAG = "LocalControlServer" }
}
