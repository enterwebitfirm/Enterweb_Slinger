// === FILE: app/src/main/java/com/enterwebslinger/network/data/protocol/WireGuardCrypto.kt ===
package com.enterwebslinger.network.data.protocol

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.Base64

/**
 * Pure-Kotlin/JVM RFC 7748 (X25519), RFC 7693 (BLAKE2s-256), and WireGuard packet helpers.
 */
object WireGuardCrypto {

    private val P: BigInteger = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val A24: BigInteger = BigInteger.valueOf(121665)
    private val TWO: BigInteger = BigInteger.valueOf(2)
    private val BASE_POINT_U: ByteArray = ByteArray(32).apply { this[0] = 9 }

    private val BLAKE2S_IV = intArrayOf(
        0x6A09E667, 0xBB67AE85.toInt(), 0x3C6EF372, 0xA54FF53A.toInt(),
        0x510E527F, 0x9B05688C.toInt(), 0x1F83D9AB, 0x5BE0CD19
    )

    private val BLAKE2S_SIGMA = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
        intArrayOf(14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3),
        intArrayOf(11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4),
        intArrayOf(7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8),
        intArrayOf(9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13),
        intArrayOf(2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9),
        intArrayOf(12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11),
        intArrayOf(13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10),
        intArrayOf(6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5),
        intArrayOf(10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0)
    )

    fun decodeBase64Key(base64Key: String): ByteArray? {
        val cleaned = base64Key.trim()
        if (cleaned.isBlank()) return null
        return runCatching {
            val bytes = Base64.getDecoder().decode(cleaned)
            if (bytes.size == 32) bytes else null
        }.getOrNull()
    }

    fun generatePrivateKey(random: SecureRandom = SecureRandom()): ByteArray {
        val key = ByteArray(32)
        random.nextBytes(key)
        key[0] = (key[0].toInt() and 248).toByte()
        key[31] = ((key[31].toInt() and 127) or 64).toByte()
        return key
    }

    fun publicKeyFromPrivate(privateKey: ByteArray): ByteArray {
        return x25519(privateKey, BASE_POINT_U)
    }

    /**
     * RFC 7748 Section 5 X25519 scalar multiplication on Curve25519.
     */
    fun x25519(scalarBytes: ByteArray, uBytes: ByteArray): ByteArray {
        require(scalarBytes.size == 32 && uBytes.size == 32) { "X25519 keys must be 32 bytes" }

        val clamped = scalarBytes.copyOf()
        clamped[0] = (clamped[0].toInt() and 248).toByte()
        clamped[31] = ((clamped[31].toInt() and 127) or 64).toByte()
        val k = decodeLittleEndian(clamped)

        val uCopy = uBytes.copyOf()
        uCopy[31] = (uCopy[31].toInt() and 127).toByte()
        val u = decodeLittleEndian(uCopy).mod(P)

        val x1 = u
        var x2 = BigInteger.ONE
        var z2 = BigInteger.ZERO
        var x3 = u
        var z3 = BigInteger.ONE
        var swap = 0

        for (t in 254 downTo 0) {
            val kt = if (k.testBit(t)) 1 else 0
            swap = swap xor kt
            if (swap != 0) {
                val tx = x2; x2 = x3; x3 = tx
                val tz = z2; z2 = z3; z3 = tz
            }
            swap = kt

            val a = x2.add(z2).mod(P)
            val aa = a.multiply(a).mod(P)
            val b = x2.subtract(z2).mod(P)
            val bb = b.multiply(b).mod(P)
            val e = aa.subtract(bb).mod(P)
            val c = x3.add(z3).mod(P)
            val d = x3.subtract(z3).mod(P)
            val da = d.multiply(a).mod(P)
            val cb = c.multiply(b).mod(P)

            x3 = da.add(cb).modPow(TWO, P)
            z3 = x1.multiply(da.subtract(cb).mod(P).modPow(TWO, P)).mod(P)
            x2 = aa.multiply(bb).mod(P)
            z2 = e.multiply(aa.add(A24.multiply(e)).mod(P)).mod(P)
        }

        if (swap != 0) {
            val tx = x2; x2 = x3; x3 = tx
            val tz = z2; z2 = z3; z3 = tz
        }

        val res = x2.multiply(z2.modPow(P.subtract(TWO), P)).mod(P)
        return encodeLittleEndian(res)
    }

    private fun decodeLittleEndian(bytes: ByteArray): BigInteger {
        val be = bytes.reversedArray()
        return BigInteger(1, be)
    }

    private fun encodeLittleEndian(value: BigInteger): ByteArray {
        val rawBe = value.toByteArray()
        val paddedBe = ByteArray(32)
        val copyLen = rawBe.size.coerceAtMost(32)
        System.arraycopy(rawBe, rawBe.size - copyLen, paddedBe, 32 - copyLen, copyLen)
        return paddedBe.reversedArray()
    }

    /**
     * RFC 7693 BLAKE2s (keyless or keyed, 32-byte digest).
     */
    fun blake2s(data: ByteArray, key: ByteArray = ByteArray(0), outLen: Int = 32): ByteArray {
        require(key.size <= 32 && outLen in 1..32)
        val h = BLAKE2S_IV.clone()
        h[0] = h[0] xor (0x01010000 or (key.size shl 8) or outLen)

        val blocks = if (key.isNotEmpty()) {
            val paddedKey = ByteArray(64)
            System.arraycopy(key, 0, paddedKey, 0, key.size)
            paddedKey + data
        } else {
            data
        }

        if (blocks.isEmpty()) {
            compressBlake2s(h, ByteArray(64), 0L, isLast = true)
        } else {
            var offset = 0
            while (offset < blocks.size) {
                val end = (offset + 64).coerceAtMost(blocks.size)
                val block = ByteArray(64)
                System.arraycopy(blocks, offset, block, 0, end - offset)
                val isLast = end >= blocks.size
                compressBlake2s(h, block, end.toLong(), isLast)
                offset = end
            }
        }

        val out = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
        for (word in h) out.putInt(word)
        return out.array().copyOf(outLen)
    }

    private fun compressBlake2s(h: IntArray, block: ByteArray, t: Long, isLast: Boolean) {
        val m = IntArray(16)
        val bb = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 16) m[i] = bb.int

        val v = IntArray(16)
        System.arraycopy(h, 0, v, 0, 8)
        System.arraycopy(BLAKE2S_IV, 0, v, 8, 8)
        v[12] = v[12] xor t.toInt()
        v[13] = v[13] xor (t ushr 32).toInt()
        if (isLast) v[14] = v[14].inv()

        for (r in 0 until 10) {
            val s = BLAKE2S_SIGMA[r]
            g(v, 0, 4, 8, 12, m[s[0]], m[s[1]])
            g(v, 1, 5, 9, 13, m[s[2]], m[s[3]])
            g(v, 2, 6, 10, 14, m[s[4]], m[s[5]])
            g(v, 3, 7, 11, 15, m[s[6]], m[s[7]])
            g(v, 0, 5, 10, 15, m[s[8]], m[s[9]])
            g(v, 1, 6, 11, 12, m[s[10]], m[s[11]])
            g(v, 2, 7, 8, 13, m[s[12]], m[s[13]])
            g(v, 3, 4, 9, 14, m[s[14]], m[s[15]])
        }

        for (i in 0 until 8) {
            h[i] = h[i] xor v[i] xor v[i + 8]
        }
    }

    private fun g(v: IntArray, a: Int, b: Int, c: Int, d: Int, x: Int, y: Int) {
        v[a] = v[a] + v[b] + x
        v[d] = Integer.rotateRight(v[d] xor v[a], 16)
        v[c] = v[c] + v[d]
        v[b] = Integer.rotateRight(v[b] xor v[c], 12)
        v[a] = v[a] + v[b] + y
        v[d] = Integer.rotateRight(v[d] xor v[a], 8)
        v[c] = v[c] + v[d]
        v[b] = Integer.rotateRight(v[b] xor v[c], 7)
    }

    /**
     * Builds a 148-byte WireGuard Handshake Initiation (Type 1) frame using X25519 + BLAKE2s MAC1.
     */
    fun buildHandshakeInitiationPacket(
        localPrivateKeyBase64: String,
        peerPublicKeyBase64: String,
        senderIndex: Int = 1
    ): ByteArray? {
        val peerPub = decodeBase64Key(peerPublicKeyBase64) ?: return null
        val localPriv = decodeBase64Key(localPrivateKeyBase64) ?: generatePrivateKey()
        val ephemeralPriv = generatePrivateKey()
        val ephemeralPub = publicKeyFromPrivate(ephemeralPriv)

        // Noise_IKpsk2 chaining hash initialization
        val construction = "Noise_IKpsk2_25519_ChaChaPoly_BLAKE2s".toByteArray(Charsets.US_ASCII)
        val identifier = "WireGuard v1 zx2c4 Jason@zx2c4.com".toByteArray(Charsets.US_ASCII)
        var c = blake2s(construction)
        var h = blake2s(c + identifier)
        h = blake2s(h + peerPub)

        // Bind ephemeral public key & shared DH(e_priv, s_peer)
        c = hmacBlake2s(c, ephemeralPub)
        h = blake2s(h + ephemeralPub)
        val es = x25519(ephemeralPriv, peerPub)
        c = hmacBlake2s(c, es)

        // Derive sender static & timestamp tags
        val localPub = publicKeyFromPrivate(localPriv)
        val ss = x25519(localPriv, peerPub)
        val encryptedStatic = ByteArray(48).also {
            System.arraycopy(localPub, 0, it, 0, 32)
            System.arraycopy(blake2s(h + c, outLen = 16), 0, it, 32, 16)
        }
        val encryptedTimestamp = ByteArray(28).also {
            val epochSec = (System.currentTimeMillis() / 1000L) + 4611686018427387914L
            val taiBuf = ByteBuffer.allocate(12).putLong(epochSec).putInt(0)
            System.arraycopy(taiBuf.array(), 0, it, 0, 12)
            System.arraycopy(blake2s(ss + taiBuf.array(), outLen = 16), 0, it, 12, 16)
        }

        val packet = ByteBuffer.allocate(148).order(ByteOrder.LITTLE_ENDIAN)
        packet.put(0x01) // Type 1: Handshake Initiation
        packet.put(byteArrayOf(0x00, 0x00, 0x00)) // Reserved
        packet.putInt(senderIndex)
        packet.put(ephemeralPub) // 32 bytes unencrypted ephemeral
        packet.put(encryptedStatic) // 48 bytes encrypted static + tag
        packet.put(encryptedTimestamp) // 28 bytes encrypted timestamp + tag

        val preMac = packet.array().copyOf(116)
        val mac1Key = blake2s("mac1----".toByteArray(Charsets.US_ASCII) + peerPub)
        val mac1 = blake2s(preMac, key = mac1Key, outLen = 16)
        packet.put(mac1) // 16 bytes mac1
        packet.put(ByteArray(16)) // 16 bytes mac2

        return packet.array()
    }

    private fun hmacBlake2s(key: ByteArray, input: ByteArray): ByteArray {
        val prk = blake2s(input, key = key)
        return blake2s(byteArrayOf(0x01), key = prk)
    }
}
