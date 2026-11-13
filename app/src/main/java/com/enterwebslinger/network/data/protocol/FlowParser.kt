// === FILE: app/src/main/java/com/enterwebslinger/network/data/protocol/FlowParser.kt ===
package com.enterwebslinger.network.data.protocol

import com.enterwebslinger.network.domain.model.FlowProtocolType
import com.enterwebslinger.network.domain.model.FlowRecord
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

class FlowParser {

    data class TemplateField(val type: Int, val length: Int)

    private val v9TemplateCache = ConcurrentHashMap<String, List<TemplateField>>()
    private val ipfixTemplateCache = ConcurrentHashMap<String, List<TemplateField>>()

    fun parsePacket(data: ByteArray, length: Int): List<FlowRecord> {
        val safeLen = length.coerceAtMost(data.size)
        if (safeLen < 4) return emptyList()
        val buffer = ByteBuffer.wrap(data, 0, safeLen)
        val version = buffer.getShort(0).toInt() and 0xFFFF

        return try {
            when (version) {
                5 -> parseNetFlowV5(buffer)
                9 -> parseNetFlowV9(buffer)
                10 -> parseIpfix(buffer)
                else -> {
                    val sflowVersion = buffer.getInt(0)
                    if (sflowVersion == 5) {
                        parseSflowV5(buffer)
                    } else {
                        emptyList()
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseNetFlowV5(buffer: ByteBuffer): List<FlowRecord> {
        if (buffer.remaining() < 24) return emptyList()
        val records = mutableListOf<FlowRecord>()
        buffer.short // version
        val count = buffer.short.toInt() and 0xFFFF
        buffer.int // sysUptime
        buffer.int // unixSecs
        buffer.int // unixNsecs
        buffer.int // flowSeq
        buffer.get() // engineType
        buffer.get() // engineId
        buffer.short // samplingInterval

        for (i in 0 until count) {
            if (buffer.remaining() < 48) break
            val srcIp = readIpv4(buffer)
            val dstIp = readIpv4(buffer)
            readIpv4(buffer) // nextHop
            val inputIf = buffer.short.toInt() and 0xFFFF
            val outputIf = buffer.short.toInt() and 0xFFFF
            val dPkts = buffer.int.toLong() and 0xFFFFFFFFL
            val dOctets = buffer.int.toLong() and 0xFFFFFFFFL
            buffer.int // firstSwitched
            buffer.int // lastSwitched
            val srcPort = buffer.short.toInt() and 0xFFFF
            val dstPort = buffer.short.toInt() and 0xFFFF
            buffer.get() // pad1
            val tcpFlagsByte = buffer.get().toInt() and 0xFF
            val prot = buffer.get().toInt() and 0xFF
            val tos = buffer.get().toInt() and 0xFF
            val srcAs = buffer.short.toInt() and 0xFFFF
            val dstAs = buffer.short.toInt() and 0xFFFF
            buffer.get() // srcMask
            buffer.get() // dstMask
            buffer.short // pad2

            val dscpValue = (tos ushr 2) and 0x3F
            records.add(
                FlowRecord(
                    protocolType = FlowProtocolType.NETFLOW_V5,
                    srcIp = srcIp,
                    dstIp = dstIp,
                    srcPort = srcPort,
                    dstPort = dstPort,
                    ipProtocol = prot,
                    protocolName = getProtocolName(prot),
                    bytes = dOctets,
                    packets = dPkts,
                    tcpFlags = formatTcpFlags(tcpFlagsByte),
                    dscp = dscpValue,
                    dscpClass = getDscpClassName(dscpValue),
                    srcAs = srcAs,
                    dstAs = dstAs,
                    inputIfIndex = inputIf,
                    outputIfIndex = outputIf
                )
            )
        }
        return records
    }

    private fun parseNetFlowV9(buffer: ByteBuffer): List<FlowRecord> {
        if (buffer.remaining() < 20) return emptyList()
        val records = mutableListOf<FlowRecord>()
        buffer.short // version
        buffer.short // count
        buffer.int // sysUptime
        buffer.int // unixSecs
        buffer.int // seqNum
        val sourceId = buffer.int.toLong() and 0xFFFFFFFFL

        while (buffer.remaining() >= 4) {
            val flowsetId = buffer.short.toInt() and 0xFFFF
            val length = buffer.short.toInt() and 0xFFFF
            if (length < 4 || buffer.remaining() < length - 4) break
            val flowsetEnd = (buffer.position() + (length - 4)).coerceAtMost(buffer.limit())

            when (flowsetId) {
                0 -> {
                    while (buffer.position() < flowsetEnd && buffer.remaining() >= 4) {
                        val templateId = buffer.short.toInt() and 0xFFFF
                        val fieldCount = buffer.short.toInt() and 0xFFFF
                        val fields = mutableListOf<TemplateField>()
                        for (f in 0 until fieldCount) {
                            if (buffer.remaining() < 4 || buffer.position() + 4 > flowsetEnd) break
                            val fType = buffer.short.toInt() and 0xFFFF
                            val fLen = buffer.short.toInt() and 0xFFFF
                            if (fLen > 0) fields.add(TemplateField(fType, fLen))
                        }
                        if (fields.isNotEmpty()) {
                            v9TemplateCache["$sourceId-$templateId"] = fields
                        }
                    }
                    buffer.position(flowsetEnd)
                }
                1 -> buffer.position(flowsetEnd)
                else -> {
                    val fields = v9TemplateCache["$sourceId-$flowsetId"]
                    if (fields != null && fields.isNotEmpty()) {
                        val recordLength = fields.sumOf { it.length }
                        while (recordLength > 0 && buffer.position() + recordLength <= flowsetEnd) {
                            var srcIp = "0.0.0.0"
                            var dstIp = "0.0.0.0"
                            var srcPort = 0
                            var dstPort = 0
                            var prot = 0
                            var bytes = 0L
                            var pkts = 0L
                            var tcpFlags = 0
                            var tos = 0

                            for (field in fields) {
                                if (field.length <= 0 || buffer.remaining() < field.length) break
                                when (field.type) {
                                    8 -> srcIp = if (field.length == 4) readIpv4(buffer) else skipBytes(buffer, field.length)
                                    12 -> dstIp = if (field.length == 4) readIpv4(buffer) else skipBytes(buffer, field.length)
                                    7 -> srcPort = readVariableInt(buffer, field.length).toInt()
                                    11 -> dstPort = readVariableInt(buffer, field.length).toInt()
                                    4 -> prot = readVariableInt(buffer, field.length).toInt()
                                    1 -> bytes = readVariableInt(buffer, field.length)
                                    2 -> pkts = readVariableInt(buffer, field.length)
                                    6 -> tcpFlags = readVariableInt(buffer, field.length).toInt()
                                    5 -> tos = readVariableInt(buffer, field.length).toInt()
                                    else -> buffer.position(buffer.position() + field.length)
                                }
                            }

                            val dscp = (tos ushr 2) and 0x3F
                            records.add(
                                FlowRecord(
                                    protocolType = FlowProtocolType.NETFLOW_V9,
                                    srcIp = srcIp,
                                    dstIp = dstIp,
                                    srcPort = srcPort,
                                    dstPort = dstPort,
                                    ipProtocol = prot,
                                    protocolName = getProtocolName(prot),
                                    bytes = bytes,
                                    packets = pkts,
                                    tcpFlags = formatTcpFlags(tcpFlags),
                                    dscp = dscp,
                                    dscpClass = getDscpClassName(dscp)
                                )
                            )
                        }
                    }
                    buffer.position(flowsetEnd)
                }
            }
        }
        return records
    }

    private fun parseIpfix(buffer: ByteBuffer): List<FlowRecord> {
        if (buffer.remaining() < 16) return emptyList()
        val records = mutableListOf<FlowRecord>()
        buffer.short // version
        buffer.short // length
        buffer.int // exportTime
        buffer.int // seqNum
        val observationDomainId = buffer.int.toLong() and 0xFFFFFFFFL

        while (buffer.remaining() >= 4) {
            val setId = buffer.short.toInt() and 0xFFFF
            val setLen = buffer.short.toInt() and 0xFFFF
            if (setLen < 4 || buffer.remaining() < setLen - 4) break
            val setEnd = (buffer.position() + (setLen - 4)).coerceAtMost(buffer.limit())

            when (setId) {
                2 -> {
                    while (buffer.position() < setEnd && buffer.remaining() >= 4) {
                        val templateId = buffer.short.toInt() and 0xFFFF
                        val fieldCount = buffer.short.toInt() and 0xFFFF
                        val fields = mutableListOf<TemplateField>()
                        for (f in 0 until fieldCount) {
                            if (buffer.remaining() < 4 || buffer.position() + 4 > setEnd) break
                            val elementId = buffer.short.toInt() and 0xFFFF
                            val elemLen = buffer.short.toInt() and 0xFFFF
                            val cleanType = elementId and 0x7FFF
                            if ((elementId and 0x8000) != 0 && buffer.remaining() >= 4) {
                                buffer.int
                            }
                            if (elemLen in 1..65534) {
                                fields.add(TemplateField(cleanType, elemLen))
                            }
                        }
                        if (fields.isNotEmpty()) {
                            ipfixTemplateCache["$observationDomainId-$templateId"] = fields
                        }
                    }
                    buffer.position(setEnd)
                }
                else -> {
                    val fields = ipfixTemplateCache["$observationDomainId-$setId"]
                    if (fields != null && fields.isNotEmpty()) {
                        val recordLength = fields.sumOf { it.length }
                        while (recordLength > 0 && buffer.position() + recordLength <= setEnd) {
                            var srcIp = "0.0.0.0"
                            var dstIp = "0.0.0.0"
                            var srcPort = 0
                            var dstPort = 0
                            var prot = 0
                            var bytes = 0L
                            var pkts = 0L

                            for (field in fields) {
                                if (field.length <= 0 || buffer.remaining() < field.length) break
                                when (field.type) {
                                    8 -> srcIp = if (field.length == 4) readIpv4(buffer) else skipBytes(buffer, field.length)
                                    12 -> dstIp = if (field.length == 4) readIpv4(buffer) else skipBytes(buffer, field.length)
                                    7 -> srcPort = readVariableInt(buffer, field.length).toInt()
                                    11 -> dstPort = readVariableInt(buffer, field.length).toInt()
                                    4 -> prot = readVariableInt(buffer, field.length).toInt()
                                    1 -> bytes = readVariableInt(buffer, field.length)
                                    2 -> pkts = readVariableInt(buffer, field.length)
                                    else -> buffer.position(buffer.position() + field.length)
                                }
                            }

                            records.add(
                                FlowRecord(
                                    protocolType = FlowProtocolType.IPFIX,
                                    srcIp = srcIp,
                                    dstIp = dstIp,
                                    srcPort = srcPort,
                                    dstPort = dstPort,
                                    ipProtocol = prot,
                                    protocolName = getProtocolName(prot),
                                    bytes = bytes,
                                    packets = pkts
                                )
                            )
                        }
                    }
                    buffer.position(setEnd)
                }
            }
        }
        return records
    }

    private fun parseSflowV5(buffer: ByteBuffer): List<FlowRecord> {
        if (buffer.remaining() < 28) return emptyList()
        val records = mutableListOf<FlowRecord>()
        buffer.int // version
        val agentIpType = buffer.int
        if (agentIpType == 1) {
            if (buffer.remaining() < 20) return emptyList()
            readIpv4(buffer)
        } else {
            if (buffer.remaining() < 32) return emptyList()
            buffer.position(buffer.position() + 16)
        }
        if (buffer.remaining() < 16) return emptyList()
        buffer.int // subAgentId
        buffer.int // seqNum
        buffer.int // sysUptime
        val numSamples = buffer.int
        if (numSamples <= 0 || numSamples > 1024) return emptyList()

        for (s in 0 until numSamples) {
            if (buffer.remaining() < 8) break
            val sampleTag = buffer.int
            val sampleLen = buffer.int
            if (sampleLen < 0 || buffer.remaining() < sampleLen) break
            val sampleEnd = (buffer.position() + sampleLen).coerceAtMost(buffer.limit())

            if (sampleTag == 1 && (sampleEnd - buffer.position()) >= 32) {
                buffer.int // seq
                buffer.int // sourceId
                val rate = buffer.int
                buffer.int // pool
                buffer.int // drops
                val inputIf = buffer.int
                val outputIf = buffer.int
                val numElements = buffer.int
                if (numElements <= 0 || numElements > 256) {
                    buffer.position(sampleEnd)
                    continue
                }

                for (e in 0 until numElements) {
                    if (buffer.position() + 8 > sampleEnd) break
                    val elemTag = buffer.int
                    val elemLen = buffer.int
                    if (elemLen < 0 || buffer.position() + elemLen > sampleEnd) break
                    val elemEnd = (buffer.position() + elemLen).coerceAtMost(sampleEnd)

                    if (elemTag == 1 && (elemEnd - buffer.position()) >= 16) {
                        val headerProtocol = buffer.int
                        val frameLen = buffer.int
                        buffer.int // stripped
                        val headerBytesLen = buffer.int

                        if (headerProtocol == 1 &&
                            headerBytesLen >= 34 &&
                            (elemEnd - buffer.position()) >= 34 &&
                            buffer.position() + 34 <= buffer.limit()
                        ) {
                            val ethStart = buffer.position()
                            val etherType = buffer.getShort(ethStart + 12).toInt() and 0xFFFF
                            if (etherType == 0x0800) {
                                val ipStart = ethStart + 14
                                if (ipStart + 20 <= buffer.limit()) {
                                    val prot = buffer.get(ipStart + 9).toInt() and 0xFF
                                    val srcIp = "%d.%d.%d.%d".format(
                                        buffer.get(ipStart + 12).toInt() and 0xFF,
                                        buffer.get(ipStart + 13).toInt() and 0xFF,
                                        buffer.get(ipStart + 14).toInt() and 0xFF,
                                        buffer.get(ipStart + 15).toInt() and 0xFF
                                    )
                                    val dstIp = "%d.%d.%d.%d".format(
                                        buffer.get(ipStart + 16).toInt() and 0xFF,
                                        buffer.get(ipStart + 17).toInt() and 0xFF,
                                        buffer.get(ipStart + 18).toInt() and 0xFF,
                                        buffer.get(ipStart + 19).toInt() and 0xFF
                                    )
                                    val ihl = ((buffer.get(ipStart).toInt() and 0x0F) * 4).coerceAtLeast(20)
                                    val l4Start = ipStart + ihl
                                    val srcPort = if (l4Start + 2 <= buffer.limit()) buffer.getShort(l4Start).toInt() and 0xFFFF else 0
                                    val dstPort = if (l4Start + 4 <= buffer.limit()) buffer.getShort(l4Start + 2).toInt() and 0xFFFF else 0

                                    records.add(
                                        FlowRecord(
                                            protocolType = FlowProtocolType.SFLOW_V5,
                                            srcIp = srcIp,
                                            dstIp = dstIp,
                                            srcPort = srcPort,
                                            dstPort = dstPort,
                                            ipProtocol = prot,
                                            protocolName = getProtocolName(prot),
                                            bytes = frameLen.toLong(),
                                            packets = rate.toLong().coerceAtLeast(1L),
                                            inputIfIndex = inputIf,
                                            outputIfIndex = outputIf
                                        )
                                    )
                                }
                            }
                        }
                    }
                    buffer.position(elemEnd)
                }
            }
            buffer.position(sampleEnd)
        }
        return records
    }

    private fun readIpv4(buffer: ByteBuffer): String {
        if (buffer.remaining() < 4) return "0.0.0.0"
        val b0 = buffer.get().toInt() and 0xFF
        val b1 = buffer.get().toInt() and 0xFF
        val b2 = buffer.get().toInt() and 0xFF
        val b3 = buffer.get().toInt() and 0xFF
        return "$b0.$b1.$b2.$b3"
    }

    private fun skipBytes(buffer: ByteBuffer, len: Int): String {
        val toSkip = len.coerceIn(0, buffer.remaining())
        buffer.position(buffer.position() + toSkip)
        return "0.0.0.0"
    }

    private fun readVariableInt(buffer: ByteBuffer, length: Int): Long {
        val safeLen = length.coerceIn(0, buffer.remaining())
        var result = 0L
        for (i in 0 until safeLen) {
            result = (result shl 8) or (buffer.get().toLong() and 0xFFL)
        }
        return result
    }

    private fun getProtocolName(proto: Int): String = when (proto) {
        1 -> "ICMP"
        6 -> "TCP"
        17 -> "UDP"
        47 -> "GRE"
        50 -> "ESP"
        51 -> "AH"
        89 -> "OSPF"
        132 -> "SCTP"
        else -> "IP-$proto"
    }

    private fun formatTcpFlags(flags: Int): String {
        val sb = StringBuilder()
        if ((flags and 0x01) != 0) sb.append("FIN ")
        if ((flags and 0x02) != 0) sb.append("SYN ")
        if ((flags and 0x04) != 0) sb.append("RST ")
        if ((flags and 0x08) != 0) sb.append("PSH ")
        if ((flags and 0x10) != 0) sb.append("ACK ")
        if ((flags and 0x20) != 0) sb.append("URG ")
        return sb.toString().trim()
    }

    private fun getDscpClassName(dscp: Int): String = when (dscp) {
        0 -> "CS0 (Best Effort)"
        8 -> "CS1"
        10 -> "AF11"
        12 -> "AF12"
        14 -> "AF13"
        16 -> "CS2"
        18 -> "AF21"
        20 -> "AF22"
        22 -> "AF23"
        24 -> "CS3"
        26 -> "AF31"
        28 -> "AF32"
        30 -> "AF33"
        32 -> "CS4"
        34 -> "AF41"
        36 -> "AF42"
        38 -> "AF43"
        40 -> "CS5"
        46 -> "EF (Voice)"
        48 -> "CS6 (Network Control)"
        56 -> "CS7"
        else -> "DSCP $dscp"
    }

    fun getActiveTemplateCount(): Int = v9TemplateCache.size + ipfixTemplateCache.size
}
