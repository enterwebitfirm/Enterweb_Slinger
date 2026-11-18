// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/SecurityRepository.kt ===
package com.enterwebslinger.network.data.repository

import com.enterwebslinger.network.core.util.NetworkByteUtils
import com.enterwebslinger.network.core.util.NetworkUtils
import com.enterwebslinger.network.data.local.datastore.UserPreferencesDataStore
import com.enterwebslinger.network.data.local.db.dao.EndpointProfileDao
import com.enterwebslinger.network.data.local.db.entities.SavedEndpointProfileEntity
import com.enterwebslinger.network.domain.model.HostWatcherTarget
import com.enterwebslinger.network.domain.model.PortScanResult
import com.enterwebslinger.network.domain.model.SavedWolProfile
import com.enterwebslinger.network.domain.model.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class SecurityRepository(
    private val scope: CoroutineScope,
    private val userPreferencesDataStore: UserPreferencesDataStore? = null,
    private val endpointProfileDao: EndpointProfileDao? = null,
    private val networkInfoRepository: NetworkInfoRepository? = null
) {
    val savedWolProfilesFlow: Flow<List<SavedWolProfile>> =
        endpointProfileDao?.getProfilesByTypeFlow("WOL")?.map { entities ->
            entities.map { entity ->
                SavedWolProfile(
                    id = entity.id.toString(),
                    name = entity.name,
                    macAddress = entity.host,
                    ipAddress = entity.extraConfigJson.takeIf { it != "{}" } ?: ""
                )
            }
        } ?: (userPreferencesDataStore?.wolProfilesFlow ?: emptyFlow())

    suspend fun saveWolProfile(profile: SavedWolProfile) = withContext(Dispatchers.IO) {
        val dao = endpointProfileDao
        if (dao != null) {
            val normalizedMac = profile.macAddress.trim().uppercase()
            val existing = dao.getProfilesByTypeFlow("WOL").first()
                .firstOrNull { it.host.equals(normalizedMac, ignoreCase = true) }
            val entity = SavedEndpointProfileEntity(
                id = existing?.id ?: (profile.id.toLongOrNull() ?: 0L),
                profileType = "WOL",
                name = profile.name.trim(),
                host = normalizedMac,
                port = 9,
                username = "",
                credentialVaultKey = "",
                extraConfigJson = profile.ipAddress.trim(),
                lastUsedTimestamp = System.currentTimeMillis()
            )
            dao.insertProfile(entity)
        } else {
            userPreferencesDataStore?.saveWolProfile(profile)
        }
    }

    suspend fun deleteWolProfile(id: String) = withContext(Dispatchers.IO) {
        val dao = endpointProfileDao
        val idLong = id.toLongOrNull()
        if (dao != null && idLong != null) {
            dao.deleteProfileById(idLong)
        } else {
            userPreferencesDataStore?.deleteWolProfile(id)
        }
    }

    private val _isScanningPorts = MutableStateFlow(false)
    val isScanningPorts: StateFlow<Boolean> = _isScanningPorts.asStateFlow()

    private val _portScanProgress = MutableStateFlow(0f)
    val portScanProgress: StateFlow<Float> = _portScanProgress.asStateFlow()

    private val _portResults = MutableStateFlow<List<PortScanResult>>(emptyList())
    val portResults: StateFlow<List<PortScanResult>> = _portResults.asStateFlow()

    private val _portScanUiState = MutableStateFlow<UiState<List<PortScanResult>>>(UiState.Idle)
    val portScanUiState: StateFlow<UiState<List<PortScanResult>>> = _portScanUiState.asStateFlow()

    private var portScanJob: Job? = null

    private val _wolUiState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val wolUiState: StateFlow<UiState<String>> = _wolUiState.asStateFlow()

    private val _watchedHosts = MutableStateFlow<List<HostWatcherTarget>>(emptyList())
    val watchedHosts: StateFlow<List<HostWatcherTarget>> = _watchedHosts.asStateFlow()

    private val _isWatcherRunning = MutableStateFlow(false)
    val isWatcherRunning: StateFlow<Boolean> = _isWatcherRunning.asStateFlow()

    private val _watcherUiState = MutableStateFlow<UiState<List<HostWatcherTarget>>>(UiState.Idle)
    val watcherUiState: StateFlow<UiState<List<HostWatcherTarget>>> = _watcherUiState.asStateFlow()

    private var watcherJob: Job? = null

    fun dismissPortScanError() {
        val current = _portResults.value
        _portScanUiState.value = if (current.isEmpty()) UiState.Idle else UiState.Success(current)
    }

    fun dismissWolStatus() {
        _wolUiState.value = UiState.Idle
    }

    fun dismissWatcherError() {
        val current = _watchedHosts.value
        _watcherUiState.value = if (current.isEmpty()) UiState.Idle else UiState.Success(current)
    }

    fun startPortScan(targetHost: String, ports: List<Int>) {
        val cleanTarget = targetHost.trim()
        if (_isScanningPorts.value || cleanTarget.isBlank() || ports.isEmpty()) {
            if (cleanTarget.isBlank()) {
                _portScanUiState.value = UiState.Error("Enter a target IP address or hostname before scanning.")
            }
            return
        }
        portScanJob?.cancel()
        _isScanningPorts.value = true
        _portScanProgress.value = 0f
        _portResults.value = emptyList()
        _portScanUiState.value = UiState.Loading

        portScanJob = scope.launch(Dispatchers.IO) {
            try {
                val addr = InetAddress.getByName(cleanTarget)
                val targetIp = addr.hostAddress ?: cleanTarget
                val isPrivateIp = isPrivateOrLocalIp(targetIp)

                val openPortsQueue = ConcurrentLinkedQueue<PortScanResult>()
                val semaphore = Semaphore(128)
                val total = ports.size
                val counter = AtomicInteger(0)

                val tasks = ports.map { port ->
                    async {
                        semaphore.withPermit {
                            val banner = probePortAndGrabBanner(targetIp, cleanTarget, port, 600, isPrivateIp)
                            val isOpen = banner != null
                            val completed = counter.incrementAndGet()
                            _portScanProgress.value = completed.toFloat() / total

                            if (isOpen) {
                                val serviceName = NetworkUtils.WELL_KNOWN_PORTS[port] ?: "Custom Service"
                                val isHttp = NetworkUtils.isHttpPort(port) || port in listOf(443, 8443)
                                val item = PortScanResult(
                                    port = port,
                                    serviceName = serviceName,
                                    isOpen = true,
                                    isHttp = isHttp,
                                    banner = banner?.takeIf { it.isNotBlank() }
                                )
                                openPortsQueue.add(item)
                                val sorted = openPortsQueue.sortedBy { it.port }
                                _portResults.value = sorted
                            }
                        }
                    }
                }

                tasks.awaitAll()
                val finalSorted = openPortsQueue.sortedBy { it.port }
                _portResults.value = finalSorted
                _portScanUiState.value = UiState.Success(finalSorted)
            } catch (e: CancellationException) {
                _portScanUiState.value = if (_portResults.value.isNotEmpty()) {
                    UiState.Success(_portResults.value)
                } else {
                    UiState.Idle
                }
                throw e
            } catch (e: Exception) {
                _portScanUiState.value = UiState.Error("Port scan failed for '$cleanTarget': ${e.message ?: "Unreachable host"}", e)
            } finally {
                _isScanningPorts.value = false
                _portScanProgress.value = 1f
            }
        }
    }

    fun stopPortScan() {
        portScanJob?.cancel()
        portScanJob = null
        _isScanningPorts.value = false
        _portScanUiState.value = if (_portResults.value.isNotEmpty()) {
            UiState.Success(_portResults.value)
        } else {
            UiState.Idle
        }
    }

    private fun isPrivateOrLocalIp(ip: String): Boolean {
        return try {
            val addr = InetAddress.getByName(ip)
            addr.isSiteLocalAddress || addr.isLoopbackAddress || ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("172.")
        } catch (_: Exception) {
            false
        }
    }

    private fun probePortAndGrabBanner(ip: String, host: String, port: Int, timeoutMs: Int, isPrivateIp: Boolean): String? {
        val tlsPorts = listOf(443, 8443, 853, 993, 995, 465, 636)
        val serverSpeaksFirstPorts = mapOf(
            21 to "220",
            22 to "SSH-",
            25 to "220",
            110 to "+OK",
            143 to "* OK"
        )

        return try {
            if (tlsPorts.contains(port)) {
                val factory = buildTrustAllSocketFactory()
                val socket = (factory.createSocket() as SSLSocket).apply {
                    soTimeout = timeoutMs
                    runCatching {
                        val params = sslParameters
                        params.serverNames = listOf(SNIHostName(host))
                        sslParameters = params
                    }
                    connect(InetSocketAddress(ip, port), timeoutMs)
                    startHandshake()
                }
                socket.use { sslSock ->
                    if (port == 443 || port == 8443) {
                        val writer = OutputStreamWriter(sslSock.outputStream, Charsets.US_ASCII)
                        writer.write("HEAD / HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n")
                        writer.flush()
                        val reader = BufferedReader(InputStreamReader(sslSock.inputStream, Charsets.UTF_8))
                        val line = reader.readLine()?.trim().orEmpty()
                        val cipher = sslSock.session?.cipherSuite ?: "TLS"
                        if (line.startsWith("HTTP/", true)) {
                            "$line ($cipher)"
                        } else {
                            "TLS Handshake OK ($cipher)"
                        }
                    } else {
                        "TLS Handshake OK (${sslSock.session?.cipherSuite ?: "Secure"})"
                    }
                }
            } else {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), timeoutMs)
                    socket.soTimeout = 800
                    val inputStream = socket.inputStream
                    val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))

                    if (serverSpeaksFirstPorts.containsKey(port) && !isPrivateIp) {
                        val firstLine = reader.readLine()?.trim().orEmpty()
                        val expectedPrefix = serverSpeaksFirstPorts[port]!!
                        if (firstLine.isBlank() || !firstLine.startsWith(expectedPrefix, ignoreCase = true)) {
                            // Transparent ISP proxy or fake open state filter
                            return null
                        }
                        return firstLine.replace(Regex("[^\\x20-\\x7E]"), "").take(100)
                    }

                    if (NetworkUtils.isHttpPort(port)) {
                        val writer = OutputStreamWriter(socket.outputStream, Charsets.UTF_8)
                        writer.write("HEAD / HTTP/1.0\r\nHost: $host\r\nConnection: close\r\n\r\n")
                        writer.flush()
                        val lines = mutableListOf<String>()
                        repeat(4) {
                            val line = reader.readLine() ?: return@repeat
                            if (line.startsWith("HTTP/", true) || line.startsWith("Server:", true)) {
                                lines.add(line.trim())
                            }
                        }
                        lines.joinToString(" • ").take(120).ifBlank { "" }
                    } else {
                        val firstLine = reader.readLine()?.trim().orEmpty()
                        firstLine.replace(Regex("[^\\x20-\\x7E]"), "").take(100)
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun inspectTlsCertificate(host: String, port: Int, sniOverride: String? = null): Result<Map<String, Any>> = runCatching {
        val cleanHost = host.trim()
        val sni = sniOverride?.takeIf { it.isNotBlank() } ?: cleanHost
        val factory = buildTrustAllSocketFactory()
        var sslSocket: SSLSocket? = null
        try {
            sslSocket = (factory.createSocket() as SSLSocket).apply {
                soTimeout = 5000
                runCatching {
                    val params = sslParameters
                    params.serverNames = listOf(SNIHostName(sni))
                    sslParameters = params
                }
                connect(InetSocketAddress(cleanHost, port), 5000)
                startHandshake()
            }
            val certs = sslSocket.session?.peerCertificates
            val cert = certs?.firstOrNull() as? X509Certificate
                ?: throw IllegalStateException("No X.509 peer certificates presented by $cleanHost:$port")

            mapOf(
                "subject" to cert.subjectX500Principal.name,
                "issuer" to cert.issuerX500Principal.name,
                "version" to cert.version,
                "serialNumber" to cert.serialNumber.toString(16),
                "notBefore" to cert.notBefore.toString(),
                "notAfter" to cert.notAfter.toString(),
                "sigAlgName" to cert.sigAlgName,
                "cipherSuite" to (sslSocket.session?.cipherSuite ?: "Unknown"),
                "protocol" to (sslSocket.session?.protocol ?: "TLS")
            )
        } finally {
            NetworkByteUtils.safeClose(sslSocket)
        }
    }

    private fun buildTrustAllSocketFactory(): SSLSocketFactory {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        return SSLContext.getInstance("TLS").apply {
            init(null, trustAll, SecureRandom())
        }.socketFactory
    }

    suspend fun sendWakeOnLan(macAddress: String, customBroadcastIp: String? = null): Result<String> = withContext(Dispatchers.IO) {
        _wolUiState.value = UiState.Loading
        try {
            val magicPacket = NetworkUtils.buildMagicPacket(macAddress)
            val targets = linkedSetOf<String>()
            targets.add("255.255.255.255")

            computeActiveSubnetBroadcast()?.let { targets.add(it) }

            if (!customBroadcastIp.isNullOrBlank()) {
                targets.add(customBroadcastIp.trim())
            }

            val sentTargets = mutableListOf<String>()
            DatagramSocket().use { socket ->
                socket.broadcast = true
                for (ipStr in targets) {
                    try {
                        val dest = InetAddress.getByName(ipStr)
                        for (port in listOf(7, 9)) {
                            socket.send(DatagramPacket(magicPacket, magicPacket.size, dest, port))
                        }
                        sentTargets.add(ipStr)
                    } catch (_: Exception) {}
                }
            }

            if (sentTargets.isEmpty()) {
                val err = IllegalStateException("Failed to transmit Magic Packet to any broadcast address.")
                _wolUiState.value = UiState.Error(err.message!!, err)
                return@withContext Result.failure(err)
            }

            val msg = "102-byte Magic Packet sent to $macAddress via ${sentTargets.joinToString(", ")} (UDP 7 & 9)"
            _wolUiState.value = UiState.Success(msg)
            Result.success(msg)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: "Invalid MAC address or broadcast error"
            _wolUiState.value = UiState.Error(msg, e)
            Result.failure(e)
        }
    }

    private fun computeActiveSubnetBroadcast(): String? {
        val topo = networkInfoRepository?.topology?.value ?: return null
        val localIp = topo.localIpv4
        if (!localIp.contains(".") || localIp == "Unavailable") return null
        val prefix = topo.subnetCidr.toIntOrNull()?.coerceIn(1, 30) ?: 24
        val ipLong = NetworkByteUtils.stringToIpLong(localIp)
        if (ipLong == 0L) return null
        val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        val broadcast = (ipLong and mask) or (mask.inv() and 0xFFFFFFFFL)
        return NetworkByteUtils.ipLongToString(broadcast)
    }

    fun addWatcherHost(host: String, label: String, intervalSec: Int = 10) {
        val cleanHost = host.trim()
        if (cleanHost.isBlank()) return
        val newTarget = HostWatcherTarget(
            id = UUID.randomUUID().toString(),
            host = cleanHost,
            label = label.ifBlank { cleanHost },
            checkIntervalSec = intervalSec.coerceAtLeast(3)
        )
        val updated = _watchedHosts.value + newTarget
        _watchedHosts.value = updated
        _watcherUiState.value = UiState.Success(updated)
        if (!_isWatcherRunning.value) {
            startWatcherLoop()
        }
    }

    fun removeWatcherHost(id: String) {
        val updated = _watchedHosts.value.filter { it.id != id }
        _watchedHosts.value = updated
        _watcherUiState.value = if (updated.isEmpty()) UiState.Idle else UiState.Success(updated)
    }

    fun startWatcherLoop() {
        if (watcherJob?.isActive == true) return
        _isWatcherRunning.value = true
        watcherJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val targets = _watchedHosts.value
                    if (targets.isNotEmpty()) {
                        val updatedList = mutableListOf<HostWatcherTarget>()

                        for (target in targets) {
                            val t0 = System.currentTimeMillis()
                            var isUp = false
                            var latency = 0L

                            try {
                                val addr = InetAddress.getByName(target.host)
                                isUp = addr.isReachable(1200)
                                latency = System.currentTimeMillis() - t0
                                if (!isUp) {
                                    for (probePort in listOf(443, 80)) {
                                        try {
                                            Socket().use { s ->
                                                s.connect(InetSocketAddress(addr.hostAddress ?: target.host, probePort), 800)
                                                isUp = true
                                                latency = System.currentTimeMillis() - t0
                                            }
                                            if (isUp) break
                                        } catch (_: Exception) {}
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                isUp = false
                            }

                            val newHistory = (target.recentHistory + isUp).takeLast(20)
                            val newTotal = target.totalChecks + 1
                            val newUp = target.upChecks + (if (isUp) 1 else 0)

                            updatedList.add(
                                target.copy(
                                    isUp = isUp,
                                    lastLatencyMs = if (isUp) latency.coerceAtLeast(1L) else 0L,
                                    totalChecks = newTotal,
                                    upChecks = newUp,
                                    lastCheckedTimestamp = System.currentTimeMillis(),
                                    recentHistory = newHistory
                                )
                            )
                        }

                        _watchedHosts.value = updatedList
                        _watcherUiState.value = UiState.Success(updatedList)
                    }
                    delay(4000L)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _watcherUiState.value = UiState.Error("Host watcher error: ${e.message}", e)
            } finally {
                _isWatcherRunning.value = false
            }
        }
    }

    fun stopWatcherLoop() {
        watcherJob?.cancel()
        watcherJob = null
        _isWatcherRunning.value = false
    }
}
