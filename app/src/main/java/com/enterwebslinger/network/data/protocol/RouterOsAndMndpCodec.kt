// === FILE: app/src/main/java/com/enterwebslinger/network/data/protocol/RouterOsAndMndpCodec.kt ===
package com.enterwebslinger.network.data.protocol

import com.enterwebslinger.network.core.util.NetworkByteUtils
import com.enterwebslinger.network.domain.model.MndpNeighborDevice
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object RouterOsAndMndpCodec {

    fun writeRouterOsSentence(out: OutputStream, words: List<String>) {
        for (word in words) {
            val bytes = word.toByteArray(StandardCharsets.UTF_8)
            writeRouterOsLength(out, bytes.size)
            out.write(bytes)
        }
        out.write(0) // end of sentence marker
        out.flush()
    }

    private fun writeRouterOsLength(out: OutputStream, len: Int) {
        when {
            len < 0x80 -> out.write(len)
            len < 0x4000 -> {
                out.write((len shr 8) or 0x80)
                out.write(len and 0xFF)
            }
            len < 0x200000 -> {
                out.write((len shr 16) or 0xC0)
                out.write((len shr 8) and 0xFF)
                out.write(len and 0xFF)
            }
            len < 0x10000000 -> {
                out.write((len shr 24) or 0xE0)
                out.write((len shr 16) and 0xFF)
                out.write((len shr 8) and 0xFF)
                out.write(len and 0xFF)
            }
            else -> {
                out.write(0xF0)
                out.write((len shr 24) and 0xFF)
                out.write((len shr 16) and 0xFF)
                out.write((len shr 8) and 0xFF)
                out.write(len and 0xFF)
            }
        }
    }

    fun readRouterOsSentence(input: InputStream): List<String> {
        val words = mutableListOf<String>()
        while (true) {
            val len = readRouterOsLength(input)
            if (len <= 0) break
            val buf = ByteArray(len)
            var totalRead = 0
            while (totalRead < len) {
                val r = input.read(buf, totalRead, len - totalRead)
                if (r == -1) break
                totalRead += r
            }
            words.add(String(buf, 0, totalRead, StandardCharsets.UTF_8))
        }
        return words
    }

    private fun readRouterOsLength(input: InputStream): Int {
        val b1 = input.read()
        if (b1 == -1) return -1
        if ((b1 and 0x80) == 0) return b1
        if ((b1 and 0xC0) == 0x80) {
            val b2 = input.read()
            return ((b1 and 0x3F) shl 8) or (b2 and 0xFF)
        }
        if ((b1 and 0xE0) == 0xC0) {
            val b2 = input.read()
            val b3 = input.read()
            return ((b1 and 0x1F) shl 16) or ((b2 and 0xFF) shl 8) or (b3 and 0xFF)
        }
        if ((b1 and 0xF0) == 0xE0) {
            val b2 = input.read()
            val b3 = input.read()
            val b4 = input.read()
            return ((b1 and 0x0F) shl 24) or ((b2 and 0xFF) shl 16) or ((b3 and 0xFF) shl 8) or (b4 and 0xFF)
        }
        val b2 = input.read()
        val b3 = input.read()
        val b4 = input.read()
        val b5 = input.read()
        return ((b2 and 0xFF) shl 24) or ((b3 and 0xFF) shl 16) or ((b4 and 0xFF) shl 8) or (b5 and 0xFF)
    }

    fun computeRouterOsMd5Response(challengeHex: String, password: String): String {
        val challengeBytes = NetworkByteUtils.hexToBytes(challengeHex)
        val md5 = MessageDigest.getInstance("MD5")
        md5.update(0.toByte())
        md5.update(password.toByteArray(StandardCharsets.UTF_8))
        md5.update(challengeBytes)
        val digest = md5.digest()
        return "00" + NetworkByteUtils.bytesToHex(digest)
    }

    fun parseMndpPacket(data: ByteArray, length: Int, sourceIp: String): MndpNeighborDevice? {
        if (length < 8) return null
        try {
            val buf = ByteBuffer.wrap(data, 0, length)
            // MNDP header: 2 bytes zero, 2 bytes sequence/type
            buf.short
            buf.short

            var identity = "MikroTik-Device"
            var mac = "--:--:--:--:--:--"
            var platform = "RouterOS"
            var version = "6.x"
            var board = "RouterBoard"
            var uptime = 0L

            while (buf.remaining() >= 4) {
                val type = buf.short.toInt() and 0xFFFF
                val len = buf.short.toInt() and 0xFFFF
                if (buf.remaining() < len) break
                val valBytes = ByteArray(len)
                buf.get(valBytes)

                when (type) {
                    1 -> identity = String(valBytes, StandardCharsets.UTF_8).trim()
                    5 -> version = String(valBytes, StandardCharsets.UTF_8).trim()
                    7 -> platform = String(valBytes, StandardCharsets.UTF_8).trim()
                    8 -> board = String(valBytes, StandardCharsets.UTF_8).trim()
                    10 -> if (valBytes.size >= 6) mac = NetworkByteUtils.formatMacAddress(valBytes)
                    14 -> if (valBytes.size >= 4) uptime = ByteBuffer.wrap(valBytes).int.toLong() and 0xFFFFFFFFL
                }
            }

            return MndpNeighborDevice(
                identity = identity,
                ipAddress = sourceIp,
                macAddress = mac,
                platform = platform,
                version = version,
                board = board,
                uptimeSeconds = uptime
            )
        } catch (_: Exception) {
            return null
        }
    }
}
