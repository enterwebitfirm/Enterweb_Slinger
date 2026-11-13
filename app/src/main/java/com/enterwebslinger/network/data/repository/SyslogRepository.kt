// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/SyslogRepository.kt ===
package com.enterwebslinger.network.data.repository

import com.enterwebslinger.network.data.protocol.SyslogParser
import com.enterwebslinger.network.domain.model.SyslogFacility
import com.enterwebslinger.network.domain.model.SyslogMessage
import com.enterwebslinger.network.domain.model.SyslogReceiverConfig
import com.enterwebslinger.network.domain.model.SyslogSeverity
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.StandardSocketOptions
import java.nio.channels.DatagramChannel
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong

data class SyslogStats(
    val isRunning: Boolean = false,
    val udpPort: Int? = null,
    val tcpPort: Int? = null,
    val totalReceived: Long = 0L,
    val emergencyCount: Long = 0L,
    val errorCount: Long = 0L,
    val warningCount: Long = 0L,
    val infoCount: Long = 0L,
    val errorMessage: String? = null
)

class SyslogRepository(private val externalScope: CoroutineScope) {

    private val _messages = MutableStateFlow<List<SyslogMessage>>(emptyList())
    val messages: StateFlow<List<SyslogMessage>> = _messages.asStateFlow()

    private val _stats = MutableStateFlow(SyslogStats())
    val stats: StateFlow<SyslogStats> = _stats.asStateFlow()

    private var udpJob: Job? = null
    private var tcpJob: Job? = null
    private var udpChannel: DatagramChannel? = null
    private var tcpServerSocket: ServerSocket? = null
    private val activeClientSockets = Collections.synchronizedSet(mutableSetOf<Socket>())

    private val totalCount = AtomicLong(0)
    private val emergencyCount = AtomicLong(0)
    private val errorCount = AtomicLong(0)
    private val warningCount = AtomicLong(0)
    private val infoCount = AtomicLong(0)

