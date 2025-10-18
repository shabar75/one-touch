package com.onetouch.remote

import android.content.Context

object AuthManager {
    fun validateToken(context: Context, token: String): Boolean {
        // Placeholder: verify ephemeral token issued by app/signalling
        return token.isNotBlank()
    }
    fun validatePin(context: Context, streamId: String, pin: String): Boolean {
        // Placeholder: verify PIN/password bound to streamId
        return pin.length in 4..8
    }
}
