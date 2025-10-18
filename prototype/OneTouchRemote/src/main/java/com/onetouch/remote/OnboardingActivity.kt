package com.onetouch.remote

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class OnboardingActivity : ComponentActivity() {

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* handle results */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 1) Guide user to enable AccessibilityService
        if (!RemoteControlAccessibilityService.isEnabled(this)) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        // 2) Ask runtime permissions for optional features (CALL/SMS) if user opts in
        // Only request when feature is invoked; shown here for prototype
        requestPermissions.launch(arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS
        ))

        // 3) Start foreground session service to display persistent notification
        ContextCompat.startForegroundService(this, Intent(this, ControlSessionService::class.java))
        finish()
    }
}
