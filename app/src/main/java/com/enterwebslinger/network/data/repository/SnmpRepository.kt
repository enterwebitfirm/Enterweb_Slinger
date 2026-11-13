// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/SnmpRepository.kt ===
package com.enterwebslinger.network.data.repository

import com.enterwebslinger.network.data.protocol.SnmpCodec
import com.enterwebslinger.network.domain.model.SnmpInterfaceEntry
import com.enterwebslinger.network.domain.model.SnmpMibSummary
import com.enterwebslinger.network.domain.model.SnmpPduResult
import com.enterwebslinger.network.domain.model.SnmpTargetConfig
import com.enterwebslinger.network.domain.model.SnmpTrapRecord
import com.enterwebslinger.network.domain.model.SnmpVarBind
import com.enterwebslinger.network.domain.model.SnmpVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.channels.DatagramChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class SnmpRepository(private val externalScope: CoroutineScope) {

    private data class CachedV3Engine(
        val engineId: ByteArray,
        val engineBoots: Int,
        val engineTime: Int,
        val discoveredAtMs: Long
    )

    private val requestIdGenerator = AtomicInteger(1000)
    private val v3EngineCache = ConcurrentHashMap<String, CachedV3Engine>()

    private val _trapRecords = MutableStateFlow<List<SnmpTrapRecord>>(emptyList())
    val trapRecords: StateFlow<List<SnmpTrapRecord>> = _trapRecords.asStateFlow()

    private val _isTrapReceiverRunning = MutableStateFlow(false)
    val isTrapReceiverRunning: StateFlow<Boolean> = _isTrapReceiverRunning.asStateFlow()

    private val _trapErrorMessage = MutableStateFlow<String?>(null)
    val trapErrorMessage: StateFlow<String?> = _trapErrorMessage.asStateFlow()

    private var trapReceiverJob: Job? = null
    private var trapChannel: DatagramChannel? = null

    private val prevInterfaceSamples = ConcurrentHashMap<Int, Triple<Long, Long, Long>>()

    suspend fun sendGet(config: SnmpTargetConfig, oids: List<String>): Result<SnmpPduResult> {
        if (config.host.isBlank()) return Result.failure(IllegalArgumentException("SNMP target host is required"))
        return executeSnmpQuery(config, oids, isGetNext = false)
    }

    suspend fun sendGetNext(config: SnmpTargetConfig, oid: String): Result<SnmpPduResult> {
        if (config.host.isBlank()) return Result.failure(IllegalArgumentException("SNMP target host is required"))
        return executeSnmpQuery(config, listOf(oid), isGetNext = true)
    }

    private suspend fun executeSnmpQuery(
        config: SnmpTargetConfig,
        oids: List<String>,
        isGetNext: Boolean
    ): Result<SnmpPduResult> = withContext(Dispatchers.IO) {
        try {
            val reqId = requestIdGenerator.incrementAndGet()
            val start = System.currentTimeMillis()
            val maxAttempts = (config.retries + 1).coerceIn(1, 4)

            if (config.version == SnmpVersion.V3) {
                val engine = resolveV3Engine(config)
                val requestBytes = SnmpCodec.buildV3GetRequest(
                    config = config,
                    requestId = reqId,
                    oids = oids,
                    isGetNext = isGetNext,
                    engineParams = engine
                )
                val responseBytes = executeUdpQueryWithRetries(
                    host = config.host,
                    port = config.port,
                    requestBytes = requestBytes,
                    timeoutMs = config.timeoutMs,
                    attempts = maxAttempts
                )
                val duration = System.currentTimeMillis() - start
                Result.success(SnmpCodec.parseSnmpResponse(responseBytes, duration, v3Config = config))
            } else {
                val requestBytes = SnmpCodec.buildV1OrV2cGetRequest(
                    versionWireValue = config.version.wireValue,
                    community = config.community,
                    requestId = reqId,
                    oids = oids,
                    isGetNext = isGetNext
                )
                val responseBytes = executeUdpQueryWithRetries(
                    host = config.host,
                    port = config.port,
                    requestBytes = requestBytes,
                    timeoutMs = config.timeoutMs,
                    attempts = maxAttempts
                )
                val duration = System.currentTimeMillis() - start
                Result.success(SnmpCodec.parseSnmpResponse(responseBytes, duration))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun resolveV3Engine(config: SnmpTargetConfig): SnmpCodec.SnmpV3EngineParams {
        val key = "${config.host.trim()}:${config.port}"
        val cached = v3EngineCache[key]
        if (cached != null) {
            val elapsedSec = ((System.currentTimeMillis() - cached.discoveredAtMs) / 1000L).toInt().coerceAtLeast(0)
            return SnmpCodec.SnmpV3EngineParams(
                engineId = cached.engineId,
                engineBoots = cached.engineBoots,
                engineTime = cached.engineTime + elapsedSec
            )
        }
        val probeReqId = requestIdGenerator.incrementAndGet()
        val discoveryBytes = SnmpCodec.buildV3DiscoveryPacket(probeReqId)
        val responseBytes = executeUdpQueryWithRetries(
            host = config.host,
            port = config.port,
            requestBytes = discoveryBytes,
            timeoutMs = config.timeoutMs,
            attempts = 2
        )
        val params = SnmpCodec.extractV3EngineParams(responseBytes)
            ?: throw IllegalStateException("SNMPv3 engine discovery failed for ${config.host}:${config.port}")
        v3EngineCache[key] = CachedV3Engine(
            engineId = params.engineId,
            engineBoots = params.engineBoots,
            engineTime = params.engineTime,
            discoveredAtMs = System.currentTimeMillis()
        )
        return params
    }

    suspend fun snmpWalk(config: SnmpTargetConfig, rootOid: String, maxSteps: Int = 50): Result<List<SnmpVarBind>> {
        if (config.host.isBlank()) return Result.failure(IllegalArgumentException("SNMP target host is required"))
        return withContext(Dispatchers.IO) {
            val results = mutableListOf<SnmpVarBind>()
            var currentOid = rootOid
            var steps = 0

            try {
                while (steps < maxSteps) {
                    val nextResult = sendGetNext(config, currentOid).getOrThrow()
                    val vb = nextResult.varBinds.firstOrNull() ?: break
                    if (vb.syntax == "endOfMibView" || vb.syntax == "noSuchObject" || vb.syntax == "noSuchInstance") break
                    if (!vb.oid.startsWith(rootOid)) break
                    if (vb.oid == currentOid) break

                    results.add(vb)
                    currentOid = vb.oid
                    steps++
                }
                Result.success(results)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (results.isNotEmpty()) {
                    Result.success(results)
                } else {
                    Result.failure(e)
                }
            }
        }
    }

    suspend fun fetchMibSummary(config: SnmpTargetConfig): Result<SnmpMibSummary> {
        if (config.host.isBlank()) return Result.failure(IllegalArgumentException("SNMP target host is required"))
        return withContext(Dispatchers.IO) {
            try {
                val sysOids = listOf(
                    "1.3.6.1.2.1.1.1.0",
                    "1.3.6.1.2.1.1.2.0",
                    "1.3.6.1.2.1.1.3.0",
                    "1.3.6.1.2.1.1.4.0",
                    "1.3.6.1.2.1.1.5.0",
                    "1.3.6.1.2.1.1.6.0"
                )

                val sysResult = sendGet(config, sysOids).getOrElse { return@withContext Result.failure(it) }
                if (sysResult.varBinds.isEmpty()) {
                    return@withContext Result.failure(Exception("SNMP sysGroup returned no variable bindings"))
                }

                val sysDescr = sysResult.varBinds.getOrNull(0)?.value ?: "Unknown"
                val sysObjId = sysResult.varBinds.getOrNull(1)?.value ?: "Unknown"
                val sysUpTime = sysResult.varBinds.getOrNull(2)?.value ?: "N/A"
                val sysContact = sysResult.varBinds.getOrNull(3)?.value ?: "N/A"
                val sysName = sysResult.varBinds.getOrNull(4)?.value ?: "Unknown"
                val sysLocation = sysResult.varBinds.getOrNull(5)?.value ?: "N/A"

                val cpuResult = sendGet(config, listOf("1.3.6.1.2.1.25.3.3.1.2.1")).getOrNull()
                val cpuLoad = cpuResult?.varBinds?.firstOrNull()?.value?.toIntOrNull()

                val descrWalk = snmpWalk(config, "1.3.6.1.2.1.2.2.1.2", 16).getOrDefault(emptyList())
                val now = System.currentTimeMillis()

                val ifaces = mutableListOf<SnmpInterfaceEntry>()
                for ((idx, descrVb) in descrWalk.withIndex()) {
                    val ifIndex = descrVb.oid.substringAfterLast(".").toIntOrNull() ?: (idx + 1)
                    val ifName = descrVb.value

                    val statsOids = listOf(
                        "1.3.6.1.2.1.2.2.1.3.$ifIndex",
                        "1.3.6.1.2.1.2.2.1.5.$ifIndex",
                        "1.3.6.1.2.1.2.2.1.6.$ifIndex",
                        "1.3.6.1.2.1.2.2.1.8.$ifIndex",
                        "1.3.6.1.2.1.2.2.1.10.$ifIndex",
                        "1.3.6.1.2.1.2.2.1.16.$ifIndex"
                    )
                    val statsResult = sendGet(config, statsOids).getOrNull()
                    val type = when (statsResult?.varBinds?.getOrNull(0)?.value) {
                        "6" -> "ethernetCsmacd"
                        "24" -> "softwareLoopback"
                        "71" -> "ieee80211 (Wi-Fi)"
                        else -> "other"
                    }
                    val speed = (statsResult?.varBinds?.getOrNull(1)?.value?.toLongOrNull() ?: 0L) / 1_000_000L
                    val mac = statsResult?.varBinds?.getOrNull(2)?.value ?: ""
                    val oper = if (statsResult?.varBinds?.getOrNull(3)?.value == "1") "UP" else "DOWN"
                    val inOctets = statsResult?.varBinds?.getOrNull(4)?.value?.toLongOrNull() ?: 0L
                    val outOctets = statsResult?.varBinds?.getOrNull(5)?.value?.toLongOrNull() ?: 0L

                    var inRate = 0L
                    var outRate = 0L
                    val prev = prevInterfaceSamples[ifIndex]
                    if (prev != null) {
                        val dtSec = ((now - prev.first) / 1000.0).coerceAtLeast(1.0)
                        val dIn = (inOctets - prev.second).coerceAtLeast(0L)
                        val dOut = (outOctets - prev.third).coerceAtLeast(0L)
                        inRate = (dIn * 8 / dtSec).toLong()
                        outRate = (dOut * 8 / dtSec).toLong()
                    }
                    prevInterfaceSamples[ifIndex] = Triple(now, inOctets, outOctets)

                    ifaces.add(
                        SnmpInterfaceEntry(
                            index = ifIndex,
                            name = ifName,
                            type = type,
                            speedMbps = speed,
                            macAddress = mac,
                            adminStatus = oper,
                            operStatus = oper,
                            inOctets = inOctets,
                            outOctets = outOctets,
                            inOctetsRateBps = inRate,
                            outOctetsRateBps = outRate
                        )
                    )
                }

                Result.success(
                    SnmpMibSummary(
                        sysDescr = sysDescr,
                        sysObjectId = sysObjId,
                        sysUpTime = sysUpTime,
                        sysContact = sysContact,
                        sysName = sysName,
                        sysLocation = sysLocation,
                        cpuUtilization = cpuLoad,
                        memoryUtilization = null,
                        interfaces = ifaces
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun startTrapReceiver(port: Int = 1162) {
        if (_isTrapReceiverRunning.value) return
        stopTrapReceiver()
        _trapErrorMessage.value = null
        val safePort = port.coerceAtLeast(1024)

        trapReceiverJob = externalScope.launch(Dispatchers.IO) {
            var channel: DatagramChannel? = null
            try {
                channel = DatagramChannel.open().apply {
                    setOption(StandardSocketOptions.SO_REUSEADDR, true)
                    socket().bind(InetSocketAddress(safePort))
                }
                trapChannel = channel
                val socket = channel.socket()
                _isTrapReceiverRunning.value = true
                val buffer = ByteArray(4096)

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)

                    val sourceIp = packet.address.hostAddress ?: "unknown"
                    val sourcePort = packet.port
                    val receivedBytes = packet.data.copyOf(packet.length)

                    val trapRecord = SnmpCodec.parseTrap(receivedBytes, sourceIp, sourcePort)
                    val currentList = _trapRecords.value.toMutableList()
                    currentList.add(0, trapRecord)
                    if (currentList.size > 200) currentList.removeAt(currentList.lastIndex)
                    _trapRecords.value = currentList
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isActive) {
                    _trapErrorMessage.value = "Trap listener error on UDP $safePort: ${e.localizedMessage ?: e.javaClass.simpleName}"
                }
            } finally {
                try { channel?.close() } catch (_: Exception) {}
                if (trapChannel === channel) trapChannel = null
                _isTrapReceiverRunning.value = false
            }
        }
    }

    fun stopTrapReceiver() {
        trapReceiverJob?.cancel()
        trapReceiverJob = null
        try {
            trapChannel?.close()
        } catch (_: Exception) {}
        trapChannel = null
        _isTrapReceiverRunning.value = false
    }

    fun clearTraps() {
        _trapRecords.value = emptyList()
    }

    private fun executeUdpQueryWithRetries(
        host: String,
        port: Int,
        requestBytes: ByteArray,
        timeoutMs: Int,
        attempts: Int
    ): ByteArray {
        var lastError: Exception? = null
        for (attempt in 0 until attempts) {
            try {
                return executeUdpQuery(host, port, requestBytes, timeoutMs)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("SNMP UDP query failed")
    }

    private fun executeUdpQuery(host: String, port: Int, requestBytes: ByteArray, timeoutMs: Int): ByteArray {
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            val address = InetAddress.getByName(host)
            socket.send(DatagramPacket(requestBytes, requestBytes.size, address, port))

            val receiveBuffer = ByteArray(8192)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
            socket.receive(receivePacket)

            return receivePacket.data.copyOf(receivePacket.length)
        }
    }
}
