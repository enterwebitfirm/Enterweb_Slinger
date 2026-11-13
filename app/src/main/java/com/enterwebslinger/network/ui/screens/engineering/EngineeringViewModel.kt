// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/engineering/EngineeringViewModel.kt ===
package com.enterwebslinger.network.ui.screens.engineering

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterwebslinger.network.EnterwebSlingerApp
import com.enterwebslinger.network.data.repository.ArpNeighborEntry
import com.enterwebslinger.network.data.repository.EngineeringUtilitiesRepository
import com.enterwebslinger.network.data.repository.Iperf3TestProgress
import com.enterwebslinger.network.data.repository.Ipv4SubnetCalculation
import com.enterwebslinger.network.data.repository.Ipv6Calculation
import com.enterwebslinger.network.data.repository.SipOptionsResult
import com.enterwebslinger.network.data.repository.SslCertInfo
import com.enterwebslinger.network.data.repository.VlsmSubnetAllocation
import com.enterwebslinger.network.data.repository.WhoisResult
import com.enterwebslinger.network.data.repository.WifiRoamingEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EngineeringViewModel @JvmOverloads constructor(
    private val engineeringRepo: EngineeringUtilitiesRepository = EnterwebSlingerApp.getContainer().engineeringUtilitiesRepository
) : ViewModel() {

    // Tab selection: 0: MAC/ARP, 1: Subnet/VLSM/IPv6, 2: TLS Cert, 3: Whois/ASN, 4: iPerf3, 5: Wi-Fi Roaming, 6: SIP Ping
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab.coerceIn(0, 6)
    }

    // --- Tab 0: MAC & ARP Table ---
    private val _macInput = MutableStateFlow("")
    val macInput: StateFlow<String> = _macInput.asStateFlow()
    fun setMacInput(value: String) { _macInput.value = value }

    private val _macVendorResult = MutableStateFlow<String?>(null)
    val macVendorResult: StateFlow<String?> = _macVendorResult.asStateFlow()

    private val _arpNeighbors = MutableStateFlow<List<ArpNeighborEntry>>(emptyList())
    val arpNeighbors: StateFlow<List<ArpNeighborEntry>> = _arpNeighbors.asStateFlow()

    private val _isLoadingArp = MutableStateFlow(false)
    val isLoadingArp: StateFlow<Boolean> = _isLoadingArp.asStateFlow()

    fun resolveMacVendor() {
        val mac = _macInput.value.trim()
        if (mac.isBlank()) {
            _macVendorResult.value = null
            return
        }
        val vendor = engineeringRepo.resolveVendor(mac)
        if (vendor.isBlank()) {
            _macVendorResult.value = "Invalid MAC address format"
            return
        }
        val isRandom = engineeringRepo.isRandomizedMac(mac)
        _macVendorResult.value = if (isRandom) "$vendor (Locally Administered / Randomized)" else vendor
    }

    fun refreshArpTable() {
        viewModelScope.launch {
            _isLoadingArp.value = true
            _arpNeighbors.value = engineeringRepo.getArpNeighbors()
            _isLoadingArp.value = false
        }
    }

    // --- Tab 1: Subnet / CIDR / VLSM / IPv6 ---
    private val _subnetIpInput = MutableStateFlow("")
    val subnetIpInput: StateFlow<String> = _subnetIpInput.asStateFlow()
    fun setSubnetIpInput(v: String) {
        _subnetIpInput.value = v
        _subnetError.value = null
    }

    private val _cidrPrefixInput = MutableStateFlow(24)
    val cidrPrefixInput: StateFlow<Int> = _cidrPrefixInput.asStateFlow()
    fun setCidrPrefixInput(v: Int) { _cidrPrefixInput.value = v.coerceIn(0, 32) }

    private val _ipv4CalcResult = MutableStateFlow<Ipv4SubnetCalculation?>(null)
    val ipv4CalcResult: StateFlow<Ipv4SubnetCalculation?> = _ipv4CalcResult.asStateFlow()

    private val _subnetError = MutableStateFlow<String?>(null)
    val subnetError: StateFlow<String?> = _subnetError.asStateFlow()

    fun calculateSubnet() {
        val ip = _subnetIpInput.value.trim()
        if (ip.isBlank()) {
            _subnetError.value = "Enter a valid IPv4 address"
            _ipv4CalcResult.value = null
            return
        }
        val calc = engineeringRepo.calculateIpv4Subnet(ip, _cidrPrefixInput.value)
        if (calc == null) {
            _subnetError.value = "Invalid IPv4 address syntax"
            _ipv4CalcResult.value = null
        } else {
            _subnetError.value = null
            _ipv4CalcResult.value = calc
        }
    }

    // VLSM
    private val _vlsmParentCidr = MutableStateFlow("")
    val vlsmParentCidr: StateFlow<String> = _vlsmParentCidr.asStateFlow()
    fun setVlsmParentCidr(v: String) {
        _vlsmParentCidr.value = v
        _vlsmError.value = null
    }

    private val _vlsmRequirements = MutableStateFlow<List<Pair<String, Int>>>(emptyList())
    val vlsmRequirements: StateFlow<List<Pair<String, Int>>> = _vlsmRequirements.asStateFlow()

    private val _vlsmAllocations = MutableStateFlow<List<VlsmSubnetAllocation>>(emptyList())
    val vlsmAllocations: StateFlow<List<VlsmSubnetAllocation>> = _vlsmAllocations.asStateFlow()

    private val _vlsmError = MutableStateFlow<String?>(null)
    val vlsmError: StateFlow<String?> = _vlsmError.asStateFlow()

    fun addVlsmRequirement(name: String, hosts: Int) {
        val cleanName = name.trim()
        if (cleanName.isNotBlank() && hosts > 0) {
            _vlsmRequirements.value = _vlsmRequirements.value + (cleanName to hosts)
            _vlsmError.value = null
        }
    }

    fun removeVlsmRequirement(index: Int) {
        if (index in _vlsmRequirements.value.indices) {
            _vlsmRequirements.value = _vlsmRequirements.value.toMutableList().apply { removeAt(index) }
        }
    }

    fun calculateVlsm() {
        val cidr = _vlsmParentCidr.value.trim()
        val reqs = _vlsmRequirements.value
        if (cidr.isBlank() || reqs.isEmpty()) {
            _vlsmError.value = "Provide a parent CIDR block and at least one subnet requirement."
            _vlsmAllocations.value = emptyList()
            return
        }
        val res = engineeringRepo.calculateVlsm(cidr, reqs)
        if (res.isEmpty()) {
            _vlsmError.value = "Invalid parent CIDR syntax (expected format a.b.c.d/prefix)."
        } else {
            _vlsmError.value = null
        }
        _vlsmAllocations.value = res
    }

    // IPv6
    private val _ipv6Input = MutableStateFlow("")
    val ipv6Input: StateFlow<String> = _ipv6Input.asStateFlow()
    fun setIpv6Input(v: String) {
        _ipv6Input.value = v
        _ipv6Error.value = null
    }

    private val _ipv6Result = MutableStateFlow<Ipv6Calculation?>(null)
    val ipv6Result: StateFlow<Ipv6Calculation?> = _ipv6Result.asStateFlow()

    private val _ipv6Error = MutableStateFlow<String?>(null)
    val ipv6Error: StateFlow<String?> = _ipv6Error.asStateFlow()

    fun calculateIpv6() {
        val input = _ipv6Input.value.trim()
        if (input.isBlank()) {
            _ipv6Error.value = "Enter an IPv6 address"
            _ipv6Result.value = null
            return
        }
        val res = engineeringRepo.calculateIpv6(input, 64)
        if (res == null) {
            _ipv6Error.value = "Invalid IPv6 address syntax"
            _ipv6Result.value = null
        } else {
            _ipv6Error.value = null
            _ipv6Result.value = res
        }
    }

    // --- Tab 2: TLS Certificate Inspector ---
    private val _tlsHostInput = MutableStateFlow("")
    val tlsHostInput: StateFlow<String> = _tlsHostInput.asStateFlow()
    fun setTlsHostInput(v: String) { _tlsHostInput.value = v }

    private val _tlsPortInput = MutableStateFlow(443)
    val tlsPortInput: StateFlow<Int> = _tlsPortInput.asStateFlow()
    fun setTlsPortInput(v: Int) { _tlsPortInput.value = v }

    private val _tlsCertInfo = MutableStateFlow<SslCertInfo?>(null)
    val tlsCertInfo: StateFlow<SslCertInfo?> = _tlsCertInfo.asStateFlow()

    private val _tlsLoading = MutableStateFlow(false)
    val tlsLoading: StateFlow<Boolean> = _tlsLoading.asStateFlow()

    private val _tlsError = MutableStateFlow<String?>(null)
    val tlsError: StateFlow<String?> = _tlsError.asStateFlow()

    fun inspectTlsCertificate() {
        val host = _tlsHostInput.value.trim()
        if (host.isBlank()) return
        viewModelScope.launch {
            _tlsLoading.value = true
            _tlsError.value = null
            _tlsCertInfo.value = null
            try {
                _tlsCertInfo.value = engineeringRepo.inspectTlsCertificate(host, _tlsPortInput.value)
            } catch (e: Exception) {
                _tlsError.value = "Failed to inspect TLS certificate: ${e.message}"
            } finally {
                _tlsLoading.value = false
            }
        }
    }

    // --- Tab 3: Whois & BGP ASN Inspector ---
    private val _whoisQueryInput = MutableStateFlow("")
    val whoisQueryInput: StateFlow<String> = _whoisQueryInput.asStateFlow()
    fun setWhoisQueryInput(v: String) { _whoisQueryInput.value = v }

    private val _whoisResult = MutableStateFlow<WhoisResult?>(null)
    val whoisResult: StateFlow<WhoisResult?> = _whoisResult.asStateFlow()

    private val _whoisLoading = MutableStateFlow(false)
    val whoisLoading: StateFlow<Boolean> = _whoisLoading.asStateFlow()

    fun performWhois() {
        val query = _whoisQueryInput.value.trim()
        if (query.isBlank()) return
        viewModelScope.launch {
            _whoisLoading.value = true
            _whoisResult.value = engineeringRepo.queryWhois(query)
            _whoisLoading.value = false
        }
    }

    // --- Tab 4: iPerf3 LAN Throughput ---
    private val _iperfTargetHost = MutableStateFlow("")
    val iperfTargetHost: StateFlow<String> = _iperfTargetHost.asStateFlow()
    fun setIperfTargetHost(v: String) { _iperfTargetHost.value = v }

    private val _iperfTargetPort = MutableStateFlow(5201)
    val iperfTargetPort: StateFlow<Int> = _iperfTargetPort.asStateFlow()
    fun setIperfTargetPort(v: Int) { _iperfTargetPort.value = v }

    private val _iperfProgress = MutableStateFlow(Iperf3TestProgress())
    val iperfProgress: StateFlow<Iperf3TestProgress> = _iperfProgress.asStateFlow()

    fun startIperf3Test() {
        val host = _iperfTargetHost.value.trim()
        if (host.isBlank() || _iperfProgress.value.isRunning) return
        viewModelScope.launch {
            engineeringRepo.runIperf3Client(
                serverHost = host,
                serverPort = _iperfTargetPort.value,
                durationSeconds = 5,
                onProgress = { progress -> _iperfProgress.value = progress }
            )
        }
    }

    // --- Tab 5: Wi-Fi Roaming Monitor ---
    val roamingEvents: Flow<List<WifiRoamingEvent>> = engineeringRepo.roamingEvents

    // --- Tab 6: SIP OPTIONS VoIP Ping ---
    private val _sipHostInput = MutableStateFlow("")
    val sipHostInput: StateFlow<String> = _sipHostInput.asStateFlow()
    fun setSipHostInput(v: String) { _sipHostInput.value = v }

    private val _sipPortInput = MutableStateFlow(5060)
    val sipPortInput: StateFlow<Int> = _sipPortInput.asStateFlow()
    fun setSipPortInput(v: Int) { _sipPortInput.value = v }

    private val _sipUseTcp = MutableStateFlow(false)
    val sipUseTcp: StateFlow<Boolean> = _sipUseTcp.asStateFlow()
    fun setSipUseTcp(v: Boolean) { _sipUseTcp.value = v }

    private val _sipResult = MutableStateFlow<SipOptionsResult?>(null)
    val sipResult: StateFlow<SipOptionsResult?> = _sipResult.asStateFlow()

    private val _sipLoading = MutableStateFlow(false)
    val sipLoading: StateFlow<Boolean> = _sipLoading.asStateFlow()

    fun pingSipOptions() {
        val host = _sipHostInput.value.trim()
        if (host.isBlank()) return
        viewModelScope.launch {
            _sipLoading.value = true
            _sipResult.value = engineeringRepo.pingSipOptions(
                targetHost = host,
                targetPort = _sipPortInput.value,
                useTcp = _sipUseTcp.value
            )
            _sipLoading.value = false
        }
    }

    init {
        refreshArpTable()
    }
}
