// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/LanSecurityAuditRepository.kt ===
package com.enterwebslinger.network.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.enterwebslinger.network.core.util.NetworkByteUtils
import com.enterwebslinger.network.core.util.NetworkUtils
import com.enterwebslinger.network.data.local.datastore.UserPreferencesDataStore
import com.enterwebslinger.network.domain.model.WifiAuditSeverity
import com.enterwebslinger.network.domain.model.EvilTwinFinding
import com.enterwebslinger.network.domain.model.OnvifCameraDevice
import com.enterwebslinger.network.domain.model.TrustedApBaseline
import com.enterwebslinger.network.domain.model.WifiSecurityFindingType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.UUID

/**
 * Pure-Kotlin Enterprise Security Auditor Repository:
 * 1) ONVIF / IoT Camera Discovery via UDP WS-Discovery (239.255.255.250:3702) + TCP 554/8000/8080/37777
 * 2) Evil Twin & Wi-Fi Security Auditor cross-referencing live ScanResults against TrustedApBaselines
 */
class LanSecurityAuditRepository(
    private val context: Context?,
    private val userPreferencesDataStore: UserPreferencesDataStore?,
    private val networkInfoRepository: NetworkInfoRepository?
) {
    private val wifiManager = context?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    companion object {
        private val CAMERA_PORTS = listOf(554, 8000, 8080, 37777, 80)
        private const val WS_DISCOVERY_MULTICAST = "239.255.255.250"
        private const val WS_DISCOVERY_PORT = 3702
    }

    suspend fun discoverOnvifCameras(targetSubnetIps: List<String> = emptyList()): List<OnvifCameraDevice> =
        withContext(Dispatchers.IO) {
            val foundByIp = linkedMapOf<String, OnvifCameraDevice>()
            val startTime = System.currentTimeMillis()

            // Phase 1: Real WS-Discovery UDP Multicast 239.255.255.250:3702
            var multicastLock: WifiManager.MulticastLock? = null
            var socket: DatagramSocket? = null
            try {
                multicastLock = wifiManager?.createMulticastLock("enterweb_onvif_ws_disc")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
                val probeId = UUID.randomUUID().toString()
                val soapProbe = """<?xml version="1.0" encoding="utf-8"?>
<e:Envelope xmlns:e="http://www.w3.org/2003/05/soap-envelope"
            xmlns:w="http://schemas.xmlsoap.org/ws/2004/08/addressing"
            xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery"
            xmlns:dn="http://www.onvif.org/ver10/network/wsdl">
  <e:Header>
    <w:MessageID>uuid:$probeId</w:MessageID>
    <w:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To>
    <w:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action>
  </e:Header>
  <e:Body>
    <d:Probe>
      <d:Types>dn:NetworkVideoTransmitter</d:Types>
    </d:Probe>
  </e:Body>
</e:Envelope>""".trimIndent().toByteArray(Charsets.UTF_8)

                socket = DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 700
                }
                val group = InetAddress.getByName(WS_DISCOVERY_MULTICAST)
                socket.send(DatagramPacket(soapProbe, soapProbe.size, group, WS_DISCOVERY_PORT))

                val deadline = System.currentTimeMillis() + 2200L
                val rxBuf = ByteArray(8192)
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val pkt = DatagramPacket(rxBuf, rxBuf.size)
                        socket.receive(pkt)
                        val senderIp = pkt.address?.hostAddress ?: continue
                        val xml = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                        val parsed = parseWsDiscoveryXml(senderIp, xml, System.currentTimeMillis() - startTime)
                        foundByIp[senderIp] = parsed
                    } catch (_: SocketTimeoutException) {
                        // Loop until deadline expires
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                NetworkByteUtils.safeClose(socket)
                runCatching {
                    if (multicastLock?.isHeld == true) multicastLock.release()
                }
            }

            // Phase 2: Probe ARP neighbors + any provided LAN host IPs for RTSP/NVR camera ports
            val candidateIps = buildSet {
                addAll(targetSubnetIps.filter { it.isNotBlank() })
                addAll(readArpNeighbors())
                addAll(foundByIp.keys)
            }

            val sem = Semaphore(24)
            val probedCameras = coroutineScope {
                candidateIps.map { ip ->
                    async {
                        sem.withPermit {
                            val t0 = System.currentTimeMillis()
                            val openPorts = mutableListOf<Int>()
                            for (p in CAMERA_PORTS) {
                                if (isTcpPortOpen(ip, p, 220)) {
                                    openPorts.add(p)
                                }
                            }
                            val existing = foundByIp[ip]
                            val hasCameraPorts = openPorts.any { it in listOf(554, 8000, 37777) }
                            if (existing != null || hasCameraPorts) {
                                val rtspValid = if (554 in openPorts) probeRtspOptions(ip) else false
                                val rtspUrls = if (554 in openPorts) {
                                    listOf(
                                        "rtsp://$ip:554/stream1",
                                        "rtsp://$ip:554/h264Preview_01_main"
                                    )
                                } else emptyList()

                                val method = when {
                                    existing != null && openPorts.isNotEmpty() -> "WS-Discovery + TCP Port Audit"
                                    existing != null -> "ONVIF WS-Discovery (UDP 3702)"
                                    rtspValid -> "RTSP OPTIONS + NVR Probe"
                                    else -> "Camera Port Probe (554/8000/37777)"
                                }
                                OnvifCameraDevice(
                                    ip = ip,
                                    hostName = existing?.hostName?.takeIf { it.isNotBlank() } ?: ip,
                                    endpointUrl = existing?.endpointUrl?.takeIf { it.isNotBlank() }
                                        ?: if (8000 in openPorts) "http://$ip:8000" else "http://$ip/onvif/device_service",
                                    hardwareModel = existing?.hardwareModel?.takeIf { it.isNotBlank() }
                                        ?: inferCameraBrandFromPorts(openPorts),
                                    scopes = existing?.scopes.orEmpty(),
                                    openPorts = (existing?.openPorts.orEmpty() + openPorts).distinct().sorted(),
                                    rtspUrls = rtspUrls,
                                    discoveryMethod = method,
                                    responseTimeMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
                                )
                            } else {
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            probedCameras.forEach { cam -> foundByIp[cam.ip] = cam }
            foundByIp.values.toList().sortedBy { it.ip }
        }

    fun parseWsDiscoveryXml(senderIp: String, xml: String, elapsedMs: Long): OnvifCameraDevice {
        val xAddrsRaw = Regex("""<[^>]*XAddrs[^>]*>([^<]+)</[^>]*XAddrs>""", RegexOption.IGNORE_CASE)
            .find(xml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val endpoint = xAddrsRaw.split(Regex("""\s+""")).firstOrNull { it.startsWith("http", true) }
            ?: "http://$senderIp/onvif/device_service"

        val scopesRaw = Regex("""<[^>]*Scopes[^>]*>([^<]+)</[^>]*Scopes>""", RegexOption.IGNORE_CASE)
            .find(xml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val scopesList = scopesRaw.split(Regex("""\s+""")).filter { it.isNotBlank() }

        val model = scopesList.firstOrNull { it.contains("/hardware/", true) }
            ?.substringAfterLast("/")?.replace("%20", " ")
            ?: scopesList.firstOrNull { it.contains("/name/", true) }?.substringAfterLast("/")?.replace("%20", " ")
            ?: "ONVIF Transmitter"

        val hostName = runCatching {
            java.net.URI(endpoint).host ?: senderIp
        }.getOrDefault(senderIp)

        return OnvifCameraDevice(
            ip = senderIp,
            hostName = hostName,
            endpointUrl = endpoint,
            hardwareModel = model,
            scopes = scopesList,
            openPorts = emptyList(),
            rtspUrls = emptyList(),
            discoveryMethod = "ONVIF WS-Discovery (UDP 3702)",
            responseTimeMs = elapsedMs.coerceAtLeast(1L)
        )
    }

    @SuppressLint("MissingPermission")
    suspend fun auditEvilTwinAndWifiSecurity(): Result<List<EvilTwinFinding>> = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext Result.failure(
            IllegalStateException("Context unavailable.")
        )
        val fineGranted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val nearbyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        if (!fineGranted && !nearbyGranted) {
            return@withContext Result.failure(
                SecurityException("ACCESS_FINE_LOCATION and NEARBY_WIFI_DEVICES are required to audit surrounding BSSIDs.")
            )
        }

        val scanResults: List<ScanResult> = try {
            @Suppress("DEPRECATION")
            wifiManager?.scanResults.orEmpty()
        } catch (e: SecurityException) {
            return@withContext Result.failure(e)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        if (scanResults.isEmpty()) {
            return@withContext Result.success(emptyList())
        }

        val baselines = userPreferencesDataStore?.trustedApBaselinesFlow?.first().orEmpty()
        val activeTop = networkInfoRepository?.topology?.value
        val activeChannel = NetworkUtils.frequencyToChannel(activeTop?.frequencyMhz ?: 0)

        val findings = analyzeWifiScanForEvilTwins(
            scanResults = scanResults.map { sr ->
                @Suppress("DEPRECATION")
                val rawSsid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    sr.wifiSsid?.toString()?.removeSurrounding("\"") ?: ""
                } else {
                    sr.SSID.orEmpty()
                }
                ScannedApSnapshot(
                    ssid = rawSsid.trim(),
                    bssid = (sr.BSSID ?: "").uppercase(),
                    capabilities = sr.capabilities.orEmpty(),
                    channel = NetworkUtils.frequencyToChannel(sr.frequency),
                    band = NetworkUtils.frequencyToBand(sr.frequency),
                    rssiDbm = sr.level
                )
            },
            trustedBaselines = baselines,
            activeSsid = activeTop?.ssid.orEmpty(),
            activeBssid = activeTop?.bssid.orEmpty(),
            activeChannel = activeChannel
        )
        Result.success(findings)
    }

    data class ScannedApSnapshot(
        val ssid: String,
        val bssid: String,
        val capabilities: String,
        val channel: Int,
        val band: String,
        val rssiDbm: Int
    )

    fun analyzeWifiScanForEvilTwins(
        scanResults: List<ScannedApSnapshot>,
        trustedBaselines: List<TrustedApBaseline>,
        activeSsid: String = "",
        activeBssid: String = "",
        activeChannel: Int = 0
    ): List<EvilTwinFinding> {
        val findings = mutableListOf<EvilTwinFinding>()
        val baselineBySsid = trustedBaselines.groupBy { it.ssid.lowercase() }
        val channelsInUse = scanResults.filter { it.ssid.isNotBlank() && it.channel > 0 }
            .map { it.channel }
            .toMutableSet().apply { if (activeChannel > 0) add(activeChannel) }

        for (ap in scanResults) {
            val securityLabel = normalizeSecurityLabel(ap.capabilities)
            val isHidden = ap.ssid.isBlank() || ap.ssid.equals("<hidden>", true)

            if (isHidden) {
                if (ap.channel in channelsInUse) {
                    findings.add(
                        EvilTwinFinding(
                            ssid = "<Hidden SSID>",
                            bssid = ap.bssid.ifBlank { "Unknown" },
                            securityCapabilities = securityLabel,
                            channel = ap.channel,
                            band = ap.band,
                            rssiDbm = ap.rssiDbm,
                            severity = WifiAuditSeverity.MEDIUM,
                            findingType = WifiSecurityFindingType.HIDDEN_SSID_OVERLAPPING_CHANNEL,
                            reason = "Hidden SSID beacon sharing active Channel ${ap.channel} (${ap.band}) at ${ap.rssiDbm} dBm."
                        )
                    )
                }
                continue
            }

            val ssidKey = ap.ssid.lowercase()
            val trustedForSsid = baselineBySsid[ssidKey].orEmpty()

            if (trustedForSsid.isNotEmpty()) {
                val knownBssidMatch = trustedForSsid.any { it.bssid.equals(ap.bssid, ignoreCase = true) }
                if (!knownBssidMatch && ap.bssid.isNotBlank()) {
                    findings.add(
                        EvilTwinFinding(
                            ssid = ap.ssid,
                            bssid = ap.bssid,
                            securityCapabilities = securityLabel,
                            channel = ap.channel,
                            band = ap.band,
                            rssiDbm = ap.rssiDbm,
                            severity = WifiAuditSeverity.HIGH,
                            findingType = WifiSecurityFindingType.DUPLICATE_SSID_UNKNOWN_BSSID,
                            reason = "Trusted SSID '${ap.ssid}' advertised by unbaselined BSSID ${ap.bssid} (possible Evil Twin / Rogue AP)."
                        )
                    )
                }

                val expectedRank = trustedForSsid.maxOf { securityStrengthRank(it.expectedSecurity) }
                val observedRank = securityStrengthRank(securityLabel)
                if (observedRank < expectedRank) {
                    findings.add(
                        EvilTwinFinding(
                            ssid = ap.ssid,
                            bssid = ap.bssid,
                            securityCapabilities = securityLabel,
                            channel = ap.channel,
                            band = ap.band,
                            rssiDbm = ap.rssiDbm,
                            severity = WifiAuditSeverity.HIGH,
                            findingType = WifiSecurityFindingType.SECURITY_DOWNGRADE,
                            reason = "Expected ${trustedForSsid.first().expectedSecurity} on '${ap.ssid}', observed downgraded '$securityLabel'."
                        )
                    )
                }
            } else if (activeSsid.isNotBlank() &&
                ap.ssid.equals(activeSsid, ignoreCase = true) &&
                activeBssid.startsWith("--").not() &&
                ap.bssid.isNotBlank() &&
                !ap.bssid.equals(activeBssid, ignoreCase = true) &&
                securityStrengthRank(securityLabel) <= 1
            ) {
                findings.add(
                    EvilTwinFinding(
                        ssid = ap.ssid,
                        bssid = ap.bssid,
                        securityCapabilities = securityLabel,
                        channel = ap.channel,
                        band = ap.band,
                        rssiDbm = ap.rssiDbm,
                        severity = WifiAuditSeverity.HIGH,
                        findingType = WifiSecurityFindingType.SECURITY_DOWNGRADE,
                        reason = "Duplicate of currently connected SSID '${ap.ssid}' running weak/open security ($securityLabel)."
                    )
                )
            }

            if (securityLabel == "OPEN" || securityLabel == "WEP") {
                findings.add(
                    EvilTwinFinding(
                        ssid = ap.ssid,
                        bssid = ap.bssid,
                        securityCapabilities = securityLabel,
                        channel = ap.channel,
                        band = ap.band,
                        rssiDbm = ap.rssiDbm,
                        severity = if (securityLabel == "WEP") WifiAuditSeverity.HIGH else WifiAuditSeverity.INFO,
                        findingType = WifiSecurityFindingType.OPEN_UNENCRYPTED_AP,
                        reason = if (securityLabel == "WEP") "Obsolete WEP encryption vulnerable to key recovery." else "Open unencrypted Wi-Fi AP."
                    )
                )
            }
        }

        return findings.sortedWith(compareBy<EvilTwinFinding> { it.severity.ordinal }.thenByDescending { it.rssiDbm })
    }

    suspend fun saveTrustedBaseline(ssid: String, bssid: String, expectedSecurity: String) {
        if (ssid.isBlank() || bssid.isBlank() || bssid.startsWith("--")) return
        userPreferencesDataStore?.saveTrustedApBaseline(
            TrustedApBaseline(ssid.trim(), bssid.trim().uppercase(), expectedSecurity.trim().uppercase())
        )
    }

    fun normalizeSecurityLabel(capabilities: String): String {
        val upper = capabilities.uppercase()
        return when {
            upper.contains("SAE") || upper.contains("WPA3") -> "WPA3"
            upper.contains("RSN") || upper.contains("WPA2") -> "WPA2"
            upper.contains("WPA") -> "WPA"
            upper.contains("WEP") -> "WEP"
            else -> "OPEN"
        }
    }

    fun securityStrengthRank(sec: String): Int = when (sec.uppercase()) {
        "WPA3" -> 4
        "WPA2" -> 3
        "WPA" -> 2
        "WEP" -> 1
        else -> 0
    }

    private fun readArpNeighbors(): List<String> {
        val list = mutableListOf<String>()
        try {
            val arp = File("/proc/net/arp")
            if (arp.exists() && arp.canRead()) {
                arp.readLines().drop(1).forEach { line ->
                    val cols = line.trim().split(Regex("""\s+"""))
                    if (cols.size >= 4 && cols[3] != "00:00:00:00:00:00") {
                        list.add(cols[0])
                    }
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun isTcpPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun probeRtspOptions(ip: String): Boolean {
        return try {
            Socket().use { s ->
                s.soTimeout = 500
                s.connect(InetSocketAddress(ip, 554), 400)
                val req = "OPTIONS rtsp://$ip:554/ RTSP/1.0\r\nCSeq: 1\r\n\r\n"
                s.outputStream.write(req.toByteArray(Charsets.US_ASCII))
                s.outputStream.flush()
                val buf = ByteArray(256)
                val read = s.inputStream.read(buf)
                if (read > 0) String(buf, 0, read, Charsets.US_ASCII).contains("RTSP/", true) else false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun inferCameraBrandFromPorts(ports: List<Int>): String = when {
        8000 in ports -> "Hikvision / ONVIF NVR"
        37777 in ports -> "Dahua / OEM NVR"
        554 in ports -> "RTSP IP Camera"
        else -> "Video Device"
    }
}
