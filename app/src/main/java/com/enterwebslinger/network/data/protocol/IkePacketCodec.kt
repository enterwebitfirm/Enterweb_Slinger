// === FILE: app/src/main/java/com/enterwebslinger/network/data/protocol/IkePacketCodec.kt ===
package com.enterwebslinger.network.data.protocol

import com.enterwebslinger.network.core.util.NetworkByteUtils
import com.enterwebslinger.network.domain.model.AllowedIpsCalculation
import com.enterwebslinger.network.domain.model.IkeProbeResult
import com.enterwebslinger.network.domain.model.TunnelEncapsulationType
import com.enterwebslinger.network.domain.model.TunnelMssCalculation
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom

/**
 * Pure-Kotlin / JVM RFC 7296 IKEv2 SA_INIT & RFC 3948 NAT-T Packet Codec,
 * Tunnel Encapsulation Overhead / TCP MSS Clamping Calculator, and
 * Exact Binary CIDR Subtraction Calculator for WireGuard AllowedIPs.
 */
object IkePacketCodec {

    private val random = SecureRandom()

    fun buildIkev2SaInitProbe(
        useNatT4500: Boolean = false,
        initiatorSpi: ByteArray = ByteArray(8).apply { random.nextBytes(this) }
    ): ByteArray {
        require(initiatorSpi.size == 8) { "Initiator SPI must be 8 bytes" }

        // Transform 1: ENCR_AES_CBC (12) + Key Length 256 (14)
        val tEncr = buildTransform(isLast = false, type = 1, id = 12, keyLengthBits = 256)
        // Transform 2: PRF_HMAC_SHA2_256 (5)
        val tPrf = buildTransform(isLast = false, type = 2, id = 5)
        // Transform 3: AUTH_HMAC_SHA2_256_128 (12)
        val tAuth = buildTransform(isLast = false, type = 3, id = 12)
        // Transform 4: DH Group 14 (2048-bit MODP)
        val tDh = buildTransform(isLast = true, type = 4, id = 14)

        val transforms = tEncr + tPrf + tAuth + tDh
        // Proposal #1 (IKE=1, 4 transforms, last=0)
        val proposalLen = 8 + transforms.size
        val proposal = ByteBuffer.allocate(proposalLen).apply {
            put(0.toByte()) // Last Proposal (0 = last)
            put(0.toByte())
            putShort(proposalLen.toShort())
            put(1.toByte()) // Proposal #1
            put(1.toByte()) // Protocol ID = IKE (1)
            put(0.toByte()) // SPI Size = 0 in IKE_SA_INIT
            put(4.toByte()) // 4 transforms
            put(transforms)
        }.array()

        // Payload 33 (SA) -> next = 34 (KE)
        val saPayloadLen = 4 + proposal.size
        val saPayload = ByteBuffer.allocate(saPayloadLen).apply {
            put(34.toByte()) // Next Payload: KE
            put(0.toByte())
            putShort(saPayloadLen.toShort())
            put(proposal)
        }.array()

        // Payload 34 (KE, DH Group 14 = 256 bytes public value) -> next = 40 (Nonce)
        val keData = ByteArray(256).apply { random.nextBytes(this) }
        val kePayloadLen = 8 + keData.size
        val kePayload = ByteBuffer.allocate(kePayloadLen).apply {
            put(40.toByte()) // Next Payload: Nonce
            put(0.toByte())
            putShort(kePayloadLen.toShort())
            putShort(14.toShort()) // DH Group 14
            putShort(0.toShort())  // Reserved
            put(keData)
        }.array()

        // Payload 40 (Nonce, 32 bytes) -> next = 0 (None)
        val nonceData = ByteArray(32).apply { random.nextBytes(this) }
        val noncePayloadLen = 4 + nonceData.size
        val noncePayload = ByteBuffer.allocate(noncePayloadLen).apply {
            put(0.toByte()) // Next Payload: None
            put(0.toByte())
            putShort(noncePayloadLen.toShort())
            put(nonceData)
        }.array()

        val ikeTotalLen = 28 + saPayload.size + kePayload.size + noncePayload.size
        val ikeHeader = ByteBuffer.allocate(28).apply {
            put(initiatorSpi)
            put(ByteArray(8)) // Responder SPI = 0
            put(33.toByte())  // Next Payload: SA (33)
            put(0x20.toByte()) // IKEv2 (Major 2, Minor 0)
            put(34.toByte())  // Exchange Type: IKE_SA_INIT (34)
            put(0x08.toByte()) // Flags: Initiator
            putInt(0)         // Message ID: 0
            putInt(ikeTotalLen)
        }.array()

        val out = ByteArrayOutputStream()
        if (useNatT4500) {
            out.write(byteArrayOf(0x00, 0x00, 0x00, 0x00)) // RFC 3948 Non-ESP Marker
        }
        out.write(ikeHeader)
        out.write(saPayload)
        out.write(kePayload)
        out.write(noncePayload)
        return out.toByteArray()
    }

