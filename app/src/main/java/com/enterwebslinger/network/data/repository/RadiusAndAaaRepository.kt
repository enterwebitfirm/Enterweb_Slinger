// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/RadiusAndAaaRepository.kt ===
package com.enterwebslinger.network.data.repository

import com.enterwebslinger.network.core.security.CredentialVault
import com.enterwebslinger.network.core.util.NetworkByteUtils
import com.enterwebslinger.network.data.local.db.dao.AaaServerProfileDao
import com.enterwebslinger.network.data.local.db.entities.SavedAaaServerEntity
import com.enterwebslinger.network.data.protocol.RadiusPacketCodec
import com.enterwebslinger.network.domain.model.AaaProfileKind
import com.enterwebslinger.network.domain.model.CoaListenerEvent
import com.enterwebslinger.network.domain.model.CoaListenerStatus
import com.enterwebslinger.network.domain.model.RadiusAccountingConfig
import com.enterwebslinger.network.domain.model.RadiusAuthRequestConfig
import com.enterwebslinger.network.domain.model.RadiusCoaConfig
import com.enterwebslinger.network.domain.model.RadiusPacketCode
import com.enterwebslinger.network.domain.model.RadiusResponseCode
import com.enterwebslinger.network.domain.model.RadiusWorkbenchResult
import com.enterwebslinger.network.domain.model.SavedAaaServerProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/**
 * Full-Spectrum RADIUS Workbench Repository:
 * - Authentication (RFC 2865 PAP, CHAP, MS-CHAPv2 Vendor 311 + Message-Authenticator Attr 80)
 * - Accounting (RFC 2866 Start/Stop/Interim/On/Off + 64-bit Gigawords)
 * - Dynamic Authorization Client (RFC 5176 CoA-Request / Disconnect-Request + Error-Cause 101)
 * - Local NAS CoA Listener Mode (UDP bind w/ reuseAddress, validates Authenticator & replies ACK/NAK)
 * - Room + CredentialVault profile persistence
 */
