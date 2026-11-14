// === FILE: app/src/main/java/com/enterwebslinger/network/data/protocol/PcapCaptureEngine.kt ===
package com.enterwebslinger.network.data.protocol

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.enterwebslinger.network.core.util.NetworkByteUtils
import com.enterwebslinger.network.domain.model.CapturedPacketRecord
import com.enterwebslinger.network.domain.model.PcapCaptureStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicLong

/**
 * Pure-Kotlin Libpcap Binary Writer (LINKTYPE_RAW = 101) & L3/L4 Header Dissector.
 * Zero C/C++ native code. Parses IPv4/IPv6 + TCP/UDP/ICMP directly from raw TUN buffers,
 * writes standard .pcap capture files readable by Wireshark/tcpdump, and exports via FileProvider.
 */
class PcapCaptureEngine(private val context: Context?) {

    companion object {
        const val PCAP_MAGIC_LE = 0xA1B2C3D4.toInt()
        const val LINKTYPE_RAW = 101
        private const val MAX_RETAINED_PACKETS = 500
    }

    private val _packets = MutableStateFlow<List<CapturedPacketRecord>>(emptyList())
    val packets: StateFlow<List<CapturedPacketRecord>> = _packets.asStateFlow()

    private val _stats = MutableStateFlow(PcapCaptureStats())
    val stats: StateFlow<PcapCaptureStats> = _stats.asStateFlow()

    private val sequenceId = AtomicLong(0L)
    private val writerLock = Any()
    private var pcapStream: BufferedOutputStream? = null
    private var currentPcapFile: File? = null

    fun startCaptureSession(): Result<File> = synchronized(writerLock) {
        stopCaptureSession()
        return try {
            val ctx = context ?: return Result.failure(IllegalStateException("Application context unavailable."))
            val reportsDir = File(ctx.cacheDir, "reports").apply { mkdirs() }
            val outFile = File(reportsDir, "enterweb_capture_${System.currentTimeMillis()}.pcap")
            val bos = BufferedOutputStream(FileOutputStream(outFile))
            bos.write(buildLibpcapGlobalHeader())
            bos.flush()

            pcapStream = bos
            currentPcapFile = outFile
            sequenceId.set(0L)
            _packets.value = emptyList()
            _stats.value = PcapCaptureStats(
                isCapturing = true,
                activeFilePath = outFile.absolutePath,
                errorMessage = null
            )
            Result.success(outFile)
        } catch (e: Exception) {
            _stats.update { it.copy(isCapturing = false, errorMessage = e.message) }
            Result.failure(e)
        }
    }

    fun recordRawIpPacket(buffer: ByteArray, length: Int) {
        if (length <= 0 || length > buffer.size) return
        val slice = buffer.copyOf(length)
        val nowMs = System.currentTimeMillis()
        val tsSec = (nowMs / 1000L).toInt()
        val tsUsec = ((nowMs % 1000L) * 1000L).toInt()
        val tsMicros = nowMs * 1000L

        val pktId = sequenceId.incrementAndGet()
        val parsed = parseIpPacket(slice, length, pktId, tsMicros)

        synchronized(writerLock) {
            val bos = pcapStream
            if (bos != null) {
                runCatching {
                    bos.write(buildLibpcapPacketHeader(tsSec, tsUsec, slice.size, slice.size))
                    bos.write(slice)
                    bos.flush()
                }
            }
        }

        if (parsed != null) {
            _packets.update { current ->
                (listOf(parsed) + current).take(MAX_RETAINED_PACKETS)
            }
            _stats.update { s ->
                s.copy(
                    packetCount = s.packetCount + 1L,
                    totalBytes = s.totalBytes + length,
                    tcpCount = s.tcpCount + if (parsed.protocolName == "TCP") 1L else 0L,
                    udpCount = s.udpCount + if (parsed.protocolName == "UDP") 1L else 0L,
                    icmpCount = s.icmpCount + if (parsed.protocolName.startsWith("ICMP")) 1L else 0L
                )
            }
        }
    }

