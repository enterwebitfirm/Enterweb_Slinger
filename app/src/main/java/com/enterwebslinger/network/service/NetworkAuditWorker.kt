// === FILE: app/src/main/java/com/enterwebslinger/network/service/NetworkAuditWorker.kt ===
package com.enterwebslinger.network.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.enterwebslinger.network.EnterwebSlingerApp
import com.enterwebslinger.network.domain.model.AuditRunSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Scheduled Background Network Auditor powered by WorkManager.
 * Performs lightweight ARP neighbor table sweep + default gateway & watched host checks,
 * persists latest AuditRunSummary, and raises notifications when new unknown MACs
 * or offline watched hosts are discovered.
 */
class NetworkAuditWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME_PERIODIC = "enterweb_periodic_network_audit"
        const val CHANNEL_ID = "enterweb_audit_alerts"
        private const val ALERT_NOTIFICATION_ID = 5051

        fun schedulePeriodicAudit(context: Context, intervalMinutes: Long) {
            val safeMinutes = intervalMinutes.coerceIn(15L, 360L)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<NetworkAuditWorker>(safeMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun runOneTimeAuditNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<NetworkAuditWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun cancelAudit(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PERIODIC)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val container = EnterwebSlingerApp.getContainer()
            val config = container.userPreferencesDataStore.auditMonitorConfigFlow.first()
            container.networkInfoRepository.manualRefresh()
            val top = container.networkInfoRepository.topology.value
            val currentSsid = top.ssid

            // 1) Read /proc/net/arp table
            val arpMacs = mutableListOf<Pair<String, String>>()
            runCatching {
                val arpFile = File("/proc/net/arp")
                if (arpFile.exists() && arpFile.canRead()) {
                    arpFile.readLines().drop(1).forEach { line ->
                        val cols = line.trim().split(Regex("""\s+"""))
                        if (cols.size >= 4 && cols[3] != "00:00:00:00:00:00") {
                            arpMacs.add(cols[0] to cols[3].uppercase())
                        }
                    }
                }
            }

            // 2) Check gateway reachability
            val gwIp = top.defaultGateway
            val gatewayOk = if (gwIp.isNotBlank() && gwIp != "Unavailable") {
                isHostReachable(gwIp)
            } else false

            // 3) Check unknown MACs when whitelist or trusted SSID is configured
            val whitelistSet = config.trustedMacWhitelist.map { it.trim().uppercase() }.toSet()
            val unknownMacs = if (whitelistSet.isNotEmpty() &&
                (config.trustedSsid.isBlank() || currentSsid.equals(config.trustedSsid, ignoreCase = true))
            ) {
                arpMacs.filter { (_, mac) -> mac !in whitelistSet }
                    .map { (ip, mac) -> "$mac ($ip)" }
            } else {
                emptyList()
            }

            // 4) Probe watched hosts
            val offlineHosts = config.watchedHosts
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .filter { host -> !isHostReachable(host) }

            val statusMsg = buildString {
                append("SSID: $currentSsid | ARP: ${arpMacs.size} | GW: ${if (gatewayOk) "UP" else "DOWN"}")
                if (unknownMacs.isNotEmpty()) append(" | Unknown MACs: ${unknownMacs.size}")
                if (offlineHosts.isNotEmpty()) append(" | Offline Hosts: ${offlineHosts.joinToString(", ")}")
            }

            val summary = AuditRunSummary(
                timestampMs = System.currentTimeMillis(),
                ssid = currentSsid,
                arpCount = arpMacs.size,
                unknownMacsFound = unknownMacs,
                offlineHosts = offlineHosts,
                gatewayReachable = gatewayOk,
                statusMessage = statusMsg
            )
            container.userPreferencesDataStore.saveAuditSummary(summary)

            if (unknownMacs.isNotEmpty() || offlineHosts.isNotEmpty()) {
                postAlertNotification(
                    title = "Enterweb Network Alert",
                    body = buildString {
                        if (unknownMacs.isNotEmpty()) append("${unknownMacs.size} unknown MAC(s) detected. ")
                        if (offlineHosts.isNotEmpty()) append("Offline: ${offlineHosts.joinToString(", ")}")
                    }.trim()
                )
            }

            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun isHostReachable(host: String): Boolean {
        val cleanHost = host.substringBefore(":")
        val customPort = host.substringAfter(":", "").toIntOrNull()
        val probePorts = if (customPort != null) listOf(customPort) else listOf(80, 443, 22, 53)
        for (port in probePorts) {
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(cleanHost, port), 800)
                    return true
                }
            } catch (_: Exception) {}
        }
        return runCatching { InetAddress.getByName(cleanHost).isReachable(1000) }.getOrDefault(false)
    }

    private fun postAlertNotification(title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Network Audit Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for rogue/unknown MAC addresses and offline watched hosts"
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(ALERT_NOTIFICATION_ID, notification)
    }
}
