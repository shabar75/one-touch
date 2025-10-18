package com.onetouch.remote

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class ConsentManager private constructor(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "consent_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun isControllerAllowed(controllerId: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val exp = prefs.getLong(key(controllerId), -1L)
        return exp > nowMs
    }

    fun rememberController(controllerId: String, hours: Int) {
        val exp = System.currentTimeMillis() + hours * 60L * 60L * 1000L
        prefs.edit().putLong(key(controllerId), exp).apply()
    }

    fun clear(controllerId: String) {
        prefs.edit().remove(key(controllerId)).apply()
    }

    private fun key(controllerId: String) = "controller_$controllerId"

    companion object {
        @Volatile private var INSTANCE: ConsentManager? = null
        fun get(context: Context): ConsentManager = INSTANCE ?: synchronized(this) {
            INSTANCE ?: ConsentManager(context.applicationContext).also { INSTANCE = it }
        }
    }
}
