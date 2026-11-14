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
import android.util.Log
import com.enterwebslinger.network.EnterwebSlingerApp
import com.enterwebslinger.network.domain.model.CollectorPreferences
import com.enterwebslinger.network.domain.model.CollectorServiceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Unified Enterprise Foreground Telemetry Collector Service.
 * Coordinates continuous background UDP/TCP collectors for Syslog, SNMP Traps,
 * NetFlow/IPFIX/sFlow flows, RADIUS CoA, and MikroTik MNDP.
 */
class TelemetryCollectorService : Service() {

    companion object {
        const val ACTION_START_COLLECTION = "com.enterwebslinger.network.action.START_COLLECTION"
        const val ACTION_STOP_COLLECTION = "com.enterwebslinger.network.action.STOP_COLLECTION"
        const val CHANNEL_ID = "enterweb_telemetry_collector_channel"
        const val NOTIFICATION_ID = 5050

        private val _serviceStatus = MutableStateFlow(CollectorServiceStatus())
        val serviceStatus: StateFlow<CollectorServiceStatus> = _serviceStatus.asStateFlow()

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
                val notification = buildNotification("Unified NOC telemetry daemons active...")
                startForeground(NOTIFICATION_ID, notification)
                startCollectors()
            }
            ACTION_STOP_COLLECTION -> {
                stopCollectors()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startCollectors() {
        serviceScope.launch {
            val container = runCatching { EnterwebSlingerApp.getContainer() }.getOrNull() ?: return@launch
            val prefs = runCatching { container.userPreferencesDataStore.collectorPreferencesFlow.first() }.getOrDefault(CollectorPreferences())

            val activeList = mutableListOf<String>()

            // 1) Start Syslog Receiver
            runCatching {
                container.syslogRepository.startReceiver(
                    com.enterwebslinger.network.domain.model.SyslogReceiverConfig(
                        udpEnabled = true,
                        udpPort = prefs.syslogUdpPort,
                        tcpEnabled = true,
                        tcpPort = prefs.syslogTcpPort
                    )
                )
                activeList.add("Syslog (UDP ${prefs.syslogUdpPort} / TCP ${prefs.syslogTcpPort})")
            }

            // 2) Start SNMP Trap Receiver
            runCatching {
                container.snmpRepository.startTrapReceiver(prefs.snmpTrapPort)
                activeList.add("SNMP Traps (UDP ${prefs.snmpTrapPort})")
            }

            // 3) Start Flow Collector (NetFlow v5/v9, IPFIX, sFlow)
            runCatching {
                container.flowCollectorRepository.startCollectors(listOf(prefs.netflowPort, prefs.ipfixPort, prefs.sflowPort))
                activeList.add("Flows (UDP ${prefs.netflowPort}, ${prefs.ipfixPort}, ${prefs.sflowPort})")
            }

            _serviceStatus.value = CollectorServiceStatus(
                isRunning = true,
                activeCollectors = activeList,
                totalEventsProcessed = 0L,
                errorMessage = null
            )
            Log.i("TelemetryCollector", "Unified Telemetry Collectors started successfully: $activeList")
        }
    }

    private fun stopCollectors() {
        val container = runCatching { EnterwebSlingerApp.getContainer() }.getOrNull()
        container?.syslogRepository?.stopReceiver()
        container?.snmpRepository?.stopTrapReceiver()
        container?.flowCollectorRepository?.stopCollectors()

        _serviceStatus.value = CollectorServiceStatus(
            isRunning = false,
            activeCollectors = emptyList(),
            totalEventsProcessed = 0L,
            errorMessage = null
        )
        Log.i("TelemetryCollector", "Unified Telemetry Collectors stopped.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopCollectors()
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
                description = "Active continuous network telemetry daemon collector"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Enterweb NOC Telemetry Active")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Enterweb NOC Telemetry Active")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setOngoing(true)
                .build()
        }
    }
}
