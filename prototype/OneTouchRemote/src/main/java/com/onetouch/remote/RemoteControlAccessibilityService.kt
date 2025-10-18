package com.onetouch.remote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.net.Uri
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class RemoteControlAccessibilityService : AccessibilityService(), RemoteAccessibilityBridge {

    override fun onServiceConnected() {
        // Service connected; user enabled manually in settings
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) { }

    override fun onInterrupt() { }

    // --- RemoteAccessibilityBridge implementation ---
    override suspend fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 60)
        performGesture(GestureDescription.Builder().addStroke(stroke).build())
    }

    suspend fun swipe(x0: Float, y0: Float, x1: Float, y1: Float, duration: Long) {
        val path = Path().apply { moveTo(x0, y0); lineTo(x1, y1) }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        performGesture(GestureDescription.Builder().addStroke(stroke).build())
    }

    suspend fun longPress(x: Float, y: Float, duration: Long) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration, false)
        performGesture(GestureDescription.Builder().addStroke(stroke).build())
    }

    suspend fun multiTouch(points: List<Pair<Float, Float>>, duration: Long) {
        val builder = GestureDescription.Builder()
        points.forEachIndexed { idx, p ->
            val path = Path().apply { moveTo(p.first, p.second) }
            builder.addStroke(GestureDescription.StrokeDescription(path, 0, duration, idx != 0))
        }
        performGesture(builder.build())
    }

    override suspend fun setTextOnFocused(text: String) {
        val root = rootInActiveWindow ?: return
        val focused = findFocusedEditable(root) ?: return
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    override suspend fun performNodeAction(action: String, nodePath: String) {
        // Minimal placeholder: act on focused node for prototype
        val node = rootInActiveWindow ?: return
        when (action) {
            "click" -> node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            "paste" -> node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }
    }

    override suspend fun performGlobal(action: String) {
        when (action) {
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        }
    }

    override suspend fun requestCall(phone: String) {
        // Always require visible confirmation: prefer ACTION_DIAL
        val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        startActivity(dial)
    }

    suspend fun requestSms(phone: String, body: String) {
        // Safer path: open SMS composer
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$phone")
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    suspend fun launchApp(packageName: String) {
        val pm = packageManager
        val launch = pm.getLaunchIntentForPackage(packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launch)
    }

    suspend fun mediaControl(action: String) {
        // Placeholder: in production use MediaSession controls or key events via Accessibility where possible
        when (action) {
            "play_pause" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) // stub
        }
    }

    private suspend fun performGesture(gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) { cont.resume(true) }
                override fun onCancelled(gestureDescription: GestureDescription) { cont.resume(false) }
            }, null)
        }

    private fun findFocusedEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isFocused && node.isEditable) return node
        for (i in 0 until node.childCount) {
            val res = findFocusedEditable(node.getChild(i))
            if (res != null) return res
        }
        return null
    }

    companion object {
        fun isEnabled(context: android.content.Context): Boolean {
            // Best-effort check: user must enable manually
            return Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )?.contains(RemoteControlAccessibilityService::class.java.name) == true
        }
    }
}
