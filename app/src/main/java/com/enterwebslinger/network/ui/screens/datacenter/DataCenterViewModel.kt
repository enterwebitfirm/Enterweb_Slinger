// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/datacenter/DataCenterViewModel.kt ===
package com.enterwebslinger.network.ui.screens.datacenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterwebslinger.network.EnterwebSlingerApp
import com.enterwebslinger.network.data.repository.CiscoCollaborationRepository
import com.enterwebslinger.network.data.repository.OobHardwareRepository
import com.enterwebslinger.network.data.repository.VirtualizationRepository
import com.enterwebslinger.network.domain.model.AsfPresenceResult
import com.enterwebslinger.network.domain.model.BootDevice
import com.enterwebslinger.network.domain.model.CallQualityMetric
import com.enterwebslinger.network.domain.model.HypervisorHost
import com.enterwebslinger.network.domain.model.PhoneEndpoint
import com.enterwebslinger.network.domain.model.PowerAction
import com.enterwebslinger.network.domain.model.RedfishServerSummary
import com.enterwebslinger.network.domain.model.SipTrunk
import com.enterwebslinger.network.domain.model.VmItem
import com.enterwebslinger.network.domain.model.VoiceGatewayDsp
import com.enterwebslinger.network.domain.model.WinRmCommandResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DataCenterViewModel @JvmOverloads constructor(
    val oobRepository: OobHardwareRepository = EnterwebSlingerApp.getContainer().oobHardwareRepository,
    val virtualizationRepository: VirtualizationRepository = EnterwebSlingerApp.getContainer().virtualizationRepository,
    val ciscoUcRepository: CiscoCollaborationRepository = EnterwebSlingerApp.getContainer().ciscoCollaborationRepository
) : ViewModel() {

    // Active Section: 0 = OOB Bare Metal, 1 = Hypervisors & VMs, 2 = Cisco UC & VoIP
    private val _selectedSection = MutableStateFlow(0)
    val selectedSection: StateFlow<Int> = _selectedSection.asStateFlow()

    fun setSelectedSection(section: Int) {
        _selectedSection.value = section.coerceIn(0, 2)
    }

    // --- 1. OOB Server Management State ---
    private val _redfishSummary = MutableStateFlow<RedfishServerSummary?>(null)
    val redfishSummary: StateFlow<RedfishServerSummary?> = _redfishSummary.asStateFlow()

    private val _asfResult = MutableStateFlow<AsfPresenceResult?>(null)
    val asfResult: StateFlow<AsfPresenceResult?> = _asfResult.asStateFlow()

    private val _ipmiAuthCapsResult = MutableStateFlow<String?>(null)
    val ipmiAuthCapsResult: StateFlow<String?> = _ipmiAuthCapsResult.asStateFlow()

    private val _isOobLoading = MutableStateFlow(false)
    val isOobLoading: StateFlow<Boolean> = _isOobLoading.asStateFlow()

    private val _oobStatusMessage = MutableStateFlow<String?>(null)
    val oobStatusMessage: StateFlow<String?> = _oobStatusMessage.asStateFlow()

    fun fetchRedfishSummary(
        host: String,
        port: Int = 443,
        user: String,
        pass: String,
        systemId: String = "1",
        ignoreSslErrors: Boolean = true
    ) {
        val target = host.trim()
        if (target.isBlank()) return
        viewModelScope.launch {
            _isOobLoading.value = true
            _oobStatusMessage.value = "Connecting to Redfish BMC at https://$target:$port..."
            val result = oobRepository.getRedfishSummary(
                target,
                port,
                user.trim(),
                pass,
                systemId.trim().ifBlank { "1" },
                ignoreSslErrors
            )
            result.onSuccess {
                _redfishSummary.value = it
                _oobStatusMessage.value = "Redfish telemetry synchronized (${it.model})"
            }.onFailure {
                _oobStatusMessage.value = "Redfish Error: ${it.message}"
            }
            _isOobLoading.value = false
        }
    }

    fun executePowerAction(
        host: String,
        port: Int = 443,
        user: String,
        pass: String,
        systemId: String = "1",
        action: PowerAction,
        ignoreSslErrors: Boolean = true
    ) {
        val target = host.trim()
        if (target.isBlank()) return
        viewModelScope.launch {
            _isOobLoading.value = true
            val result = oobRepository.setRedfishPowerState(
                target,
                port,
                user.trim(),
                pass,
                systemId.trim().ifBlank { "1" },
                action,
                ignoreSslErrors
            )
            result.onSuccess {
                _oobStatusMessage.value = it
                fetchRedfishSummary(target, port, user, pass, systemId, ignoreSslErrors)
            }.onFailure {
                _oobStatusMessage.value = "Power Action Failed: ${it.message}"
            }
            _isOobLoading.value = false
        }
    }

    fun setBootOverride(
        host: String,
        port: Int = 443,
        user: String,
        pass: String,
        systemId: String = "1",
        bootDevice: BootDevice,
        ignoreSslErrors: Boolean = true
    ) {
        val target = host.trim()
        if (target.isBlank()) return
        viewModelScope.launch {
            _isOobLoading.value = true
            val result = oobRepository.setRedfishOneTimeBoot(
                target,
                port,
                user.trim(),
                pass,
                systemId.trim().ifBlank { "1" },
                bootDevice,
                ignoreSslErrors
            )
            result.onSuccess {
                _oobStatusMessage.value = it
            }.onFailure {
                _oobStatusMessage.value = "Boot Override Failed: ${it.message}"
            }
            _isOobLoading.value = false
        }
    }

    fun pingAsfPresence(host: String, port: Int = 623) {
        val target = host.trim()
        if (target.isBlank()) return
        viewModelScope.launch {
            _isOobLoading.value = true
            val result = oobRepository.sendAsfPresencePing(target, port)
            _asfResult.value = result
            _oobStatusMessage.value = if (result.isAlive) {
                "ASF Presence Ping SUCCESS (${result.oemType}, ${result.responseTimeMs} ms)"
            } else {
                "ASF Ping: Host unreachable on UDP $port"
            }
            _isOobLoading.value = false
        }
    }

    fun queryIpmiAuthCapabilities(host: String, port: Int = 623) {
        val target = host.trim()
        if (target.isBlank()) return
        viewModelScope.launch {
            _isOobLoading.value = true
            val result = oobRepository.queryIpmiChannelAuthCapabilities(target, port)
            result.onSuccess { caps ->
                _ipmiAuthCapsResult.value = caps
                _oobStatusMessage.value = "IPMI Channel Auth Caps retrieved from UDP $port"
            }.onFailure { err ->
                _ipmiAuthCapsResult.value = null
                _oobStatusMessage.value = "IPMI Auth Caps Error: ${err.message}"
            }
            _isOobLoading.value = false
        }
    }

    // --- 2. Virtualization Connectors State ---
    private val _vms = MutableStateFlow<List<VmItem>>(emptyList())
    val vms: StateFlow<List<VmItem>> = _vms.asStateFlow()

    private val _hosts = MutableStateFlow<List<HypervisorHost>>(emptyList())
    val hosts: StateFlow<List<HypervisorHost>> = _hosts.asStateFlow()

    private val _winRmResult = MutableStateFlow<WinRmCommandResult?>(null)
    val winRmResult: StateFlow<WinRmCommandResult?> = _winRmResult.asStateFlow()

    private val _isVirtLoading = MutableStateFlow(false)
    val isVirtLoading: StateFlow<Boolean> = _isVirtLoading.asStateFlow()

    private val _virtStatusMessage = MutableStateFlow<String?>(null)
    val virtStatusMessage: StateFlow<String?> = _virtStatusMessage.asStateFlow()

    fun fetchVcenterData(host: String, port: Int = 443, user: String, pass: String) {
        val target = host.trim()
        if (target.isBlank()) return
        viewModelScope.launch {
            _isVirtLoading.value = true
            _virtStatusMessage.value = "Querying VMware vCenter REST API..."
            val errors = mutableListOf<String>()
            val vmResult = virtualizationRepository.getVcenterVms(target, port, user.trim(), pass)
            vmResult.onSuccess { _vms.value = it }
                .onFailure { errors.add("VMs: ${it.message}") }

            val hostResult = virtualizationRepository.getVcenterHosts(target, port, user.trim(), pass)
            hostResult.onSuccess { _hosts.value = it }
                .onFailure { errors.add("Hosts: ${it.message}") }

            _virtStatusMessage.value = when {
                errors.isEmpty() ->
                    "vCenter synchronized: ${_vms.value.size} VMs, ${_hosts.value.size} ESXi hosts"
                errors.size == 1 ->
                    "vCenter partial sync (${_vms.value.size} VMs, ${_hosts.value.size} hosts) — Error ${errors.first()}"
                else ->
                    "vCenter Error: ${errors.joinToString("; ")}"
            }
            _isVirtLoading.value = false
        }
    }

    fun controlVcenterVm(host: String, port: Int = 443, user: String, pass: String, vmId: String, action: String) {
        val target = host.trim()
        if (target.isBlank() || vmId.isBlank()) return
        viewModelScope.launch {
            _isVirtLoading.value = true
            val res = virtualizationRepository.controlVcenterVmPower(target, port, user.trim(), pass, vmId, action)
            res.onSuccess {
                _virtStatusMessage.value = it
                fetchVcenterData(target, port, user, pass)
            }.onFailure {
                _virtStatusMessage.value = "Power action failed: ${it.message}"
            }
            _isVirtLoading.value = false
        }
    }

    fun queryEsxiSoap(host: String, port: Int = 443) {
        val target = host.trim()
        if (target.isBlank()) return
        viewModelScope.launch {
            _isVirtLoading.value = true
            val res = virtualizationRepository.queryEsxiSoapServiceContent(target, port)
            res.onSuccess { _virtStatusMessage.value = it }
                .onFailure { _virtStatusMessage.value = "ESXi SOAP Error: ${it.message}" }
            _isVirtLoading.value = false
        }
    }

    fun fetchOvirtData(host: String, port: Int = 443, user: String, pass: String) {
        val target = host.trim()
        if (target.isBlank()) return
        viewModelScope.launch {
            _isVirtLoading.value = true
            val res = virtualizationRepository.getOvirtVms(target, port, user.trim(), pass)
            res.onSuccess {
                _vms.value = it
                _virtStatusMessage.value = "RHEV / oVirt synchronized: ${it.size} VMs"
            }.onFailure {
                _virtStatusMessage.value = "RHEV error: ${it.message}"
            }
            _isVirtLoading.value = false
        }
    }

    fun executeWinRmQuery(
        host: String,
        port: Int = 5985,
        user: String,
        pass: String,
        query: String,
        useTls: Boolean = (port == 5986)
    ) {
        val target = host.trim()
        if (target.isBlank() || query.isBlank()) return
        viewModelScope.launch {
            _isVirtLoading.value = true
            val res = virtualizationRepository.executeWinRmQuery(
                target,
                port,
                user.trim(),
                pass,
                query.trim(),
                useTls = useTls
            )
            res.onSuccess {
                _winRmResult.value = it
                _virtStatusMessage.value = if (it.isSuccess) {
                    "WinRM query completed in ${it.executionTimeMs} ms"
                } else {
                    "WinRM Error: HTTP ${it.exitCode}"
                }
            }.onFailure {
                _virtStatusMessage.value = "WinRM error: ${it.message}"
            }
            _isVirtLoading.value = false
        }
    }

    fun queryVirtualBox(host: String, port: Int = 18083, user: String = "", pass: String = "") {
        val target = host.trim()
        if (target.isBlank()) return
        viewModelScope.launch {
            _isVirtLoading.value = true
            val res = virtualizationRepository.queryVirtualBoxWebservice(target, port, user.trim(), pass)
            res.onSuccess { _virtStatusMessage.value = it }
                .onFailure { _virtStatusMessage.value = "vboxwebsrv error: ${it.message}" }
            _isVirtLoading.value = false
        }
    }

    // --- 3. Cisco UC & Collaboration State ---
    private val _phoneEndpoints = MutableStateFlow<List<PhoneEndpoint>>(emptyList())
    val phoneEndpoints: StateFlow<List<PhoneEndpoint>> = _phoneEndpoints.asStateFlow()

    private val _sipTrunks = MutableStateFlow<List<SipTrunk>>(emptyList())
    val sipTrunks: StateFlow<List<SipTrunk>> = _sipTrunks.asStateFlow()

    private val _callQualityMetrics = MutableStateFlow<List<CallQualityMetric>>(emptyList())
    val callQualityMetrics: StateFlow<List<CallQualityMetric>> = _callQualityMetrics.asStateFlow()

    private val _gatewayDsps = MutableStateFlow<List<VoiceGatewayDsp>>(emptyList())
    val gatewayDsps: StateFlow<List<VoiceGatewayDsp>> = _gatewayDsps.asStateFlow()

    private val _isUcLoading = MutableStateFlow(false)
    val isUcLoading: StateFlow<Boolean> = _isUcLoading.asStateFlow()

    private val _ucStatusMessage = MutableStateFlow<String?>(null)
    val ucStatusMessage: StateFlow<String?> = _ucStatusMessage.asStateFlow()

    fun loadDefaultUcTelemetry(host: String = "", port: Int = 8443, user: String = "", pass: String = "") {
        val target = host.trim()
        if (target.isBlank()) {
            _ucStatusMessage.value = "Enter a valid CUCM / Prime host address"
            return
        }
        viewModelScope.launch {
            _isUcLoading.value = true
            val errors = mutableListOf<String>()
            val u = user.trim()

            ciscoUcRepository.queryCucmEndpoints(target, port, u, pass)
                .onSuccess { _phoneEndpoints.value = it }
                .onFailure { errors.add("UDS: ${it.message}") }

            ciscoUcRepository.querySipTrunks(target, port, u, pass)
                .onSuccess { _sipTrunks.value = it }
                .onFailure { errors.add("AXL Trunks: ${it.message}") }

            ciscoUcRepository.queryCallQualityMos(target, port, u, pass)
                .onSuccess { _callQualityMetrics.value = it }
                .onFailure { errors.add("AST MOS: ${it.message}") }

            ciscoUcRepository.queryVoiceGatewayDsps(target, port, u, pass)
                .onSuccess { _gatewayDsps.value = it }
                .onFailure { errors.add("Perfmon DSP: ${it.message}") }

            _ucStatusMessage.value = when {
                errors.isEmpty() ->
                    "Cisco Collaboration telemetry synchronized (${_phoneEndpoints.value.size} endpoints, ${_sipTrunks.value.size} trunks)"
                errors.size < 4 ->
                    "Partial UC sync (${4 - errors.size}/4 succeeded) — Errors: ${errors.joinToString(" | ")}"
                else ->
                    "Cisco UC Sync Error: ${errors.joinToString(" | ")}"
            }
            _isUcLoading.value = false
        }
    }
}
