// === FILE: app/src/main/java/com/enterwebslinger/network/core/util/NetworkByteUtils.kt ===
package com.enterwebslinger.network.core.util

import java.io.InputStream
import java.net.DatagramSocket
import java.net.Socket

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