    fun stopCaptureSession() = synchronized(writerLock) {
        runCatching { pcapStream?.flush() }
        NetworkByteUtils.safeClose(pcapStream)
        pcapStream = null
        _stats.update { it.copy(isCapturing = false) }
    }

    fun clearCapturedPackets() {
        _packets.value = emptyList()
        _stats.update {
            it.copy(
                packetCount = 0L,
                totalBytes = 0L,
                tcpCount = 0L,
                udpCount = 0L,
                icmpCount = 0L
            )
        }
    }

    fun exportPcapUri(): Result<Pair<File, Uri>> = synchronized(writerLock) {
        runCatching { pcapStream?.flush() }
        val file = currentPcapFile
        if (file == null || !file.exists() || file.length() < 24L) {
            return Result.failure(IllegalStateException("No captured .pcap file available yet. Start capture and generate traffic first."))
        }
        return try {
            val ctx = context ?: return Result.failure(IllegalStateException("Application context unavailable."))
            val uri = FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.fileprovider",
                file
            )
            Result.success(file to uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildLibpcapGlobalHeader(snapLen: Int = 65535, linkType: Int = LINKTYPE_RAW): ByteArray {
        return ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(PCAP_MAGIC_LE)
            putShort(2.toShort()) // version_major
            putShort(4.toShort()) // version_minor
            putInt(0)             // thiszone
            putInt(0)             // sigfigs
            putInt(snapLen)
            putInt(linkType)
        }.array()
    }

    fun buildLibpcapPacketHeader(tsSec: Int, tsUsec: Int, inclLen: Int, origLen: Int): ByteArray {
        return ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(tsSec)
            putInt(tsUsec)
            putInt(inclLen)
            putInt(origLen)
        }.array()
    }

    fun parseIpPacket(
        rawBytes: ByteArray,
        length: Int = rawBytes.size,
        id: Long = 1L,
        timestampUs: Long = System.currentTimeMillis() * 1000L
    ): CapturedPacketRecord? {
        if (length < 20 || length > rawBytes.size) return null
        val version = (rawBytes[0].toInt() ushr 4) and 0x0F

        return when (version) {
            4 -> parseIpv4Packet(rawBytes, length, id, timestampUs)
            6 -> parseIpv6Packet(rawBytes, length, id, timestampUs)
            else -> null
        }
    }

    private fun parseIpv4Packet(
        raw: ByteArray,
        length: Int,
        id: Long,
        timestampUs: Long
    ): CapturedPacketRecord? {
        val ihl = (raw[0].toInt() and 0x0F) * 4
        if (ihl < 20 || length < ihl) return null
        val protoNum = raw[9].toInt() and 0xFF
        val srcIp = "${raw[12].toInt() and 0xFF}.${raw[13].toInt() and 0xFF}.${raw[14].toInt() and 0xFF}.${raw[15].toInt() and 0xFF}"
        val dstIp = "${raw[16].toInt() and 0xFF}.${raw[17].toInt() and 0xFF}.${raw[18].toInt() and 0xFF}.${raw[19].toInt() and 0xFF}"
        return parseL4AndBuildRecord(raw, length, ihl, protoNum, 4, srcIp, dstIp, id, timestampUs)
    }

    private fun parseIpv6Packet(
        raw: ByteArray,
        length: Int,
        id: Long,
        timestampUs: Long
    ): CapturedPacketRecord? {
        if (length < 40) return null
        val nextHeader = raw[6].toInt() and 0xFF
        val srcIp = runCatching {
            InetAddress.getByAddress(raw.copyOfRange(8, 24)).hostAddress ?: "::"
        }.getOrDefault("::")
        val dstIp = runCatching {
            InetAddress.getByAddress(raw.copyOfRange(24, 40)).hostAddress ?: "::"
        }.getOrDefault("::")
        return parseL4AndBuildRecord(raw, length, 40, nextHeader, 6, srcIp, dstIp, id, timestampUs)
    }

