// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/vpn/VpnViewModel.kt ===
package com.enterwebslinger.network.ui.screens.vpn

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterwebslinger.network.EnterwebSlingerApp
import com.enterwebslinger.network.data.protocol.PcapCaptureEngine
import com.enterwebslinger.network.data.repository.EnterpriseVpnRepository
import com.enterwebslinger.network.data.repository.ImportedPkcs12Bundle
import com.enterwebslinger.network.data.repository.VpnRepository
import com.enterwebslinger.network.domain.model.AllowedIpsCalculation
import com.enterwebslinger.network.domain.model.CapturedPacketRecord
import com.enterwebslinger.network.domain.model.DnsLeakResult
import com.enterwebslinger.network.domain.model.EnterpriseSslVpnVendor
import com.enterwebslinger.network.domain.model.IkeProbeResult
import com.enterwebslinger.network.domain.model.InstalledAppEntry
import com.enterwebslinger.network.domain.model.PcapCaptureStats
import com.enterwebslinger.network.domain.model.SslVpnProbeResult
import com.enterwebslinger.network.domain.model.TunnelEncapsulationType
import com.enterwebslinger.network.domain.model.TunnelMssCalculation
import com.enterwebslinger.network.domain.model.VpnAuditReport
import com.enterwebslinger.network.domain.model.VpnProfile
import com.enterwebslinger.network.domain.model.VpnProtocol
import com.enterwebslinger.network.domain.model.VpnTelemetry
import com.enterwebslinger.network.domain.model.WireGuardConfig
import com.enterwebslinger.network.domain.model.WireGuardKeyPair
import com.enterwebslinger.network.service.OmniVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class VpnViewModel @JvmOverloads constructor(
    private val vpnRepository: VpnRepository = EnterwebSlingerApp.getContainer().vpnRepository,
    private val pcapCaptureEngine: PcapCaptureEngine = EnterwebSlingerApp.getContainer().pcapCaptureEngine,
    private val enterpriseVpnRepository: EnterpriseVpnRepository = EnterwebSlingerApp.getContainer().enterpriseVpnRepository
) : ViewModel() {

    val profiles: StateFlow<List<VpnProfile>> = vpnRepository.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val telemetry: StateFlow<VpnTelemetry> = vpnRepository.telemetry
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VpnTelemetry())

    val pcapPackets: StateFlow<List<CapturedPacketRecord>> = pcapCaptureEngine.packets
    val pcapStats: StateFlow<PcapCaptureStats> = pcapCaptureEngine.stats

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _selectedProfile = MutableStateFlow<VpnProfile?>(null)
    val selectedProfile: StateFlow<VpnProfile?> = _selectedProfile.asStateFlow()

    private val _diagnosticStatus = MutableStateFlow<String?>(null)
    val diagnosticStatus: StateFlow<String?> = _diagnosticStatus.asStateFlow()

    private val _dnsLeakResult = MutableStateFlow<DnsLeakResult?>(null)
    val dnsLeakResult: StateFlow<DnsLeakResult?> = _dnsLeakResult.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    // Enterprise VPN & Diagnostics State
    private val _generatedKeyPair = MutableStateFlow<WireGuardKeyPair?>(null)
    val generatedKeyPair: StateFlow<WireGuardKeyPair?> = _generatedKeyPair.asStateFlow()

    private val _allowedIpsCalc = MutableStateFlow<AllowedIpsCalculation?>(null)
    val allowedIpsCalc: StateFlow<AllowedIpsCalculation?> = _allowedIpsCalc.asStateFlow()

    private val _qrPreviewBitmap = MutableStateFlow<Bitmap?>(null)
    val qrPreviewBitmap: StateFlow<Bitmap?> = _qrPreviewBitmap.asStateFlow()

    private val _qrPreviewText = MutableStateFlow<String>("")
    val qrPreviewText: StateFlow<String> = _qrPreviewText.asStateFlow()

    private val _importedPkcs12 = MutableStateFlow<ImportedPkcs12Bundle?>(null)
    val importedPkcs12: StateFlow<ImportedPkcs12Bundle?> = _importedPkcs12.asStateFlow()

    private val _sslProbeResult = MutableStateFlow<SslVpnProbeResult?>(null)
    val sslProbeResult: StateFlow<SslVpnProbeResult?> = _sslProbeResult.asStateFlow()

    private val _ikeProbeResult = MutableStateFlow<IkeProbeResult?>(null)
    val ikeProbeResult: StateFlow<IkeProbeResult?> = _ikeProbeResult.asStateFlow()

    private val _tunnelMssCalc = MutableStateFlow<TunnelMssCalculation?>(
        enterpriseVpnRepository.calculateTunnelOverheadAndMss(TunnelEncapsulationType.IPSEC_ESP_TUNNEL_GCM)
    )
    val tunnelMssCalc: StateFlow<TunnelMssCalculation?> = _tunnelMssCalc.asStateFlow()

    private val _vpnAuditReport = MutableStateFlow<VpnAuditReport?>(null)
    val vpnAuditReport: StateFlow<VpnAuditReport?> = _vpnAuditReport.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppEntry>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppEntry>> = _installedApps.asStateFlow()

    init {
        viewModelScope.launch {
            profiles.collect { list ->
                val cur = _selectedProfile.value
                if (cur == null && list.isNotEmpty()) {
                    _selectedProfile.value = list.firstOrNull { it.isDefault } ?: list.first()
                } else if (cur != null) {
                    val refreshed = list.firstOrNull { it.id == cur.id }
                    if (refreshed != null) _selectedProfile.value = refreshed
                }
            }
        }
    }

    fun setSelectedTab(tabIndex: Int) {
        _selectedTab.value = tabIndex.coerceIn(0, 4)
    }

    fun startPcapCapture(context: Context) {
        OmniVpnService.startLocalPcapCapture(context)
        _diagnosticStatus.value = "Local loopback PCAP capture started (writes LINKTYPE_RAW .pcap to cacheDir/reports/)."
    }

    fun stopPcapCapture(context: Context) {
        OmniVpnService.stopLocalPcapCapture(context)
        _diagnosticStatus.value = "Local PCAP capture stopped."
    }

    fun clearPcapPackets() {
        pcapCaptureEngine.clearCapturedPackets()
    }

    fun exportPcapFile(): Result<Pair<File, Uri>> {
        val result = pcapCaptureEngine.exportPcapUri()
        result.onFailure { _diagnosticStatus.value = it.message ?: "PCAP export error" }
        return result
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

    fun generateWireGuardKeyPair() {
        _generatedKeyPair.value = enterpriseVpnRepository.generateWireGuardKeyPair()
        _diagnosticStatus.value = "Generated fresh Curve25519 WireGuard keypair + 256-bit PresharedKey."
    }

    fun calculateAllowedIps(includedCsv: String, excludedCsv: String) {
        val res = enterpriseVpnRepository.calculateAllowedIps(includedCsv, excludedCsv)
        _allowedIpsCalc.value = res
        _diagnosticStatus.value = "Calculated ${res.resultCidrs.size} CIDR subnets for AllowedIPs."
    }

    fun createWireGuardProfileFromBuilder(
        name: String,
        privateKey: String,
        localAddress: String,
        dnsCsv: String,
        peerPublicKey: String,
        presharedKey: String,
        endpoint: String,
        allowedIpsCsv: String,
        keepaliveSec: Int
    ) {
        val allowedList = allowedIpsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val dnsList = dnsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val addrList = localAddress.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val host = endpoint.substringBeforeLast(":").trim()
        val port = endpoint.substringAfterLast(":", "51820").toIntOrNull() ?: 51820
        val derivedPub = _generatedKeyPair.value?.publicKeyBase64.orEmpty()

        val profile = VpnProfile(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "WG-Client" },
            protocol = VpnProtocol.WIREGUARD,
            serverAddress = host,
            port = port,
            wireGuardConfig = WireGuardConfig(
                privateKey = privateKey.trim(),
                publicKey = peerPublicKey.trim(),
                derivedLocalPublicKey = derivedPub,
                presharedKey = presharedKey.trim(),
                interfaceAddresses = addrList,
                dnsServers = dnsList,
                peerEndpoint = endpoint.trim(),
                peerAllowedIps = allowedList,
                persistentKeepalive = keepaliveSec.coerceIn(0, 120)
            )
        )
        vpnRepository.addProfile(profile)
        _selectedProfile.value = profile
        _diagnosticStatus.value = "Created WireGuard profile '${profile.name}' (Keepalive ${keepaliveSec}s)."
    }

    fun tuneSelectedWireGuardKeepalive(keepaliveSec: Int, allowedIpsCsv: String?) {
        val cur = _selectedProfile.value ?: return
        if (cur.protocol != VpnProtocol.WIREGUARD) {
            _diagnosticStatus.value = "Select a WireGuard profile to tune keepalive / AllowedIPs."
            return
        }
        val updated = enterpriseVpnRepository.tuneWireGuardProfile(cur, keepaliveSec, allowedIpsCsv)
        _selectedProfile.value = updated
        _diagnosticStatus.value = "Updated '${updated.name}' PersistentKeepalive=${updated.wireGuardConfig?.persistentKeepalive}s."
    }

    fun showQrForSelectedWireGuard() {
        val wg = _selectedProfile.value?.wireGuardConfig ?: run {
            _diagnosticStatus.value = "Select a WireGuard profile to render QR."
            return
        }
        val confText = enterpriseVpnRepository.buildWireGuardConfText(wg)
        enterpriseVpnRepository.generateQrBitmap(confText).fold(
            onSuccess = { bmp ->
                _qrPreviewBitmap.value = bmp
                _qrPreviewText.value = confText
            },
            onFailure = { _diagnosticStatus.value = "QR generation failed: ${it.message}" }
        )
    }

    fun dismissQrPreview() {
        _qrPreviewBitmap.value = null
    }

    fun importWireGuardFromQrBitmap(bitmap: Bitmap, profileName: String = "WireGuard-QR") {
        enterpriseVpnRepository.decodeQrFromBitmap(bitmap).fold(
            onSuccess = { text ->
                if (text.contains("[Interface]", true)) {
                    importWireGuardConf(text, profileName)
                } else {
                    _diagnosticStatus.value = "QR decoded text, but no [Interface] block found: ${text.take(80)}"
                }
            },
            onFailure = { _diagnosticStatus.value = "Could not detect WireGuard QR code in image: ${it.message}" }
        )
    }

    fun importPkcs12Bytes(bytes: ByteArray, password: String) {
        enterpriseVpnRepository.importPkcs12Certificate(bytes, password).fold(
            onSuccess = { bundle ->
                _importedPkcs12.value = bundle
                _diagnosticStatus.value = "Unlocked PKCS#12 cert (${bundle.alias}): ${bundle.subjectDn.take(64)}"
            },
            onFailure = { _diagnosticStatus.value = "PKCS#12 unlock failed: ${it.message}" }
        )
    }

    fun createIkev2Profile(
        name: String,
        serverAddress: String,
        remoteIdentifier: String,
        preSharedKey: String,
        username: String,
        password: String,
        authModeOverride: String? = null
    ): VpnProfile {
        val p12 = if (authModeOverride.equals("RSA", true)) _importedPkcs12.value else null
        val profile = vpnRepository.addIkev2Profile(
            name = name,
            serverAddress = serverAddress,
            remoteIdentifier = remoteIdentifier,
            preSharedKey = preSharedKey,
            username = username,
            password = password,
            authModeOverride = authModeOverride,
            userCertPem = p12?.userCertPem.orEmpty(),
            userKeyPem = p12?.userKeyPem.orEmpty(),
            caCertPem = p12?.caCertPem.orEmpty(),
            certSubjectSummary = p12?.subjectDn.orEmpty()
        )
        _selectedProfile.value = profile
        _diagnosticStatus.value = "Created IKEv2 (${profile.ikev2Config?.authType}) profile: ${profile.name}"
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
            _diagnosticStatus.value = "Imported WireGuard profile: ${profile.name}"
        } catch (e: Exception) {
            _diagnosticStatus.value = "WireGuard import error: ${e.message}"
        }
    }

    fun importOpenVpnConf(ovpn: String, name: String) {
        if (ovpn.isBlank()) return
        try {
            var profile = vpnRepository.parseOpenVpnConf(ovpn, name.ifBlank { "OpenVPN Conf" })
            val p12 = _importedPkcs12.value
            if (p12 != null && profile.openVpnConfig != null) {
                profile = profile.copy(
                    openVpnConfig = profile.openVpnConfig.copy(
                        clientCert = profile.openVpnConfig.clientCert.ifBlank { p12.userCertPem },
                        clientKey = profile.openVpnConfig.clientKey.ifBlank { p12.userKeyPem },
                        caCert = profile.openVpnConfig.caCert.ifBlank { p12.caCertPem },
                        pkcs12Subject = p12.subjectDn,
                        pkcs12Alias = p12.alias
                    )
                )
                vpnRepository.updateProfile(profile)
            }
            _selectedProfile.value = profile
            _diagnosticStatus.value = "Imported OpenVPN profile: ${profile.name}"
        } catch (e: Exception) {
            _diagnosticStatus.value = "OpenVPN import error: ${e.message}"
        }
    }

    fun probeEnterpriseSslVpn(
        vendor: EnterpriseSslVpnVendor,
        host: String,
        port: Int = vendor.defaultPort,
        allowSelfSigned: Boolean = true
    ) {
        if (host.isBlank()) return
        viewModelScope.launch {
            _isTesting.value = true
            _diagnosticStatus.value = "Probing ${vendor.displayName} on $host:$port..."
            val res = enterpriseVpnRepository.probeEnterpriseSslVpn(vendor, host, port, allowSelfSigned)
            _sslProbeResult.value = res
            _isTesting.value = false
            _diagnosticStatus.value = if (res.isReachable) {
                "${vendor.displayName} reachable (${res.rttMs}ms): ${res.httpStatusOrCode}"
            } else {
                "${vendor.displayName} failed: ${res.errorMessage}"
            }
        }
    }

    fun probeIkev2Peer(host: String, useNatT4500: Boolean) {
        if (host.isBlank()) return
        viewModelScope.launch {
            _isTesting.value = true
            val port = if (useNatT4500) 4500 else 500
            _diagnosticStatus.value = "Dispatching IKE_SA_INIT probe to $host:$port..."
            val res = enterpriseVpnRepository.probeIkev2Peer(host, useNatT4500)
            _ikeProbeResult.value = res
            _isTesting.value = false
            _diagnosticStatus.value = res.diagnosisSummary
        }
    }

    fun calculateTunnelMss(
        encapsulation: TunnelEncapsulationType,
        physicalMtu: Int,
        isIpv6Outer: Boolean,
        natTEnabled: Boolean
    ) {
        _tunnelMssCalc.value = enterpriseVpnRepository.calculateTunnelOverheadAndMss(
            encapsulation = encapsulation,
            physicalMtu = physicalMtu,
            isIpv6Outer = isIpv6Outer,
            natTEnabled = natTEnabled
        )
    }

    fun runFullLeakAndRouteAudit() {
        viewModelScope.launch {
            _isTesting.value = true
            _diagnosticStatus.value = "Auditing DNS resolvers, global IPv6 leak vectors, and kernel routes..."
            val report = enterpriseVpnRepository.runFullVpnLeakAndRouteAudit(_selectedProfile.value)
            _vpnAuditReport.value = report
            _dnsLeakResult.value = report.dnsLeakResult
            _isTesting.value = false
            _diagnosticStatus.value = report.overallSummary
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            if (_installedApps.value.isEmpty()) {
                _installedApps.value = enterpriseVpnRepository.listInstalledApps()
            }
        }
    }

    fun toggleExcludedApp(packageName: String) {
        val cur = _selectedProfile.value ?: run {
            _diagnosticStatus.value = "Select a VPN profile before configuring Per-App Exclusions."
            return
        }
        val list = cur.splitTunnelExcludedApps.toMutableList()
        if (packageName in list) list.remove(packageName) else list.add(packageName)
        vpnRepository.updateProfileExcludedApps(cur.id, list)
        _selectedProfile.value = cur.copy(splitTunnelExcludedApps = list.sorted())
    }

    fun clearDiagnosticStatus() {
        _diagnosticStatus.value = null
    }
}
