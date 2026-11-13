// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/dashboard/DashboardViewModel.kt ===
package com.enterwebslinger.network.ui.screens.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterwebslinger.network.EnterwebSlingerApp
import com.enterwebslinger.network.data.repository.NetworkInfoRepository
import com.enterwebslinger.network.domain.model.NetworkTopology
import com.enterwebslinger.network.domain.model.RssiDataPoint
import com.enterwebslinger.network.domain.model.UiState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DashboardViewModel @JvmOverloads constructor(
    private val repository: NetworkInfoRepository = EnterwebSlingerApp.getContainer().networkInfoRepository
) : ViewModel() {

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("DashboardViewModel", "Unhandled coroutine error", throwable)
    }

    val topology: StateFlow<NetworkTopology> = repository.topology
    val rssiHistory: StateFlow<List<RssiDataPoint>> = repository.rssiHistory
    val uiState: StateFlow<UiState<NetworkTopology>> = repository.uiState
    val isRefreshing: StateFlow<Boolean> = repository.isRefreshing

    fun startTelemetry() {
        repository.startTelemetry()
    }

    fun stopTelemetry() {
        repository.stopTelemetry()
    }

    fun refreshPublicIp() {
        viewModelScope.launch(exceptionHandler) {
            repository.fetchPublicIpAsync()
        }
    }

    fun manualRefresh() {
        viewModelScope.launch(exceptionHandler) {
            repository.manualRefresh()
        }
    }

    fun cancelRefresh() {
        repository.cancelRefresh()
    }

    fun dismissError() {
        repository.dismissError()
    }
}
