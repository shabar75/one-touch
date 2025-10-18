package com.onetouch.remote

import android.app.Activity
import android.os.Bundle
import android.widget.CheckBox
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class SessionConsentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controllerId = intent.getStringExtra(EXTRA_CONTROLLER_ID) ?: ""
        val rememberHours = 8
        val cm = ConsentManager.get(this)
        if (cm.isControllerAllowed(controllerId)) {
            setResult(Activity.RESULT_OK)
            finish(); return
        }
        // Minimal UI (placeholder)
        val tv = TextView(this).apply {
            text = "Allow remote control by: $controllerId?"
            textSize = 18f
            setPadding(32, 64, 32, 32)
        }
        val cb = CheckBox(this).apply { text = "Remember for $rememberHours hours" }
        setContentView(tv)
        tv.setOnClickListener {
            if (cb.isChecked) cm.rememberController(controllerId, rememberHours)
            setResult(Activity.RESULT_OK); finish()
        }
    }

    companion object { const val EXTRA_CONTROLLER_ID = "extra_controller_id" }
}
