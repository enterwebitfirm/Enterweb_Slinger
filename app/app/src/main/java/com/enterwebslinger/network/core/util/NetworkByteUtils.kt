// === FILE: app/src/main/java/com/enterwebslinger/network/core/util/NetworkByteUtils.kt ===
package com.enterwebslinger.network.core.util

import java.io.InputStream
import java.net.DatagramSocket
import java.net.Socket
import java.nio.ByteBuffer

data class VlsmSubnetResult(
    val network: String,
    val subnetMask: String,
    val prefixLength: Int,
    val networkAddress: String,
    val broadcastAddress: String,
    val usableHostsStart: String,
    val usableHostsEnd: String,
    val totalUsableHosts: Int,
    val requestedHosts: Int
)

data class EncapsulationMtuResult(
    val protocolName: String,
    val baseMtu: Int,
    val overheadBytes: Int,
    val effectiveMtu: Int,
    val tcpMss: Int,
    val notes: String
)

/**
 * Consolidated Pure-Kotlin Network, Binary, Byte, and Socket Utility Functions.
 * Eliminates duplicate network conversion logic across repositories and engines.
 */
object NetworkByteUtils {

    /**
     * Converts a 32-bit Integer (Network Byte Order / Big Endian) into IPv4 dotted quad string.
     */
    fun ipIntToStringBigEndian(ip: Int): String {
        return "${(ip shr 24) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 8) and 0xFF}.${ip and 0xFF}"
    }

    /**
     * Converts a 32-bit Integer (Little Endian, as returned by Android's legacy DhcpInfo) to IPv4.
     */
    fun ipIntToStringLittleEndian(ip: Int): String {
        return "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
    }

    /**
     * Converts a 64-bit Long into an IPv4 dotted string.
     */
    fun ipLongToString(ip: Long): String {
        return "${(ip shr 24) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 8) and 0xFF}.${ip and 0xFF}"
    }

