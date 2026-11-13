// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/LanScannerRepository.kt ===
package com.enterwebslinger.network.data.repository

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.enterwebslinger.network.core.util.NetworkByteUtils
import com.enterwebslinger.network.core.util.NetworkUtils
import com.enterwebslinger.network.data.local.datastore.UserPreferencesDataStore
import com.enterwebslinger.network.domain.model.DiscoveredDevice
import com.enterwebslinger.network.domain.model.ScanHistoryEntry
import com.enterwebslinger.network.domain.model.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.FileReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class LanScannerRepository(
    context: Context,
    private val networkInfoRepository: NetworkInfoRepository,
    private val userPreferencesDataStore: UserPreferencesDataStore? = null
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress.asStateFlow()

    private val _statusMessage = MutableStateFlow("Tap 'Scan Subnet' to discover devices")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

    private val _scanUiState = MutableStateFlow<UiState<List<DiscoveredDevice>>>(UiState.Idle)
    val scanUiState: StateFlow<UiState<List<DiscoveredDevice>>> = _scanUiState.asStateFlow()

    private var scanJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private val stage1Ports = intArrayOf(22, 80, 443, 445, 8080)

    fun dismissError() {
        val current = _devices.value
        _scanUiState.value = if (current.isEmpty()) UiState.Idle else UiState.Success(current)
    }

    fun startScan(scope: CoroutineScope) {
        if (_isScanning.value) return
        scanJob?.cancel()
        _isScanning.value = true
        _scanProgress.value = 0f
        _devices.value = emptyList()
        _scanUiState.value = UiState.Loading
        _statusMessage.value = "Initializing 3-stage discovery pipeline..."

        scanJob = scope.launch(Dispatchers.IO) {
            try {
                acquireMulticastLock()

                val topo = networkInfoRepository.topology.value
                var localIp = topo.localIpv4
                var gatewayIp = topo.defaultGateway
                var cidrPrefix = topo.subnetCidr.toIntOrNull() ?: 24
                val ssid = topo.ssid

                if (!localIp.contains(".") || localIp == "Unavailable") {
                    val detected = detectActiveIpv4Interface()
                    if (detected != null) {
                        localIp = detected.first
                        cidrPrefix = detected.second
                    }
                }

                if (!localIp.contains(".") || localIp == "Unavailable") {
                    val errMsg = "No active IPv4 subnet detected. Connect to Wi-Fi or Ethernet to scan."
                    _statusMessage.value = errMsg
                    _scanUiState.value = UiState.Error(errMsg)
                    return@launch
                }

                val targetIps = computeTargetHosts(localIp, cidrPrefix)
                if (targetIps.isEmpty()) {
                    val errMsg = "Unable to derive scannable host range for $localIp/$cidrPrefix"
                    _statusMessage.value = errMsg
                    _scanUiState.value = UiState.Error(errMsg)
                    return@launch
                }

                val parts = localIp.split(".")
                val subnetLabel = if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}.0/${cidrPrefix.coerceAtLeast(24)}" else localIp
                _statusMessage.value = "Sweeping ${targetIps.size} hosts in $subnetLabel..."

                val discoveredMap = ConcurrentHashMap<String, DiscoveredDevice>()

                val customNicknames = try {
                    userPreferencesDataStore?.customNicknamesFlow?.first() ?: emptyMap()
                } catch (_: Exception) {
                    emptyMap()
                }

                fun applyCustomLabel(dev: DiscoveredDevice): DiscoveredDevice {
                    val custom = customNicknames[dev.mac] ?: customNicknames[dev.ip]
                    return if (custom != null) {
                        dev.copy(
                            customLabel = custom.customName,
                            deviceType = if (custom.customType != "Generic") custom.customType else dev.deviceType
                        )
                    } else dev
                }

                fun publishDevices() {
                    val sorted = discoveredMap.values.sortedBy { NetworkByteUtils.stringToIpLong(it.ip) }
                    _devices.value = sorted
                }

                if (gatewayIp.isNotBlank() && gatewayIp != "Unavailable" && gatewayIp != "0.0.0.0") {
                    discoveredMap[gatewayIp] = applyCustomLabel(
                        DiscoveredDevice(
                            ip = gatewayIp,
                            hostname = "Default Gateway",
                            vendor = "Network Router",
                            deviceType = "Router",
                            services = listOf("Gateway", "DNS", "DHCP"),
                            isAlive = true
                        )
                    )
                }

                discoveredMap[localIp] = applyCustomLabel(
                    DiscoveredDevice(
                        ip = localIp,
                        hostname = "This Android Device",
                        vendor = "Self Device",
                        deviceType = "Phone",
                        services = listOf("Local Host"),
                        isAlive = true
                    )
                )
                publishDevices()

                val stage2Job = launch(Dispatchers.IO) {
                    discoverSsdpStage2(discoveredMap) { dev ->
                        val finalDev = applyCustomLabel(dev)
                        discoveredMap[finalDev.ip] = finalDev
                        publishDevices()
                    }
                }

                val stage3Job = launch(Dispatchers.IO) {
                    discoverMdnsAndNbnsStage3(discoveredMap) { dev ->
                        val finalDev = applyCustomLabel(dev)
                        discoveredMap[finalDev.ip] = finalDev
                        publishDevices()
                    }
                }

                val semaphore = Semaphore(64)
                val completedCount = AtomicInteger(0)
                val totalIps = targetIps.size

                val probeTasks = targetIps.map { ip ->
                    async {
                        semaphore.withPermit {
                            val probed = probeSubnetHostStage1(ip)
                            val done = completedCount.incrementAndGet()
                            _scanProgress.value = (done.toFloat() / totalIps) * 0.85f

                            if (probed != null) {
                                val netBiosName = queryNetBios(ip)
                                val services = probed.services.toMutableList()
                                var hostname = probed.hostname
                                var deviceType = probed.deviceType
                                if (!netBiosName.isNullOrBlank()) {
                                    if (!services.contains("NetBIOS")) services.add("NetBIOS")
                                    hostname = netBiosName
                                    if (deviceType == "Generic") deviceType = "PC / Samba"
                                }

                                val existing = discoveredMap[ip]
                                val merged = if (existing != null) {
                                    existing.copy(
                                        hostname = if (existing.hostname.startsWith("Host-") && !hostname.startsWith("Host-")) hostname else existing.hostname,
                                        responseTimeMs = if (probed.responseTimeMs > 0) probed.responseTimeMs else existing.responseTimeMs,
                                        openPorts = (existing.openPorts + probed.openPorts).distinct().sorted(),
                                        services = (existing.services + services).distinct(),
                                        deviceType = if (existing.deviceType == "Generic") deviceType else existing.deviceType,
                                        isAlive = true
                                    )
                                } else {
                                    probed.copy(hostname = hostname, services = services, deviceType = deviceType)
                                }
                                discoveredMap[ip] = applyCustomLabel(merged)
                                publishDevices()
                            }
                        }
                    }
                }

                probeTasks.awaitAll()

                _statusMessage.value = "Finalizing mDNS, SSDP, and ip neigh ARP resolution..."
                _scanProgress.value = 0.92f
                delay(900L)
                stage2Job.cancel()
                stage3Job.cancel()

                enrichArpData(discoveredMap)

                val finalList = discoveredMap.values
                    .map { applyCustomLabel(it) }
                    .sortedBy { NetworkByteUtils.stringToIpLong(it.ip) }

                _devices.value = finalList
                _scanProgress.value = 1.0f
                _statusMessage.value = "Scan complete. Found ${finalList.size} devices on $subnetLabel."
                _scanUiState.value = UiState.Success(finalList)

                userPreferencesDataStore?.appendScanHistory(
                    ScanHistoryEntry(
                        timestamp = System.currentTimeMillis(),
                        ssid = ssid,
                        deviceCount = finalList.size,
                        summary = "${finalList.size} devices found on $subnetLabel"
                    )
                )
            } catch (e: CancellationException) {
                _statusMessage.value = "Scan cancelled"
                _scanUiState.value = if (_devices.value.isNotEmpty()) UiState.Success(_devices.value) else UiState.Idle
                throw e
            } catch (e: SecurityException) {
                val msg = "Network/Wi-Fi permission denied: ${e.message ?: "Grant location permission"}"
                _statusMessage.value = msg
                _scanUiState.value = UiState.Error(msg, e)
            } catch (e: Exception) {
                val msg = "LAN scan failed: ${e.message ?: "Unknown error"}"
                _statusMessage.value = msg
                _scanUiState.value = UiState.Error(msg, e)
            } finally {
                releaseMulticastLock()
                _isScanning.value = false
                if (_scanProgress.value == 0f) _scanProgress.value = 1f
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        releaseMulticastLock()
        _isScanning.value = false
        _statusMessage.value = "Scan cancelled"
        _scanUiState.value = if (_devices.value.isNotEmpty()) UiState.Success(_devices.value) else UiState.Idle
    }

    private fun detectActiveIpv4Interface(): Pair<String, Int>? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                if (nif.isLoopback || !nif.isUp) continue
                for (ifaceAddr in nif.interfaceAddresses) {
                    val inet = ifaceAddr.address
                    if (inet is Inet4Address && !inet.isLoopbackAddress) {
                        val ip = inet.hostAddress ?: continue
                        val prefix = ifaceAddr.networkPrefixLength.toInt().coerceIn(8, 30)
                        return ip to prefix
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    internal fun computeTargetHosts(localIp: String, cidrPrefix: Int): List<String> {
        val ipLong = NetworkByteUtils.stringToIpLong(localIp)
        if (ipLong == 0L) return emptyList()

        // Clamp scan window to at most a /24 (254 hosts) so /16 or /8 networks don't stall mobile devices,
        // while honoring smaller subnets (/25 .. /30) accurately.
        val effectivePrefix = cidrPrefix.coerceIn(24, 30)
        val mask = (0xFFFFFFFFL shl (32 - effectivePrefix)) and 0xFFFFFFFFL
        val networkAddr = ipLong and mask
        val broadcastAddr = networkAddr or (mask.inv() and 0xFFFFFFFFL)

        val firstHost = networkAddr + 1
        val lastHost = broadcastAddr - 1
        if (firstHost > lastHost) return emptyList()

        val hosts = ArrayList<String>((lastHost - firstHost + 1).toInt())
        var addr = firstHost
        while (addr <= lastHost) {
            hosts.add(NetworkByteUtils.ipLongToString(addr))
            addr++
        }
        return hosts
    }

    private suspend fun probeSubnetHostStage1(ip: String): DiscoveredDevice? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var reachable = false
        var latency = 0L

        try {
            val addr = InetAddress.getByName(ip)
            reachable = addr.isReachable(280)
            if (reachable) {
                latency = System.currentTimeMillis() - startTime
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}

        val openPorts = mutableListOf<Int>()

        for (port in stage1Ports) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), 240)
                    openPorts.add(port)
                    reachable = true
                    if (latency == 0L) {
                        latency = System.currentTimeMillis() - startTime
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }

        if (!reachable) return@withContext null

        val services = mutableListOf<String>()
        if (openPorts.contains(22)) services.add("SSH")
        if (openPorts.contains(80)) services.add("HTTP")
        if (openPorts.contains(443)) services.add("HTTPS")
        if (openPorts.contains(445)) services.add("SMB")
        if (openPorts.contains(8080)) services.add("HTTP-Alt")

        val inferredType = when {
            ip.endsWith(".1") -> "Router"
            openPorts.contains(445) -> "PC / NAS"
            openPorts.contains(22) -> "Server / Linux"
            openPorts.contains(80) || openPorts.contains(443) || openPorts.contains(8080) -> "Web Appliance"
            else -> "Generic"
        }

        DiscoveredDevice(
            ip = ip,
            hostname = "Host-$ip",
            vendor = "Network Device",
            responseTimeMs = latency.coerceAtLeast(1L),
            isAlive = true,
            openPorts = openPorts,
            services = services,
            deviceType = inferredType
        )
    }

    private suspend fun discoverSsdpStage2(
        discoveredMap: ConcurrentHashMap<String, DiscoveredDevice>,
        onDeviceUpdated: (DiscoveredDevice) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 2200
                socket.broadcast = true

                val ssdpPacketBytes = NetworkUtils.buildSsdpDiscoveryPacket()
                val ssdpGroup = InetAddress.getByName("239.255.255.250")
                socket.send(DatagramPacket(ssdpPacketBytes, ssdpPacketBytes.size, ssdpGroup, 1900))

                val buffer = ByteArray(2048)
                val inPacket = DatagramPacket(buffer, buffer.size)
                val deadline = System.currentTimeMillis() + 2500L

                while (System.currentTimeMillis() < deadline && isActive) {
                    try {
                        socket.receive(inPacket)
                        val resp = String(inPacket.data, 0, inPacket.length)
                        val senderIp = inPacket.address.hostAddress ?: continue

                        var serverHeader: String? = null
                        var locationUrl: String? = null

                        for (line in resp.lines()) {
                            val lower = line.lowercase()
                            if (lower.startsWith("server:")) {
                                serverHeader = line.substring(7).trim()
                            } else if (lower.startsWith("location:")) {
                                locationUrl = line.substring(9).trim()
                            }
                        }

                        val existing = discoveredMap[senderIp]
                        val tags = (existing?.services ?: emptyList()).toMutableList()
                        if (!tags.contains("UPnP")) tags.add("UPnP")

                        val friendlyHost = serverHeader?.take(25) ?: existing?.hostname ?: "UPnP Media Host"
                        val vendor = serverHeader?.substringBefore("/")?.take(20) ?: existing?.vendor ?: "UPnP Device"

                        val updated = (existing ?: DiscoveredDevice(ip = senderIp, isAlive = true)).copy(
                            hostname = if (existing == null || existing.hostname.startsWith("Host-")) friendlyHost else existing.hostname,
                            vendor = if (existing == null || existing.vendor == "Network Device") vendor else existing.vendor,
                            services = tags,
                            locationUrl = locationUrl,
                            serverHeader = serverHeader,
                            deviceType = if (existing?.deviceType == null || existing.deviceType == "Generic") "IoT / UPnP" else existing.deviceType
                        )
                        onDeviceUpdated(updated)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
    }

    @Suppress("DEPRECATION")
    private suspend fun discoverMdnsAndNbnsStage3(
        discoveredMap: ConcurrentHashMap<String, DiscoveredDevice>,
        onDeviceUpdated: (DiscoveredDevice) -> Unit
    ) = withContext(Dispatchers.IO) {
        val manager = nsdManager
        val activeListeners = mutableListOf<NsdManager.DiscoveryListener>()
        try {
            // Raw UDP mDNS multicast probe to 224.0.0.251:5353
            val rawMdnsJob = launch(Dispatchers.IO) {
                try {
                    MulticastSocket().use { mSocket ->
                        mSocket.soTimeout = 1800
                        mSocket.timeToLive = 1
                        val mdnsGroup = InetAddress.getByName("224.0.0.251")
                        val queryBytes = buildMdnsPtrQuery("_services._dns-sd._udp.local")
                        mSocket.send(DatagramPacket(queryBytes, queryBytes.size, mdnsGroup, 5353))

                        val buf = ByteArray(2048)
                        val packet = DatagramPacket(buf, buf.size)
                        val deadline = System.currentTimeMillis() + 2000L
                        while (System.currentTimeMillis() < deadline && isActive) {
                            try {
                                mSocket.receive(packet)
                                val senderIp = packet.address.hostAddress ?: continue
                                val existing = discoveredMap[senderIp]
                                val tags = (existing?.services ?: emptyList()).toMutableList()
                                if (!tags.contains("mDNS")) tags.add("mDNS")

                                val parsedLabel = extractPrintableDnsLabel(packet.data, packet.length)
                                val updated = (existing ?: DiscoveredDevice(ip = senderIp, isAlive = true)).copy(
                                    hostname = if ((existing == null || existing.hostname.startsWith("Host-")) && !parsedLabel.isNullOrBlank()) {
                                        parsedLabel
                                    } else existing?.hostname ?: "mDNS Host-$senderIp",
                                    services = tags
                                )
                                onDeviceUpdated(updated)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                break
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
            }

            if (manager != null) {
                val serviceTypes = listOf("_http._tcp.", "_workstation._tcp.", "_googlecast._tcp.", "_smb._tcp.")
                for (type in serviceTypes) {
                    try {
                        val listener = object : NsdManager.DiscoveryListener {
                            override fun onDiscoveryStarted(regType: String) {}
                            override fun onServiceFound(info: NsdServiceInfo) {
                                try {
                                    manager.resolveService(info, object : NsdManager.ResolveListener {
                                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                                            val host = resolved.host?.hostAddress ?: return
                                            val existing = discoveredMap[host]
                                            val tags = (existing?.services ?: emptyList()).toMutableList()
                                            if (!tags.contains("mDNS")) tags.add("mDNS")

                                            val updated = (existing ?: DiscoveredDevice(ip = host, isAlive = true)).copy(
                                                hostname = if (existing == null || existing.hostname.startsWith("Host-")) resolved.serviceName else existing.hostname,
                                                services = tags
                                            )
                                            onDeviceUpdated(updated)
                                        }
                                    })
                                } catch (_: Exception) {}
                            }
                            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                            override fun onDiscoveryStopped(serviceType: String) {}
                            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                        }
                        manager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
                        activeListeners.add(listener)
                    } catch (_: Exception) {}
                }
            }

            rawMdnsJob.join()
        } finally {
            activeListeners.forEach { listener ->
                try {
                    manager?.stopServiceDiscovery(listener)
                } catch (_: Exception) {}
            }
        }
    }

    private fun buildMdnsPtrQuery(serviceName: String): ByteArray {
        val out = ByteArrayOutputStream()
        // Header: ID=0, Flags=0, QDCOUNT=1, ANCOUNT=0, NSCOUNT=0, ARCOUNT=0
        out.write(byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        serviceName.split(".").filter { it.isNotEmpty() }.forEach { label ->
            val bytes = label.toByteArray(Charsets.UTF_8)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0x00) // Root terminator
        out.write(byteArrayOf(0x00, 0x0C)) // QTYPE PTR (12)
        out.write(byteArrayOf(0x00, 0x01)) // QCLASS IN (1)
        return out.toByteArray()
    }

    private fun extractPrintableDnsLabel(data: ByteArray, length: Int): String? {
        if (length <= 12) return null
        val sb = StringBuilder()
        var idx = 12
        var steps = 0
        while (idx < length && steps < 4) {
            val len = data[idx].toInt() and 0xFF
            if (len == 0 || (len and 0xC0) != 0) break
            idx++
            if (idx + len > length) break
            val label = String(data, idx, len, Charsets.UTF_8)
            if (label != "local" && label != "_tcp" && label != "_udp") {
                if (sb.isNotEmpty()) sb.append(".")
                sb.append(label)
            }
            idx += len
            steps++
        }
        val res = sb.toString().trim()
        return res.ifBlank { null }
    }

    private fun queryNetBios(ip: String): String? {
        return try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 200
                val packetData = NetworkUtils.buildNbnsQueryPacket()
                val targetAddr = InetAddress.getByName(ip)
                socket.send(DatagramPacket(packetData, packetData.size, targetAddr, 137))

                val inBuffer = ByteArray(1024)
                val inPacket = DatagramPacket(inBuffer, inBuffer.size)
                socket.receive(inPacket)
                NetworkUtils.parseNbnsResponse(inBuffer, inPacket.length)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun enrichArpData(discoveredMap: ConcurrentHashMap<String, DiscoveredDevice>) {
        // Primary modern Android method: `ip neigh show` (works without /proc/net/arp root/permission errors)
        var process: Process? = null
        try {
            process = ProcessBuilder("ip", "neigh", "show")
                .redirectErrorStream(true)
                .start()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                val macRegex = Regex("([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}")
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line!!.trim()
                    if (l.isBlank()) continue
                    val ip = l.substringBefore(" ").trim()
                    val macMatch = macRegex.find(l) ?: continue
                    val mac = macMatch.value.uppercase()
                    if (mac == "00:00:00:00:00:00") continue

                    discoveredMap[ip]?.let { dev ->
                        val vendor = NetworkUtils.resolveVendor(mac)
                        discoveredMap[ip] = dev.copy(
                            mac = mac,
                            vendor = if (dev.vendor == "Network Device" || dev.vendor == "Generic Device") vendor else dev.vendor
                        )
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            NetworkByteUtils.destroyProcess(process)
        }

        // Secondary fallback: /proc/net/arp
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                var line: String?
                reader.readLine()
                while (reader.readLine().also { line = it } != null) {
                    val tokens = line!!.split("\\s+".toRegex())
                    if (tokens.size >= 4) {
                        val ip = tokens[0]
                        val mac = tokens[3]
                        if (mac != "00:00:00:00:00:00" && mac.contains(":")) {
                            discoveredMap[ip]?.let { dev ->
                                if (dev.mac == "Unavailable" || dev.mac.isBlank()) {
                                    val vendor = NetworkUtils.resolveVendor(mac)
                                    discoveredMap[ip] = dev.copy(
                                        mac = mac.uppercase(),
                                        vendor = if (dev.vendor == "Network Device" || dev.vendor == "Generic Device") vendor else dev.vendor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun acquireMulticastLock() {
        try {
            wifiManager?.let {
                multicastLock = it.createMulticastLock("EnterwebMulticastLock").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (_: Exception) {}
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
        multicastLock = null
    }
}
