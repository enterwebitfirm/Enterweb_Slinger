// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/vpn/VpnViewModel.kt ===
package com.enterwebslinger.network.ui.screens.vpn

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterwebslinger.network.EnterwebSlingerApp
import com.enterwebslinger.network.data.repository.VpnRepository
import com.enterwebslinger.network.domain.model.DnsLeakResult
import com.enterwebslinger.network.domain.model.VpnProfile
import com.enterwebslinger.network.domain.model.VpnTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VpnViewModel @JvmOverloads constructor(
    private val vpnRepository: VpnRepository = EnterwebSlingerApp.getContainer().vpnRepository
) : ViewModel() {

    val profiles: StateFlow<List<VpnProfile>> = vpnRepository.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val telemetry: StateFlow<VpnTelemetry> = vpnRepository.telemetry
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VpnTelemetry())

    private val _selectedProfile = MutableStateFlow<VpnProfile?>(null)
    val selectedProfile: StateFlow<VpnProfile?> = _selectedProfile.asStateFlow()

    private val _diagnosticStatus = MutableStateFlow<String?>(null)
    val diagnosticStatus: StateFlow<String?> = _diagnosticStatus.asStateFlow()

    private val _dnsLeakResult = MutableStateFlow<DnsLeakResult?>(null)
    val dnsLeakResult: StateFlow<DnsLeakResult?> = _dnsLeakResult.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    init {
        viewModelScope.launch {
            profiles.collect { list ->
                if (_selectedProfile.value == null && list.isNotEmpty()) {
                    _selectedProfile.value = list.firstOrNull { it.isDefault } ?: list.first()
                }
            }
        }
    }

    fun selectProfile(profile: VpnProfile) {
        _selectedProfile.value = profile
    }

    fun startTunnel(profile: VpnProfile? = null) {
        val target = profile ?: _selectedProfile.value
        if (target != null) {
            vpnRepository.startTunnel(target)
            _diagnosticStatus.value = "Starting tunnel to ${target.serverAddress}:${target.port} (${target.protocol.name})..."
        } else {
            _diagnosticStatus.value = "Error: No VPN profile selected"
        }
    }

    fun stopTunnel() {
        vpnRepository.stopTunnel()
        _diagnosticStatus.value = "VPN tunnel disconnected."
    }

    fun onVpnPermissionDenied() {
        _diagnosticStatus.value = "VPN system permission denied by user."
    }

    fun createIkev2Profile(
        name: String,
        serverAddress: String,
        remoteIdentifier: String,
        preSharedKey: String,
        username: String,
        password: String
    ): VpnProfile {
        val profile = vpnRepository.addIkev2Profile(
            name = name,
            serverAddress = serverAddress,
            remoteIdentifier = remoteIdentifier,
            preSharedKey = preSharedKey,
            username = username,
            password = password
        )
        _selectedProfile.value = profile
        _diagnosticStatus.value = "Created IKEv2 profile: ${profile.name}"
        return profile
    }

    fun provisionIkev2Profile(profile: VpnProfile): Result<Intent?> {
        val result = vpnRepository.provisionIkev2PlatformProfile(profile)
        result.onFailure { err ->
            _diagnosticStatus.value = "IKEv2 provision error: ${err.message}"
        }
        return result
    }

    fun startProvisionedIkev2() {
        val result = vpnRepository.startIkev2PlatformProfile()
        result.fold(
            onSuccess = { _diagnosticStatus.value = "Started Android VpnManager IKEv2 profile." },
            onFailure = { _diagnosticStatus.value = "IKEv2 start error: ${it.message}" }
        )
    }

    fun importWireGuardConf(conf: String, name: String) {
        if (conf.isBlank()) return
        try {
            val profile = vpnRepository.parseWireGuardConf(conf, name.ifBlank { "WireGuard Conf" })
            _selectedProfile.value = profile
            _diagnosticStatus.value = "Successfully parsed and imported WireGuard profile: ${profile.name}"
        } catch (e: Exception) {
            _diagnosticStatus.value = "WireGuard import error: ${e.message}"
        }
    }

    fun importOpenVpnConf(ovpn: String, name: String) {
        if (ovpn.isBlank()) return
        try {
            val profile = vpnRepository.parseOpenVpnConf(ovpn, name.ifBlank { "OpenVPN Conf" })
            _selectedProfile.value = profile
            _diagnosticStatus.value = "Successfully parsed and imported OpenVPN profile: ${profile.name}"
        } catch (e: Exception) {
            _diagnosticStatus.value = "OpenVPN import error: ${e.message}"
        }
    }

    fun testSstp(host: String, port: Int = 443) {
        val target = host.trim()
        if (target.isBlank()) return
        viewModelScope.launch {
            _isTesting.value = true
            _diagnosticStatus.value = "Testing SSTP HTTPS handshake to $target:$port..."
            val result = vpnRepository.testSstpHandshakeReadiness(target, port)
            _isTesting.value = false
            result.fold(
                onSuccess = { _diagnosticStatus.value = it },
                onFailure = { _diagnosticStatus.value = "SSTP Handshake Failed: ${it.message}" }
            )
        }
    }

    fun testIpsec(host: String, port: Int = 500) {
        val target = host.trim()
        if (target.isBlank()) return
        viewModelScope.launch {
            _isTesting.value = true
            _diagnosticStatus.value = "Dispatching IKEv2 SA_INIT probe to $target:$port..."
            val result = vpnRepository.testIpsecIkeReadiness(target, port)
            _isTesting.value = false
            result.fold(
                onSuccess = { _diagnosticStatus.value = it },
                onFailure = { _diagnosticStatus.value = "IKEv2 Probe Failed: ${it.message}" }
            )
        }
    }

    fun runDnsLeakTest() {
        viewModelScope.launch {
            _isTesting.value = true
            _diagnosticStatus.value = "Running deep DNS leak query..."
            val result = vpnRepository.runDnsLeakTest()
            _dnsLeakResult.value = result
            _isTesting.value = false
            _diagnosticStatus.value = "DNS Leak Test Complete: ${result.summary}"
        }
    }

    fun clearDiagnosticStatus() {
        _diagnosticStatus.value = null
    }
}
