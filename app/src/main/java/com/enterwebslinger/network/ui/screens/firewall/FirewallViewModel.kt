// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/firewall/FirewallViewModel.kt ===
package com.enterwebslinger.network.ui.screens.firewall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterwebslinger.network.EnterwebSlingerApp
import com.enterwebslinger.network.data.repository.FirewallManagementRepository
import com.enterwebslinger.network.domain.model.CiscoPacketTracerResult
import com.enterwebslinger.network.domain.model.FirewallApplianceProfile
import com.enterwebslinger.network.domain.model.FirewallBackupResult
import com.enterwebslinger.network.domain.model.FirewallDropLogEntry
import com.enterwebslinger.network.domain.model.FirewallHubSnapshot
import com.enterwebslinger.network.domain.model.FirewallVendor
import com.enterwebslinger.network.domain.model.PanOsPolicyMatchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FirewallViewModel @JvmOverloads constructor(
    private val firewallRepository: FirewallManagementRepository = EnterwebSlingerApp.getContainer().firewallManagementRepository
) : ViewModel() {

    val savedAppliances: StateFlow<List<FirewallApplianceProfile>> = firewallRepository.savedAppliancesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedAppliance = MutableStateFlow<FirewallApplianceProfile?>(null)
    val selectedAppliance: StateFlow<FirewallApplianceProfile?> = _selectedAppliance.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _snapshot = MutableStateFlow<FirewallHubSnapshot?>(null)
    val snapshot: StateFlow<FirewallHubSnapshot?> = _snapshot.asStateFlow()

    private val _panOsMatchResult = MutableStateFlow<PanOsPolicyMatchResult?>(null)
    val panOsMatchResult: StateFlow<PanOsPolicyMatchResult?> = _panOsMatchResult.asStateFlow()

    private val _packetTracerResult = MutableStateFlow<CiscoPacketTracerResult?>(null)
    val packetTracerResult: StateFlow<CiscoPacketTracerResult?> = _packetTracerResult.asStateFlow()

    private val _dropLogs = MutableStateFlow<List<FirewallDropLogEntry>>(emptyList())
    val dropLogs: StateFlow<List<FirewallDropLogEntry>> = _dropLogs.asStateFlow()

    private val _backupResult = MutableStateFlow<FirewallBackupResult?>(null)
    val backupResult: StateFlow<FirewallBackupResult?> = _backupResult.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private var activeJob: Job? = null

    init {
        viewModelScope.launch {
            savedAppliances.collect { list ->
                val cur = _selectedAppliance.value
                if (cur == null && list.isNotEmpty()) {
                    _selectedAppliance.value = list.first()
                } else if (cur != null) {
                    val refreshed = list.firstOrNull { it.id == cur.id }
                    if (refreshed != null) _selectedAppliance.value = refreshed
                }
            }
        }
    }

    fun setSelectedTab(tabIndex: Int) {
        _selectedTab.value = tabIndex.coerceIn(0, 3)
    }

    fun selectAppliance(profile: FirewallApplianceProfile) {
        _selectedAppliance.value = profile
        _snapshot.value = null
        _panOsMatchResult.value = null
        _packetTracerResult.value = null
        _dropLogs.value = emptyList()
        _backupResult.value = null
        _statusBanner.value = "Selected ${profile.name} (${profile.vendor.displayName})"
    }

    fun saveAppliance(
        existingId: Long = 0L,
        vendor: FirewallVendor,
        name: String,
        host: String,
        apiPort: Int,
        sshPort: Int,
        username: String,
        password: String,
        apiToken: String,
        allowSelfSignedTls: Boolean,
        extraContext: String
    ) {
        viewModelScope.launch {
            val savedId = firewallRepository.saveAppliance(
                existingId = existingId,
                vendor = vendor,
                name = name,
                host = host,
                apiPort = apiPort,
                sshPort = sshPort,
                username = username,
                password = password,
                apiToken = apiToken,
                allowSelfSignedTls = allowSelfSignedTls,
                extraContext = extraContext
            )
            _statusBanner.value = "Saved appliance profile #$savedId to Room + Keystore Vault."
        }
    }

    fun deleteAppliance(profile: FirewallApplianceProfile) {
        viewModelScope.launch {
            firewallRepository.deleteAppliance(profile)
            if (_selectedAppliance.value?.id == profile.id) {
                _selectedAppliance.value = null
                _snapshot.value = null
            }
            _statusBanner.value = "Removed ${profile.name} and wiped vaulted credentials."
        }
    }

    fun refreshDashboard() {
        val target = _selectedAppliance.value ?: run {
            _statusBanner.value = "Select or add a firewall appliance first."
            return
        }
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _isLoading.value = true
            _statusBanner.value = "Querying ${target.vendor.shortCode} telemetry on ${target.host}..."
            val res = firewallRepository.fetchApplianceDashboard(target)
            _isLoading.value = false
            res.fold(
                onSuccess = { snap ->
                    _snapshot.value = snap
                    _statusBanner.value = snap.health?.statusNote ?: "Firewall telemetry refreshed."
                },
                onFailure = { err ->
                    _statusBanner.value = "Telemetry error: ${err.message}"
                }
            )
        }
    }

    fun cancelActiveTask() {
        activeJob?.cancel()
        _isLoading.value = false
        _statusBanner.value = "Operation cancelled."
    }

    fun bounceSophosIpsec(tunnelName: String) {
        val target = _selectedAppliance.value ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _isLoading.value = true
            val res = firewallRepository.bounceSophosIpsecTunnel(target, tunnelName)
            _isLoading.value = false
            res.fold(
                onSuccess = { msg ->
                    _statusBanner.value = msg.trim().ifBlank { "IPsec tunnel bounce completed." }
                    refreshDashboard()
                },
                onFailure = { _statusBanner.value = "Bounce failed: ${it.message}" }
            )
        }
    }

    fun fetchSophosDropPackets(filterIp: String) {
        val target = _selectedAppliance.value ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _isLoading.value = true
            val res = firewallRepository.fetchSophosDropPackets(target, filterIp)
            _isLoading.value = false
            res.fold(
                onSuccess = { logs ->
                    _dropLogs.value = logs
                    _statusBanner.value = "Parsed ${logs.size} dropped packet events from ${target.host}."
                },
                onFailure = { _statusBanner.value = "Drop log query failed: ${it.message}" }
            )
        }
    }

    fun triggerSophosBackup() {
        val target = _selectedAppliance.value ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _isLoading.value = true
            val res = firewallRepository.triggerSophosConfigBackup(target)
            _isLoading.value = false
            res.fold(
                onSuccess = { _statusBanner.value = it },
                onFailure = { _statusBanner.value = "Sophos backup error: ${it.message}" }
            )
        }
    }

    fun toggleFortiGateIpsec(p1Name: String, p2Name: String, bringUp: Boolean) {
        val target = _selectedAppliance.value ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _isLoading.value = true
            val res = firewallRepository.controlFortiGateIpsecTunnel(target, p1Name, p2Name, bringUp)
            _isLoading.value = false
            res.fold(
                onSuccess = { msg ->
                    _statusBanner.value = msg
                    refreshDashboard()
                },
                onFailure = { _statusBanner.value = "FortiGate IPsec control error: ${it.message}" }
            )
        }
    }

    fun downloadFortiGateBackup() {
        val target = _selectedAppliance.value ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _isLoading.value = true
            val res = firewallRepository.downloadFortiGateBackup(target)
            _isLoading.value = false
            res.fold(
                onSuccess = { bkp ->
                    _backupResult.value = bkp
                    _statusBanner.value = bkp.summary
                },
                onFailure = { _statusBanner.value = "FortiGate backup failed: ${it.message}" }
            )
        }
    }

    fun testPanOsPolicyMatch(
        fromZone: String,
        toZone: String,
        srcIp: String,
        dstIp: String,
        dstPort: Int,
        protocolNum: Int,
        application: String
    ) {
        val target = _selectedAppliance.value ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _isLoading.value = true
            val res = firewallRepository.testPanOsSecurityAndNatMatch(
                profile = target,
                fromZone = fromZone,
                toZone = toZone,
                srcIp = srcIp,
                dstIp = dstIp,
                dstPort = dstPort,
                protocolNumber = protocolNum,
                application = application
            )
            _isLoading.value = false
            res.fold(
                onSuccess = { match ->
                    _panOsMatchResult.value = match
                    _statusBanner.value = "PAN-OS matched rule '${match.matchedRuleName}' (${match.action})."
                },
                onFailure = { _statusBanner.value = "PAN-OS test failed: ${it.message}" }
            )
        }
    }

    fun runCiscoPacketTracer(
        inputIfc: String,
        protocol: String,
        srcIp: String,
        srcPort: Int,
        dstIp: String,
        dstPort: Int
    ) {
        val target = _selectedAppliance.value ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _isLoading.value = true
            val res = firewallRepository.runCiscoPacketTracer(
                profile = target,
                inputInterface = inputIfc,
                protocol = protocol,
                srcIp = srcIp,
                srcPort = srcPort,
                dstIp = dstIp,
                dstPort = dstPort
            )
            _isLoading.value = false
            res.fold(
                onSuccess = { pt ->
                    _packetTracerResult.value = pt
                    _statusBanner.value = "ASA packet-tracer verdict: ${pt.verdict} (${pt.phases.size} phases)."
                },
                onFailure = { _statusBanner.value = "Cisco packet-tracer failed: ${it.message}" }
            )
        }
    }

    fun restartPfService(serviceName: String) {
        val target = _selectedAppliance.value ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _isLoading.value = true
            val res = firewallRepository.restartPfService(target, serviceName)
            _isLoading.value = false
            res.fold(
                onSuccess = { msg ->
                    _statusBanner.value = msg
                    refreshDashboard()
                },
                onFailure = { _statusBanner.value = "Service restart failed: ${it.message}" }
            )
        }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }
}
