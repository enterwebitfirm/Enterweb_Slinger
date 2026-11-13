// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/NetworkTopologyRepository.kt ===
package com.enterwebslinger.network.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.enterwebslinger.network.core.util.NetworkUtils
import com.enterwebslinger.network.domain.model.NetworkTopologyState
import com.enterwebslinger.network.domain.model.RssiDataPoint
import com.enterwebslinger.network.domain.model.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

class NetworkTopologyRepository(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    private val _topology = MutableStateFlow(NetworkTopologyState())
    val topology: StateFlow<NetworkTopologyState> = _topology.asStateFlow()

    private val _rssiHistory = MutableStateFlow<List<RssiDataPoint>>(emptyList())
    val rssiHistory: StateFlow<List<RssiDataPoint>> = _rssiHistory.asStateFlow()

    private val _uiState = MutableStateFlow<UiState<NetworkTopologyState>>(UiState.Idle)
    val uiState: StateFlow<UiState<NetworkTopologyState>> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val networkCallbackFlow: Flow<NetworkTopologyState> = callbackFlow {
        val cm = connectivityManager
        if (cm == null) {
            close()
            return@callbackFlow
        }

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(inspectCurrentTopology())
            }

            override fun onLost(network: Network) {
                trySend(inspectCurrentTopology())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(inspectCurrentTopology())
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                trySend(inspectCurrentTopology())
            }
        }

        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, networkCallback)
        } catch (_: Exception) {
            try {
                cm.registerDefaultNetworkCallback(networkCallback)
            } catch (_: Exception) {}
        }

        trySend(inspectCurrentTopology())

        awaitClose {
            try {
                cm.unregisterNetworkCallback(networkCallback)
            } catch (_: Exception) {}
        }
    }

    private var telemetryJob: Job? = null
    private var callbackJob: Job? = null
    private var refreshJob: Job? = null

    fun startTelemetry() {
        if (telemetryJob?.isActive == true) return

        callbackJob?.cancel()
        callbackJob = scope.launch(Dispatchers.IO) {
            try {
                networkCallbackFlow.collect { state ->
                    _topology.value = state
                    if (_uiState.value !is UiState.Loading && _uiState.value !is UiState.Error) {
                        _uiState.value = UiState.Success(state)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                Log.w("NetworkTopology", "Permission denied for network callback", e)
                _uiState.value = UiState.Error("Network permission denied: ${e.message ?: "Grant location/Wi-Fi permissions"}", e)
            } catch (e: Exception) {
                Log.w("NetworkTopology", "Error in network callback flow", e)
                _uiState.value = UiState.Error("Failed to monitor network callbacks: ${e.message}", e)
            }
        }

        telemetryJob?.cancel()
        telemetryJob = scope.launch(Dispatchers.IO) {
            var tickCount = 0
            while (isActive) {
                try {
                    val updated = inspectCurrentTopology()
                    _topology.value = updated
                    sampleRssiTelemetry(updated)
                    if (updated.publicIp == "Unavailable" && updated.isConnected && tickCount % 5 == 0) {
                        val ip = fetchPublicIp()
                        if (ip != "Unavailable") {
                            _topology.value = _topology.value.copy(publicIp = ip)
                        }
                    }
                    if (_uiState.value !is UiState.Error && !_isRefreshing.value) {
                        _uiState.value = UiState.Success(_topology.value)
                    }
                    tickCount++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SecurityException) {
                    Log.w("NetworkTopology", "SecurityException sampling telemetry", e)
                    _uiState.value = UiState.Error("Location/Wi-Fi permission required for SSID & RSSI telemetry", e)
                } catch (e: Exception) {
                    Log.w("NetworkTopology", "Error sampling telemetry", e)
                }
                delay(2000L)
            }
        }

        refreshPublicIp()
    }

    fun stopTelemetry() {
        telemetryJob?.cancel()
        telemetryJob = null
        callbackJob?.cancel()
        callbackJob = null
        refreshJob?.cancel()
        refreshJob = null
        _isRefreshing.value = false
    }

    fun manualRefresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            _uiState.value = UiState.Loading
            try {
                val snapshot = inspectCurrentTopology()
                _topology.value = snapshot
                sampleRssiTelemetry(snapshot)

                if (!snapshot.isConnected) {
                    _uiState.value = UiState.Error("No active Internet connection detected.")
                    return@launch
                }

                val ipResult = fetchPublicIpResult()
                ipResult.fold(
                    onSuccess = { ip ->
                        val updated = _topology.value.copy(publicIp = ip)
                        _topology.value = updated
                        _uiState.value = UiState.Success(updated)
                    },
                    onFailure = { err ->
                        val updated = _topology.value.copy(publicIp = "Unavailable")
                        _topology.value = updated
                        _uiState.value = UiState.Error("Public IP lookup failed: ${err.message ?: "Network unreachable"}", err)
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Refresh failed: ${e.message ?: "Unknown error"}", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun cancelRefresh() {
        refreshJob?.cancel()
        refreshJob = null
        _isRefreshing.value = false
        _uiState.value = UiState.Success(_topology.value)
    }

    fun dismissError() {
        _uiState.value = UiState.Success(_topology.value)
    }

    fun refreshPublicIp() {
        refreshJob?.cancel()
        refreshJob = scope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                val ip = fetchPublicIp()
                val updated = _topology.value.copy(publicIp = ip)
                _topology.value = updated
                if (_uiState.value is UiState.Loading) {
                    _uiState.value = UiState.Success(updated)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Public IP lookup failed: ${e.message}", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun fetchPublicIpResult(): Result<String> = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        try {
            val reqJson = Request.Builder().url("https://api.ipify.org?format=json").build()
            okHttpClient.newCall(reqJson).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val ip = JSONObject(body).optString("ip", "")
                        if (ip.isNotBlank()) return@withContext Result.success(ip)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = e
        }

        try {
            val reqPlain = Request.Builder().url("https://api.ipify.org").build()
            okHttpClient.newCall(reqPlain).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()?.trim()
                    if (!body.isNullOrBlank()) return@withContext Result.success(body)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = e
        }

        try {
            val reqFallback = Request.Builder().url("https://icanhazip.com").build()
            okHttpClient.newCall(reqFallback).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()?.trim()
                    if (!body.isNullOrBlank()) return@withContext Result.success(body)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = e
        }

        Result.failure(lastError ?: IllegalStateException("Public IP endpoint returned empty response"))
    }

    private suspend fun fetchPublicIp(): String {
        return fetchPublicIpResult().getOrDefault("Unavailable")
    }

    private fun getActiveWifiInfo(): WifiInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val network = connectivityManager?.activeNetwork
            val caps = connectivityManager?.getNetworkCapabilities(network)
            val info = caps?.transportInfo as? WifiInfo
            if (info != null) return info
        }
        @Suppress("DEPRECATION")
        return wifiManager?.connectionInfo
    }

    private fun inspectCurrentTopology(): NetworkTopologyState {
        val cm = connectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val linkProps = activeNetwork?.let { cm.getLinkProperties(it) }

        val isConnected = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val isEthernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true

        val netType = when {
            isWifi -> "Wi-Fi"
            isCellular -> "Cellular"
            isEthernet -> "Ethernet"
            isConnected -> "Active Connection"
            else -> "Disconnected"
        }

        var localIpv4 = "Unavailable"
        var localIpv6 = "Unavailable"
        var subnetMask = "Unavailable"
        var subnetCidr = ""
        var defaultGateway = "Unavailable"
        val dnsList = mutableListOf<String>()

        linkProps?.let { props ->
            for (route in props.routes) {
                val gw = route.gateway
                if (route.isDefaultRoute && gw != null) {
                    if (gw is Inet4Address || defaultGateway == "Unavailable") {
                        defaultGateway = gw.hostAddress ?: defaultGateway
                    }
                }
            }
            for (dns in props.dnsServers) {
                dns.hostAddress?.let { dnsList.add(it) }
            }
            for (linkAddr in props.linkAddresses) {
                val addr = linkAddr.address
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    localIpv4 = addr.hostAddress ?: localIpv4
                    val prefix = linkAddr.prefixLength.coerceIn(0, 32)
                    subnetCidr = prefix.toString()
                    subnetMask = cidrToSubnetMask(prefix)
                } else if (addr is Inet6Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                    localIpv6 = addr.hostAddress ?: localIpv6
                }
            }
        }

        if (localIpv4 == "Unavailable") {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces != null && interfaces.hasMoreElements()) {
                    val nif = interfaces.nextElement()
                    if (nif.isLoopback || !nif.isUp) continue
                    for (ifaceAddr in nif.interfaceAddresses) {
                        val inet = ifaceAddr.address
                        if (inet is Inet4Address && !inet.isLoopbackAddress) {
                            localIpv4 = inet.hostAddress ?: localIpv4
                            if (subnetCidr.isBlank()) {
                                val prefix = ifaceAddr.networkPrefixLength.toInt().coerceIn(0, 32)
                                subnetCidr = prefix.toString()
                                subnetMask = cidrToSubnetMask(prefix)
                            }
                            break
                        }
                    }
                    if (localIpv4 != "Unavailable") break
                }
            } catch (_: Exception) {}
        }

        var ssid = "Unknown SSID"
        var bssid = "--:--:--:--:--:--"
        var freqMhz = 0
        var linkSpeed = 0
        var rssi = -100
        var band = "Unknown"
        var leaseDuration = 0
        var dhcpServerIp = defaultGateway

        try {
            val info: WifiInfo? = getActiveWifiInfo()
            if (info != null) {
                val rawSsid = info.ssid ?: ""
                ssid = if (rawSsid.startsWith("\"") && rawSsid.endsWith("\"") && rawSsid.length > 2) {
                    rawSsid.substring(1, rawSsid.length - 1)
                } else if (rawSsid.isNotBlank() && rawSsid != "<unknown ssid>") {
                    rawSsid
                } else {
                    "Unknown SSID"
                }
                bssid = info.bssid ?: bssid
                rssi = info.rssi
                linkSpeed = info.linkSpeed
                freqMhz = info.frequency
                band = NetworkUtils.frequencyToBand(freqMhz)
            }

            @Suppress("DEPRECATION")
            val dhcp = wifiManager?.dhcpInfo
            if (dhcp != null) {
                if (dhcp.leaseDuration > 0) {
                    leaseDuration = dhcp.leaseDuration
                }
                if (dhcp.serverAddress != 0) {
                    val server = "${dhcp.serverAddress and 0xFF}.${(dhcp.serverAddress shr 8) and 0xFF}.${(dhcp.serverAddress shr 16) and 0xFF}.${(dhcp.serverAddress shr 24) and 0xFF}"
                    if (server != "0.0.0.0") dhcpServerIp = server
                }
                if (defaultGateway == "Unavailable" && dhcp.gateway != 0) {
                    val gw = "${dhcp.gateway and 0xFF}.${(dhcp.gateway shr 8) and 0xFF}.${(dhcp.gateway shr 16) and 0xFF}.${(dhcp.gateway shr 24) and 0xFF}"
                    if (gw != "0.0.0.0") defaultGateway = gw
                }
            }
        } catch (e: SecurityException) {
            Log.w("NetworkTopology", "Permission required for WifiManager connectionInfo", e)
        } catch (_: Exception) {}

        val signalPercent = when {
            rssi <= -100 -> 0
            rssi >= -50 -> 100
            else -> ((rssi + 100) * 2).coerceIn(0, 100)
        }

        return _topology.value.copy(
            isConnected = isConnected,
            networkType = netType,
            localIpv4 = localIpv4,
            localIpv6 = localIpv6,
            subnetMask = subnetMask,
            subnetCidr = subnetCidr,
            defaultGateway = defaultGateway,
            dnsServers = dnsList,
            dhcpLeaseServer = dhcpServerIp,
            dhcpLeaseDurationSec = leaseDuration,
            ssid = ssid,
            bssid = bssid,
            frequencyBand = band,
            frequencyMhz = freqMhz,
            linkSpeedMbps = linkSpeed,
            rssiDbm = rssi,
            signalPercent = signalPercent
        )
    }

    private fun sampleRssiTelemetry(snapshot: NetworkTopologyState? = null) {
        try {
            val info = getActiveWifiInfo()
            val rssi = info?.rssi ?: snapshot?.rssiDbm ?: -100
            val linkSpeed = info?.linkSpeed ?: snapshot?.linkSpeedMbps ?: 0
            val now = System.currentTimeMillis()

            val currentHistory = _rssiHistory.value.toMutableList()
            currentHistory.add(RssiDataPoint(now, rssi, linkSpeed))
            val cutoff = now - 60_000L
            _rssiHistory.value = currentHistory.filter { it.timestamp >= cutoff }.takeLast(45)

            _topology.value = _topology.value.copy(
                rssiDbm = rssi,
                linkSpeedMbps = linkSpeed,
                signalPercent = when {
                    rssi <= -100 -> 0
                    rssi >= -50 -> 100
                    else -> ((rssi + 100) * 2).coerceIn(0, 100)
                }
            )
        } catch (e: SecurityException) {
            Log.w("NetworkTopology", "Permission required for sampling RSSI", e)
        } catch (_: Exception) {}
    }

    private fun cidrToSubnetMask(prefix: Int): String {
        val safePrefix = prefix.coerceIn(0, 32)
        val mask = if (safePrefix == 0) 0L else (0xFFFFFFFFL shl (32 - safePrefix)) and 0xFFFFFFFFL
        return "${(mask shr 24) and 0xFF}.${(mask shr 16) and 0xFF}.${(mask shr 8) and 0xFF}.${mask and 0xFF}"
    }
}