    private fun buildTransform(isLast: Boolean, type: Int, id: Int, keyLengthBits: Int? = null): ByteArray {
        val attrSize = if (keyLengthBits != null) 4 else 0
        val total = 8 + attrSize
        return ByteBuffer.allocate(total).apply {
            put(if (isLast) 0.toByte() else 3.toByte())
            put(0.toByte())
            putShort(total.toShort())
            put(type.toByte())
            put(0.toByte())
            putShort(id.toShort())
            if (keyLengthBits != null) {
                putShort(0x800E.toShort()) // TV Format + Type 14 (Key Length)
                putShort(keyLengthBits.toShort())
            }
        }.array()
    }

    fun parseIkev2Response(
        raw: ByteArray,
        length: Int,
        host: String,
        port: Int,
        useNatT4500: Boolean,
        expectedISpiHex: String,
        rttMs: Long
    ): IkeProbeResult {
        var offset = 0
        var nonEspMarkerSeen = false
        if (length >= 32 && raw[0] == 0.toByte() && raw[1] == 0.toByte() && raw[2] == 0.toByte() && raw[3] == 0.toByte()) {
            offset = 4
            nonEspMarkerSeen = true
        }
        if (length - offset < 28) {
            return IkeProbeResult(
                host = host,
                port = port,
                useNatT4500 = useNatT4500,
                ikeVersionLabel = "Unknown",
                peerResponded = true,
                initiatorSpiHex = expectedISpiHex,
                responderSpiHex = "n/a",
                responseBytes = length,
                rttMs = rttMs,
                diagnosisSummary = "Short UDP reply ($length bytes) received from $host:$port"
            )
        }

        val iSpi = NetworkByteUtils.bytesToHex(raw.copyOfRange(offset, offset + 8), "")
        val rSpi = NetworkByteUtils.bytesToHex(raw.copyOfRange(offset + 8, offset + 16), "")
        var nextPayload = raw[offset + 16].toInt() and 0xFF
        val versionByte = raw[offset + 17].toInt() and 0xFF
        val major = (versionByte ushr 4) and 0x0F
        val minor = versionByte and 0x0F
        val exchangeType = raw[offset + 18].toInt() and 0xFF
        val exchangeName = when (exchangeType) {
            34 -> "IKE_SA_INIT"
            35 -> "IKE_AUTH"
            37 -> "INFORMATIONAL"
            else -> "Exchange-$exchangeType"
        }

        val notifyNames = mutableListOf<String>()
        var noProposal = false
        var natTDetected = nonEspMarkerSeen || useNatT4500

        var cursor = offset + 28
        var safety = 0
        while (nextPayload != 0 && cursor + 4 <= length && safety++ < 24) {
            val currentType = nextPayload
            val upcomingType = raw[cursor].toInt() and 0xFF
            val payloadLen = ((raw[cursor + 2].toInt() and 0xFF) shl 8) or (raw[cursor + 3].toInt() and 0xFF)
            if (payloadLen < 4 || cursor + payloadLen > length) break

            if (currentType == 41 && payloadLen >= 8) { // Notify Payload
                val notifyType = ((raw[cursor + 6].toInt() and 0xFF) shl 8) or (raw[cursor + 7].toInt() and 0xFF)
                val label = mapIkeNotifyType(notifyType)
                notifyNames.add(label)
                if (notifyType == 14) noProposal = true
                if (notifyType == 16388 || notifyType == 16389) natTDetected = true
            }

            nextPayload = upcomingType
            cursor += payloadLen
        }

        val diagnosis = buildString {
            append("IKE v$major.$minor responder active on $host:$port ($exchangeName, RTT ${rttMs}ms)")
            if (noProposal) {
                append(" — NO_PROPOSAL_CHOSEN: peer rejected AES256-CBC/SHA256/DH14 (check Phase 1 crypto profile).")
            } else if (notifyNames.isNotEmpty()) {
                append(" — Notifies: ${notifyNames.joinToString(", ")}")
            } else {
                append(" — Phase 1 SA proposal accepted cleanly.")
            }
            if (natTDetected) append(" [NAT-T Ready]")
        }

        return IkeProbeResult(
            host = host,
            port = port,
            useNatT4500 = useNatT4500,
            ikeVersionLabel = "IKEv$major.$minor",
            peerResponded = true,
            initiatorSpiHex = iSpi,
            responderSpiHex = rSpi,
            exchangeType = exchangeType,
            exchangeName = exchangeName,
            notifyPayloads = notifyNames,
            noProposalChosenDetected = noProposal,
            natTDetected = natTDetected,
            responseBytes = length,
            rttMs = rttMs,
            diagnosisSummary = diagnosis
        )
    }