    private fun parseL4AndBuildRecord(
        raw: ByteArray,
        length: Int,
        l3HeaderLen: Int,
        protoNum: Int,
        ipVersion: Int,
        srcIp: String,
        dstIp: String,
        id: Long,
        timestampUs: Long
    ): CapturedPacketRecord {
        var protoName = when (protoNum) {
            6 -> "TCP"
            17 -> "UDP"
            1 -> "ICMP"
            58 -> "ICMPv6"
            else -> "IP-$protoNum"
        }
        var srcPort = 0
        var dstPort = 0
        var tcpFlags = ""
        var payloadOffset = l3HeaderLen

        when (protoNum) {
            6 -> {
                if (length >= l3HeaderLen + 14) {
                    srcPort = ((raw[l3HeaderLen].toInt() and 0xFF) shl 8) or (raw[l3HeaderLen + 1].toInt() and 0xFF)
                    dstPort = ((raw[l3HeaderLen + 2].toInt() and 0xFF) shl 8) or (raw[l3HeaderLen + 3].toInt() and 0xFF)
                    val dataOffset = ((raw[l3HeaderLen + 12].toInt() ushr 4) and 0x0F) * 4
                    payloadOffset = (l3HeaderLen + dataOffset.coerceAtLeast(20)).coerceAtMost(length)
                    val flagsByte = raw[l3HeaderLen + 13].toInt() and 0xFF
                    tcpFlags = buildList {
                        if ((flagsByte and 0x02) != 0) add("SYN")
                        if ((flagsByte and 0x10) != 0) add("ACK")
                        if ((flagsByte and 0x01) != 0) add("FIN")
                        if ((flagsByte and 0x04) != 0) add("RST")
                        if ((flagsByte and 0x08) != 0) add("PSH")
                        if ((flagsByte and 0x20) != 0) add("URG")
                    }.joinToString(",")
                }
            }
            17 -> {
                if (length >= l3HeaderLen + 8) {
                    srcPort = ((raw[l3HeaderLen].toInt() and 0xFF) shl 8) or (raw[l3HeaderLen + 1].toInt() and 0xFF)
                    dstPort = ((raw[l3HeaderLen + 2].toInt() and 0xFF) shl 8) or (raw[l3HeaderLen + 3].toInt() and 0xFF)
                    payloadOffset = (l3HeaderLen + 8).coerceAtMost(length)
                    if (srcPort == 53 || dstPort == 53) protoName = "DNS"
                }
            }
            1, 58 -> {
                if (length >= l3HeaderLen + 2) {
                    val icmpType = raw[l3HeaderLen].toInt() and 0xFF
                    val icmpCode = raw[l3HeaderLen + 1].toInt() and 0xFF
                    tcpFlags = "Type=$icmpType Code=$icmpCode"
                    payloadOffset = (l3HeaderLen + 8).coerceAtMost(length)
                }
            }
        }

        val payloadLen = (length - payloadOffset).coerceAtLeast(0)
        val preview = if (payloadLen > 0) {
            val previewLen = payloadLen.coerceAtMost(64)
            val sb = StringBuilder(previewLen)
            for (i in 0 until previewLen) {
                val c = raw[payloadOffset + i].toInt() and 0xFF
                sb.append(if (c in 32..126) c.toChar() else '.')
            }
            sb.toString()
        } else ""

        val summary = buildString {
            append("$srcIp")
            if (srcPort > 0) append(":$srcPort")
            append(" -> $dstIp")
            if (dstPort > 0) append(":$dstPort")
            if (tcpFlags.isNotBlank()) append(" [$tcpFlags]")
            append(" Len=$payloadLen")
        }

        return CapturedPacketRecord(
            id = id,
            timestampUs = timestampUs,
            capturedLength = length,
            originalLength = length,
            ipVersion = ipVersion,
            protocolName = protoName,
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = srcPort,
            dstPort = dstPort,
            tcpFlags = tcpFlags,
            summary = summary,
            payloadPreviewAscii = preview,
            rawIpBytes = raw.copyOf(length)
        )
    }
}
