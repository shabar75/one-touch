package com.onetouch.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ControlSessionService : Service() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Remote Control", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
        }
        val stopIntent = Intent(this, ControlSessionService::class.java).apply { action = ACTION_STOP }
        val stopPi = androidx.core.app.PendingIntentCompat.getService(this, 0, stopIntent, 0, false)
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("OneTouch Remote Control Active")
            .setContentText("Tap to stop")
            .setOngoing(true)
            .addAction(0, "Stop", stopPi)
            .build()
        startForeground(NOTIF_ID, notif)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(true)
            stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "remote_control"
        private const val NOTIF_ID = 4242
        private const val ACTION_STOP = "com.onetouch.remote.ACTION_STOP"
    }
}