    private fun mapIkeNotifyType(type: Int): String = when (type) {
        14 -> "NO_PROPOSAL_CHOSEN (14)"
        17 -> "INVALID_KE_PAYLOAD (17)"
        24 -> "AUTHENTICATION_FAILED (24)"
        16388 -> "NAT_DETECTION_SOURCE_IP (16388)"
        16389 -> "NAT_DETECTION_DESTINATION_IP (16389)"
        16390 -> "COOKIE (16390)"
        16406 -> "IKEV2_FRAGMENTATION_SUPPORTED (16406)"
        16430 -> "SIGNATURE_HASH_ALGORITHMS (16430)"
        else -> "NOTIFY_$type"
    }

    fun calculateTunnelOverheadAndMss(
        encapsulation: TunnelEncapsulationType,
        physicalMtu: Int = 1500,
        isIpv6Outer: Boolean = false,
        natTEnabled: Boolean = true
    ): TunnelMssCalculation {
        val safeMtu = physicalMtu.coerceIn(576, 9000)
        val outerIpBytes = if (isIpv6Outer) 40 else 20
        val natTBytes = if (natTEnabled && encapsulation.name.contains("IPSEC") || natTEnabled && encapsulation == TunnelEncapsulationType.XFRM_INTERFACE || natTEnabled && encapsulation == TunnelEncapsulationType.GRE_OVER_IPSEC) 8 else 0

        val encapBytes = when (encapsulation) {
            TunnelEncapsulationType.IPSEC_ESP_TUNNEL_GCM -> outerIpBytes + natTBytes + 8 /*ESP SPI+Seq*/ + 8 /*IV*/ + 16 /*ICV*/ + 2 /*pad*/
            TunnelEncapsulationType.IPSEC_ESP_TUNNEL_CBC_SHA256 -> outerIpBytes + natTBytes + 8 + 16 /*IV*/ + 16 /*HMAC-SHA256-128*/ + 15 /*worst-case AES block pad*/
            TunnelEncapsulationType.IPSEC_ESP_TRANSPORT_GCM -> natTBytes + 8 + 8 + 16 + 2
            TunnelEncapsulationType.XFRM_INTERFACE -> outerIpBytes + natTBytes + 8 + 8 + 16 + 2
            TunnelEncapsulationType.GRE_OVER_IPSEC -> outerIpBytes + natTBytes + 8 + 16 + 16 + 15 + 4 /*GRE*/ + 20 /*Inner IP if tunnel*/
            TunnelEncapsulationType.WIREGUARD -> outerIpBytes + 8 /*UDP*/ + 32 /*WG header+MAC*/
            TunnelEncapsulationType.OPENVPN_UDP_GCM -> outerIpBytes + 8 /*UDP*/ + 1 /*opcode*/ + 8 /*packet-id*/ + 16 /*AEAD tag*/
            TunnelEncapsulationType.OPENVPN_TCP_CBC -> outerIpBytes + 20 /*TCP*/ + 3 /*len+opcode*/ + 16 /*IV*/ + 32 /*HMAC-SHA256*/ + 15
            TunnelEncapsulationType.VXLAN_OVERLAY -> outerIpBytes + 8 /*UDP*/ + 8 /*VXLAN*/ + 14 /*Inner Ethernet*/
        }

        val effectiveMtu = (safeMtu - encapBytes).coerceAtLeast(576)
        val tcpIpHeader = 40 // IPv4(20)+TCP(20)
        val mss = (effectiveMtu - tcpIpHeader).coerceAtLeast(536)

        val snippet = buildString {
            appendLine("# Recommended Router / Firewall MSS Clamping:")
            appendLine("Cisco IOS/ASA: sysopt connection tcpmss $mss  |  ip tcp adjust-mss $mss")
            appendLine("FortiGate CLI: set tcp-mss-sender $mss ; set tcp-mss-receiver $mss")
            appendLine("Linux/iptables: iptables -t mangle -A FORWARD -p tcp --tcp-flags SYN,RST SYN -j TCPMSS --set-mss $mss")
        }.trim()

        return TunnelMssCalculation(
            encapsulation = encapsulation,
            physicalMtu = safeMtu,
            isIpv6Outer = isIpv6Outer,
            natTEnabled = natTEnabled,
            overheadBytes = encapBytes,
            effectiveTunnelMtu = effectiveMtu,
            recommendedTcpMss = mss,
            routerCliSnippet = snippet
        )
    }

