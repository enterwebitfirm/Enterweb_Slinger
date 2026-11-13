// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/TerminalAndNetOsRepository.kt ===
package com.enterwebslinger.network.data.repository

import com.enterwebslinger.network.domain.model.ConfigDiffResult
import com.enterwebslinger.network.domain.model.DiffLine
import com.enterwebslinger.network.domain.model.DiffLineType
import com.enterwebslinger.network.domain.model.NetconfRpcResult
import com.enterwebslinger.network.domain.model.ParsedInterfaceBrief
import com.enterwebslinger.network.domain.model.ParsedNeighbor
import com.enterwebslinger.network.domain.model.ParsedRoute
import com.enterwebslinger.network.domain.model.ParsedVersionInfo
import com.enterwebslinger.network.domain.model.TerminalProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.apache.commons.net.telnet.TelnetClient
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class TerminalAndNetOsRepository(
    private val scope: CoroutineScope
) {
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectedHost = MutableStateFlow<String?>(null)
    val connectedHost: StateFlow<String?> = _connectedHost.asStateFlow()

    private val _terminalBuffer = MutableStateFlow<List<String>>(emptyList())
    val terminalBuffer: StateFlow<List<String>> = _terminalBuffer.asStateFlow()

    private val _statusText = MutableStateFlow<String?>("Idle")
    val statusText: StateFlow<String?> = _statusText.asStateFlow()

    private var terminalOutputStream: OutputStream? = null
    private var sshClient: SSHClient? = null
    private var sshSession: Session? = null
    private var telnetClient: TelnetClient? = null
    private var readerJob: Job? = null
    @Volatile
    private var manualDisconnect = false

    suspend fun connect(
        host: String,
        port: Int,
        protocol: TerminalProtocol,
        username: String = "",
        password: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (host.isBlank()) return@withContext Result.failure(IllegalArgumentException("Host is required"))
        disconnect()
        manualDisconnect = false
        _statusText.value = "Connecting to $host:$port ($protocol)..."

        try {
            when (protocol) {
                TerminalProtocol.SSH -> {
                    val client = SSHClient()
                    client.connectTimeout = 10000
                    client.timeout = 15000
                    client.addHostKeyVerifier(PromiscuousVerifier())
                    client.connect(host, port)
                    client.authPassword(username, password)

                    val session = client.startSession()
                    session.allocateDefaultPTY()
                    val shell = session.startShell()

                    sshClient = client
                    sshSession = session
                    terminalOutputStream = shell.outputStream

                    _isConnected.value = true
                    _connectedHost.value = "$host:$port (SSH)"
                    _statusText.value = "Connected to $host via SSH"

                    startOutputReader(shell.inputStream)
                }

                TerminalProtocol.TELNET -> {
                    val telnet = TelnetClient()
                    telnet.connectTimeout = 8000
                    telnet.connect(host, port)

                    telnetClient = telnet
                    terminalOutputStream = telnet.outputStream

                    _isConnected.value = true
                    _connectedHost.value = "$host:$port (Telnet)"
                    _statusText.value = "Connected to $host via Telnet"

                    startOutputReader(telnet.inputStream)
                }

                TerminalProtocol.NETCONF -> {
                    _statusText.value = "Use NETCONF RPC tab to query $host:$port"
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            disconnect()
            throw e
        } catch (e: Exception) {
            disconnect()
            _statusText.value = "Connection error: ${e.message}"
            Result.failure(e)
        }
    }

    private fun startOutputReader(inputStream: InputStream) {
        readerJob?.cancel()
        readerJob = scope.launch(Dispatchers.IO) {
            val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
            val charBuffer = CharArray(2048)
            var count: Int
            val currentLine = StringBuilder()

            try {
                while (reader.read(charBuffer).also { count = it } != -1) {
                    for (i in 0 until count) {
                        val c = charBuffer[i]
                        if (c == '\n') {
                            val line = currentLine.toString().trimEnd('\r')
                            currentLine.clear()
                            appendTerminalLine(line)
                        } else if (c != '\r') {
                            currentLine.append(c)
                            if (currentLine.length > 200) {
                                val chunk = currentLine.toString()
                                currentLine.clear()
                                appendTerminalLine(chunk)
                            }
                        }
                    }
                    if (currentLine.isNotEmpty()) {
                        val chunk = currentLine.toString()
                        currentLine.clear()
                        appendTerminalLine(chunk)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                if (!manualDisconnect) {
                    appendTerminalLine("[Session terminated by remote host]")
                }
                _isConnected.value = false
                _statusText.value = "Disconnected"
            }
        }
    }

    private fun appendTerminalLine(line: String) {
        _terminalBuffer.update { current ->
            val updated = current + line
            if (updated.size > 1000) updated.takeLast(800) else updated
        }
    }

    fun sendInput(text: String) {
        scope.launch(Dispatchers.IO) {
            try {
                terminalOutputStream?.let { os ->
                    os.write(text.toByteArray(StandardCharsets.UTF_8))
                    os.flush()
                }
            } catch (e: Exception) {
                _statusText.value = "Failed to send: ${e.message}"
            }
        }
    }

    fun sendSpecialKey(keySequence: String) {
        scope.launch(Dispatchers.IO) {
            try {
                terminalOutputStream?.let { os ->
                    os.write(keySequence.toByteArray(StandardCharsets.ISO_8859_1))
                    os.flush()
                }
            } catch (e: Exception) {
                _statusText.value = "Key error: ${e.message}"
            }
        }
    }

    fun clearTerminal() {
        _terminalBuffer.value = emptyList()
    }

    fun disconnect() {
        manualDisconnect = true
        readerJob?.cancel()
        readerJob = null

        try { sshSession?.close() } catch (_: Exception) {}
        sshSession = null

        try { sshClient?.disconnect() } catch (_: Exception) {}
        sshClient = null

        try { telnetClient?.disconnect() } catch (_: Exception) {}
        telnetClient = null

        terminalOutputStream = null
        _isConnected.value = false
        _connectedHost.value = null
        _statusText.value = "Disconnected"
    }

    suspend fun executeNetconfRpc(
        host: String,
        port: Int = 830,
        username: String,
        password: String,
        rpcXml: String
    ): NetconfRpcResult = withContext(Dispatchers.IO) {
        if (host.isBlank()) {
            return@withContext NetconfRpcResult(rpcXml, "", false, 0L, "Host is required")
        }
        val startTime = System.currentTimeMillis()
        val ssh = SSHClient()
        ssh.connectTimeout = 10000
        ssh.timeout = 15000
        ssh.addHostKeyVerifier(PromiscuousVerifier())

        try {
            ssh.connect(host, port)
            ssh.authPassword(username, password)

            ssh.startSession().use { session ->
                val netconf = session.startSubsystem("netconf")
                val os = netconf.outputStream
                val reader = BufferedReader(InputStreamReader(netconf.inputStream, StandardCharsets.UTF_8))

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line?.contains("]]>]]>") == true) break
                }

                val clientHello = "<hello xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"><capabilities><capability>urn:ietf:params:xml:ns:netconf:base:1.0</capability></capabilities></hello>]]>]]>\n"
                os.write(clientHello.toByteArray(StandardCharsets.UTF_8))
                os.flush()

                val formattedRpc = if (rpcXml.trim().endsWith("]]>]]>")) rpcXml else "$rpcXml\n]]>]]>\n"
                os.write(formattedRpc.toByteArray(StandardCharsets.UTF_8))
                os.flush()

                val responseSb = StringBuilder()
                while (reader.readLine().also { line = it } != null) {
                    if (line?.contains("]]>]]>") == true) {
                        val clean = line!!.replace("]]>]]>", "")
                        if (clean.isNotBlank()) responseSb.append(clean).append("\n")
                        break
                    }
                    responseSb.append(line).append("\n")
                }

                val executionTime = System.currentTimeMillis() - startTime
                val responseText = responseSb.toString()
                NetconfRpcResult(
                    rpcXml = rpcXml,
                    responseXml = responseText,
                    isSuccess = !responseText.contains("<rpc-error>"),
                    executionTimeMs = executionTime
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetconfRpcResult(
                rpcXml = rpcXml,
                responseXml = "",
                isSuccess = false,
                executionTimeMs = System.currentTimeMillis() - startTime,
                errorMessage = e.message ?: "NETCONF RPC Error"
            )
        } finally {
            try { ssh.disconnect() } catch (_: Exception) {}
        }
    }

    suspend fun executeSshCommand(
        host: String,
        port: Int = 22,
        username: String = "",
        password: String = "",
        command: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (host.isBlank()) return@withContext Result.failure(IllegalArgumentException("Host is required"))
        val ssh = SSHClient()
        ssh.connectTimeout = 10000
        ssh.timeout = 15000
        ssh.addHostKeyVerifier(PromiscuousVerifier())
        try {
            ssh.connect(host, port)
            ssh.authPassword(username, password)
            ssh.startSession().use { session ->
                val cmd = session.exec(command)
                val output = cmd.inputStream.bufferedReader().use { it.readText() }
                cmd.join(5, TimeUnit.SECONDS)
                Result.success(output)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { ssh.disconnect() } catch (_: Exception) {}
        }
    }

    fun parseShowVersion(output: String): ParsedVersionInfo {
        var vendor = "Unknown"
        var osVersion = "Unknown"
        var model = "Unknown"
        var uptime = "Unknown"
        var serialNumber = "Unknown"
        var memory = "Unknown"

        for (line in output.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Cisco IOS Software", ignoreCase = true) || trimmed.contains("Version ", ignoreCase = true)) {
                vendor = "Cisco Systems"
                val vIdx = trimmed.indexOf("Version ", ignoreCase = true)
                if (vIdx != -1) {
                    osVersion = trimmed.substring(vIdx).split(",")[0].replace("Version ", "").trim()
                }
            }
            if (trimmed.contains("uptime is", ignoreCase = true)) {
                uptime = trimmed.substringAfter("uptime is").trim()
            }
            if (trimmed.contains("cisco ", ignoreCase = true) && trimmed.contains("processor", ignoreCase = true)) {
                vendor = "Cisco Systems"
                val rawModel = trimmed.substringBefore("processor").replace("cisco", "", ignoreCase = true).trim()
                model = if (rawModel.contains("(")) rawModel.substringBefore("(").trim() else rawModel
                if (trimmed.contains("with ") && trimmed.contains("bytes of memory")) {
                    memory = trimmed.substringAfter("with ").substringBefore("bytes of memory").trim()
                }
            }
            if (trimmed.startsWith("Processor board ID", ignoreCase = true)) {
                serialNumber = trimmed.substringAfter("ID").trim()
            }
            if (trimmed.contains("JUNOS Software", ignoreCase = true)) {
                vendor = "Juniper Networks"
            }
        }

        return ParsedVersionInfo(
            vendor = vendor,
            osVersion = osVersion,
            model = model,
            uptime = uptime,
            serialNumber = serialNumber,
            memory = memory,
            rawText = output
        )
    }

    fun parseIpInterfaceBrief(output: String): List<ParsedInterfaceBrief> {
        val result = mutableListOf<ParsedInterfaceBrief>()
        var headerSkipped = false

        for (line in output.lines()) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue
            if (trimmed.startsWith("Interface", ignoreCase = true)) {
                headerSkipped = true
                continue
            }
            if (!headerSkipped && (trimmed.startsWith("---") || trimmed.contains("IP-Address"))) {
                headerSkipped = true
                continue
            }

            val tokens = trimmed.split("\\s+".toRegex())
            if (tokens.size >= 4) {
                val ifName = tokens[0]
                val ip = tokens[1]
                val proto = tokens.last()
                val status = if (tokens.size >= 5 && tokens[2].equals("YES", ignoreCase = true)) {
                    if (tokens.size > 5) tokens.subList(4, tokens.size - 1).joinToString(" ") else tokens[4]
                } else {
                    tokens.subList(2, tokens.size - 1).joinToString(" ")
                }

                result.add(
                    ParsedInterfaceBrief(
                        interfaceName = ifName,
                        ipAddress = ip,
                        status = status,
                        protocol = proto
                    )
                )
            }
        }
        return result
    }

    fun parseIpRoute(output: String): List<ParsedRoute> {
        val result = mutableListOf<ParsedRoute>()
        for (line in output.lines()) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("Codes:") || trimmed.startsWith("Gateway of")) continue

            val tokens = trimmed.split("\\s+".toRegex())
            if (tokens.size >= 3) {
                val proto = tokens[0]
                val network = tokens[1]
                val nextHop = when {
                    trimmed.contains("via ") -> trimmed.substringAfter("via ").substringBefore(",").trim()
                    trimmed.contains("is directly connected") -> "Direct"
                    else -> tokens.getOrElse(2) { "Direct" }
                }
                val metric = if (trimmed.contains("[") && trimmed.contains("]")) {
                    trimmed.substringAfter("[").substringBefore("]")
                } else "0/0"

                val iface = if (trimmed.contains(", ")) trimmed.substringAfterLast(", ").trim() else "N/A"
                val prefix = if (network.contains("/")) network.substringAfter("/").toIntOrNull() ?: 32 else 32

                result.add(
                    ParsedRoute(
                        network = network.substringBefore("/"),
                        prefixLength = prefix,
                        protocol = proto,
                        nextHop = nextHop,
                        metric = metric,
                        interfaceName = iface
                    )
                )
            }
        }
        return result
    }

    fun parseCdpLldpNeighbors(output: String): List<ParsedNeighbor> {
        val result = mutableListOf<ParsedNeighbor>()
        var inNeighbors = false

        for (line in output.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Device ID", ignoreCase = true) || trimmed.startsWith("Device-ID", ignoreCase = true)) {
                inNeighbors = true
                continue
            }
            if (!inNeighbors) continue
            if (trimmed.isBlank() || trimmed.startsWith("Total entries")) continue

            val tokens = trimmed.split("\\s+".toRegex())
            if (tokens.size >= 5) {
                result.add(
                    ParsedNeighbor(
                        deviceId = tokens[0],
                        localInterface = "${tokens[1]} ${tokens.getOrElse(2) { "" }}".trim(),
                        remoteInterface = tokens.last(),
                        platform = tokens.getOrElse(tokens.size - 3) { "" },
                        capabilities = tokens.getOrElse(tokens.size - 4) { "" }
                    )
                )
            }
        }
        return result
    }

    fun parseJunosInterfaces(output: String): List<ParsedInterfaceBrief> {
        val result = mutableListOf<ParsedInterfaceBrief>()
        for (line in output.lines()) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("Interface", ignoreCase = true)) continue

            val tokens = trimmed.split("\\s+".toRegex())
            if (tokens.size >= 3) {
                result.add(
                    ParsedInterfaceBrief(
                        interfaceName = tokens[0],
                        ipAddress = tokens.getOrElse(4) { "--" },
                        status = "${tokens[1]}/${tokens[2]}",
                        protocol = tokens.getOrElse(3) { "" }
                    )
                )
            }
        }
        return result
    }

    fun parseJunosRoutes(output: String): List<ParsedRoute> {
        val result = mutableListOf<ParsedRoute>()
        var currentPrefix = ""
        var currentProto = ""
        var currentMetric = "0"

        val headerRegex = Regex("""^(\d+\.\d+\.\d+\.\d+/\d+)\s+[*+\-]?\s*\[([^/\]]+)/(\d+)]""")
        val nextHopViaRegex = Regex("""to\s+(\S+)\s+via\s+(\S+)""")
        val onlyViaRegex = Regex("""via\s+(\S+)""")

        for (rawLine in output.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isBlank() || trimmed.startsWith("inet.") || trimmed.startsWith("Total ")) continue

            val hdrMatch = headerRegex.find(trimmed)
            if (hdrMatch != null) {
                currentPrefix = hdrMatch.groupValues[1]
                currentProto = hdrMatch.groupValues[2]
                currentMetric = hdrMatch.groupValues[3]
                continue
            }

            if (currentPrefix.isNotBlank() && (trimmed.contains(" via ") || trimmed.contains("Local"))) {
                val netIp = currentPrefix.substringBefore("/")
                val prefixLen = currentPrefix.substringAfter("/", "32").toIntOrNull() ?: 32
                val viaMatch = nextHopViaRegex.find(trimmed)
                val onlyVia = onlyViaRegex.find(trimmed)

                val nextHop = when {
                    viaMatch != null -> viaMatch.groupValues[1]
                    trimmed.contains("Local") -> "Local"
                    else -> "Direct"
                }
                val iface = when {
                    viaMatch != null -> viaMatch.groupValues[2]
                    onlyVia != null -> onlyVia.groupValues[1]
                    else -> "N/A"
                }

                result.add(
                    ParsedRoute(
                        network = netIp,
                        prefixLength = prefixLen,
                        protocol = currentProto,
                        nextHop = nextHop,
                        metric = currentMetric,
                        interfaceName = iface
                    )
                )
            }
        }
        return result
    }

    fun parseJunosBgpSummary(output: String): List<ParsedNeighbor> {
        val result = mutableListOf<ParsedNeighbor>()
        val peerLineRegex = Regex(
            """^(\d+\.\d+\.\d+\.\d+|[0-9a-fA-F:]+)\s+(\d+)\s+\d+\s+\d+\s+\d+\s+\d+\s+(?:\d+[wd]\s+)?\S+\s+(.+)$"""
        )
        for (rawLine in output.lines()) {
            val trimmed = rawLine.trim()
            val match = peerLineRegex.find(trimmed) ?: continue
            val peerIp = match.groupValues[1]
            val asn = match.groupValues[2]
            val state = match.groupValues[3].trim()
            result.add(
                ParsedNeighbor(
                    deviceId = peerIp,
                    localInterface = "AS $asn",
                    remoteInterface = "BGP",
                    platform = "Junos BGP Peer",
                    capabilities = state
                )
            )
        }
        return result
    }

    fun generateConfigDiff(runningConfig: String, startupConfig: String): ConfigDiffResult {
        val runningLines = runningConfig.lines()
        val startupLines = startupConfig.lines()

        val diffLines = mutableListOf<DiffLine>()
        var added = 0
        var removed = 0

        val startupSet = startupLines.map { it.trim() }.toSet()
        val runningSet = runningLines.map { it.trim() }.toSet()

        var lineNo = 1
        for (line in runningLines) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !startupSet.contains(trimmed)) {
                diffLines.add(DiffLine(DiffLineType.ADDED, lineNo, "+ $line"))
                added++
            } else {
                diffLines.add(DiffLine(DiffLineType.UNCHANGED, lineNo, "  $line"))
            }
            lineNo++
        }

        for (line in startupLines) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !runningSet.contains(trimmed)) {
                diffLines.add(DiffLine(DiffLineType.REMOVED, lineNo, "- $line"))
                removed++
                lineNo++
            }
        }

        return ConfigDiffResult(addedCount = added, removedCount = removed, lines = diffLines)
    }
}
