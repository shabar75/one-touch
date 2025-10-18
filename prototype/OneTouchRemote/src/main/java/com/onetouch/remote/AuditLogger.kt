package com.onetouch.remote

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.nio.charset.Charset

object AuditLogger {
    private const val FILE_NAME = "remote_audit.log"

    fun logAction(context: Context, type: String, clientId: String, payload: String) {
        val file = File(context.filesDir, FILE_NAME)
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val encrypted = EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
        encrypted.openFileOutput().use { out ->
            val line = "${System.currentTimeMillis()}\t$clientId\t$type\t${payload.take(256)}\n"
            out.write(line.toByteArray(Charset.forName("UTF-8")))
        }
    }
}
