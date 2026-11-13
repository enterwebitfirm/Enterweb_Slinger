// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/lanscanner/LanScannerViewModel.kt ===
package com.enterwebslinger.network.ui.screens.lanscanner

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterwebslinger.network.EnterwebSlingerApp
import com.enterwebslinger.network.core.util.ReportExporter
import com.enterwebslinger.network.data.local.datastore.UserPreferencesDataStore
import com.enterwebslinger.network.data.repository.LanScannerRepository
import com.enterwebslinger.network.data.repository.NetworkInfoRepository
import com.enterwebslinger.network.domain.model.DiscoveredDevice
import com.enterwebslinger.network.domain.model.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LanScannerViewModel @JvmOverloads constructor(
    private val repository: LanScannerRepository = EnterwebSlingerApp.getContainer().lanScannerRepository,
    private val networkInfoRepository: NetworkInfoRepository = EnterwebSlingerApp.getContainer().networkInfoRepository,
    private val userPreferencesDataStore: UserPreferencesDataStore? = EnterwebSlingerApp.getContainer().userPreferencesDataStore
) : ViewModel() {

    val isScanning: StateFlow<Boolean> = repository.isScanning
    val scanProgress: StateFlow<Float> = repository.scanProgress
    val statusMessage: StateFlow<String> = repository.statusMessage
    val scanUiState: StateFlow<UiState<List<DiscoveredDevice>>> = repository.scanUiState

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedDevice = MutableStateFlow<DiscoveredDevice?>(null)
    val selectedDevice: StateFlow<DiscoveredDevice?> = _selectedDevice.asStateFlow()

    private val _nicknameFeedback = MutableStateFlow<String?>(null)
    val nicknameFeedback: StateFlow<String?> = _nicknameFeedback.asStateFlow()

    val filteredDevices: StateFlow<List<DiscoveredDevice>> = combine(
        repository.devices,
        _searchQuery
    ) { devices, query ->
        if (query.isBlank()) {
            devices
        } else {
            val q = query.lowercase().trim()
            devices.filter {
                it.ip.contains(q) ||
                    it.hostname.lowercase().contains(q) ||
                    it.vendor.lowercase().contains(q) ||
                    it.mac.lowercase().contains(q) ||
                    (it.customLabel?.lowercase()?.contains(q) == true) ||
                    it.services.any { s -> s.lowercase().contains(q) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startScan() {
        repository.startScan(viewModelScope)
    }

    fun stopScan() {
        repository.stopScan()
    }

    fun dismissError() {
        repository.dismissError()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectDevice(device: DiscoveredDevice?) {
        _nicknameFeedback.value = null
        _selectedDevice.value = device
    }

    fun saveDeviceNickname(identifier: String, nickname: String, type: String) {
        viewModelScope.launch {
            try {
                val cleanName = nickname.trim()
                userPreferencesDataStore?.setDeviceNickname(identifier, cleanName, type)
                val current = _selectedDevice.value
                if (current != null && (current.ip == identifier || current.mac == identifier)) {
                    _selectedDevice.value = current.copy(
                        customLabel = cleanName.ifBlank { null },
                        deviceType = type
                    )
                }
                _nicknameFeedback.value = if (cleanName.isBlank()) "Nickname cleared" else "Saved nickname '$cleanName'"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _nicknameFeedback.value = "Failed to save nickname: ${e.message}"
            }
        }
    }

    fun dismissNicknameFeedback() {
        _nicknameFeedback.value = null
    }

    fun exportReport(context: Context, formatAsJson: Boolean = false) {
        val currentDevices = filteredDevices.value.ifEmpty { repository.devices.value }
        val topology = networkInfoRepository.topology.value
        ReportExporter.shareLanScanReport(context, currentDevices, topology, formatAsJson)
    }
}
