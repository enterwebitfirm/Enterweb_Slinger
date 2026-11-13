// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/enterprise/EnterpriseViewModel.kt ===
package com.enterwebslinger.network.ui.screens.enterprise

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import com.enterwebslinger.network.EnterwebSlingerApp
import com.enterwebslinger.network.data.protocol.TftpClientEngine
import com.enterwebslinger.network.data.repository.CoreProtocolsRepository
import com.enterwebslinger.network.data.repository.FileTransferRepository
import com.enterwebslinger.network.data.repository.FlowCollectorRepository
import com.enterwebslinger.network.data.repository.GnmiRepository
import com.enterwebslinger.network.data.repository.LibreNmsRepository
import com.enterwebslinger.network.data.repository.NetworkInfoRepository
import com.enterwebslinger.network.data.repository.NmapFingerprintEngine
import com.enterwebslinger.network.data.repository.SnmpRepository
import com.enterwebslinger.network.data.repository.SyslogRepository
import com.enterwebslinger.network.data.repository.SyslogStats
import com.enterwebslinger.network.data.repository.TerminalAndNetOsRepository
import com.enterwebslinger.network.domain.model.ConfigDiffResult
import com.enterwebslinger.network.domain.model.DhcpServerOffer
import com.enterwebslinger.network.domain.model.FlowCollectorStats
import com.enterwebslinger.network.domain.model.FlowRecord
import com.enterwebslinger.network.domain.model.GnmiSessionConfig
import com.enterwebslinger.network.domain.model.GnmiTelemetryNode
import com.enterwebslinger.network.domain.model.GrpcHealthCheckResult
import com.enterwebslinger.network.domain.model.HostFingerprintReport
import com.enterwebslinger.network.domain.model.LibreNmsAlert
import com.enterwebslinger.network.domain.model.LibreNmsDevice
import com.enterwebslinger.network.domain.model.LibreNmsServerConfig
import com.enterwebslinger.network.domain.model.LocalFileItem
import com.enterwebslinger.network.domain.model.NetconfRpcResult
import com.enterwebslinger.network.domain.model.NtpResult
import com.enterwebslinger.network.domain.model.ParsedInterfaceBrief
import com.enterwebslinger.network.domain.model.ParsedNeighbor
import com.enterwebslinger.network.domain.model.ParsedRoute
import com.enterwebslinger.network.domain.model.ParsedVersionInfo
import com.enterwebslinger.network.domain.model.RemoteFileItem
import com.enterwebslinger.network.domain.model.ScanProfile
import com.enterwebslinger.network.domain.model.SmtpDiagResult
import com.enterwebslinger.network.domain.model.SnmpMibSummary
import com.enterwebslinger.network.domain.model.SnmpPduResult
import com.enterwebslinger.network.domain.model.SnmpTargetConfig
import com.enterwebslinger.network.domain.model.SnmpTrapRecord
import com.enterwebslinger.network.domain.model.SnmpVarBind
import com.enterwebslinger.network.domain.model.SyslogFacility
import com.enterwebslinger.network.domain.model.SyslogMessage
import com.enterwebslinger.network.domain.model.SyslogReceiverConfig
import com.enterwebslinger.network.domain.model.SyslogSeverity
import com.enterwebslinger.network.domain.model.TerminalProtocol
import com.enterwebslinger.network.domain.model.TodResult
import com.enterwebslinger.network.domain.model.TopologyGraphData
import com.enterwebslinger.network.domain.model.TransferProtocol
import com.enterwebslinger.network.domain.model.VendorProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EnterpriseViewModel @JvmOverloads constructor(
    val snmpRepository: SnmpRepository = EnterwebSlingerApp.getContainer().snmpRepository,
    val flowCollectorRepository: FlowCollectorRepository = EnterwebSlingerApp.getContainer().flowCollectorRepository,
    val syslogRepository: SyslogRepository = EnterwebSlingerApp.getContainer().syslogRepository,
    val libreNmsRepository: LibreNmsRepository = EnterwebSlingerApp.getContainer().libreNmsRepository,
    val gnmiRepository: GnmiRepository = EnterwebSlingerApp.getContainer().gnmiRepository,
    val nmapFingerprintEngine: NmapFingerprintEngine = EnterwebSlingerApp.getContainer().nmapFingerprintEngine,
    val networkInfoRepository: NetworkInfoRepository = EnterwebSlingerApp.getContainer().networkInfoRepository,
    val fileTransferRepository: FileTransferRepository = EnterwebSlingerApp.getContainer().fileTransferRepository,
    val terminalAndNetOsRepository: TerminalAndNetOsRepository = EnterwebSlingerApp.getContainer().terminalAndNetOsRepository,
    val coreProtocolsRepository: CoreProtocolsRepository = EnterwebSlingerApp.getContainer().coreProtocolsRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab.coerceIn(0, 7)
    }

    // ==========================================
    // 1. SNMP State & Actions
    // ==========================================
    private val _snmpConfig = MutableStateFlow(SnmpTargetConfig(host = ""))
    val snmpConfig: StateFlow<SnmpTargetConfig> = _snmpConfig.asStateFlow()

    private val _snmpLoading = MutableStateFlow(false)
    val snmpLoading: StateFlow<Boolean> = _snmpLoading.asStateFlow()

    private val _snmpMibSummary = MutableStateFlow<SnmpMibSummary?>(null)
    val snmpMibSummary: StateFlow<SnmpMibSummary?> = _snmpMibSummary.asStateFlow()

    private val _snmpLastQueryResult = MutableStateFlow<SnmpPduResult?>(null)
    val snmpLastQueryResult: StateFlow<SnmpPduResult?> = _snmpLastQueryResult.asStateFlow()

    private val _snmpWalkResults = MutableStateFlow<List<SnmpVarBind>>(emptyList())
    val snmpWalkResults: StateFlow<List<SnmpVarBind>> = _snmpWalkResults.asStateFlow()

    private val _snmpStatusMessage = MutableStateFlow<String?>(null)
    val snmpStatusMessage: StateFlow<String?> = _snmpStatusMessage.asStateFlow()

    val trapRecords: StateFlow<List<SnmpTrapRecord>> = snmpRepository.trapRecords
    val isTrapReceiverRunning: StateFlow<Boolean> = snmpRepository.isTrapReceiverRunning
    val trapErrorMessage: StateFlow<String?> = snmpRepository.trapErrorMessage

    init {
        viewModelScope.launch {
            networkInfoRepository.topology.collect { netState ->
                val gw = netState.defaultGateway
                if (gw.isNotBlank() && gw != "0.0.0.0" && gw != "Unavailable") {
                    if (_snmpConfig.value.host.isBlank()) {
                        _snmpConfig.value = _snmpConfig.value.copy(host = gw)
                    }
                    if (_fingerprintTarget.value.isBlank()) {
                        _fingerprintTarget.value = gw
                    }
                }
            }
        }
    }

    fun updateSnmpConfig(newConfig: SnmpTargetConfig) {
        _snmpConfig.value = newConfig
    }

    fun querySnmpMibSummary() {
        val host = _snmpConfig.value.host.trim()
        if (host.isBlank()) {
            _snmpStatusMessage.value = "Target SNMP agent IP/hostname is required"
            return
        }
        viewModelScope.launch {
            _snmpLoading.value = true
            _snmpStatusMessage.value = "Querying MIB-II & Host Resources from $host..."
            val result = snmpRepository.fetchMibSummary(_snmpConfig.value.copy(host = host))
            result.onSuccess { summary ->
                _snmpMibSummary.value = summary
                _snmpStatusMessage.value = "MIB-II queried successfully (${summary.interfaces.size} interfaces detected)"
            }.onFailure { err ->
                _snmpStatusMessage.value = "SNMP Query Failed: ${err.message}"
            }
            _snmpLoading.value = false
        }
    }

    fun querySnmpOid(oid: String) {
        val host = _snmpConfig.value.host.trim()
        val trimmedOid = oid.trim()
        if (host.isBlank() || trimmedOid.isBlank()) {
            _snmpStatusMessage.value = "Target host and OID are required"
            return
        }
        viewModelScope.launch {
            _snmpLoading.value = true
            _snmpStatusMessage.value = "Executing SNMP GET for $trimmedOid..."
            val result = snmpRepository.sendGet(_snmpConfig.value.copy(host = host), listOf(trimmedOid))
            result.onSuccess { pdu ->
                _snmpLastQueryResult.value = pdu
                _snmpStatusMessage.value = "GET succeeded in ${pdu.responseTimeMs}ms with ${pdu.varBinds.size} bindings"
            }.onFailure { err ->
                _snmpStatusMessage.value = "SNMP GET error: ${err.message}"
            }
            _snmpLoading.value = false
        }
    }

    fun walkSnmpSubtree(rootOid: String) {
        val host = _snmpConfig.value.host.trim()
        val trimmedOid = rootOid.trim()
        if (host.isBlank() || trimmedOid.isBlank()) {
            _snmpStatusMessage.value = "Target host and subtree OID are required"
            return
        }
        viewModelScope.launch {
            _snmpLoading.value = true
            _snmpStatusMessage.value = "Walking subtree $trimmedOid..."
            val result = snmpRepository.snmpWalk(_snmpConfig.value.copy(host = host), trimmedOid)
            result.onSuccess { list ->
                _snmpWalkResults.value = list
                _snmpStatusMessage.value = "Walk completed: ${list.size} OIDs traversed"
            }.onFailure { err ->
                _snmpStatusMessage.value = "SNMP Walk failed: ${err.message}"
            }
            _snmpLoading.value = false
        }
    }

    fun toggleTrapReceiver(port: Int = 1162) {
        if (isTrapReceiverRunning.value) {
            snmpRepository.stopTrapReceiver()
        } else {
            snmpRepository.startTrapReceiver(port)
        }
    }

    fun clearTraps() {
        snmpRepository.clearTraps()
    }

    // ==========================================
    // 2. Flow Telemetry State & Actions
    // ==========================================
    val flowStats: StateFlow<FlowCollectorStats> = flowCollectorRepository.stats
    val recentFlows: StateFlow<List<FlowRecord>> = flowCollectorRepository.recentFlows
    val topTalkers = flowCollectorRepository.topTalkers
    val protocolDistribution = flowCollectorRepository.protocolDistribution
    val dscpDistribution = flowCollectorRepository.dscpDistribution

    fun toggleFlowCollectors(ports: List<Int> = listOf(2055, 4739, 6343)) {
        if (flowStats.value.isRunning) {
            flowCollectorRepository.stopCollectors()
        } else {
            flowCollectorRepository.startCollectors(ports)
        }
    }

    fun clearFlowStats() {
        flowCollectorRepository.clearStats()
    }

    // ==========================================
    // 3. Syslog State & Actions
    // ==========================================
    val syslogMessages: StateFlow<List<SyslogMessage>> = syslogRepository.messages
    val syslogStats: StateFlow<SyslogStats> = syslogRepository.stats

    private val _syslogSearchQuery = MutableStateFlow("")
    val syslogSearchQuery: StateFlow<String> = _syslogSearchQuery.asStateFlow()

    private val _selectedSeverityFilter = MutableStateFlow<SyslogSeverity?>(null)
    val selectedSeverityFilter: StateFlow<SyslogSeverity?> = _selectedSeverityFilter.asStateFlow()

    private val _syslogStatusMessage = MutableStateFlow<String?>(null)
    val syslogStatusMessage: StateFlow<String?> = _syslogStatusMessage.asStateFlow()

    val filteredSyslogMessages: StateFlow<List<SyslogMessage>> = combine(
        syslogMessages,
        _syslogSearchQuery,
        _selectedSeverityFilter
    ) { msgs, query, severity ->
        msgs.filter { msg ->
            val matchesQuery = if (query.isBlank()) true else {
                msg.messageContent.contains(query, ignoreCase = true) ||
                    msg.hostname.contains(query, ignoreCase = true) ||
                    msg.appName.contains(query, ignoreCase = true)
            }
            val matchesSeverity = if (severity == null) true else msg.severity.code <= severity.code
            matchesQuery && matchesSeverity
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setSyslogSearchQuery(query: String) {
        _syslogSearchQuery.value = query
    }

    fun setSeverityFilter(severity: SyslogSeverity?) {
        _selectedSeverityFilter.value = severity
    }

    fun toggleSyslogReceiver(udpPort: Int = 1514, tcpPort: Int = 6514) {
        if (syslogStats.value.isRunning) {
            syslogRepository.stopReceiver()
        } else {
            syslogRepository.startReceiver(
                SyslogReceiverConfig(
                    udpEnabled = true,
                    udpPort = udpPort,
                    tcpEnabled = true,
                    tcpPort = tcpPort
                )
            )
        }
    }

    fun clearSyslogs() {
        syslogRepository.clearLogs()
    }

    fun sendTestSyslog(
        host: String = "127.0.0.1",
        port: Int = 1514,
        isRfc5424: Boolean = true,
        facility: SyslogFacility = SyslogFacility.LOCAL0,
        severity: SyslogSeverity = SyslogSeverity.WARNING,
        appName: String = "EnterwebSlinger",
        message: String
    ) {
        val targetHost = host.trim()
        val payload = message.trim()
        if (targetHost.isBlank() || payload.isBlank()) {
            _syslogStatusMessage.value = "Target host and syslog payload are required"
            return
        }
        viewModelScope.launch {
            val result = syslogRepository.sendTestSyslog(targetHost, port, isRfc5424, facility, severity, appName, payload)
            result.onSuccess {
                _syslogStatusMessage.value = "Syslog datagram dispatched to $targetHost:$port"
            }.onFailure { err ->
                _syslogStatusMessage.value = "Failed to dispatch syslog: ${err.message}"
            }
        }
    }

    // ==========================================
    // 4. Deep Audit & Topology Map State & Actions
    // ==========================================
    private val _fingerprintTarget = MutableStateFlow("")
    val fingerprintTarget: StateFlow<String> = _fingerprintTarget.asStateFlow()

    private val _scanProfile = MutableStateFlow(ScanProfile.QUICK_SCAN)
    val scanProfile: StateFlow<ScanProfile> = _scanProfile.asStateFlow()

    private val _isAuditScanning = MutableStateFlow(false)
    val isAuditScanning: StateFlow<Boolean> = _isAuditScanning.asStateFlow()

    private val _auditProgress = MutableStateFlow(Pair(0, 25))
    val auditProgress: StateFlow<Pair<Int, Int>> = _auditProgress.asStateFlow()

    private val _fingerprintReports = MutableStateFlow<List<HostFingerprintReport>>(emptyList())
    val fingerprintReports: StateFlow<List<HostFingerprintReport>> = _fingerprintReports.asStateFlow()

    private val _topologyData = MutableStateFlow<TopologyGraphData?>(null)
    val topologyData: StateFlow<TopologyGraphData?> = _topologyData.asStateFlow()

    private val _customPortsSpec = MutableStateFlow("22,80,443,8080,8443")
    val customPortsSpec: StateFlow<String> = _customPortsSpec.asStateFlow()

    private var auditJob: Job? = null

    fun setFingerprintTarget(target: String) {
        _fingerprintTarget.value = target
    }

    fun setScanProfile(profile: ScanProfile) {
        _scanProfile.value = profile
    }

    fun setCustomPortsSpec(spec: String) {
        _customPortsSpec.value = spec
    }

    fun stopDeepAudit() {
        auditJob?.cancel()
        auditJob = null
        _isAuditScanning.value = false
    }

    fun startDeepAudit() {
        val target = _fingerprintTarget.value.trim()
        if (target.isBlank() || _isAuditScanning.value) return

        val parsedCustom = if (_scanProfile.value == ScanProfile.CUSTOM_RANGE) {
            nmapFingerprintEngine.parseCustomPorts(_customPortsSpec.value)
        } else emptyList()

        auditJob = viewModelScope.launch {
            _isAuditScanning.value = true
            try {
                val report = nmapFingerprintEngine.auditHost(
                    targetHost = target,
                    profile = _scanProfile.value,
                    customPorts = parsedCustom,
                    onProgress = { current, total ->
                        _auditProgress.value = Pair(current, total)
                    }
                )

                val currentReports = _fingerprintReports.value.toMutableList()
                currentReports.removeAll { it.host == report.host }
                currentReports.add(0, report)
                _fingerprintReports.value = currentReports

                val currentNet = networkInfoRepository.topology.value
                val gw = currentNet.defaultGateway.takeIf { it != "Unavailable" && it.isNotBlank() } ?: target
                val localIp = currentNet.localIpv4.takeIf { it != "Unavailable" && it.isNotBlank() } ?: ""
                _topologyData.value = nmapFingerprintEngine.buildTopologyGraph(gw, currentReports, localIp)
            } finally {
                _isAuditScanning.value = false
            }
        }
    }

    // ==========================================
    // 5. LibreNMS & gNMI State & Actions
    // ==========================================
    private val _libreNmsConfig = MutableStateFlow(LibreNmsServerConfig())
    val libreNmsConfig: StateFlow<LibreNmsServerConfig> = _libreNmsConfig.asStateFlow()

    private val _libreNmsDevices = MutableStateFlow<List<LibreNmsDevice>>(emptyList())
    val libreNmsDevices: StateFlow<List<LibreNmsDevice>> = _libreNmsDevices.asStateFlow()

    private val _libreNmsAlerts = MutableStateFlow<List<LibreNmsAlert>>(emptyList())
    val libreNmsAlerts: StateFlow<List<LibreNmsAlert>> = _libreNmsAlerts.asStateFlow()

    private val _libreNmsStatusMessage = MutableStateFlow<String?>(null)
    val libreNmsStatusMessage: StateFlow<String?> = _libreNmsStatusMessage.asStateFlow()

    private val _libreNmsLoading = MutableStateFlow(false)
    val libreNmsLoading: StateFlow<Boolean> = _libreNmsLoading.asStateFlow()

    private val _gnmiConfig = MutableStateFlow(GnmiSessionConfig())
    val gnmiConfig: StateFlow<GnmiSessionConfig> = _gnmiConfig.asStateFlow()

    private val _grpcHealthResult = MutableStateFlow<GrpcHealthCheckResult?>(null)
    val grpcHealthResult: StateFlow<GrpcHealthCheckResult?> = _grpcHealthResult.asStateFlow()

    private val _gnmiTelemetryNodes = MutableStateFlow<List<GnmiTelemetryNode>>(emptyList())
    val gnmiTelemetryNodes: StateFlow<List<GnmiTelemetryNode>> = _gnmiTelemetryNodes.asStateFlow()

    private val _gnmiStatusMessage = MutableStateFlow<String?>(null)
    val gnmiStatusMessage: StateFlow<String?> = _gnmiStatusMessage.asStateFlow()

    private val _gnmiLoading = MutableStateFlow(false)
    val gnmiLoading: StateFlow<Boolean> = _gnmiLoading.asStateFlow()

    fun updateLibreNmsConfig(config: LibreNmsServerConfig) {
        _libreNmsConfig.value = config
    }

    fun testLibreNmsConnection() {
        val url = _libreNmsConfig.value.serverUrl.trim()
        if (url.isBlank()) {
            _libreNmsStatusMessage.value = "LibreNMS server URL is required"
            return
        }
        viewModelScope.launch {
            _libreNmsLoading.value = true
            val result = libreNmsRepository.testConnection(_libreNmsConfig.value.copy(serverUrl = url))
            result.onSuccess { msg ->
                _libreNmsStatusMessage.value = msg
                fetchLibreNmsData()
            }.onFailure { err ->
                _libreNmsStatusMessage.value = "Connection Failed: ${err.message}"
            }
            _libreNmsLoading.value = false
        }
    }

    fun fetchLibreNmsData() {
        val url = _libreNmsConfig.value.serverUrl.trim()
        if (url.isBlank()) return
        viewModelScope.launch {
            _libreNmsLoading.value = true
            val cfg = _libreNmsConfig.value.copy(serverUrl = url)
            val errors = mutableListOf<String>()
            libreNmsRepository.getDevices(cfg)
                .onSuccess { _libreNmsDevices.value = it }
                .onFailure { errors.add("Devices: ${it.message}") }

            libreNmsRepository.getAlerts(cfg)
                .onSuccess { _libreNmsAlerts.value = it }
                .onFailure { errors.add("Alerts: ${it.message}") }

            if (errors.isNotEmpty()) {
                _libreNmsStatusMessage.value = "Sync Error — ${errors.joinToString("; ")}"
            }
            _libreNmsLoading.value = false
        }
    }

    fun acknowledgeAlert(alertId: Int) {
        if (_libreNmsConfig.value.serverUrl.isBlank()) return
        viewModelScope.launch {
            val result = libreNmsRepository.acknowledgeAlert(_libreNmsConfig.value, alertId)
            result.onSuccess {
                fetchLibreNmsData()
                _libreNmsStatusMessage.value = "Alert #$alertId Acknowledged"
            }.onFailure { err ->
                _libreNmsStatusMessage.value = "Acknowledge failed: ${err.message}"
            }
        }
    }

    fun updateGnmiConfig(config: GnmiSessionConfig) {
        _gnmiConfig.value = config
    }

    fun checkGrpcHealth(serviceName: String = "") {
        val host = _gnmiConfig.value.host.trim()
        if (host.isBlank()) return
        viewModelScope.launch {
            _gnmiLoading.value = true
            val result = gnmiRepository.checkGrpcHealth(
                host = host,
                port = _gnmiConfig.value.port,
                serviceName = serviceName,
                useTls = _gnmiConfig.value.useTls
            )
            _grpcHealthResult.value = result
            _gnmiStatusMessage.value = result.errorMessage ?: "Health probe: ${result.status.name} (${result.responseTimeMs}ms)"
            _gnmiLoading.value = false
        }
    }

    fun fetchGnmiTelemetry() {
        val host = _gnmiConfig.value.host.trim()
        if (host.isBlank()) return
        viewModelScope.launch {
            _gnmiLoading.value = true
            _gnmiStatusMessage.value = "Querying gNMI paths on $host:${_gnmiConfig.value.port}..."
            val result = gnmiRepository.fetchGnmiTelemetry(_gnmiConfig.value.copy(host = host))
            result.onSuccess { nodes ->
                _gnmiTelemetryNodes.value = nodes
                _gnmiStatusMessage.value = "Retrieved ${nodes.size} gNMI telemetry update(s)"
            }.onFailure { err ->
                _gnmiStatusMessage.value = "gNMI Query Error: ${err.message}"
            }
            _gnmiLoading.value = false
        }
    }

    // ==========================================
    // 6. File Transfer Suite (FTP, FTPS, SFTP, SCP, TFTP)
    // ==========================================
    val fileSession = fileTransferRepository.session
    val remoteFiles = fileTransferRepository.remoteFiles
    val localFiles = fileTransferRepository.localFiles
    val transferQueue = fileTransferRepository.transferQueue
    val transferLoading = fileTransferRepository.isLoading
    val transferStatus = fileTransferRepository.statusMessage

    private val _filePreviewText = MutableStateFlow<String?>(null)
    val filePreviewText: StateFlow<String?> = _filePreviewText.asStateFlow()

    private val _tftpResult = MutableStateFlow<TftpClientEngine.TftpTransferResult?>(null)
    val tftpResult: StateFlow<TftpClientEngine.TftpTransferResult?> = _tftpResult.asStateFlow()

    private val _isTftpLoading = MutableStateFlow(false)
    val isTftpLoading: StateFlow<Boolean> = _isTftpLoading.asStateFlow()

    fun connectFileTransfer(
        host: String,
        port: Int,
        protocol: TransferProtocol,
        user: String,
        pass: String,
        passive: Boolean = true,
        binary: Boolean = true
    ) {
        val trimmedHost = host.trim()
        if (trimmedHost.isBlank()) return
        viewModelScope.launch {
            fileTransferRepository.connect(trimmedHost, port, protocol, user.trim(), pass, passive, binary)
        }
    }

    fun disconnectFileTransfer() {
        fileTransferRepository.disconnect()
    }

    fun refreshRemoteFiles(path: String = fileSession.value.currentRemotePath) {
        viewModelScope.launch {
            fileTransferRepository.refreshRemoteFiles(path)
        }
    }

    fun refreshLocalFiles(path: String = fileSession.value.currentLocalPath) {
        fileTransferRepository.refreshLocalFiles(path)
    }

    fun navigateLocalUp() = fileTransferRepository.navigateLocalUp()

    fun navigateRemoteUp() {
        viewModelScope.launch { fileTransferRepository.navigateRemoteUp() }
    }

    fun queueDownload(remoteFile: RemoteFileItem) {
        fileTransferRepository.queueDownload(remoteFile)
    }

    fun queueUpload(localFile: LocalFileItem) {
        fileTransferRepository.queueUpload(localFile)
    }

    fun queueUploadUri(uri: Uri) {
        fileTransferRepository.queueUploadUri(uri)
    }

    fun exportLocalFileToUri(localPath: String, destUri: Uri) {
        viewModelScope.launch {
            fileTransferRepository.exportLocalFileToUri(localPath, destUri)
        }
    }

    fun executeTftpUriUpload(host: String, port: Int = 69, blksize: Int = 1428, uri: Uri, remoteFilenameOverride: String = "") {
        val trimmedHost = host.trim()
        if (trimmedHost.isBlank()) return
        viewModelScope.launch {
            _isTftpLoading.value = true
            _tftpResult.value = null
            fileTransferRepository.readUriBytes(uri)
                .onSuccess { (docName, bytes) ->
                    val targetName = remoteFilenameOverride.trim().ifBlank { docName }
                    _tftpResult.value = fileTransferRepository.tftpEngine.uploadFile(
                        host = trimmedHost,
                        port = port,
                        remoteFilename = targetName,
                        data = bytes,
                        requestedBlksize = blksize
                    )
                }
                .onFailure { err ->
                    _tftpResult.value = TftpClientEngine.TftpTransferResult(
                        isSuccess = false,
                        bytesTransferred = 0L,
                        totalBlocks = 0,
                        negotiatedBlockSize = blksize,
                        durationMs = 0L,
                        errorMessage = "SAF Read Error: ${err.message}"
                    )
                }
            _isTftpLoading.value = false
        }
    }

    fun chmodRemote(path: String, octal: String) {
        viewModelScope.launch {
            fileTransferRepository.chmodRemote(path, octal)
        }
    }

    fun previewRemoteFile(path: String) {
        viewModelScope.launch {
            val res = fileTransferRepository.readRemoteTextPreview(path)
            res.onSuccess { _filePreviewText.value = it }
                .onFailure { _filePreviewText.value = "Error reading preview: ${it.message}" }
        }
    }

    fun clearFilePreview() {
        _filePreviewText.value = null
    }

    fun clearFinishedTransfers() {
        fileTransferRepository.clearFinishedJobs()
    }

    fun executeTftpDownload(host: String, port: Int = 69, filename: String, blksize: Int = 1428) {
        val trimmedHost = host.trim()
        val trimmedName = filename.trim()
        if (trimmedHost.isBlank() || trimmedName.isBlank()) return
        viewModelScope.launch {
            _isTftpLoading.value = true
            _tftpResult.value = null
            val res = fileTransferRepository.tftpEngine.downloadFile(
                host = trimmedHost,
                port = port,
                remoteFilename = trimmedName,
                requestedBlksize = blksize
            )
            _tftpResult.value = res
            if (res.isSuccess && res.data != null) {
                fileTransferRepository.createLocalFile(trimmedName, String(res.data, Charsets.UTF_8))
            }
            _isTftpLoading.value = false
        }
    }

    fun executeTftpUpload(host: String, port: Int = 69, filename: String, content: String, blksize: Int = 1428) {
        val trimmedHost = host.trim()
        val trimmedName = filename.trim()
        if (trimmedHost.isBlank() || trimmedName.isBlank()) return
        viewModelScope.launch {
            _isTftpLoading.value = true
            _tftpResult.value = null
            val res = fileTransferRepository.tftpEngine.uploadFile(
                host = trimmedHost,
                port = port,
                remoteFilename = trimmedName,
                data = content.toByteArray(Charsets.UTF_8),
                requestedBlksize = blksize
            )
            _tftpResult.value = res
            _isTftpLoading.value = false
        }
    }

    // ==========================================
    // 7. Interactive Terminal & Vendor Automation (Cisco, Juniper, NETCONF, Diff)
    // ==========================================
    val terminalConnected = terminalAndNetOsRepository.isConnected
    val terminalHost = terminalAndNetOsRepository.connectedHost
    val terminalBuffer = terminalAndNetOsRepository.terminalBuffer
    val terminalStatus = terminalAndNetOsRepository.statusText

    private val _parsedVersion = MutableStateFlow<ParsedVersionInfo?>(null)
    val parsedVersion: StateFlow<ParsedVersionInfo?> = _parsedVersion.asStateFlow()

    private val _parsedInterfaces = MutableStateFlow<List<ParsedInterfaceBrief>>(emptyList())
    val parsedInterfaces: StateFlow<List<ParsedInterfaceBrief>> = _parsedInterfaces.asStateFlow()

    private val _parsedRoutes = MutableStateFlow<List<ParsedRoute>>(emptyList())
    val parsedRoutes: StateFlow<List<ParsedRoute>> = _parsedRoutes.asStateFlow()

    private val _parsedNeighbors = MutableStateFlow<List<ParsedNeighbor>>(emptyList())
    val parsedNeighbors: StateFlow<List<ParsedNeighbor>> = _parsedNeighbors.asStateFlow()

    private val _netconfResult = MutableStateFlow<NetconfRpcResult?>(null)
    val netconfResult: StateFlow<NetconfRpcResult?> = _netconfResult.asStateFlow()

    private val _isNetconfLoading = MutableStateFlow(false)
    val isNetconfLoading: StateFlow<Boolean> = _isNetconfLoading.asStateFlow()

    private val _configDiffResult = MutableStateFlow<ConfigDiffResult?>(null)
    val configDiffResult: StateFlow<ConfigDiffResult?> = _configDiffResult.asStateFlow()

    fun connectTerminal(host: String, port: Int, protocol: TerminalProtocol, user: String = "", pass: String = "") {
        val trimmedHost = host.trim()
        if (trimmedHost.isBlank()) return
        viewModelScope.launch {
            terminalAndNetOsRepository.connect(trimmedHost, port, protocol, user.trim(), pass)
        }
    }

    fun disconnectTerminal() = terminalAndNetOsRepository.disconnect()

    fun sendTerminalInput(cmd: String) = terminalAndNetOsRepository.sendInput(cmd)

    fun sendSpecialKey(seq: String) = terminalAndNetOsRepository.sendSpecialKey(seq)

    fun clearTerminalBuffer() = terminalAndNetOsRepository.clearTerminal()

    fun executeNetconfRpc(host: String, port: Int = 830, user: String, pass: String, rpcXml: String) {
        val trimmedHost = host.trim()
        if (trimmedHost.isBlank() || rpcXml.isBlank()) return
        viewModelScope.launch {
            _isNetconfLoading.value = true
            val res = terminalAndNetOsRepository.executeNetconfRpc(trimmedHost, port, user.trim(), pass, rpcXml)
            _netconfResult.value = res
            _isNetconfLoading.value = false
        }
    }

    fun parseVendorCommand(vendor: VendorProfile, command: String, rawOutput: String) {
        if (rawOutput.isBlank()) return
        when (vendor) {
            VendorProfile.CISCO_IOS, VendorProfile.CISCO_NXOS -> {
                when {
                    command.contains("version", ignoreCase = true) -> {
                        _parsedVersion.value = terminalAndNetOsRepository.parseShowVersion(rawOutput)
                    }
                    command.contains("interface", ignoreCase = true) || command.contains("brief", ignoreCase = true) -> {
                        _parsedInterfaces.value = terminalAndNetOsRepository.parseIpInterfaceBrief(rawOutput)
                    }
                    command.contains("route", ignoreCase = true) -> {
                        _parsedRoutes.value = terminalAndNetOsRepository.parseIpRoute(rawOutput)
                    }
                    command.contains("cdp", ignoreCase = true) || command.contains("lldp", ignoreCase = true) -> {
                        _parsedNeighbors.value = terminalAndNetOsRepository.parseCdpLldpNeighbors(rawOutput)
                    }
                }
            }
            VendorProfile.JUNIPER_JUNOS -> {
                when {
                    command.contains("version", ignoreCase = true) -> {
                        _parsedVersion.value = terminalAndNetOsRepository.parseShowVersion(rawOutput)
                    }
                    command.contains("terse", ignoreCase = true) || command.contains("interface", ignoreCase = true) -> {
                        _parsedInterfaces.value = terminalAndNetOsRepository.parseJunosInterfaces(rawOutput)
                    }
                    command.contains("route", ignoreCase = true) -> {
                        _parsedRoutes.value = terminalAndNetOsRepository.parseJunosRoutes(rawOutput)
                    }
                    command.contains("bgp", ignoreCase = true) -> {
                        _parsedNeighbors.value = terminalAndNetOsRepository.parseJunosBgpSummary(rawOutput)
                    }
                    command.contains("lldp", ignoreCase = true) || command.contains("cdp", ignoreCase = true) -> {
                        _parsedNeighbors.value = terminalAndNetOsRepository.parseCdpLldpNeighbors(rawOutput)
                    }
                }
            }
        }
    }

    fun computeConfigDiff(runningConfig: String, startupConfig: String) {
        _configDiffResult.value = terminalAndNetOsRepository.generateConfigDiff(runningConfig, startupConfig)
    }

    // ==========================================
    // 8. Core Infrastructure Protocols (DHCP Rogue, ToD RFC 868, SNTP, SMTP Diag)
    // ==========================================
    private val _dhcpOffers = MutableStateFlow<List<DhcpServerOffer>>(emptyList())
    val dhcpOffers: StateFlow<List<DhcpServerOffer>> = _dhcpOffers.asStateFlow()

    private val _isDhcpScanning = MutableStateFlow(false)
    val isDhcpScanning: StateFlow<Boolean> = _isDhcpScanning.asStateFlow()

    private val _todResult = MutableStateFlow<TodResult?>(null)
    val todResult: StateFlow<TodResult?> = _todResult.asStateFlow()

    private val _isTodLoading = MutableStateFlow(false)
    val isTodLoading: StateFlow<Boolean> = _isTodLoading.asStateFlow()

    private val _ntpResult = MutableStateFlow<NtpResult?>(null)
    val ntpResult: StateFlow<NtpResult?> = _ntpResult.asStateFlow()

    private val _isNtpLoading = MutableStateFlow(false)
    val isNtpLoading: StateFlow<Boolean> = _isNtpLoading.asStateFlow()

    private val _smtpResult = MutableStateFlow<SmtpDiagResult?>(null)
    val smtpResult: StateFlow<SmtpDiagResult?> = _smtpResult.asStateFlow()

    private val _isSmtpLoading = MutableStateFlow(false)
    val isSmtpLoading: StateFlow<Boolean> = _isSmtpLoading.asStateFlow()

    val dhcpDiagnostic: StateFlow<String?> = coreProtocolsRepository.lastDhcpDiagnostic

    fun runDhcpRogueScan(authorizedGateway: String? = null, useInform: Boolean = false) {
        viewModelScope.launch {
            _isDhcpScanning.value = true
            _dhcpOffers.value = emptyList()
            val gw = authorizedGateway?.trim()?.takeIf { it.isNotBlank() }
                ?: networkInfoRepository.topology.value.defaultGateway
            val results = coreProtocolsRepository.discoverDhcpServers(gw, useInform = useInform)
            _dhcpOffers.value = results
            _isDhcpScanning.value = false
        }
    }

    fun queryTimeOfDay(host: String, port: Int = 37, useUdp: Boolean = false) {
        val trimmedHost = host.trim()
        if (trimmedHost.isBlank()) return
        viewModelScope.launch {
            _isTodLoading.value = true
            _todResult.value = null
            val res = coreProtocolsRepository.queryTimeOfDay(trimmedHost, port, useUdp = useUdp)
            _todResult.value = res
            _isTodLoading.value = false
        }
    }

    fun querySntp(host: String) {
        val trimmedHost = host.trim()
        if (trimmedHost.isBlank()) return
        viewModelScope.launch {
            _isNtpLoading.value = true
            _ntpResult.value = null
            val res = coreProtocolsRepository.querySntp(trimmedHost)
            _ntpResult.value = res
            _isNtpLoading.value = false
        }
    }

    fun runSmtpDiagnostics(host: String, port: Int = 25, testRelay: Boolean = true, helo: String = "localhost") {
        val trimmedHost = host.trim()
        if (trimmedHost.isBlank()) return
        val effectiveHelo = helo.trim().ifBlank { "localhost" }
        viewModelScope.launch {
            _isSmtpLoading.value = true
            _smtpResult.value = null
            val res = coreProtocolsRepository.diagnoseSmtp(
                host = trimmedHost,
                port = port,
                testOpenRelay = testRelay,
                heloHost = effectiveHelo
            )
            _smtpResult.value = res
            _isSmtpLoading.value = false
        }
    }
}
