package com.onetouch.remote

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import org.webrtc.*
import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer

/**
 * Manages the remote control channel over WebRTC DataChannel (preferred) or WSS fallback.
 */
class ControlChannelManager(
    private val appContext: Context,
    private val accessibilityBridge: RemoteAccessibilityBridge,
    private val scope: CoroutineScope
) {
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    private var webSocket: WebSocket? = null
    private val httpClient: OkHttpClient = OkHttpClient()

    private var expectedSeq: Long = 0L
    private var activeControllerId: String? = null

    fun initWebRtc(factory: PeerConnectionFactory, pc: PeerConnection) {
        peerConnectionFactory = factory
        peerConnection = pc
        val init = DataChannel.Init()
        init.ordered = true
        init.id = 0
        dataChannel = pc.createDataChannel("control", init)
        dataChannel?.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() { Log.d(TAG, "DC state: ${dataChannel?.state()}") }
            override fun onMessage(buffer: DataChannel.Buffer) {
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                val text = String(bytes, StandardCharsets.UTF_8)
                handleIncoming(text)
            }
        })
    }

    fun initWebSocketFallback(url: String) {
        val request = Request.Builder().url(url).build()
        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncoming(text)
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleIncoming(bytes.utf8())
            }
        })
    }

    private fun handleIncoming(text: String) {
        try {
            val msg = JSONObject(text)
            val seq = msg.getLong("seq")
            val type = msg.getString("type")
            val clientId = msg.optString("client_id", "")
            val payload = msg.getJSONObject("payload")
            // simple de-dupe
            if (seq < expectedSeq) { sendAck(seq, true, "duplicate"); return }
            expectedSeq = seq + 1

            // single active controller enforcement
            if (activeControllerId == null) {
                activeControllerId = clientId
            } else if (activeControllerId != clientId) {
                sendAck(seq, false, "controller_conflict")
                return
            }

            when (type) {
                "gesture" -> handleGesture(payload)
                "text" -> handleText(payload)
                "node_action" -> handleNodeAction(payload)
                "global_action" -> handleGlobalAction(payload)
                "call_request" -> handleCallRequest(payload)
                "sms_request" -> handleSmsRequest(payload)
                "app_launch" -> handleAppLaunch(payload)
                "media_action" -> handleMediaAction(payload)
                else -> sendAck(seq, false, "unknown_type")
            }
            sendAck(seq, true, null)

            // audit log (best-effort)
            try { AuditLogger.logAction(appContext, type, clientId, payload.toString()) } catch (_: Throwable) {}
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to handle message", t)
        }
    }

    private fun sendAck(seq: Long, ok: Boolean, error: String?) {
        val ack = JSONObject()
            .put("seq", System.currentTimeMillis())
            .put("type", "ack")
            .put("timestamp", System.currentTimeMillis())
            .put("client_id", "device")
            .put("payload", JSONObject().put("ack_seq", seq).put("status", if (ok) "ok" else "error").apply {
                if (!ok && error != null) put("error", error)
            })
        val data = ack.toString()
        dataChannel?.send(DataChannel.Buffer(ByteBuffer.wrap(data.toByteArray()), false))
        webSocket?.send(data)
    }

    private fun handleGesture(payload: JSONObject) {
        val kind = payload.getString("kind")
        when (kind) {
            "tap" -> {
                val p0 = payload.getJSONArray("points").getJSONObject(0)
                val x = p0.getDouble("x").toFloat()
                val y = p0.getDouble("y").toFloat()
                scope.launch(Dispatchers.Main) {
                    accessibilityBridge.tap(x, y)
                }
            }
            // TODO: swipe, long_press, multi_touch (coalesce points/duration)
        }
    }

    private fun handleText(payload: JSONObject) {
        val text = payload.optString("text", "")
        scope.launch(Dispatchers.Main) { accessibilityBridge.setTextOnFocused(text) }
    }

    private fun handleNodeAction(payload: JSONObject) {
        val action = payload.optString("action")
        val nodePath = payload.optString("node_path")
        scope.launch(Dispatchers.Main) { accessibilityBridge.performNodeAction(action, nodePath) }
    }

    private fun handleGlobalAction(payload: JSONObject) {
        val action = payload.optString("action")
        scope.launch(Dispatchers.Main) { accessibilityBridge.performGlobal(action) }
    }

    private fun handleCallRequest(payload: JSONObject) {
        val phone = payload.optString("phone")
        scope.launch(Dispatchers.Main) { accessibilityBridge.requestCall(phone) }
    }

    private fun handleSmsRequest(payload: JSONObject) {
        val phone = payload.optString("phone")
        val body = payload.optString("body")
        scope.launch(Dispatchers.Main) { accessibilityBridge.requestSms(phone, body) }
    }

    private fun handleAppLaunch(payload: JSONObject) {
        val pkg = payload.optString("package")
        scope.launch(Dispatchers.Main) { accessibilityBridge.launchApp(pkg) }
    }

    private fun handleMediaAction(payload: JSONObject) {
        val action = payload.optString("action")
        scope.launch(Dispatchers.Main) { accessibilityBridge.mediaControl(action) }
    }

    companion object { private const val TAG = "ControlChannel" }
}

interface RemoteAccessibilityBridge {
    suspend fun tap(x: Float, y: Float)
    suspend fun setTextOnFocused(text: String)
    suspend fun performNodeAction(action: String, nodePath: String)
    suspend fun performGlobal(action: String)
    suspend fun requestCall(phone: String)
}