    fun startReceiver(config: SyslogReceiverConfig = SyslogReceiverConfig()) {
        if (_stats.value.isRunning) return
        stopReceiver()

        val safeUdpPort = config.udpPort.coerceAtLeast(1024)
        val safeTcpPort = config.tcpPort.coerceAtLeast(1024)

        _stats.value = _stats.value.copy(
            isRunning = true,
            udpPort = if (config.udpEnabled) safeUdpPort else null,
            tcpPort = if (config.tcpEnabled) safeTcpPort else null,
            errorMessage = null
        )

        if (config.udpEnabled) {
            udpJob = externalScope.launch(Dispatchers.IO) {
                var channel: DatagramChannel? = null
                try {
                    channel = DatagramChannel.open().apply {
                        setOption(StandardSocketOptions.SO_REUSEADDR, true)
                        socket().bind(InetSocketAddress(safeUdpPort))
                    }
                    udpChannel = channel
                    val socket = channel.socket()
                    val buffer = ByteArray(4096)
                    while (isActive) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)

                        val sourceIp = packet.address.hostAddress ?: "unknown"
                        val sourcePort = packet.port
                        val rawText = String(packet.data, 0, packet.length, Charsets.UTF_8)

                        val msg = SyslogParser.parse(rawText, sourceIp, sourcePort, transport = "UDP")
                        appendMessage(msg)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isActive) {
                        appendError("Syslog UDP $safeUdpPort error: ${e.localizedMessage ?: e.javaClass.simpleName}")
                    }
                } finally {
                    try { channel?.close() } catch (_: Exception) {}
                    if (udpChannel === channel) udpChannel = null
                }
            }
        }

        if (config.tcpEnabled) {
            tcpJob = externalScope.launch(Dispatchers.IO) {
                var server: ServerSocket? = null
                try {
                    server = ServerSocket().apply {
                        reuseAddress = true
                        bind(InetSocketAddress(safeTcpPort))
                    }
                    tcpServerSocket = server
                    while (isActive) {
                        val clientSocket: Socket = server.accept()
                        activeClientSockets.add(clientSocket)
                        launch(Dispatchers.IO) {
                            handleTcpConnection(clientSocket)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isActive) {
                        appendError("Syslog TCP $safeTcpPort error: ${e.localizedMessage ?: e.javaClass.simpleName}")
                    }
                } finally {
                    try { server?.close() } catch (_: Exception) {}
                    if (tcpServerSocket === server) tcpServerSocket = null
                }
            }
        }
    }

    private fun handleTcpConnection(socket: Socket) {
        val sourceIp = socket.inetAddress.hostAddress ?: "unknown"
        val sourcePort = socket.port
        try {
            socket.use { s ->
                s.soTimeout = 30_000
                BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        if (line.isNotBlank()) {
                            val msg = SyslogParser.parse(line, sourceIp, sourcePort, transport = "TCP")
                            appendMessage(msg)
                        }
                        line = reader.readLine()
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            activeClientSockets.remove(socket)
        }
    }

    private fun appendError(errMsg: String) {
        val prev = _stats.value.errorMessage
        val combined = if (prev.isNullOrBlank()) errMsg else "$prev | $errMsg"
        _stats.value = _stats.value.copy(errorMessage = combined)
    }

    private fun appendMessage(msg: SyslogMessage) {
        totalCount.incrementAndGet()
        when (msg.severity) {
            SyslogSeverity.EMERGENCY, SyslogSeverity.ALERT, SyslogSeverity.CRITICAL -> emergencyCount.incrementAndGet()
            SyslogSeverity.ERROR -> errorCount.incrementAndGet()
            SyslogSeverity.WARNING -> warningCount.incrementAndGet()
            else -> infoCount.incrementAndGet()
        }

        val current = _messages.value.toMutableList()
        current.add(0, msg)
        if (current.size > 250) {
            current.removeAt(current.lastIndex)
        }
        _messages.value = current

        _stats.value = _stats.value.copy(
            totalReceived = totalCount.get(),
            emergencyCount = emergencyCount.get(),
            errorCount = errorCount.get(),
            warningCount = warningCount.get(),
            infoCount = infoCount.get()
        )
    }

    fun stopReceiver() {
        udpJob?.cancel()
        udpJob = null
        try { udpChannel?.close() } catch (_: Exception) {}
        udpChannel = null

        tcpJob?.cancel()
        tcpJob = null
        try { tcpServerSocket?.close() } catch (_: Exception) {}
        tcpServerSocket = null

        synchronized(activeClientSockets) {
            activeClientSockets.forEach {
                try { it.close() } catch (_: Exception) {}
            }
            activeClientSockets.clear()
        }

        _stats.value = _stats.value.copy(isRunning = false)
    }

    fun clearLogs() {
        _messages.value = emptyList()
        totalCount.set(0)
        emergencyCount.set(0)
        errorCount.set(0)
        warningCount.set(0)
        infoCount.set(0)
        _stats.value = _stats.value.copy(
            totalReceived = 0,
            emergencyCount = 0,
            errorCount = 0,
            warningCount = 0,
            infoCount = 0,
            errorMessage = null
        )
    }

    suspend fun sendTestSyslog(
        host: String,
        port: Int = 1514,
        isRfc5424: Boolean = true,
        facility: SyslogFacility = SyslogFacility.LOCAL0,
        severity: SyslogSeverity = SyslogSeverity.WARNING,
        appName: String = "EnterwebSlinger",
        message: String
    ): Result<String> {
        if (host.isBlank() || message.isBlank()) {
            return Result.failure(IllegalArgumentException("Target host and syslog message are required"))
        }
        return withContext(Dispatchers.IO) {
            try {
                val localHost = try {
                    InetAddress.getLocalHost().hostAddress ?: "localhost"
                } catch (_: Exception) {
                    "localhost"
                }
                val wireMessage = if (isRfc5424) {
                    SyslogParser.generateRfc5424(
                        facility = facility,
                        severity = severity,
                        hostname = localHost,
                        appName = appName,
                        procId = android.os.Process.myPid().toString(),
                        msgId = "SYSLOG_PROBE",
                        structuredDataId = "origin@32473",
                        sdParams = mapOf("ip" to localHost),
                        message = message
                    )
                } else {
                    SyslogParser.generateRfc3164(
                        facility = facility,
                        severity = severity,
                        hostname = localHost,
                        tag = appName,
                        pid = android.os.Process.myPid().toString(),
                        message = message
                    )
                }

                val success = SyslogParser.sendUdpSyslog(host, port, wireMessage)
                if (success) {
                    Result.success("Dispatched to $host:$port: $wireMessage")
                } else {
                    Result.failure(Exception("Failed to transmit UDP syslog datagram to $host:$port"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