    /**
     * Converts an IPv4 dotted string into a 64-bit Long.
     */
    fun stringToIpLong(ipStr: String): Long {
        val parts = ipStr.trim().split(".")
        if (parts.size != 4) return 0L
        return try {
            ((parts[0].toLong() and 0xFF) shl 24) or
                ((parts[1].toLong() and 0xFF) shl 16) or
                ((parts[2].toLong() and 0xFF) shl 8) or
                (parts[3].toLong() and 0xFF)
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Calculates Cisco wildcard mask from a subnet mask (e.g. "255.255.255.0" -> "0.0.0.255").
     */
    fun calculateWildcardMask(subnetMask: String): String {
        val parts = subnetMask.trim().split(".")
        if (parts.size != 4) return "0.0.0.0"
        return try {
            parts.joinToString(".") { (255 - (it.toInt() and 0xFF)).toString() }
        } catch (_: Exception) {
            "0.0.0.0"
        }
    }

    /**
     * Performs VLSM subnet calculation given a base network and sorted list of host requirements.
     */
    fun calculateVlsm(cidrBase: String, hostRequirements: List<Int>): List<VlsmSubnetResult> {
        val parts = cidrBase.split("/")
        if (parts.size != 2) return emptyList()
        val baseIpLong = stringToIpLong(parts[0])
        var currentIpLong = baseIpLong
        val basePrefix = parts[1].toIntOrNull() ?: 24

        val sortedHosts = hostRequirements.sortedDescending()
        val results = mutableListOf<VlsmSubnetResult>()

        for (hosts in sortedHosts) {
            // Need hosts + 2 (network + broadcast)
            val needed = hosts + 2
            var hostBits = 1
            while ((1 shl hostBits) < needed) {
                hostBits++
            }
            val prefix = 32 - hostBits
            if (prefix < 0 || prefix > 32) continue

            val blockSize = 1 shl hostBits
            // Align to block size
            if (currentIpLong % blockSize != 0L) {
                currentIpLong = ((currentIpLong / blockSize) + 1) * blockSize
            }

            val netAddr = currentIpLong
            val bcastAddr = netAddr + blockSize - 1
            val usableStart = if (blockSize > 2) netAddr + 1 else netAddr
            val usableEnd = if (blockSize > 2) bcastAddr - 1 else bcastAddr
            val usableCount = if (blockSize > 2) blockSize - 2 else blockSize

            val subnetMaskInt = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL

            results.add(
                VlsmSubnetResult(
                    network = "${ipLongToString(netAddr)}/$prefix",
                    subnetMask = ipLongToString(subnetMaskInt),
                    prefixLength = prefix,
                    networkAddress = ipLongToString(netAddr),
                    broadcastAddress = ipLongToString(bcastAddr),
                    usableHostsStart = ipLongToString(usableStart),
                    usableHostsEnd = ipLongToString(usableEnd),
                    totalUsableHosts = usableCount,
                    requestedHosts = hosts
                )
            )

            currentIpLong = bcastAddr + 1
        }

        return results
    }

    /**
     * Calculates encapsulation MTU and TCP MSS for tunnels (PPPoE, IPsec, WireGuard, VXLAN, GRE, MPLS).
     */
    fun calculateEncapsulationMtu(baseMtu: Int, protocol: String): EncapsulationMtuResult {
        val overhead = when (protocol.uppercase()) {
            "PPPOE" -> 8
            "IPSEC" -> 60 // ESP + IPv4/UDP encapsulation
            "WIREGUARD" -> 60 // IPv4/UDP + WireGuard header
            "VXLAN" -> 50 // Outer IP/UDP + VXLAN header + inner Ethernet
            "GRE" -> 24 // Outer IP + GRE header
            "MPLS" -> 4 * 2 // 2 MPLS labels (4 bytes each)
            else -> 0
        }
        val effective = (baseMtu - overhead).coerceAtLeast(576)
        val mss = (effective - 40).coerceAtLeast(536) // IP header (20) + TCP header (20)

        val notes = when (protocol.uppercase()) {
            "PPPOE" -> "Standard PPPoE adds 8 bytes (PPPoE header + PPP protocol ID). Recommended MSS clamping: 1412."
            "IPSEC" -> "IPsec transport/tunnel mode adds up to 60 bytes with ESP encryption and outer headers."
            "WIREGUARD" -> "WireGuard encapsulation requires 60 bytes overhead. Recommended interface MTU: 1420."
            "VXLAN" -> "VXLAN encapsulation adds 50 bytes. Recommended interface MTU: 1450 or jumbo frames (9000)."
            "GRE" -> "GRE tunneling adds 24 bytes (outer IP + GRE header)."
            "MPLS" -> "Each MPLS label adds 4 bytes. Multi-protocol label stacking requires MTU adjustment on transit routers."
            else -> "Standard unencapsulated IP packet."
        }

        return EncapsulationMtuResult(
            protocolName = protocol.uppercase(),
            baseMtu = baseMtu,
            overheadBytes = overhead,
            effectiveMtu = effective,
            tcpMss = mss,
            notes = notes
        )
    }

    /**
     * Converts a byte array to a hex string with optional delimiter (e.g. ":", " ", or "").
     */
    fun bytesToHex(bytes: ByteArray, separator: String = ""): String {
        val sb = StringBuilder(bytes.size * (2 + separator.length))
        for (i in bytes.indices) {
            val b = bytes[i].toInt() and 0xFF
            sb.append(String.format("%02X", b))
            if (separator.isNotEmpty() && i < bytes.size - 1) {
                sb.append(separator)
            }
        }
        return sb.toString()
    }

    /**
     * Converts a hex string into a ByteArray.
     */
    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(":", "").replace("-", "").replace(" ", "")
        val len = clean.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(clean[i], 16) shl 4) + Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    /**
     * Formats 6 bytes into a colon-separated uppercase MAC string.
     */
    fun formatMacAddress(macBytes: ByteArray): String {
        if (macBytes.size < 6) return "00:00:00:00:00:00"
        return macBytes.take(6).joinToString(":") { "%02X".format(it) }
    }

    /**
     * Reads exact byte count from an InputStream or throws EOFException.
     */
    fun readExact(inputStream: InputStream, length: Int): ByteArray {
        val buffer = ByteArray(length)
        var totalRead = 0
        while (totalRead < length) {
            val bytesRead = inputStream.read(buffer, totalRead, length - totalRead)
            if (bytesRead == -1) {
                throw java.io.EOFException("Premature end of stream while reading $length bytes (read $totalRead)")
            }
            totalRead += bytesRead
        }
        return buffer
    }

    /**
     * Safely closes any Closeable/AutoCloseable without throwing exceptions.
     */
    fun safeClose(closeable: AutoCloseable?) {
        try {
            closeable?.close()
        } catch (_: Exception) {}
    }

    /**
     * Destroys a Process safely, attempting graceful termination then destroyForcibly.
     */
    fun destroyProcess(process: Process?) {
        if (process == null) return
        try {
            process.destroy()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                process.destroyForcibly()
            }
        } catch (_: Exception) {}
    }

    /**
     * Configures a Socket with standard timeouts and TCP_NODELAY.
     */
    fun configureSocket(socket: Socket, timeoutMs: Int = 5000) {
        socket.tcpNoDelay = true
        socket.soTimeout = timeoutMs
    }

    /**
     * Configures a DatagramSocket with standard timeouts.
     */
    fun configureDatagramSocket(socket: DatagramSocket, timeoutMs: Int = 4000) {
        socket.soTimeout = timeoutMs
    }
}
