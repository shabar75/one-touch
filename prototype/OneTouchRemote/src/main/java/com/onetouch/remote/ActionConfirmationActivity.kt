package com.onetouch.remote

import android.app.AlertDialog
import android.os.Bundle
import androidx.activity.ComponentActivity

class ActionConfirmationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent.getStringExtra(EXTRA_ACTION) ?: ""
        val details = intent.getStringExtra(EXTRA_DETAILS) ?: ""
        AlertDialog.Builder(this)
            .setTitle("Confirm action")
            .setMessage("Allow: $action\n$details")
            .setPositiveButton("Allow") { _, _ -> setResult(RESULT_OK); finish() }
            .setNegativeButton("Deny") { _, _ -> setResult(RESULT_CANCELED); finish() }
            .setOnCancelListener { setResult(RESULT_CANCELED); finish() }
            .show()
    }
    companion object {
        const val EXTRA_ACTION = "extra_action"
        const val EXTRA_DETAILS = "extra_details"
    }
}
