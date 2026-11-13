// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/IotProtocolsRepository.kt ===
package com.enterwebslinger.network.data.repository

import com.enterwebslinger.network.core.util.NetworkByteUtils
import com.enterwebslinger.network.domain.model.CoapMethod
import com.enterwebslinger.network.domain.model.CoapResource
import com.enterwebslinger.network.domain.model.CoapResponse
import com.enterwebslinger.network.domain.model.CoapType
import com.enterwebslinger.network.domain.model.MqttMessageItem
import com.enterwebslinger.network.domain.model.MqttQos
import com.enterwebslinger.network.domain.model.MqttSessionConfig
import com.enterwebslinger.network.domain.model.MqttUserProperty
import com.enterwebslinger.network.domain.model.MqttVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLSocketFactory

class IotProtocolsRepository(private val scope: CoroutineScope) {

    // --- MQTT State ---
    private val _isMqttConnected = MutableStateFlow(false)
    val isMqttConnected: StateFlow<Boolean> = _isMqttConnected.asStateFlow()

    private val _mqttMessages = MutableStateFlow<List<MqttMessageItem>>(emptyList())
    val mqttMessages: StateFlow<List<MqttMessageItem>> = _mqttMessages.asStateFlow()

    private val _mqttSubscribedTopics = MutableStateFlow<Set<String>>(emptySet())
    val mqttSubscribedTopics: StateFlow<Set<String>> = _mqttSubscribedTopics.asStateFlow()

    private val _mqttStatus = MutableStateFlow("Disconnected")
    val mqttStatus: StateFlow<String> = _mqttStatus.asStateFlow()

    private var mqttSocket: Socket? = null
    private var mqttReaderJob: Job? = null
    private var mqttKeepAliveJob: Job? = null
    private var mqttOutputStream: OutputStream? = null
    private var activeMqttVersion: MqttVersion = MqttVersion.MQTT_5_0
    private val mqttWriteLock = Any()

    private val packetIdSequence = AtomicInteger(1)
    private val coapMsgIdSequence = AtomicInteger(1)
    private val secureRandom = SecureRandom()

    // --- CoAP State ---
    private val _coapDiscoveredResources = MutableStateFlow<List<CoapResource>>(emptyList())
    val coapDiscoveredResources: StateFlow<List<CoapResource>> = _coapDiscoveredResources.asStateFlow()

    private val _lastCoapResponse = MutableStateFlow<CoapResponse?>(null)
    val lastCoapResponse: StateFlow<CoapResponse?> = _lastCoapResponse.asStateFlow()

    private fun nextPacketId(): Int {
        val next = packetIdSequence.getAndIncrement() and 0xFFFF
        return if (next == 0) 1 else next
    }

    private fun nextCoapMsgId(): Int {
        val next = coapMsgIdSequence.getAndIncrement() and 0xFFFF
        return if (next == 0) 1 else next
    }

    // ==========================================
    // 1. MQTT 3.1.1 & MQTT 5.0 Wire Protocol Engine
    // ==========================================

    suspend fun connectMqtt(config: MqttSessionConfig): Result<Unit> = withContext(Dispatchers.IO) {
        if (config.brokerHost.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("MQTT broker host is required"))
        }
        disconnectMqtt()
        activeMqttVersion = config.version
        _mqttStatus.value = "Connecting to ${config.brokerHost}:${config.port} (${config.version.name})..."

