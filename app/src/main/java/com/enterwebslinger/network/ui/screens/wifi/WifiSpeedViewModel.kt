// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/wifi/WifiSpeedViewModel.kt ===
package com.enterwebslinger.network.ui.screens.wifi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterwebslinger.network.EnterwebSlingerApp
import com.enterwebslinger.network.data.repository.WifiSpeedRepository
import com.enterwebslinger.network.domain.model.ChannelCongestionInfo
import com.enterwebslinger.network.domain.model.SpeedTestState
import com.enterwebslinger.network.domain.model.UiState
import com.enterwebslinger.network.domain.model.WifiScanEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class WifiSpeedViewModel @JvmOverloads constructor(
    private val repository: WifiSpeedRepository = EnterwebSlingerApp.getContainer().wifiSpeedRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    val speedState: StateFlow<SpeedTestState> = repository.speedState
    val speedUiState: StateFlow<UiState<SpeedTestState>> = repository.speedUiState
    val wifiUiState: StateFlow<UiState<List<WifiScanEntry>>> = repository.wifiUiState
    val channelCongestion: StateFlow<List<ChannelCongestionInfo>> = repository.channelCongestion

    private val _selectedBandFilter = MutableStateFlow("All")
    val selectedBandFilter: StateFlow<String> = _selectedBandFilter.asStateFlow()

    val filteredWifiList: StateFlow<List<WifiScanEntry>> = combine(
        repository.wifiScanList,
        _selectedBandFilter
    ) { list, band ->
        if (band == "All") list else list.filter { it.band == band }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startTelemetry() {
        refreshWifiScan()
    }

    fun setSelectedTab(index: Int) {
        _selectedTab.value = index.coerceIn(0, 1)
    }

    fun startSpeedTest() {
        repository.startSpeedTest(viewModelScope)
    }

    fun stopSpeedTest() {
        repository.stopSpeedTest()
    }

    fun dismissSpeedError() {
        repository.dismissSpeedError()
    }

    fun setSelectedBandFilter(band: String) {
        _selectedBandFilter.value = band
    }

    fun refreshWifiScan() {
        repository.refreshWifiScan(viewModelScope)
    }

    fun cancelWifiScan() {
        repository.cancelWifiScan()
    }

    fun dismissWifiError() {
        repository.dismissWifiError()
    }
}
