// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/diagnostics/DiagnosticsViewModel.kt ===
package com.enterwebslinger.network.ui.screens.diagnostics

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterwebslinger.network.EnterwebSlingerApp
import com.enterwebslinger.network.core.util.ReportExporter
import com.enterwebslinger.network.data.repository.DiagnosticsRepository
import com.enterwebslinger.network.data.repository.NetworkInfoRepository
import com.enterwebslinger.network.domain.model.DnsRecord
import com.enterwebslinger.network.domain.model.PingSummary
import com.enterwebslinger.network.domain.model.TracerouteHop
import com.enterwebslinger.network.domain.model.UiState
import com.enterwebslinger.network.domain.model.WhoisLookupResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DiagnosticsViewModel @JvmOverloads constructor(
    private val diagnosticsRepository: DiagnosticsRepository = EnterwebSlingerApp.getContainer().diagnosticsRepository,
    @Suppress("unused")
    private val networkInfoRepository: NetworkInfoRepository = EnterwebSlingerApp.getContainer().networkInfoRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _pingHost = MutableStateFlow("")
    val pingHost: StateFlow<String> = _pingHost.asStateFlow()

    private val _pingProtocol = MutableStateFlow("ICMP")
    val pingProtocol: StateFlow<String> = _pingProtocol.asStateFlow()

    private val _pingSummary = MutableStateFlow(PingSummary())
    val pingSummary: StateFlow<PingSummary> = _pingSummary.asStateFlow()

    private val _pingUiState = MutableStateFlow<UiState<PingSummary>>(UiState.Idle)
    val pingUiState: StateFlow<UiState<PingSummary>> = _pingUiState.asStateFlow()

    private var pingJob: Job? = null

    private val _tracerouteTarget = MutableStateFlow("")
    val tracerouteTarget: StateFlow<String> = _tracerouteTarget.asStateFlow()

    private val _isTracing = MutableStateFlow(false)
    val isTracing: StateFlow<Boolean> = _isTracing.asStateFlow()

    private val _tracerouteHops = MutableStateFlow<List<TracerouteHop>>(emptyList())
    val tracerouteHops: StateFlow<List<TracerouteHop>> = _tracerouteHops.asStateFlow()

    private val _tracerouteUiState = MutableStateFlow<UiState<List<TracerouteHop>>>(UiState.Idle)
    val tracerouteUiState: StateFlow<UiState<List<TracerouteHop>>> = _tracerouteUiState.asStateFlow()

    private var tracerouteJob: Job? = null

    private val _dnsDomain = MutableStateFlow("")
    val dnsDomain: StateFlow<String> = _dnsDomain.asStateFlow()

    private val _dnsRecordType = MutableStateFlow("A")
    val dnsRecordType: StateFlow<String> = _dnsRecordType.asStateFlow()

    private val _isResolvingDns = MutableStateFlow(false)
    val isResolvingDns: StateFlow<Boolean> = _isResolvingDns.asStateFlow()

    private val _dnsResults = MutableStateFlow<List<DnsRecord>>(emptyList())
    val dnsResults: StateFlow<List<DnsRecord>> = _dnsResults.asStateFlow()

    private val _dnsUiState = MutableStateFlow<UiState<List<DnsRecord>>>(UiState.Idle)
    val dnsUiState: StateFlow<UiState<List<DnsRecord>>> = _dnsUiState.asStateFlow()

    private var dnsJob: Job? = null

    private val _whoisQuery = MutableStateFlow("")
    val whoisQuery: StateFlow<String> = _whoisQuery.asStateFlow()

    private val _isQueryingWhois = MutableStateFlow(false)
    val isQueryingWhois: StateFlow<Boolean> = _isQueryingWhois.asStateFlow()

    private val _whoisResult = MutableStateFlow<WhoisLookupResult?>(null)
    val whoisResult: StateFlow<WhoisLookupResult?> = _whoisResult.asStateFlow()

    private val _whoisUiState = MutableStateFlow<UiState<WhoisLookupResult>>(UiState.Idle)
    val whoisUiState: StateFlow<UiState<WhoisLookupResult>> = _whoisUiState.asStateFlow()

    private var whoisJob: Job? = null

    fun setSelectedTab(index: Int) {
        _selectedTab.value = index.coerceIn(0, 3)
    }

    fun setPingHost(host: String) {
        _pingHost.value = host
    }

    fun setPingProtocol(protocol: String) {
        _pingProtocol.value = protocol
    }

    fun startPing() {
        val target = _pingHost.value.trim()
        if (target.isBlank()) {
            _pingUiState.value = UiState.Error("Enter a valid hostname or IP address to ping.")
            return
        }
        pingJob?.cancel()
        _pingSummary.value = PingSummary(host = target, protocol = _pingProtocol.value, isRunning = true)
        _pingUiState.value = UiState.Loading

        pingJob = viewModelScope.launch {
            try {
                diagnosticsRepository.startPingStream(
                    host = target,
                    protocol = _pingProtocol.value,
                    count = 20
                ).collect { summary ->
                    _pingSummary.value = summary
                    _pingUiState.value = UiState.Success(summary)
                }
            } catch (e: CancellationException) {
                _pingSummary.value = _pingSummary.value.copy(isRunning = false)
                _pingUiState.value = if (_pingSummary.value.results.isNotEmpty()) {
                    UiState.Success(_pingSummary.value)
                } else {
                    UiState.Idle
                }
                throw e
            } catch (e: Exception) {
                _pingSummary.value = _pingSummary.value.copy(isRunning = false)
                _pingUiState.value = UiState.Error(
                    "Ping failed for '$target': ${e.message ?: "Unable to resolve or reach host"}",
                    e
                )
            }
        }
    }

    fun stopPing() {
        pingJob?.cancel()
        pingJob = null
        val stopped = _pingSummary.value.copy(isRunning = false)
        _pingSummary.value = stopped
        _pingUiState.value = if (stopped.results.isNotEmpty()) UiState.Success(stopped) else UiState.Idle
    }

    fun dismissPingError() {
        val current = _pingSummary.value
        _pingUiState.value = if (current.results.isNotEmpty()) UiState.Success(current) else UiState.Idle
    }

    fun exportPingReport(context: Context, formatAsJson: Boolean = false) {
        ReportExporter.sharePingReport(context, _pingSummary.value, formatAsJson)
    }

    fun setTracerouteTarget(target: String) {
        _tracerouteTarget.value = target
    }

    fun startTraceroute() {
        val target = _tracerouteTarget.value.trim()
        if (target.isBlank()) {
            _tracerouteUiState.value = UiState.Error("Enter a destination IP or hostname before starting traceroute.")
            return
        }
        tracerouteJob?.cancel()
        _isTracing.value = true
        _tracerouteHops.value = emptyList()
        _tracerouteUiState.value = UiState.Loading

        tracerouteJob = viewModelScope.launch {
            try {
                diagnosticsRepository.runTraceroute(target, 30).collect { hop ->
                    val updated = _tracerouteHops.value + hop
                    _tracerouteHops.value = updated
                    _tracerouteUiState.value = UiState.Success(updated)
                }
                if (_tracerouteHops.value.isNotEmpty()) {
                    _tracerouteUiState.value = UiState.Success(_tracerouteHops.value)
                }
            } catch (e: CancellationException) {
                _tracerouteUiState.value = if (_tracerouteHops.value.isNotEmpty()) {
                    UiState.Success(_tracerouteHops.value)
                } else {
                    UiState.Idle
                }
                throw e
            } catch (e: Exception) {
                _tracerouteUiState.value = UiState.Error(
                    "Traceroute failed for '$target': ${e.message ?: "Destination unreachable"}",
                    e
                )
            } finally {
                _isTracing.value = false
            }
        }
    }

    fun stopTraceroute() {
        tracerouteJob?.cancel()
        tracerouteJob = null
        _isTracing.value = false
        _tracerouteUiState.value = if (_tracerouteHops.value.isNotEmpty()) {
            UiState.Success(_tracerouteHops.value)
        } else {
            UiState.Idle
        }
    }

    fun dismissTracerouteError() {
        val hops = _tracerouteHops.value
        _tracerouteUiState.value = if (hops.isEmpty()) UiState.Idle else UiState.Success(hops)
    }

    fun setDnsDomain(domain: String) {
        _dnsDomain.value = domain
    }

    fun setDnsRecordType(type: String) {
        _dnsRecordType.value = type
    }

    fun resolveDns() {
        val domain = _dnsDomain.value.trim()
        if (domain.isBlank()) {
            _dnsUiState.value = UiState.Error("Enter a domain name or IP address (for PTR) to query.")
            return
        }
        dnsJob?.cancel()
        dnsJob = viewModelScope.launch {
            _isResolvingDns.value = true
            _dnsResults.value = emptyList()
            _dnsUiState.value = UiState.Loading
            try {
                val results = diagnosticsRepository.queryDns(domain, _dnsRecordType.value)
                _dnsResults.value = results
                _dnsUiState.value = UiState.Success(results)
            } catch (e: CancellationException) {
                _dnsUiState.value = UiState.Idle
                throw e
            } catch (e: Exception) {
                _dnsUiState.value = UiState.Error(
                    "DNS lookup failed for '$domain': ${e.message ?: "Resolver unreachable"}",
                    e
                )
            } finally {
                _isResolvingDns.value = false
            }
        }
    }

    fun cancelDnsLookup() {
        dnsJob?.cancel()
        dnsJob = null
        _isResolvingDns.value = false
        _dnsUiState.value = if (_dnsResults.value.isEmpty()) UiState.Idle else UiState.Success(_dnsResults.value)
    }

    fun dismissDnsError() {
        _dnsUiState.value = if (_dnsResults.value.isEmpty()) UiState.Idle else UiState.Success(_dnsResults.value)
    }

    fun setWhoisQuery(query: String) {
        _whoisQuery.value = query
    }

    fun runWhoisLookup() {
        val q = _whoisQuery.value.trim()
        if (q.isBlank()) {
            _whoisUiState.value = UiState.Error("Enter a domain, IPv4/IPv6 address, or ASN (e.g. AS15169) to query.")
            return
        }
        whoisJob?.cancel()
        whoisJob = viewModelScope.launch {
            _isQueryingWhois.value = true
            _whoisUiState.value = UiState.Loading
            try {
                val res = diagnosticsRepository.queryWhois(q)
                res.fold(
                    onSuccess = { data ->
                        _whoisResult.value = data
                        _whoisUiState.value = UiState.Success(data)
                    },
                    onFailure = { err ->
                        _whoisUiState.value = UiState.Error(
                            "Whois lookup failed for '$q': ${err.message ?: "TCP/43 server unreachable"}",
                            err
                        )
                    }
                )
            } catch (e: CancellationException) {
                _whoisUiState.value = _whoisResult.value?.let { UiState.Success(it) } ?: UiState.Idle
                throw e
            } finally {
                _isQueryingWhois.value = false
            }
        }
    }

    fun cancelWhoisLookup() {
        whoisJob?.cancel()
        whoisJob = null
        _isQueryingWhois.value = false
        _whoisUiState.value = _whoisResult.value?.let { UiState.Success(it) } ?: UiState.Idle
    }

    fun dismissWhoisError() {
        _whoisUiState.value = _whoisResult.value?.let { UiState.Success(it) } ?: UiState.Idle
    }
}