    /**
     * Exact IPv4 CIDR subtraction using recursive binary prefix splitting.
     * E.g. Include `0.0.0.0/0`, Exclude `192.168.0.0/16, 10.0.0.0/8` -> minimal disjoint AllowedIPs list.
     */
    fun calculateAllowedIps(
        includedCidrs: List<String>,
        excludedCidrs: List<String>
    ): AllowedIpsCalculation {
        val incParsed = includedCidrs.mapNotNull { parseIpv4Cidr(it) }
        val excParsed = excludedCidrs.mapNotNull { parseIpv4Cidr(it) }
        var current = incParsed

        for (ex in excParsed) {
            val next = mutableListOf<Pair<Long, Int>>()
            for (inc in current) {
                next.addAll(subtractCidr(inc, ex))
            }
            current = next
        }

        val formatted = current
            .distinct()
            .sortedWith(compareBy<Pair<Long, Int>> { it.first }.thenBy { it.second })
            .map { (net, prefix) -> "${NetworkByteUtils.ipLongToString(net)}/$prefix" }

        return AllowedIpsCalculation(
            includedCidrs = includedCidrs.filter { it.isNotBlank() },
            excludedCidrs = excludedCidrs.filter { it.isNotBlank() },
            resultCidrs = formatted,
            formattedCsv = formatted.joinToString(", ")
        )
    }

    private fun parseIpv4Cidr(raw: String): Pair<Long, Int>? {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed.contains(":")) return null
        val ipPart = trimmed.substringBefore("/").trim()
        val prefix = trimmed.substringAfter("/", "32").toIntOrNull()?.coerceIn(0, 32) ?: return null
        val octets = ipPart.split(".")
        if (octets.size != 4) return null
        var ipLong = 0L
        for (o in octets) {
            val v = o.toLongOrNull() ?: return null
            if (v !in 0..255) return null
            ipLong = (ipLong shl 8) or v
        }
        val mask = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        return (ipLong and mask) to prefix
    }

    private fun subtractCidr(a: Pair<Long, Int>, b: Pair<Long, Int>): List<Pair<Long, Int>> {
        val (aNet, aPref) = a
        val (bNet, bPref) = b
        val aMask = if (aPref == 0) 0L else (0xFFFFFFFFL shl (32 - aPref)) and 0xFFFFFFFFL
        val bMask = if (bPref == 0) 0L else (0xFFFFFFFFL shl (32 - bPref)) and 0xFFFFFFFFL

        // Disjoint check
        val commonMask = if (aPref <= bPref) aMask else bMask
        if ((aNet and commonMask) != (bNet and commonMask)) {
            return listOf(a)
        }
        // b completely covers a
        if (bPref <= aPref) {
            return emptyList()
        }
        // Split a down toward bPref
        val result = mutableListOf<Pair<Long, Int>>()
        var currNet = aNet
        var currPref = aPref
        while (currPref < bPref) {
            val nextPref = currPref + 1
            val bitMask = 1L shl (32 - nextPref)
            val left = currNet to nextPref
            val right = (currNet or bitMask) to nextPref
            if ((bNet and bitMask) != 0L) {
                result.add(left)
                currNet = right.first
            } else {
                result.add(right)
                currNet = left.first
            }
            currPref = nextPref
        }
        return result
    }
}
