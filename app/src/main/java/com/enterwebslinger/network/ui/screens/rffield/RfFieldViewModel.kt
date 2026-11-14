// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/rffield/RfFieldViewModel.kt ===
package com.enterwebslinger.network.ui.screens.rffield

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterwebslinger.network.EnterwebSlingerApp
import com.enterwebslinger.network.data.repository.ActiveWifiRfSample
import com.enterwebslinger.network.data.repository.CellularTelemetryRepository
import com.enterwebslinger.network.data.repository.LanScannerRepository
import com.enterwebslinger.network.data.repository.LanSecurityAuditRepository
import com.enterwebslinger.network.data.repository.NetworkInfoRepository
import com.enterwebslinger.network.data.repository.RfAndBleScannerRepository
import com.enterwebslinger.network.domain.model.BleDeviceResult
import com.enterwebslinger.network.domain.model.CellularTelemetryState
import com.enterwebslinger.network.domain.model.EvilTwinFinding
import com.enterwebslinger.network.domain.model.FloorplanPin
import com.enterwebslinger.network.domain.model.OnvifCameraDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class RfFieldViewModel @JvmOverloads constructor(
    private val rfAndBleRepository: RfAndBleScannerRepository = EnterwebSlingerApp.getContainer().rfAndBleScannerRepository,
    private val cellularRepository: CellularTelemetryRepository = EnterwebSlingerApp.getContainer().cellularTelemetryRepository,
    private val lanSecurityAuditRepository: LanSecurityAuditRepository = EnterwebSlingerApp.getContainer().lanSecurityAuditRepository,
    private val networkInfoRepository: NetworkInfoRepository = EnterwebSlingerApp.getContainer().networkInfoRepository,
    private val lanScannerRepository: LanScannerRepository = EnterwebSlingerApp.getContainer().lanScannerRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // --- Floorplan Heatmap State ---
    private val _floorplanPins = MutableStateFlow<List<FloorplanPin>>(emptyList())
    val floorplanPins: StateFlow<List<FloorplanPin>> = _floorplanPins.asStateFlow()

    private val _liveWifiSample = MutableStateFlow(rfAndBleRepository.sampleActiveWifiSnapshot())
    val liveWifiSample: StateFlow<ActiveWifiRfSample> = _liveWifiSample.asStateFlow()

    private val _isStepWalkActive = MutableStateFlow(false)
    val isStepWalkActive: StateFlow<Boolean> = _isStepWalkActive.asStateFlow()

    val isStepSensorAvailable: Boolean = rfAndBleRepository.isStepDetectorAvailable()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private var walkJob: Job? = null
    private var walkCursorX = 0.1f
    private var walkCursorY = 0.15f

    // --- BLE State ---
    val bleDevices: StateFlow<List<BleDeviceResult>> = rfAndBleRepository.bleDevices
    val isBleScanning: StateFlow<Boolean> = rfAndBleRepository.isBleScanning
    val bleError: StateFlow<String?> = rfAndBleRepository.bleError

    // --- Cellular State ---
    private val _cellularState = MutableStateFlow(CellularTelemetryState())
    val cellularState: StateFlow<CellularTelemetryState> = _cellularState.asStateFlow()

    private val _isCellularLoading = MutableStateFlow(false)
    val isCellularLoading: StateFlow<Boolean> = _isCellularLoading.asStateFlow()

    // --- ONVIF & Evil Twin State ---
    private val _onvifCameras = MutableStateFlow<List<OnvifCameraDevice>>(emptyList())
    val onvifCameras: StateFlow<List<OnvifCameraDevice>> = _onvifCameras.asStateFlow()

    private val _isScanningCameras = MutableStateFlow(false)
    val isScanningCameras: StateFlow<Boolean> = _isScanningCameras.asStateFlow()

    private val _evilTwinFindings = MutableStateFlow<List<EvilTwinFinding>>(emptyList())
    val evilTwinFindings: StateFlow<List<EvilTwinFinding>> = _evilTwinFindings.asStateFlow()

    private val _isAuditingEvilTwin = MutableStateFlow(false)
    val isAuditingEvilTwin: StateFlow<Boolean> = _isAuditingEvilTwin.asStateFlow()

    fun setSelectedTab(tabIndex: Int) {
        _selectedTab.value = tabIndex.coerceIn(0, 3)
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    fun refreshLiveWifiSample() {
        _liveWifiSample.value = rfAndBleRepository.sampleActiveWifiSnapshot()
    }

    fun dropPinAt(xRatio: Float, yRatio: Float) {
        val sample = rfAndBleRepository.sampleActiveWifiSnapshot()
        _liveWifiSample.value = sample
        val pin = FloorplanPin(
            id = rfAndBleRepository.newPinId(),
            xRatio = xRatio.coerceIn(0.02f, 0.98f),
            yRatio = yRatio.coerceIn(0.02f, 0.98f),
            ssid = sample.ssid,
            bssid = sample.bssid,
            rssiDbm = sample.rssiDbm,
            linkSpeedMbps = sample.linkSpeedMbps,
            frequencyMhz = sample.frequencyMhz
        )
        _floorplanPins.update { it + pin }
        _statusBanner.value = "Dropped RF pin #${_floorplanPins.value.size} (${sample.rssiDbm} dBm @ ${sample.linkSpeedMbps} Mbps)"
    }

    fun undoLastPin() {
        _floorplanPins.update { if (it.isNotEmpty()) it.dropLast(1) else it }
    }

    fun clearFloorplanPins() {
        _floorplanPins.value = emptyList()
        walkCursorX = 0.1f
        walkCursorY = 0.15f
    }

    fun toggleStepWalkMode() {
        if (_isStepWalkActive.value) {
            stopStepWalkMode()
            return
        }
        if (!isStepSensorAvailable) {
            _statusBanner.value = "Hardware Step Detector sensor unavailable on this device/emulator. Tap grid to drop pins manually."
            return
        }
        _isStepWalkActive.value = true
        _statusBanner.value = "Auto-walk active: each physical step drops a geotagged RSSI pin along the grid."
        walkJob = viewModelScope.launch {
            try {
                rfAndBleRepository.stepDetectorFlow().collect {
                    dropPinAt(walkCursorX, walkCursorY)
                    walkCursorX += 0.12f
                    if (walkCursorX > 0.92f) {
                        walkCursorX = 0.1f
                        walkCursorY = (walkCursorY + 0.15f).let { ny -> if (ny > 0.9f) 0.15f else ny }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isStepWalkActive.value = false
            }
        }
    }

    fun stopStepWalkMode() {
        walkJob?.cancel()
        walkJob = null
        _isStepWalkActive.value = false
    }

    fun interpolateIdw(xNorm: Float, yNorm: Float): Float {
        return rfAndBleRepository.interpolateIdwRssi(xNorm, yNorm, _floorplanPins.value)
    }

    suspend fun exportFloorplanPng(): Result<Pair<File, Uri>> {
        val res = rfAndBleRepository.exportFloorplanHeatmapPng(_floorplanPins.value)
        res.onFailure { _statusBanner.value = it.message }
        res.onSuccess { (file, _) -> _statusBanner.value = "Exported heatmap PNG: ${file.name}" }
        return res
    }

    // --- BLE Actions ---
    fun startBleScan() = rfAndBleRepository.startBleScan()
    fun stopBleScan() = rfAndBleRepository.stopBleScan()
    fun clearBleDevices() = rfAndBleRepository.clearBleDevices()
    fun hasBlePermission(): Boolean = rfAndBleRepository.hasBleScanPermission()

    // --- Cellular Actions ---
    fun refreshCellularTelemetry() {
        viewModelScope.launch {
            _isCellularLoading.value = true
            try {
                _cellularState.value = cellularRepository.inspectCellularTelemetry()
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isCellularLoading.value = false
            }
        }
    }

    fun hasCellularPermissions(): Boolean = cellularRepository.hasCellularPermissions()

    // --- ONVIF & Evil Twin Actions ---
    fun scanOnvifCameras() {
        viewModelScope.launch {
            _isScanningCameras.value = true
            _statusBanner.value = "Sending WS-Discovery (239.255.255.250:3702) & probing RTSP/NVR camera ports..."
            try {
                val lanIps = lanScannerRepository.devices.value.map { it.ip }
                val list = lanSecurityAuditRepository.discoverOnvifCameras(lanIps)
                _onvifCameras.value = list
                _statusBanner.value = "ONVIF/Camera discovery complete: ${list.size} transmitter(s) detected."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusBanner.value = "ONVIF scan error: ${e.message}"
            } finally {
                _isScanningCameras.value = false
            }
        }
    }

    fun runEvilTwinAudit() {
        viewModelScope.launch {
            _isAuditingEvilTwin.value = true
            try {
                val res = lanSecurityAuditRepository.auditEvilTwinAndWifiSecurity()
                res.fold(
                    onSuccess = { list ->
                        _evilTwinFindings.value = list
                        _statusBanner.value = if (list.isEmpty()) {
                            "Evil Twin audit complete: no rogue/downgrade BSSID anomalies observed."
                        } else {
                            "Evil Twin audit flagged ${list.size} finding(s)."
                        }
                    },
                    onFailure = { err ->
                        _statusBanner.value = err.message ?: "Wi-Fi scan permission required."
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isAuditingEvilTwin.value = false
            }
        }
    }

    fun baselineCurrentConnectedAp() {
        viewModelScope.launch {
            val top = networkInfoRepository.topology.value
            if (top.ssid.isBlank() || top.ssid == "Unknown SSID" || top.bssid.startsWith("--")) {
                _statusBanner.value = "Connect to Wi-Fi with Location permission granted before baselining."
                return@launch
            }
            lanSecurityAuditRepository.saveTrustedBaseline(top.ssid, top.bssid, "WPA2")
            _statusBanner.value = "Saved '${top.ssid}' (${top.bssid}) to Trusted AP Baseline."
        }
    }

    override fun onCleared() {
        stopStepWalkMode()
        stopBleScan()
        super.onCleared()
    }
}