        try {
            val socket = if (config.useTls) {
                val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                sslFactory.createSocket().apply {
                    connect(InetSocketAddress(config.brokerHost.trim(), config.port), 8000)
                    soTimeout = 0
                }
            } else {
                Socket().apply {
                    connect(InetSocketAddress(config.brokerHost.trim(), config.port), 8000)
                    soTimeout = 0
                }
            }

            mqttSocket = socket
            val os = socket.getOutputStream()
            mqttOutputStream = os
            val isStream = socket.getInputStream()

            val connectPacket = buildMqttConnectPacket(config)
            synchronized(mqttWriteLock) {
                os.write(connectPacket)
                os.flush()
            }

            val headerByte = isStream.read()
            if (headerByte != 0x20) {
                NetworkByteUtils.safeClose(socket)
                return@withContext Result.failure(Exception("Invalid CONNACK header: 0x${headerByte.toString(16)}"))
            }

            val remLen = readVariableByteInt(isStream)
            val ackPayload = ByteArray(remLen)
            readFully(isStream, ackPayload)

            val returnCode = ackPayload.getOrElse(1) { -1 }.toInt() and 0xFF
            if (returnCode != 0) {
                NetworkByteUtils.safeClose(socket)
                _mqttStatus.value = "CONNACK rejected (code $returnCode)"
                return@withContext Result.failure(Exception("CONNACK rejected with reason code $returnCode"))
            }

            _isMqttConnected.value = true
            _mqttStatus.value = "Connected to ${config.brokerHost}:${config.port}"

            _mqttSubscribedTopics.value.forEach { topic ->
                subscribeTopic(topic, MqttQos.AT_LEAST_ONCE)
            }

            startMqttReceiver(isStream)
            startMqttKeepAliveLoop(config.keepAliveSeconds)
            Result.success(Unit)
        } catch (e: CancellationException) {
            disconnectMqtt()
            throw e
        } catch (e: Exception) {
            disconnectMqtt()
            _mqttStatus.value = "Connection error: ${e.message}"
            Result.failure(e)
        }
    }

    private fun startMqttKeepAliveLoop(keepAliveSeconds: Int) {
        mqttKeepAliveJob?.cancel()
        if (keepAliveSeconds <= 0) return
        val intervalMs = (keepAliveSeconds * 750L).coerceIn(5_000L, 45_000L)
        mqttKeepAliveJob = scope.launch(Dispatchers.IO) {
            while (isActive && _isMqttConnected.value) {
                delay(intervalMs)
                val os = mqttOutputStream ?: break
                try {
                    synchronized(mqttWriteLock) {
                        os.write(byteArrayOf(0xC0.toByte(), 0x00))
                        os.flush()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    private fun startMqttReceiver(isStream: InputStream) {
        mqttReaderJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val packetType = isStream.read()
                    if (packetType == -1) break

                    val remainingLen = readVariableByteInt(isStream)
                    val payload = ByteArray(remainingLen)
                    readFully(isStream, payload)

                    val pType = (packetType shr 4) and 0x0F
                    when (pType) {
                        3 -> { // PUBLISH
                            val isRetain = (packetType and 0x01) != 0
                            val qosVal = (packetType shr 1) and 0x03
                            val qos = when (qosVal) {
                                1 -> MqttQos.AT_LEAST_ONCE
                                2 -> MqttQos.EXACTLY_ONCE
                                else -> MqttQos.AT_MOST_ONCE
                            }
                            parseIncomingPublish(payload, qos, isRetain)
                        }
                        13 -> { // PINGRESP
                            // Keepalive acknowledged
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                mqttKeepAliveJob?.cancel()
                mqttKeepAliveJob = null
                NetworkByteUtils.safeClose(isStream)
                NetworkByteUtils.safeClose(mqttSocket)
                mqttSocket = null
                mqttOutputStream = null
                _isMqttConnected.value = false
                _mqttStatus.value = "Disconnected"
            }
        }
    }

    private fun parseIncomingPublish(data: ByteArray, qos: MqttQos, isRetain: Boolean) {
        if (data.size < 2) return
        val buf = ByteBuffer.wrap(data)
        val topicLen = buf.short.toInt() and 0xFFFF
        if (buf.remaining() < topicLen) return

        val topicBytes = ByteArray(topicLen)
        buf.get(topicBytes)
        val topic = String(topicBytes, Charsets.UTF_8)

        var packetId: Int? = null
        if (qos != MqttQos.AT_MOST_ONCE && buf.remaining() >= 2) {
            packetId = buf.short.toInt() and 0xFFFF
        }

        // Send PUBACK / PUBREC immediately for QoS >= 1 so broker advances session
        if (packetId != null) {
            val ackType = if (qos == MqttQos.EXACTLY_ONCE) 0x50 else 0x40
            val ackFrame = byteArrayOf(
                ackType.toByte(),
                0x02,
                ((packetId shr 8) and 0xFF).toByte(),
                (packetId and 0xFF).toByte()
            )
            mqttOutputStream?.let { os ->
                runCatching {
                    synchronized(mqttWriteLock) {
                        os.write(ackFrame)
                        os.flush()
                    }
                }
            }
        }

        var contentType: String? = null
        var responseTopic: String? = null
        val userProps = mutableListOf<MqttUserProperty>()

        if (activeMqttVersion == MqttVersion.MQTT_5_0 && buf.hasRemaining()) {
            val propsLen = readBufferVariableByteInt(buf).coerceIn(0, buf.remaining())
            if (propsLen > 0) {
                val propsEnd = buf.position() + propsLen
                while (buf.position() < propsEnd) {
                    val propId = buf.get().toInt() and 0xFF
                    when (propId) {
                        3 -> contentType = readMqttUtf8String(buf)
                        8 -> responseTopic = readMqttUtf8String(buf)
                        38 -> {
                            val k = readMqttUtf8String(buf) ?: ""
                            val v = readMqttUtf8String(buf) ?: ""
                            userProps.add(MqttUserProperty(k, v))
                        }
                        1, 23, 25, 36, 37, 40, 41, 42 -> {
                            if (buf.position() < propsEnd) buf.get()
                        }
                        33, 34 -> {
                            if (buf.position() + 2 <= propsEnd) buf.short
                        }
                        2, 17, 24 -> {
                            if (buf.position() + 4 <= propsEnd) buf.int
                        }
                        11 -> readBufferVariableByteInt(buf)
                        9 -> {
                            if (buf.position() + 2 <= propsEnd) {
                                val binLen = buf.short.toInt() and 0xFFFF
                                val skip = binLen.coerceAtMost(propsEnd - buf.position())
                                buf.position(buf.position() + skip)
                            } else {
                                buf.position(propsEnd)
                            }
                        }
                        else -> {
                            buf.position(propsEnd)
                        }
                    }
                }
                if (buf.position() != propsEnd) {
                    buf.position(propsEnd)
                }
            }
        }

        val remainingPayload = ByteArray(buf.remaining())
        buf.get(remainingPayload)
        val payloadStr = String(remainingPayload, Charsets.UTF_8)

        val item = MqttMessageItem(
            id = UUID.randomUUID().toString(),
            topic = topic,
            payloadString = payloadStr,
            qos = qos,
            isRetained = isRetain,
            contentType = contentType,
            responseTopic = responseTopic,
            userProperties = userProps
        )

        _mqttMessages.update { current ->
            (listOf(item) + current).take(200)
        }
    }

    suspend fun publishMqtt(
        topic: String,
        payload: String,
        qos: MqttQos = MqttQos.AT_LEAST_ONCE,
        retain: Boolean = false,
        userProperties: List<MqttUserProperty> = emptyList()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (topic.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("MQTT topic cannot be empty"))
        }
        val os = mqttOutputStream ?: return@withContext Result.failure(Exception("MQTT not connected"))
        try {
            val varHeader = ByteArrayOutputStream()
            val topicBytes = topic.toByteArray(Charsets.UTF_8)
            varHeader.write(topicBytes.size shr 8)
            varHeader.write(topicBytes.size and 0xFF)
            varHeader.write(topicBytes)

            if (qos != MqttQos.AT_MOST_ONCE) {
                val pId = nextPacketId()
                varHeader.write(pId shr 8)
                varHeader.write(pId and 0xFF)
            }

            if (activeMqttVersion == MqttVersion.MQTT_5_0) {
                if (userProperties.isNotEmpty()) {
                    val propBuf = ByteArrayOutputStream()
                    userProperties.forEach { (k, v) ->
                        propBuf.write(38)
                        val kb = k.toByteArray(Charsets.UTF_8)
                        propBuf.write(kb.size shr 8); propBuf.write(kb.size and 0xFF); propBuf.write(kb)
                        val vb = v.toByteArray(Charsets.UTF_8)
                        propBuf.write(vb.size shr 8); propBuf.write(vb.size and 0xFF); propBuf.write(vb)
                    }
                    val propBytes = propBuf.toByteArray()
                    writeVariableByteInt(varHeader, propBytes.size)
                    varHeader.write(propBytes)
                } else {
                    // MQTT 5.0 PUBLISH MUST include Properties Length (0x00) even when empty
                    varHeader.write(0x00)
                }
            }

            val payloadBytes = payload.toByteArray(Charsets.UTF_8)
            val totalRemaining = varHeader.size() + payloadBytes.size

            val fixedByte = 0x30 or (if (qos.value > 0) qos.value shl 1 else 0) or (if (retain) 0x01 else 0x00)
            val frameBuf = ByteArrayOutputStream()
            frameBuf.write(fixedByte)
            writeVariableByteInt(frameBuf, totalRemaining)
            frameBuf.write(varHeader.toByteArray())
            frameBuf.write(payloadBytes)

            synchronized(mqttWriteLock) {
                os.write(frameBuf.toByteArray())
                os.flush()
            }

            val item = MqttMessageItem(
                id = UUID.randomUUID().toString(),
                topic = topic,
                payloadString = payload,
                qos = qos,
                isRetained = retain,
                userProperties = userProperties
            )
            _mqttMessages.update { (listOf(item) + it).take(200) }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun subscribeTopic(topic: String, qos: MqttQos = MqttQos.AT_LEAST_ONCE) {
        if (topic.isBlank()) return
        scope.launch(Dispatchers.IO) {
            val os = mqttOutputStream
            if (os == null) {
                _mqttSubscribedTopics.update { it + topic }
                return@launch
            }
            try {
                val packetId = nextPacketId()
                val topicBytes = topic.toByteArray(Charsets.UTF_8)
                val v5PropByte = if (activeMqttVersion == MqttVersion.MQTT_5_0) 1 else 0
                val remainingLen = 2 + v5PropByte + (2 + topicBytes.size + 1)

                val osBuf = ByteArrayOutputStream()
                osBuf.write(0x82)
                writeVariableByteInt(osBuf, remainingLen)
                osBuf.write(packetId shr 8)
                osBuf.write(packetId and 0xFF)
                if (activeMqttVersion == MqttVersion.MQTT_5_0) {
                    osBuf.write(0x00) // Properties length
                }
                osBuf.write(topicBytes.size shr 8)
                osBuf.write(topicBytes.size and 0xFF)
                osBuf.write(topicBytes)
                osBuf.write(qos.value)

                synchronized(mqttWriteLock) {
                    os.write(osBuf.toByteArray())
                    os.flush()
                }

                _mqttSubscribedTopics.update { it + topic }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    fun unsubscribeTopic(topic: String) {
        if (topic.isBlank()) return
        _mqttSubscribedTopics.update { it - topic }
        val os = mqttOutputStream ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val packetId = nextPacketId()
                val topicBytes = topic.toByteArray(Charsets.UTF_8)
                val v5PropByte = if (activeMqttVersion == MqttVersion.MQTT_5_0) 1 else 0
                val remainingLen = 2 + v5PropByte + (2 + topicBytes.size)

                val osBuf = ByteArrayOutputStream()
                osBuf.write(0xA2)
                writeVariableByteInt(osBuf, remainingLen)
                osBuf.write(packetId shr 8)
                osBuf.write(packetId and 0xFF)
                if (activeMqttVersion == MqttVersion.MQTT_5_0) {
                    osBuf.write(0x00)
                }
                osBuf.write(topicBytes.size shr 8)
                osBuf.write(topicBytes.size and 0xFF)
                osBuf.write(topicBytes)

                synchronized(mqttWriteLock) {
                    os.write(osBuf.toByteArray())
                    os.flush()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    fun clearMqttMessages() {
        _mqttMessages.value = emptyList()
    }

    fun disconnectMqtt() {
        mqttKeepAliveJob?.cancel()
        mqttKeepAliveJob = null
        mqttReaderJob?.cancel()
        mqttReaderJob = null
        try {
            synchronized(mqttWriteLock) {
                mqttOutputStream?.write(byteArrayOf(0xE0.toByte(), 0x00))
                mqttOutputStream?.flush()
            }
        } catch (_: Exception) {}
        NetworkByteUtils.safeClose(mqttOutputStream)
        NetworkByteUtils.safeClose(mqttSocket)
        mqttSocket = null
        mqttOutputStream = null
        _isMqttConnected.value = false
        _mqttStatus.value = "Disconnected"
    }

    private fun buildMqttConnectPacket(config: MqttSessionConfig): ByteArray {
        val varHeader = ByteArrayOutputStream()
        val protoNameBytes = config.version.protocolName.toByteArray(Charsets.US_ASCII)
        varHeader.write(protoNameBytes.size shr 8)
        varHeader.write(protoNameBytes.size and 0xFF)
        varHeader.write(protoNameBytes)
        varHeader.write(config.version.protocolLevel.toInt())

        var flags = 0
        if (config.cleanStart) flags = flags or 0x02
        if (config.username.isNotBlank()) flags = flags or 0x80
        if (config.password.isNotBlank()) flags = flags or 0x40
        varHeader.write(flags)

        varHeader.write(config.keepAliveSeconds shr 8)
        varHeader.write(config.keepAliveSeconds and 0xFF)

        if (config.version == MqttVersion.MQTT_5_0) {
            varHeader.write(0x00)
        }

        val payloadBuf = ByteArrayOutputStream()
        val cId = config.clientId.toByteArray(Charsets.UTF_8)
        payloadBuf.write(cId.size shr 8); payloadBuf.write(cId.size and 0xFF); payloadBuf.write(cId)

        if (config.username.isNotBlank()) {
            val ub = config.username.toByteArray(Charsets.UTF_8)
            payloadBuf.write(ub.size shr 8); payloadBuf.write(ub.size and 0xFF); payloadBuf.write(ub)
        }
        if (config.password.isNotBlank()) {
            val pb = config.password.toByteArray(Charsets.UTF_8)
            payloadBuf.write(pb.size shr 8); payloadBuf.write(pb.size and 0xFF); payloadBuf.write(pb)
        }

        val totalLen = varHeader.size() + payloadBuf.size()
        val fullPacket = ByteArrayOutputStream()
        fullPacket.write(0x10)
        writeVariableByteInt(fullPacket, totalLen)
        fullPacket.write(varHeader.toByteArray())
        fullPacket.write(payloadBuf.toByteArray())
        return fullPacket.toByteArray()
    }

    private fun readMqttUtf8String(buf: ByteBuffer): String? {
        if (buf.remaining() < 2) return null
        val len = buf.short.toInt() and 0xFFFF
        if (buf.remaining() < len) return null
        val b = ByteArray(len)
        buf.get(b)
        return String(b, Charsets.UTF_8)
    }

    private fun readBufferVariableByteInt(buf: ByteBuffer): Int {
        var multiplier = 1
        var value = 0
        while (buf.hasRemaining()) {
            val encodedByte = buf.get().toInt() and 0xFF
            value += (encodedByte and 127) * multiplier
            if ((encodedByte and 128) == 0) break
            multiplier *= 128
        }
        return value
    }

    private fun writeVariableByteInt(os: OutputStream, value: Int) {
        var x = value
        do {
            var encodedByte = x % 128
            x /= 128
            if (x > 0) encodedByte = encodedByte or 128
            os.write(encodedByte)
        } while (x > 0)
    }

    private fun readVariableByteInt(isStream: InputStream): Int {
        var multiplier = 1
        var value = 0
        do {
            val encodedByte = isStream.read()
            if (encodedByte == -1) throw EOFException("Unexpected EOF reading MQTT variable-byte integer")
            value += (encodedByte and 127) * multiplier
            multiplier *= 128
        } while ((encodedByte and 128) != 0)
        return value
    }

    private fun readFully(isStream: InputStream, buffer: ByteArray) {
        var readTotal = 0
        while (readTotal < buffer.size) {
            val r = isStream.read(buffer, readTotal, buffer.size - readTotal)
            if (r == -1) throw EOFException("Unexpected EOF reading MQTT frame ($readTotal/${buffer.size} bytes)")
            readTotal += r
        }
    }

    // ==========================================
    // 2. CoAP Client (RFC 7252 / RFC 7641 - UDP Port 5683)
    // ==========================================

    suspend fun executeCoapRequest(
        host: String,
        port: Int = 5683,
        method: CoapMethod = CoapMethod.GET,
        uriPath: String = "/.well-known/core",
        payload: String = "",
        type: CoapType = CoapType.CON,
        observe: Boolean = false
    ): CoapResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var socket: DatagramSocket? = null

        try {
            if (host.isBlank()) {
                throw IllegalArgumentException("CoAP host is required")
            }
            socket = DatagramSocket().apply { soTimeout = 2000 }

            val msgId = nextCoapMsgId()
            val token = ByteArray(2)
            secureRandom.nextBytes(token)

            val reqBytes = buildCoapRequestPacket(
                msgId = msgId,
                token = token,
                method = method,
                uriPath = uriPath,
                payload = payload,
                type = type,
                observe = observe
            )

            val targetAddr = InetAddress.getByName(host.trim())
            val reqPacket = DatagramPacket(reqBytes, reqBytes.size, targetAddr, port)
            val recvBytes = ByteArray(4096)
            val recvPacket = DatagramPacket(recvBytes, recvBytes.size)

            val maxAttempts = if (type == CoapType.CON) 2 else 1
            var received = false
            for (attempt in 1..maxAttempts) {
                socket.send(reqPacket)
                try {
                    socket.receive(recvPacket)
                    received = true
                    break
                } catch (e: SocketTimeoutException) {
                    if (attempt == maxAttempts) throw e
                }
            }
            if (!received) throw SocketTimeoutException("CoAP response timed out")

            val rtt = System.currentTimeMillis() - startTime
            val resp = parseCoapResponsePacket(
                inData = recvPacket.data,
                inLen = recvPacket.length,
                roundTripTimeMs = rtt,
                requestedUriPath = uriPath
            )
            _lastCoapResponse.value = resp
            resp
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errResp = CoapResponse(
                type = CoapType.RST,
                responseCode = "Timeout / Error",
                messageId = 0,
                tokenHex = "",
                contentType = "text/plain",
                payload = e.message ?: "CoAP Socket Timeout",
                roundTripTimeMs = System.currentTimeMillis() - startTime,
                isSuccess = false
            )
            _lastCoapResponse.value = errResp
            errResp
        } finally {
            NetworkByteUtils.safeClose(socket)
        }
    }

    internal fun buildCoapRequestPacket(
        msgId: Int,
        token: ByteArray,
        method: CoapMethod,
        uriPath: String,
        payload: String,
        type: CoapType,
        observe: Boolean
    ): ByteArray {
        val packetBuffer = ByteArrayOutputStream()
        val byte0 = (1 shl 6) or (type.code shl 4) or (token.size and 0x0F)
        packetBuffer.write(byte0)
        packetBuffer.write(method.code)
        packetBuffer.write(msgId shr 8)
        packetBuffer.write(msgId and 0xFF)
        packetBuffer.write(token)

        var lastOptionNumber = 0
        if (observe) {
            writeCoapOption(packetBuffer, 6 - lastOptionNumber, byteArrayOf())
            lastOptionNumber = 6
        }

        val pathSegments = uriPath.trim('/').split("/").filter { it.isNotBlank() }
        pathSegments.forEach { seg ->
            val segBytes = seg.toByteArray(Charsets.UTF_8)
            writeCoapOption(packetBuffer, 11 - lastOptionNumber, segBytes)
            lastOptionNumber = 11
        }

        if (payload.isNotBlank()) {
            val trimmedPayload = payload.trimStart()
            val contentFormatVal = if (trimmedPayload.startsWith("{") || trimmedPayload.startsWith("[")) {
                byteArrayOf(50) // application/json
            } else {
                byteArrayOf() // 0 = text/plain (0-byte unsigned uint in CoAP)
            }
            writeCoapOption(packetBuffer, 12 - lastOptionNumber, contentFormatVal)
            packetBuffer.write(0xFF)
            packetBuffer.write(payload.toByteArray(Charsets.UTF_8))
        }

        return packetBuffer.toByteArray()
    }

    internal fun parseCoapResponsePacket(
        inData: ByteArray,
        inLen: Int,
        roundTripTimeMs: Long,
        requestedUriPath: String = ""
    ): CoapResponse {
        if (inLen < 4) throw Exception("CoAP Response header incomplete (< 4 bytes)")

        val inType = when ((inData[0].toInt() shr 4) and 0x03) {
            0 -> CoapType.CON
            1 -> CoapType.NON
            2 -> CoapType.ACK
            else -> CoapType.RST
        }

        val codeClass = (inData[1].toInt() shr 5) and 0x07
        val codeDetail = inData[1].toInt() and 0x1F
        val responseCodeStr = "$codeClass." + String.format("%02d", codeDetail)

        val inTokenLen = inData[0].toInt() and 0x0F
        var offset = (4 + inTokenLen).coerceAtMost(inLen)
        val inTokenHex = if (4 + inTokenLen <= inLen) {
            inData.sliceArray(4 until offset).joinToString("") { String.format("%02X", it) }
        } else ""

        var inPayload = ""
        var currentOptionNumber = 0
        var observeSequence: Int? = null
        var contentTypeStr = "text/plain"

        while (offset < inLen) {
            val b = inData[offset].toInt() and 0xFF
            if (b == 0xFF) {
                offset++
                if (offset < inLen) {
                    inPayload = String(inData, offset, inLen - offset, Charsets.UTF_8)
                }
                break
            }

            offset++
            val deltaNibble = (b ushr 4) and 0x0F
            val lenNibble = b and 0x0F

            val optDelta = when (deltaNibble) {
                13 -> {
                    if (offset >= inLen) break
                    (inData[offset++].toInt() and 0xFF) + 13
                }
                14 -> {
                    if (offset + 1 >= inLen) break
                    val v = ((inData[offset].toInt() and 0xFF) shl 8) or (inData[offset + 1].toInt() and 0xFF)
                    offset += 2
                    v + 269
                }
                15 -> break
                else -> deltaNibble
            }

            val optLen = when (lenNibble) {
                13 -> {
                    if (offset >= inLen) break
                    (inData[offset++].toInt() and 0xFF) + 13
                }
                14 -> {
                    if (offset + 1 >= inLen) break
                    val v = ((inData[offset].toInt() and 0xFF) shl 8) or (inData[offset + 1].toInt() and 0xFF)
                    offset += 2
                    v + 269
                }
                15 -> break
                else -> lenNibble
            }

            if (offset + optLen > inLen) break
            currentOptionNumber += optDelta
            when (currentOptionNumber) {
                6 -> { // Observe
                    var seq = 0
                    for (i in 0 until optLen) {
                        seq = (seq shl 8) or (inData[offset + i].toInt() and 0xFF)
                    }
                    observeSequence = seq
                }
                12 -> { // Content-Format
                    var fmt = 0
                    for (i in 0 until optLen) {
                        fmt = (fmt shl 8) or (inData[offset + i].toInt() and 0xFF)
                    }
                    contentTypeStr = coapContentFormatName(fmt)
                }
            }
            offset += optLen
        }

        if (requestedUriPath.contains(".well-known/core") && inPayload.isNotBlank()) {
            _coapDiscoveredResources.value = parseCoreLinkFormat(inPayload)
        }

        return CoapResponse(
            type = inType,
            responseCode = "$responseCodeStr (${getCoapCodeDescription(codeClass, codeDetail)})",
            messageId = ((inData[2].toInt() and 0xFF) shl 8) or (inData[3].toInt() and 0xFF),
            tokenHex = inTokenHex,
            contentType = contentTypeStr,
            observeSequence = observeSequence,
            payload = inPayload,
            roundTripTimeMs = roundTripTimeMs,
            isSuccess = codeClass == 2
        )
    }

    private fun writeCoapOption(out: ByteArrayOutputStream, delta: Int, valueBytes: ByteArray) {
        val len = valueBytes.size
        val deltaNibble = when {
            delta < 13 -> delta
            delta < 269 -> 13
            else -> 14
        }
        val lenNibble = when {
            len < 13 -> len
            len < 269 -> 13
            else -> 14
        }
        out.write((deltaNibble shl 4) or lenNibble)
        if (deltaNibble == 13) {
            out.write(delta - 13)
        } else if (deltaNibble == 14) {
            val ext = delta - 269
            out.write(ext shr 8)
            out.write(ext and 0xFF)
        }
        if (lenNibble == 13) {
            out.write(len - 13)
        } else if (lenNibble == 14) {
            val ext = len - 269
            out.write(ext shr 8)
            out.write(ext and 0xFF)
        }
        if (valueBytes.isNotEmpty()) {
            out.write(valueBytes)
        }
    }

    private fun coapContentFormatName(format: Int): String = when (format) {
        0 -> "text/plain"
        40 -> "application/link-format"
        41 -> "application/xml"
        42 -> "application/octet-stream"
        47 -> "application/exi"
        50 -> "application/json"
        60 -> "application/cbor"
        else -> "format/$format"
    }

    private fun parseCoreLinkFormat(linkStr: String): List<CoapResource> {
        val list = mutableListOf<CoapResource>()
        val links = linkStr.split(",")
        links.forEach { link ->
            val parts = link.split(";")
            val path = parts[0].trim().removeSurrounding("<", ">")
            if (path.isBlank()) return@forEach
            var rt = ""
            var iface = ""
            var title = ""
            var obs = false

            parts.drop(1).forEach { attr ->
                val a = attr.trim()
                when {
                    a.startsWith("rt=") -> rt = a.substringAfter("rt=").removeSurrounding("\"")
                    a.startsWith("if=") -> iface = a.substringAfter("if=").removeSurrounding("\"")
                    a.startsWith("title=") -> title = a.substringAfter("title=").removeSurrounding("\"")
                    a == "obs" -> obs = true
                }
            }

            list.add(
                CoapResource(
                    path = path,
                    resourceType = rt.ifBlank { "core.resource" },
                    interfaceDescription = iface.ifBlank { "sensor" },
                    title = title.ifBlank { path },
                    isObservable = obs
                )
            )
        }
        return list
    }

    private fun getCoapCodeDescription(cClass: Int, cDetail: Int): String {
        return when {
            cClass == 2 && cDetail == 5 -> "2.05 Content"
            cClass == 2 && cDetail == 1 -> "2.01 Created"
            cClass == 2 && cDetail == 2 -> "2.02 Deleted"
            cClass == 2 && cDetail == 3 -> "2.03 Valid"
            cClass == 2 && cDetail == 4 -> "2.04 Changed"
            cClass == 4 && cDetail == 0 -> "4.00 Bad Request"
            cClass == 4 && cDetail == 1 -> "4.01 Unauthorized"
            cClass == 4 && cDetail == 4 -> "4.04 Not Found"
            cClass == 4 && cDetail == 5 -> "4.05 Method Not Allowed"
            cClass == 5 && cDetail == 0 -> "5.00 Internal Error"
            else -> "$cClass." + String.format("%02d", cDetail)
        }
    }
}
