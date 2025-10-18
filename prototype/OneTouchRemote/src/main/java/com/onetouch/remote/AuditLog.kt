package com.onetouch.remote

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.io.File

class AuditLog(private val context: Context) {
    private val file: File = File(context.filesDir, "audit_log.jsonl")
    private val masterKey by lazy {
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    }

    @Synchronized
    fun record(actionType: String, controllerId: String, payloadHash: String? = null) {
        val entry = JSONObject()
            .put("ts", System.currentTimeMillis())
            .put("controller_id", controllerId)
            .put("action", actionType)
            .put("payload_hash", payloadHash)
            .toString() + "\n"
        appendEncrypted(entry.toByteArray())
    }

    private fun appendEncrypted(bytes: ByteArray) {
        val existing = if (file.exists()) file.readBytes() else ByteArray(0)
        val ef = EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
        val all = existing + bytes
        ef.openFileOutput().use { it.write(all) }
    }
}
