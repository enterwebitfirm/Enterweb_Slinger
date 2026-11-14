// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/protoolkit/ProToolkitViewModel.kt ===
package com.enterwebslinger.network.ui.screens.protoolkit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterwebslinger.network.EnterwebSlingerApp
import com.enterwebslinger.network.data.local.datastore.UserPreferencesDataStore
import com.enterwebslinger.network.data.repository.AaaDiagnosticsRepository
import com.enterwebslinger.network.data.repository.AdvancedProtocolRepository
import com.enterwebslinger.network.data.repository.BatchAutomationRepository
import com.enterwebslinger.network.data.repository.MtuDiscoveryRepository
import com.enterwebslinger.network.domain.model.AuditMonitorConfig
import com.enterwebslinger.network.domain.model.AuditRunSummary
import com.enterwebslinger.network.domain.model.DnsBenchmarkEntry
import com.enterwebslinger.network.domain.model.HappyEyeballsResult
import com.enterwebslinger.network.domain.model.MtuDiscoveryResult
import com.enterwebslinger.network.domain.model.MtuProbeStep
import com.enterwebslinger.network.domain.model.RadiusTestResult
import com.enterwebslinger.network.domain.model.SshBatchResult
import com.enterwebslinger.network.domain.model.SshTargetHost
import com.enterwebslinger.network.domain.model.TacacsProbeResult
import com.enterwebslinger.network.domain.model.TrafficRateSample
import com.enterwebslinger.network.service.NetworkAuditWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProToolkitViewModel @JvmOverloads constructor(
    private val mtuRepository: MtuDiscoveryRepository = EnterwebSlingerApp.getContainer().mtuDiscoveryRepository,
    private val aaaRepository: AaaDiagnosticsRepository = EnterwebSlingerApp.getContainer().aaaDiagnosticsRepository,
    private val advancedProtocolRepository: AdvancedProtocolRepository = EnterwebSlingerApp.getContainer().advancedProtocolRepository,
    private val batchAutomationRepository: BatchAutomationRepository = EnterwebSlingerApp.getContainer().batchAutomationRepository,
    private val userPreferencesDataStore: UserPreferencesDataStore = EnterwebSlingerApp.getContainer().userPreferencesDataStore
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    // --- 0. PMTUD State ---
    private val _mtuResult = MutableStateFlow<MtuDiscoveryResult?>(null)
    val mtuResult: StateFlow<MtuDiscoveryResult?> = _mtuResult.asStateFlow()

    private val _mtuLiveSteps = MutableStateFlow<List<MtuProbeStep>>(emptyList())
    val mtuLiveSteps: StateFlow<List<MtuProbeStep>> = _mtuLiveSteps.asStateFlow()

    private val _isRunningMtu = MutableStateFlow(false)
    val isRunningMtu: StateFlow<Boolean> = _isRunningMtu.asStateFlow()
    private var mtuJob: Job? = null

    // --- 1. DNS & ALPN / Happy Eyeballs State ---
    private val _dnsBenchmarks = MutableStateFlow<List<DnsBenchmarkEntry>>(emptyList())
    val dnsBenchmarks: StateFlow<List<DnsBenchmarkEntry>> = _dnsBenchmarks.asStateFlow()

    private val _isRunningDns = MutableStateFlow(false)
    val isRunningDns: StateFlow<Boolean> = _isRunningDns.asStateFlow()

    private val _happyEyeballsResult = MutableStateFlow<HappyEyeballsResult?>(null)
    val happyEyeballsResult: StateFlow<HappyEyeballsResult?> = _happyEyeballsResult.asStateFlow()

    private val _isRunningAlpn = MutableStateFlow(false)
    val isRunningAlpn: StateFlow<Boolean> = _isRunningAlpn.asStateFlow()

    // --- 2. RADIUS & TACACS+ State ---
    private val _radiusResult = MutableStateFlow<RadiusTestResult?>(null)
    val radiusResult: StateFlow<RadiusTestResult?> = _radiusResult.asStateFlow()

    private val _tacacsResult = MutableStateFlow<TacacsProbeResult?>(null)
    val tacacsResult: StateFlow<TacacsProbeResult?> = _tacacsResult.asStateFlow()

    private val _isTestingAaa = MutableStateFlow(false)
    val isTestingAaa: StateFlow<Boolean> = _isTestingAaa.asStateFlow()

    // --- 3. SSH Batch & TrafficStats State ---
    val savedSshTargets: StateFlow<List<SshTargetHost>> = batchAutomationRepository.savedSshTargetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _customSshTargets = MutableStateFlow<List<SshTargetHost>>(emptyList())
    val customSshTargets: StateFlow<List<SshTargetHost>> = _customSshTargets.asStateFlow()

    private val _sshBatchResults = MutableStateFlow<List<SshBatchResult>>(emptyList())
    val sshBatchResults: StateFlow<List<SshBatchResult>> = _sshBatchResults.asStateFlow()

    private val _isRunningBatchSsh = MutableStateFlow(false)
    val isRunningBatchSsh: StateFlow<Boolean> = _isRunningBatchSsh.asStateFlow()
    private var batchSshJob: Job? = null

    val trafficSamples: StateFlow<List<TrafficRateSample>> = batchAutomationRepository.trafficSamples
    val latestTrafficSample: StateFlow<TrafficRateSample> = batchAutomationRepository.latestTrafficSample

    // --- 4. Background Audit Worker State ---
    val auditConfig: StateFlow<AuditMonitorConfig> = userPreferencesDataStore.auditMonitorConfigFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuditMonitorConfig())

    val lastAuditSummary: StateFlow<AuditRunSummary> = userPreferencesDataStore.lastAuditSummaryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuditRunSummary())

    fun setSelectedTab(tabIndex: Int) {
        _selectedTab.value = tabIndex.coerceIn(0, 4)
        if (tabIndex == 3) {
            batchAutomationRepository.startTrafficStatsMonitor()
        }
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    // --- PMTUD ---
    fun runPathMtuDiscovery(targetHost: String) {
        val host = targetHost.trim()
        if (host.isBlank()) {
            _statusBanner.value = "Enter a target host or IP for Path MTU Discovery."
            return
        }
        mtuJob?.cancel()
        mtuJob = viewModelScope.launch {
            _isRunningMtu.value = true
            _mtuLiveSteps.value = emptyList()
            _mtuResult.value = null
            try {
                val res = mtuRepository.discoverPathMtu(host) { step ->
                    _mtuLiveSteps.update { it + step }
                }
                _mtuResult.value = res
                _statusBanner.value = if (res.errorMessage != null) {
                    res.errorMessage
                } else {
                    "Discovered Path MTU to ${res.resolvedIp}: ${res.pathMtuBytes} bytes (${res.maxPayloadBytes} B + ${res.ipIcmpOverheadBytes} B overhead)."
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isRunningMtu.value = false
            }
        }
    }

    fun stopPathMtuDiscovery() {
        mtuJob?.cancel()
        mtuJob = null
        _isRunningMtu.value = false
    }

    // --- DNSSEC & Happy Eyeballs ---
    fun runDnsBenchmark(domain: String) {
        viewModelScope.launch {
            _isRunningDns.value = true
            try {
                val list = advancedProtocolRepository.runDnsAndDnssecBenchmark(domain)
                _dnsBenchmarks.value = list
                val validCount = list.count { it.isSuccess }
                _statusBanner.value = "DNS/DNSSEC benchmark complete: $validCount/${list.size} resolvers responded."
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isRunningDns.value = false
            }
        }
    }

    fun runHappyEyeballsTest(host: String, port: Int = 443) {
        viewModelScope.launch {
            _isRunningAlpn.value = true
            try {
                val res = advancedProtocolRepository.testHappyEyeballsAndAlpn(host, port)
                _happyEyeballsResult.value = res
                _statusBanner.value = res.errorMessage
                    ?: "ALPN: ${res.alpnProtocol} | Winner: ${res.winnerFamily}"
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isRunningAlpn.value = false
            }
        }
    }

    // --- RADIUS & TACACS+ ---
    fun testRadius(serverIp: String, port: Int, secret: String, user: String, pass: String) {
        viewModelScope.launch {
            _isTestingAaa.value = true
            try {
                val res = aaaRepository.testRadiusAuth(serverIp, port, secret, user, pass)
                _radiusResult.value = res
                _statusBanner.value = res.errorMessage ?: "${res.responseCode.label} in ${res.rttMs} ms"
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isTestingAaa.value = false
            }
        }
    }

    fun testTacacs(serverIp: String, port: Int = 49) {
        viewModelScope.launch {
            _isTestingAaa.value = true
            try {
                val res = aaaRepository.probeTacacsPlus(serverIp, port)
                _tacacsResult.value = res
                _statusBanner.value = res.statusMessage
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isTestingAaa.value = false
            }
        }
    }

    // --- SSH Batch & TrafficStats ---
    fun addCustomSshTarget(name: String, host: String, port: Int, username: String, password: String) {
        if (host.isBlank()) return
        val newTarget = SshTargetHost(
            id = "custom_${System.currentTimeMillis()}",
            name = name.ifBlank { host.trim() },
            host = host.trim(),
            port = port.coerceIn(1, 65535),
            username = username.trim().ifBlank { "admin" },
            password = password,
            vendorType = "SSH Target",
            isSelected = true
        )
        _customSshTargets.update { it + newTarget }
    }

    fun removeCustomSshTarget(id: String) {
        _customSshTargets.update { list -> list.filterNot { it.id == id } }
    }

    fun runBatchSsh(command: String, baselineOutput: String = "") {
        val allTargets = savedSshTargets.value + _customSshTargets.value
        if (allTargets.isEmpty()) {
            _statusBanner.value = "Add at least 1 SSH target host or save an SSH profile in Enterprise NOC."
            return
        }
        batchSshJob?.cancel()
        batchSshJob = viewModelScope.launch {
            _isRunningBatchSsh.value = true
            try {
                val results = batchAutomationRepository.runBatchSshCommand(allTargets, command, baselineOutput)
                _sshBatchResults.value = results
                val ok = results.count { it.isSuccess }
                _statusBanner.value = "Batch SSH complete: $ok/${results.size} hosts succeeded."
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isRunningBatchSsh.value = false
            }
        }
    }

    fun stopBatchSsh() {
        batchSshJob?.cancel()
        batchSshJob = null
        _isRunningBatchSsh.value = false
    }

    fun startTrafficStats() = batchAutomationRepository.startTrafficStatsMonitor()
    fun stopTrafficStats() = batchAutomationRepository.stopTrafficStatsMonitor()

    // --- WorkManager Audit Scheduler ---
    fun updateAuditSchedule(
        context: Context,
        enabled: Boolean,
        intervalMinutes: Long,
        trustedSsid: String,
        whitelistCsv: String,
        watchedHostsCsv: String
    ) {
        viewModelScope.launch {
            val cfg = AuditMonitorConfig(
                enabled = enabled,
                intervalMinutes = intervalMinutes.coerceIn(15L, 360L),
                trustedSsid = trustedSsid.trim(),
                trustedMacWhitelist = whitelistCsv.split(",").map { it.trim().uppercase() }.filter { it.isNotBlank() },
                watchedHosts = watchedHostsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
            )
            userPreferencesDataStore.saveAuditMonitorConfig(cfg)
            if (enabled) {
                NetworkAuditWorker.schedulePeriodicAudit(context, cfg.intervalMinutes)
                _statusBanner.value = "Scheduled WorkManager audit every ${cfg.intervalMinutes} min."
            } else {
                NetworkAuditWorker.cancelAudit(context)
                _statusBanner.value = "Cancelled background network audit schedule."
            }
        }
    }

    fun triggerImmediateAudit(context: Context) {
        NetworkAuditWorker.runOneTimeAuditNow(context)
        _statusBanner.value = "Queued immediate background ARP/gateway/watched-host audit via WorkManager."
    }

    override fun onCleared() {
        stopPathMtuDiscovery()
        stopBatchSsh()
        stopTrafficStats()
        super.onCleared()
    }
}