class RadiusAndAaaRepository(
    private val scope: CoroutineScope? = null,
    private val aaaProfileDao: AaaServerProfileDao? = null,
    private val credentialVault: CredentialVault? = null
) {

    private val _coaListenerStatus = MutableStateFlow(CoaListenerStatus())
    val coaListenerStatus: StateFlow<CoaListenerStatus> = _coaListenerStatus.asStateFlow()

    private val _coaEvents = MutableStateFlow<List<CoaListenerEvent>>(emptyList())
    val coaEvents: StateFlow<List<CoaListenerEvent>> = _coaEvents.asStateFlow()

    private var coaListenerJob: Job? = null
    private var coaSocket: DatagramSocket? = null

    val savedProfilesFlow: Flow<List<SavedAaaServerProfile>> =
        aaaProfileDao?.getAllServersFlow()?.map { entities ->
            entities.mapNotNull { entityToProfile(it) }
        } ?: flowOf(emptyList())

    suspend fun saveServerProfile(
        existingId: Long = 0L,
        name: String,
        kind: AaaProfileKind,
        host: String,
        port: Int,
        username: String,
        secretOrPassword: String,
        nasIp: String = "127.0.0.1",
        nasIdentifier: String = "EnterwebSlinger",
        baseDn: String = "",
        useTls: Boolean = false,
        allowSelfSignedTls: Boolean = true
    ): Long = withContext(Dispatchers.IO) {
        val dao = aaaProfileDao ?: return@withContext -1L
        val entity = SavedAaaServerEntity(
            id = existingId,
            name = name.trim().ifBlank { "${kind.label} (${host.trim()})" },
            kind = kind.name,
            host = host.trim(),
            port = port.coerceIn(1, 65535),
            username = username.trim(),
            nasIp = nasIp.trim().ifBlank { "127.0.0.1" },
            nasIdentifier = nasIdentifier.trim().ifBlank { "EnterwebSlinger" },
            baseDn = baseDn.trim(),
            useTls = useTls,
            allowSelfSignedTls = allowSelfSignedTls,
            credentialVaultKey = if (existingId > 0L) "aaa_${existingId}_secret" else "",
            lastUsedTimestamp = System.currentTimeMillis()
        )
        val rowId = if (existingId > 0L) {
            dao.updateServer(entity)
            existingId
        } else {
            val inserted = dao.insertServer(entity)
            dao.updateServer(entity.copy(id = inserted, credentialVaultKey = "aaa_${inserted}_secret"))
            inserted
        }
        if (secretOrPassword.isNotEmpty()) {
            credentialVault?.putSecret("aaa_${rowId}_secret", secretOrPassword)
        }
        rowId
    }

    suspend fun deleteServerProfile(profileId: Long) = withContext(Dispatchers.IO) {
        aaaProfileDao?.deleteServerById(profileId)
        credentialVault?.removeSecret("aaa_${profileId}_secret")
    }

    fun getSecretForProfile(profileId: Long): String {
        return credentialVault?.getSecret("aaa_${profileId}_secret").orEmpty()
    }

    suspend fun findSavedSecretForHost(host: String): String? = withContext(Dispatchers.IO) {
        val entity = aaaProfileDao?.findByHost(host.trim()) ?: return@withContext null
        if (entity.credentialVaultKey.isBlank()) return@withContext null
        credentialVault?.getSecret(entity.credentialVaultKey)?.takeIf { it.isNotEmpty() }
    }

    suspend fun sendAuthenticationTest(config: RadiusAuthRequestConfig): RadiusWorkbenchResult =
        withContext(Dispatchers.IO) {
            val cleanHost = config.serverIp.trim()
            if (cleanHost.isBlank() || config.sharedSecret.isBlank() || config.username.isBlank()) {
                return@withContext errorResult(
                    host = cleanHost,
                    port = config.port,
                    reqCode = RadiusPacketCode.ACCESS_REQUEST,
                    proto = config.protocol.name,
                    msg = "Server IP, Shared Secret, and Username are required."
                )
            }

            val built = RadiusPacketCodec.buildAccessRequest(config)
            sendUdpAndParse(
                host = cleanHost,
                port = config.port,
                sharedSecret = config.sharedSecret,
                built = built,
                protocolUsed = config.protocol.displayName,
                isAccountingOrCoa = false
            )
        }

    suspend fun sendAccountingTest(config: RadiusAccountingConfig): RadiusWorkbenchResult =
        withContext(Dispatchers.IO) {
            val cleanHost = config.serverIp.trim()
            if (cleanHost.isBlank() || config.sharedSecret.isBlank()) {
                return@withContext errorResult(
                    host = cleanHost,
                    port = config.port,
                    reqCode = RadiusPacketCode.ACCOUNTING_REQUEST,
                    proto = "Acct-${config.statusType.label}",
                    msg = "Server IP and Shared Secret are required."
                )
            }
            val built = RadiusPacketCodec.buildAccountingRequest(config)
            sendUdpAndParse(
                host = cleanHost,
                port = config.port,
                sharedSecret = config.sharedSecret,
                built = built,
                protocolUsed = "Acct-${config.statusType.label}",
                isAccountingOrCoa = false // Response Authenticator hashes against built.authenticator
            )
        }

    suspend fun sendCoaOrDisconnect(config: RadiusCoaConfig): RadiusWorkbenchResult =
        withContext(Dispatchers.IO) {
            val cleanHost = config.targetIp.trim()
            val reqCode = if (config.isDisconnect) RadiusPacketCode.DISCONNECT_REQUEST else RadiusPacketCode.COA_REQUEST
            if (cleanHost.isBlank() || config.sharedSecret.isBlank()) {
                return@withContext errorResult(
                    host = cleanHost,
                    port = config.port,
                    reqCode = reqCode,
                    proto = if (config.isDisconnect) "Disconnect-Request" else "CoA-Request",
                    msg = "Target NAS IP and Shared Secret are required."
                )
            }
            val built = RadiusPacketCodec.buildCoaOrDisconnectRequest(config)
            sendUdpAndParse(
                host = cleanHost,
                port = config.port,
                sharedSecret = config.sharedSecret,
                built = built,
                protocolUsed = if (config.isDisconnect) "PoD / Disconnect (40)" else "CoA (43)",
                isAccountingOrCoa = false
            )
        }

    fun startCoaListener(port: Int = 3799, sharedSecret: String, autoAck: Boolean = true) {
        val targetPort = port.coerceIn(1024, 65535)
        stopCoaListener()
        val runScope = scope ?: CoroutineScope(Dispatchers.IO)
        coaListenerJob = runScope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(targetPort))
                    soTimeout = 1000
                }
                coaSocket = socket
                _coaListenerStatus.value = CoaListenerStatus(
                    isListening = true,
                    bindPort = targetPort,
                    packetsReceived = _coaEvents.value.size
                )

                val rxBuf = ByteArray(4096)
                while (isActive) {
                    val pkt = DatagramPacket(rxBuf, rxBuf.size)
                    try {
                        socket.receive(pkt)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val len = pkt.length
                    if (len < 20) continue
                    val rawCopy = rxBuf.copyOf(len)
                    val parsed = RadiusPacketCodec.parsePacket(
                        raw = rawCopy,
                        length = len,
                        sharedSecret = sharedSecret.ifBlank { null },
                        expectedRequestAuthenticator = null,
                        isAccountingOrCoaRequest = true
                    )

                    val isDisconnect = parsed.packetCode == RadiusPacketCode.DISCONNECT_REQUEST
                    val shouldAck = autoAck && (sharedSecret.isBlank() || parsed.responseAuthenticatorValid)
                    val replyBytes = RadiusPacketCodec.buildCoaAckOrNakResponse(
                        incomingPacket = rawCopy,
                        incomingLen = len,
                        sharedSecret = sharedSecret,
                        isDisconnect = isDisconnect,
                        accept = shouldAck,
                        errorCauseCode = if (!shouldAck) 404 else null
                    )
                    runCatching {
                        socket.send(DatagramPacket(replyBytes, replyBytes.size, pkt.address, pkt.port))
                    }

                    val sentCode = when {
                        isDisconnect && shouldAck -> RadiusPacketCode.DISCONNECT_ACK
                        isDisconnect && !shouldAck -> RadiusPacketCode.DISCONNECT_NAK
                        !isDisconnect && shouldAck -> RadiusPacketCode.COA_ACK
                        else -> RadiusPacketCode.COA_NAK
                    }

                    val event = CoaListenerEvent(
                        sourceIp = pkt.address?.hostAddress ?: "unknown",
                        sourcePort = pkt.port,
                        requestCode = parsed.packetCode,
                        replySentCode = sentCode,
                        authenticatorValid = parsed.responseAuthenticatorValid,
                        messageAuthenticatorValid = if (parsed.messageAuthenticatorPresent) parsed.messageAuthenticatorValid else null,
                        username = parsed.userName,
                        sessionId = parsed.sessionId,
                        attributes = parsed.attributes,
                        decodedVsas = parsed.decodedVsas,
                        rawPacketHex = NetworkByteUtils.bytesToHex(rawCopy, " ")
                    )
                    _coaEvents.value = (listOf(event) + _coaEvents.value).take(50)
                    _coaListenerStatus.value = _coaListenerStatus.value.copy(
                        packetsReceived = _coaEvents.value.size
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _coaListenerStatus.value = CoaListenerStatus(
                    isListening = false,
                    bindPort = targetPort,
                    packetsReceived = _coaEvents.value.size,
                    errorMessage = e.localizedMessage ?: "Failed to bind UDP $targetPort"
                )
            } finally {
                NetworkByteUtils.safeClose(socket)
                coaSocket = null
                _coaListenerStatus.value = _coaListenerStatus.value.copy(isListening = false)
            }
        }
    }

    fun stopCoaListener() {
        coaListenerJob?.cancel()
        coaListenerJob = null
        NetworkByteUtils.safeClose(coaSocket)
        coaSocket = null
        _coaListenerStatus.value = _coaListenerStatus.value.copy(isListening = false)
    }

    fun clearCoaEvents() {
        _coaEvents.value = emptyList()
        _coaListenerStatus.value = _coaListenerStatus.value.copy(packetsReceived = 0)
    }

    private fun sendUdpAndParse(
        host: String,
        port: Int,
        sharedSecret: String,
        built: RadiusPacketCodec.BuiltRadiusPacket,
        protocolUsed: String,
        isAccountingOrCoa: Boolean
    ): RadiusWorkbenchResult {
        var socket: DatagramSocket? = null
        val t0 = System.currentTimeMillis()
        return try {
            val addr = InetAddress.getByName(host)
            socket = DatagramSocket().apply { soTimeout = 4500 }
            socket.send(DatagramPacket(built.packetBytes, built.packetBytes.size, addr, port.coerceIn(1, 65535)))

            val rxBuf = ByteArray(4096)
            val rxPkt = DatagramPacket(rxBuf, rxBuf.size)
            socket.receive(rxPkt)
            val rtt = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
            val respCopy = rxBuf.copyOf(rxPkt.length)

            val parsed = RadiusPacketCodec.parsePacket(
                raw = respCopy,
                length = rxPkt.length,
                sharedSecret = sharedSecret,
                expectedRequestAuthenticator = built.authenticator,
                isAccountingOrCoaRequest = isAccountingOrCoa
            )
            RadiusPacketCodec.toWorkbenchResult(
                serverIp = host,
                port = port,
                requestCode = built.code,
                protocolUsed = protocolUsed,
                rttMs = rtt,
                reqBytes = built.packetBytes,
                respBytes = respCopy,
                parsed = parsed
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorResult(
                host = host,
                port = port,
                reqCode = built.code,
                proto = protocolUsed,
                msg = e.localizedMessage ?: "No RADIUS response received",
                rtt = (System.currentTimeMillis() - t0).coerceAtLeast(1L),
                reqHex = NetworkByteUtils.bytesToHex(built.packetBytes, " ")
            )
        } finally {
            NetworkByteUtils.safeClose(socket)
        }
    }

    private fun errorResult(
        host: String,
        port: Int,
        reqCode: RadiusPacketCode,
        proto: String,
        msg: String,
        rtt: Long = 0L,
        reqHex: String = ""
    ) = RadiusWorkbenchResult(
        serverIp = host,
        port = port,
        requestCode = reqCode,
        responsePacketCode = RadiusPacketCode.UNKNOWN,
        responseCode = RadiusResponseCode.TIMEOUT_OR_ERROR,
        protocolUsed = proto,
        rttMs = rtt,
        isSuccess = false,
        rawRequestHex = reqHex,
        errorMessage = msg
    )

    private fun entityToProfile(e: SavedAaaServerEntity): SavedAaaServerProfile? {
        val kind = runCatching { AaaProfileKind.valueOf(e.kind) }.getOrNull() ?: return null
        return SavedAaaServerProfile(
            id = e.id,
            name = e.name,
            kind = kind,
            host = e.host,
            port = e.port,
            username = e.username,
            nasIp = e.nasIp,
            nasIdentifier = e.nasIdentifier,
            baseDn = e.baseDn,
            useTls = e.useTls,
            allowSelfSignedTls = e.allowSelfSignedTls,
            lastUsedTimestamp = e.lastUsedTimestamp
        )
    }
}
