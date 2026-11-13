// === FILE: app/src/main/java/com/enterwebslinger/network/service/TelemetryCollectorService.kt ===
package com.enterwebslinger.network.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Foreground Service for persistent network background auditing,
 * syslog daemon listening, and continuous host-watch telemetry polling.
 */
class TelemetryCollectorService : Service() {

    companion object {
        const val ACTION_START_COLLECTION = "com.enterwebslinger.network.action.START_COLLECTION"
        const val ACTION_STOP_COLLECTION = "com.enterwebslinger.network.action.STOP_COLLECTION"
        const val CHANNEL_ID = "enterweb_telemetry_collector_channel"
        const val NOTIFICATION_ID = 5050

        fun start(context: Context) {
            val intent = Intent(context, TelemetryCollectorService::class.java).apply {
                action = ACTION_START_COLLECTION
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TelemetryCollectorService::class.java).apply {
                action = ACTION_STOP_COLLECTION
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_COLLECTION -> {
                val notification = buildNotification("Monitoring active network interfaces and telemetry...")
                startForeground(NOTIFICATION_ID, notification)
            }
            ACTION_STOP_COLLECTION -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Enterweb Telemetry Collector",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active continuous network telemetry collector"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Enterweb Slinger Telemetry Active")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Enterweb Slinger Telemetry Active")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setOngoing(true)
                .build()
        }
    }
}
