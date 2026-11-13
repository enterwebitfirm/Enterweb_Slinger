// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/NetworkInfoRepository.kt ===
package com.enterwebslinger.network.data.repository

import android.content.Context
import com.enterwebslinger.network.domain.model.NetworkTopologyState
import com.enterwebslinger.network.domain.model.RssiDataPoint
import com.enterwebslinger.network.domain.model.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class NetworkInfoRepository(
    context: Context,
    scope: CoroutineScope
) {
    val delegate = NetworkTopologyRepository(context, scope)

    val topology: StateFlow<NetworkTopologyState> = delegate.topology
    val rssiHistory: StateFlow<List<RssiDataPoint>> = delegate.rssiHistory
    val uiState: StateFlow<UiState<NetworkTopologyState>> = delegate.uiState
    val isRefreshing: StateFlow<Boolean> = delegate.isRefreshing
    val networkCallbackFlow: Flow<NetworkTopologyState> = delegate.networkCallbackFlow

    fun startTelemetry() {
        delegate.startTelemetry()
    }

    fun stopTelemetry() {
        delegate.stopTelemetry()
    }

    fun fetchPublicIpAsync() {
        delegate.refreshPublicIp()
    }

    fun manualRefresh() {
        delegate.manualRefresh()
    }

    fun cancelRefresh() {
        delegate.cancelRefresh()
    }

    fun dismissError() {
        delegate.dismissError()
    }
}
