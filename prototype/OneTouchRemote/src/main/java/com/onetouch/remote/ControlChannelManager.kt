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
    private var authenticatedControllerId: String? = null

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

            // require authentication first
            if (type != "auth" && authenticatedControllerId != clientId) {
                sendAck(seq, false, "not_authenticated")
                return
            }

            when (type) {
                "auth" -> {
                    val token = payload.optString("token")
                    val streamId = payload.optString("stream_id")
                    val pin = payload.optString("pin")
                    // Validate token & PIN; require on-device consent
                    val ok = AuthManager.validateToken(appContext, token) && AuthManager.validatePin(appContext, streamId, pin)
                    if (!ok) { sendAck(seq, false, "auth_failed"); return }
                    val cm = ConsentManager.get(appContext)
                    if (!cm.isControllerAllowed(clientId)) {
                        // Launch consent UI; user must accept on device
                        val intent = android.content.Intent(appContext, SessionConsentActivity::class.java).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            putExtra(SessionConsentActivity.EXTRA_CONTROLLER_ID, clientId)
                        }
                        appContext.startActivity(intent)
                        // Ask client to retry after user consents
                        sendAck(seq, false, "consent_required")
                        return
                    }
                    authenticatedControllerId = clientId
                }
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
                scope.launch(Dispatchers.Main) { accessibilityBridge.tap(x, y) }
            }
            "swipe" -> {
                val arr = payload.getJSONArray("points")
                if (arr.length() >= 2) {
                    val p0 = arr.getJSONObject(0)
                    val p1 = arr.getJSONObject(arr.length() - 1)
                    val x0 = p0.getDouble("x").toFloat(); val y0 = p0.getDouble("y").toFloat()
                    val x1 = p1.getDouble("x").toFloat(); val y1 = p1.getDouble("y").toFloat()
                    val dur = payload.optLong("durationMs", 150L)
                    scope.launch(Dispatchers.Main) { accessibilityBridge.swipe(x0, y0, x1, y1, dur) }
                }
            }
            "long_press" -> {
                val p0 = payload.getJSONArray("points").getJSONObject(0)
                val x = p0.getDouble("x").toFloat()
                val y = p0.getDouble("y").toFloat()
                val dur = payload.optLong("durationMs", 600L)
                scope.launch(Dispatchers.Main) { accessibilityBridge.longPress(x, y, dur) }
            }
            "multi_touch" -> {
                val arr = payload.getJSONArray("points")
                val pts = mutableListOf<Pair<Float, Float>>()
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    pts.add(p.getDouble("x").toFloat() to p.getDouble("y").toFloat())
                }
                val dur = payload.optLong("durationMs", 120L)
                scope.launch(Dispatchers.Main) { accessibilityBridge.multiTouch(pts, dur) }
            }
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
    suspend fun swipe(x0: Float, y0: Float, x1: Float, y1: Float, duration: Long)
    suspend fun longPress(x: Float, y: Float, duration: Long)
    suspend fun multiTouch(points: List<Pair<Float, Float>>, duration: Long)
    suspend fun setTextOnFocused(text: String)
    suspend fun performNodeAction(action: String, nodePath: String)
    suspend fun performGlobal(action: String)
    suspend fun requestCall(phone: String)
    suspend fun requestSms(phone: String, body: String)
    suspend fun launchApp(package: String)
    suspend fun mediaControl(action: String)
}
