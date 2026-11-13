// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/security/SecurityViewModel.kt ===
package com.enterwebslinger.network.ui.screens.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterwebslinger.network.EnterwebSlingerApp
import com.enterwebslinger.network.core.util.NetworkUtils
import com.enterwebslinger.network.data.repository.NetworkInfoRepository
import com.enterwebslinger.network.data.repository.SecurityRepository
import com.enterwebslinger.network.domain.model.HostWatcherTarget
import com.enterwebslinger.network.domain.model.PortScanResult
import com.enterwebslinger.network.domain.model.SavedWolProfile
import com.enterwebslinger.network.domain.model.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class SecurityViewModel @JvmOverloads constructor(
    private val securityRepository: SecurityRepository = EnterwebSlingerApp.getContainer().securityRepository,
    networkInfoRepository: NetworkInfoRepository = EnterwebSlingerApp.getContainer().networkInfoRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _portTarget = MutableStateFlow("")
    val portTarget: StateFlow<String> = _portTarget.asStateFlow()

    private val _selectedPreset = MutableStateFlow("Common Top 100")
    val selectedPreset: StateFlow<String> = _selectedPreset.asStateFlow()

    private val _customPortStart = MutableStateFlow("1")
    val customPortStart: StateFlow<String> = _customPortStart.asStateFlow()

    private val _customPortEnd = MutableStateFlow("1024")
    val customPortEnd: StateFlow<String> = _customPortEnd.asStateFlow()

    private val _portValidationError = MutableStateFlow<String?>(null)

    val isScanningPorts: StateFlow<Boolean> = securityRepository.isScanningPorts
    val portScanProgress: StateFlow<Float> = securityRepository.portScanProgress
    val portResults: StateFlow<List<PortScanResult>> = securityRepository.portResults

    val portScanUiState: StateFlow<UiState<List<PortScanResult>>> = combine(
        securityRepository.portScanUiState,
        _portValidationError
    ) { repoState, validationErr ->
        if (validationErr != null) UiState.Error(validationErr) else repoState
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Idle)

    private val _wolMacAddress = MutableStateFlow("")
    val wolMacAddress: StateFlow<String> = _wolMacAddress.asStateFlow()

    private val _wolDeviceName = MutableStateFlow("")
    val wolDeviceName: StateFlow<String> = _wolDeviceName.asStateFlow()

    private val _wolBroadcastIp = MutableStateFlow("")
    val wolBroadcastIp: StateFlow<String> = _wolBroadcastIp.asStateFlow()

    private val _wolStatus = MutableStateFlow<String?>(null)
    val wolStatus: StateFlow<String?> = _wolStatus.asStateFlow()

    val wolUiState: StateFlow<UiState<String>> = securityRepository.wolUiState

    val savedWolProfiles: StateFlow<List<SavedWolProfile>> =
        securityRepository.savedWolProfilesFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val watchedHosts: StateFlow<List<HostWatcherTarget>> = securityRepository.watchedHosts
    val isWatcherRunning: StateFlow<Boolean> = securityRepository.isWatcherRunning
    val watcherUiState: StateFlow<UiState<List<HostWatcherTarget>>> = securityRepository.watcherUiState

    private val _newHostAddress = MutableStateFlow("")
    val newHostAddress: StateFlow<String> = _newHostAddress.asStateFlow()

    private val _newHostLabel = MutableStateFlow("")
    val newHostLabel: StateFlow<String> = _newHostLabel.asStateFlow()

    init {
        val gw = networkInfoRepository.topology.value.defaultGateway
        if (gw != "Unavailable" && gw.isNotBlank() && gw != "0.0.0.0") {
            _portTarget.value = gw
        }
    }

    fun setSelectedTab(index: Int) {
        val clamped = index.coerceIn(0, 2)
        _selectedTab.value = clamped
        if (clamped == 2 && watchedHosts.value.isNotEmpty()) {
            securityRepository.startWatcherLoop()
        }
    }

    fun setPortTarget(target: String) {
        _portTarget.value = target
        _portValidationError.value = null
    }

    fun setSelectedPreset(preset: String) {
        _selectedPreset.value = preset
        _portValidationError.value = null
    }

    fun setCustomPortRange(start: String, end: String) {
        _customPortStart.value = start.filter { it.isDigit() }.take(5)
        _customPortEnd.value = end.filter { it.isDigit() }.take(5)
        _portValidationError.value = null
    }

    fun startPortScan() {
        val target = _portTarget.value.trim()
        if (target.isBlank()) {
            _portValidationError.value = "Enter a target IP address or hostname to scan."
            return
        }
        val ports = when (_selectedPreset.value) {
            "Common Top 100" -> NetworkUtils.COMMON_TOP_100_PORTS
            "Web & Admin" -> NetworkUtils.WEB_ADMIN_PORTS
            else -> {
                val s = _customPortStart.value.toIntOrNull()
                val e = _customPortEnd.value.toIntOrNull()
                if (s == null || e == null || s !in 1..65535 || e !in 1..65535) {
                    _portValidationError.value = "Custom ports must be valid numbers between 1 and 65535."
                    return
                }
                val low = minOf(s, e)
                val high = maxOf(s, e)
                if (high - low > 10000) {
                    _portValidationError.value = "Custom range cannot exceed 10,000 ports per sweep (requested ${high - low + 1})."
                    return
                }
                (low..high).toList()
            }
        }
        _portValidationError.value = null
        securityRepository.startPortScan(target, ports)
    }

    fun stopPortScan() {
        securityRepository.stopPortScan()
    }

    fun dismissPortScanError() {
        _portValidationError.value = null
        securityRepository.dismissPortScanError()
    }

    fun setWolMacAddress(mac: String) {
        _wolMacAddress.value = mac
    }

    fun setWolDeviceName(name: String) {
        _wolDeviceName.value = name
    }

    fun setWolBroadcastIp(ip: String) {
        _wolBroadcastIp.value = ip
    }

    fun selectWolProfile(profile: SavedWolProfile) {
        _wolMacAddress.value = profile.macAddress
        _wolDeviceName.value = profile.name
        if (profile.ipAddress.isNotBlank()) {
            _wolBroadcastIp.value = profile.ipAddress
        }
    }

    fun saveCurrentWolProfile() {
        val mac = _wolMacAddress.value.trim().uppercase()
        val cleanHex = mac.replace(":", "").replace("-", "")
        if (cleanHex.length != 12 || !cleanHex.all { it in '0'..'9' || it in 'A'..'F' }) {
            _wolStatus.value = "Failed: Enter a valid 12-hex MAC address before saving (e.g. AA:BB:CC:DD:EE:FF)"
            return
        }
        val name = _wolDeviceName.value.trim().ifBlank { "Host $mac" }
        val broadcastOverride = _wolBroadcastIp.value.trim()
        viewModelScope.launch {
            try {
                securityRepository.saveWolProfile(
                    SavedWolProfile(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        macAddress = mac,
                        ipAddress = broadcastOverride
                    )
                )
                _wolStatus.value = "Saved profile '$name' to persistent database"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _wolStatus.value = "Failed to save profile: ${e.message}"
            }
        }
    }

    fun deleteWolProfile(id: String) {
        viewModelScope.launch {
            try {
                securityRepository.deleteWolProfile(id)
                _wolStatus.value = "Deleted saved WoL profile"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _wolStatus.value = "Failed to delete profile: ${e.message}"
            }
        }
    }

    fun triggerWakeOnLan() {
        val mac = _wolMacAddress.value.trim()
        if (mac.isBlank()) {
            _wolStatus.value = "Failed: Please enter a valid MAC address (e.g. AA:BB:CC:DD:EE:FF)"
            return
        }
        viewModelScope.launch {
            val result = securityRepository.sendWakeOnLan(
                macAddress = mac,
                customBroadcastIp = _wolBroadcastIp.value.trim().ifBlank { null }
            )
            result.onSuccess { msg ->
                _wolStatus.value = msg
            }.onFailure { err ->
                _wolStatus.value = "WoL Failed: ${err.message}"
            }
        }
    }

    fun dismissWolStatus() {
        _wolStatus.value = null
        securityRepository.dismissWolStatus()
    }

    fun setNewHostAddress(host: String) {
        _newHostAddress.value = host
    }

    fun setNewHostLabel(label: String) {
        _newHostLabel.value = label
    }

    fun addWatchedHost() {
        if (_newHostAddress.value.isNotBlank()) {
            securityRepository.addWatcherHost(
                host = _newHostAddress.value.trim(),
                label = _newHostLabel.value.trim()
            )
            _newHostAddress.value = ""
            _newHostLabel.value = ""
        }
    }

    fun removeWatchedHost(id: String) {
        securityRepository.removeWatcherHost(id)
    }

    fun startWatcher() {
        securityRepository.startWatcherLoop()
    }

    fun stopWatcher() {
        securityRepository.stopWatcherLoop()
    }

    fun dismissWatcherError() {
        securityRepository.dismissWatcherError()
    }
}
