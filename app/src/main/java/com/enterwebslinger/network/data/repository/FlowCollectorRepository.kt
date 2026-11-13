// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/FlowCollectorRepository.kt ===
package com.enterwebslinger.network.data.repository

import com.enterwebslinger.network.data.protocol.FlowParser
import com.enterwebslinger.network.domain.model.DscpDistribution
import com.enterwebslinger.network.domain.model.FlowCollectorStats
import com.enterwebslinger.network.domain.model.FlowRecord
import com.enterwebslinger.network.domain.model.ProtocolDistribution
import com.enterwebslinger.network.domain.model.TopTalkerEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.channels.DatagramChannel
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class FlowCollectorRepository(private val externalScope: CoroutineScope) {

    private val flowParser = FlowParser()

    private val _stats = MutableStateFlow(FlowCollectorStats())
    val stats: StateFlow<FlowCollectorStats> = _stats.asStateFlow()

    private val _recentFlows = MutableStateFlow<List<FlowRecord>>(emptyList())
    val recentFlows: StateFlow<List<FlowRecord>> = _recentFlows.asStateFlow()

    private val _topTalkers = MutableStateFlow<List<TopTalkerEntry>>(emptyList())
    val topTalkers: StateFlow<List<TopTalkerEntry>> = _topTalkers.asStateFlow()

    private val _protocolDistribution = MutableStateFlow<List<ProtocolDistribution>>(emptyList())
    val protocolDistribution: StateFlow<List<ProtocolDistribution>> = _protocolDistribution.asStateFlow()

    private val _dscpDistribution = MutableStateFlow<List<DscpDistribution>>(emptyList())
    val dscpDistribution: StateFlow<List<DscpDistribution>> = _dscpDistribution.asStateFlow()

    private val collectorJobs = mutableListOf<Job>()
    private val activeChannels = Collections.synchronizedList(mutableListOf<DatagramChannel>())
    private val activeBoundPorts = Collections.synchronizedSet(mutableSetOf<Int>())
    private val bindErrors = Collections.synchronizedList(mutableListOf<String>())

    private val totalPackets = AtomicLong(0)
    private val totalFlows = AtomicLong(0)
    private val totalBytes = AtomicLong(0)
    private val droppedPackets = AtomicLong(0)

    private val conversationBytesMap = ConcurrentHashMap<String, Pair<Long, Long>>()
    private val protocolBytesMap = ConcurrentHashMap<String, Pair<Long, Long>>()
    private val dscpPacketsMap = ConcurrentHashMap<String, Long>()

    private var lastRateCalcTime = System.currentTimeMillis()
    private var lastBytesCount = 0L
    private var lastPacketsCount = 0L
    private var rateMonitorJob: Job? = null

    fun startCollectors(ports: List<Int> = listOf(2055, 4739, 6343)) {
        if (_stats.value.isRunning) return
        stopCollectors()
        bindErrors.clear()
        activeBoundPorts.clear()

        val safePorts = ports.map { it.coerceAtLeast(1024) }.distinct()
        _stats.value = _stats.value.copy(isRunning = true, boundPorts = safePorts, errorMessage = null)

        for (port in safePorts) {
            val job = externalScope.launch(Dispatchers.IO) {
                var channel: DatagramChannel? = null
                try {
                    channel = DatagramChannel.open().apply {
                        setOption(StandardSocketOptions.SO_REUSEADDR, true)
                        socket().bind(InetSocketAddress(port))
                    }
                    activeChannels.add(channel)
                    activeBoundPorts.add(port)
                    val socket = channel.socket()
                    val buffer = ByteArray(8192)

                    while (isActive) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        totalPackets.incrementAndGet()

                        val records = flowParser.parsePacket(packet.data, packet.length)
                        if (records.isNotEmpty()) {
                            totalFlows.addAndGet(records.size.toLong())
                            var packetBytes = 0L

                            for (record in records) {
                                packetBytes += record.bytes
                                val convKey = "${record.srcIp} -> ${record.dstIp}:${record.dstPort}"
                                conversationBytesMap.compute(convKey) { _, prev ->
                                    Pair((prev?.first ?: 0L) + record.bytes, (prev?.second ?: 0L) + record.packets)
                                }
                                protocolBytesMap.compute(record.protocolName) { _, prev ->
                                    Pair((prev?.first ?: 0L) + record.bytes, (prev?.second ?: 0L) + record.packets)
                                }
                                dscpPacketsMap.compute(record.dscpClass) { _, prev ->
                                    (prev ?: 0L) + record.packets
                                }
                            }
                            totalBytes.addAndGet(packetBytes)

                            val current = _recentFlows.value.toMutableList()
                            current.addAll(0, records)
                            _recentFlows.value = if (current.size > 100) current.take(100) else current
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isActive) {
                        droppedPackets.incrementAndGet()
                        bindErrors.add("UDP $port: ${e.localizedMessage ?: e.javaClass.simpleName}")
                    }
                } finally {
                    try { channel?.close() } catch (_: Exception) {}
                    activeChannels.remove(channel)
                    activeBoundPorts.remove(port)
                }
            }
            collectorJobs.add(job)
        }

        rateMonitorJob = externalScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                val dt = ((now - lastRateCalcTime) / 1000.0).coerceAtLeast(1.0)
                val currentBytes = totalBytes.get()
                val currentPkts = totalPackets.get()

                val bytesPerSec = ((currentBytes - lastBytesCount) / dt).toLong().coerceAtLeast(0L)
                val pktsPerSec = ((currentPkts - lastPacketsCount) / dt).toLong().coerceAtLeast(0L)

                lastRateCalcTime = now
                lastBytesCount = currentBytes
                lastPacketsCount = currentPkts

                val totalB = currentBytes.coerceAtLeast(1L)
                _topTalkers.value = conversationBytesMap.entries
                    .sortedByDescending { it.value.first }
                    .take(10)
                    .map { (key, stats) ->
                        val parts = key.split(" -> ")
                        val src = parts.getOrNull(0) ?: "unknown"
                        val dstWithPort = parts.getOrNull(1) ?: "unknown"
                        TopTalkerEntry(
                            conversation = key,
                            srcIp = src,
                            dstIp = dstWithPort.substringBefore(":"),
                            serviceOrPort = dstWithPort.substringAfter(":", "0"),
                            totalBytes = stats.first,
                            totalPackets = stats.second,
                            percentageOfTotal = (stats.first.toFloat() / totalB.toFloat()) * 100f
                        )
                    }

                _protocolDistribution.value = protocolBytesMap.entries
                    .sortedByDescending { it.value.first }
                    .map { (proto, stats) ->
                        ProtocolDistribution(
                            protocol = proto,
                            byteCount = stats.first,
                            packetCount = stats.second,
                            percentage = (stats.first.toFloat() / totalB.toFloat()) * 100f
                        )
                    }

                val totalP = currentPkts.coerceAtLeast(1L)
                _dscpDistribution.value = dscpPacketsMap.entries
                    .sortedByDescending { it.value }
                    .map { (dscp, pkts) ->
                        DscpDistribution(
                            dscpClass = dscp,
                            packets = pkts,
                            percentage = (pkts.toFloat() / totalP.toFloat()) * 100f
                        )
                    }

                val boundSnapshot = synchronized(activeBoundPorts) { activeBoundPorts.sorted() }
                val errSnapshot = synchronized(bindErrors) {
                    if (bindErrors.isEmpty()) null else bindErrors.joinToString("; ")
                }
                val stillRunning = boundSnapshot.isNotEmpty() || bindErrors.size < safePorts.size

                _stats.value = FlowCollectorStats(
                    isRunning = stillRunning,
                    boundPorts = boundSnapshot,
                    totalPacketsReceived = currentPkts,
                    totalFlowsDecoded = totalFlows.get(),
                    totalBytesProcessed = currentBytes,
                    currentBytesPerSec = bytesPerSec,
                    currentPacketsPerSec = pktsPerSec,
                    droppedPackets = droppedPackets.get(),
                    activeTemplatesCount = flowParser.getActiveTemplateCount(),
                    errorMessage = errSnapshot
                )

                if (!stillRunning) {
                    break
                }
            }
        }
    }

    fun stopCollectors() {
        collectorJobs.forEach { it.cancel() }
        collectorJobs.clear()
        synchronized(activeChannels) {
            activeChannels.forEach {
                try { it.close() } catch (_: Exception) {}
            }
            activeChannels.clear()
        }
        activeBoundPorts.clear()
        rateMonitorJob?.cancel()
        rateMonitorJob = null

        _stats.value = _stats.value.copy(isRunning = false)
    }

    fun clearStats() {
        totalPackets.set(0)
        totalFlows.set(0)
        totalBytes.set(0)
        droppedPackets.set(0)
        bindErrors.clear()
        conversationBytesMap.clear()
        protocolBytesMap.clear()
        dscpPacketsMap.clear()
        _recentFlows.value = emptyList()
        _topTalkers.value = emptyList()
        _protocolDistribution.value = emptyList()
        _dscpDistribution.value = emptyList()
        _stats.value = _stats.value.copy(errorMessage = null)
    }
}
