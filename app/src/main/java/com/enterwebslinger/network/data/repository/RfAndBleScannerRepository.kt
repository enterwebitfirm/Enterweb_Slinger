// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/RfAndBleScannerRepository.kt ===
package com.enterwebslinger.network.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.enterwebslinger.network.core.util.NetworkByteUtils
import com.enterwebslinger.network.domain.model.BleDeviceResult
import com.enterwebslinger.network.domain.model.FloorplanPin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

data class ActiveWifiRfSample(
    val ssid: String,
    val bssid: String,
    val rssiDbm: Int,
    val linkSpeedMbps: Int,
    val frequencyMhz: Int,
    val hasPermission: Boolean
)

/**
 * Pure-Kotlin / Android SDK Repository for:
 * 1) WiFiman-style Floorplan Signal Heatmap sampling + Step Detector sensor + IDW PNG export
 * 2) Low-Energy Bluetooth (BLE) Beacon & Peripheral Scanner
 */
class RfAndBleScannerRepository(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val _bleDevices = MutableStateFlow<List<BleDeviceResult>>(emptyList())
    val bleDevices: StateFlow<List<BleDeviceResult>> = _bleDevices.asStateFlow()

    private val _isBleScanning = MutableStateFlow(false)
    val isBleScanning: StateFlow<Boolean> = _isBleScanning.asStateFlow()

    private val _bleError = MutableStateFlow<String?>(null)
    val bleError: StateFlow<String?> = _bleError.asStateFlow()

    private var activeScanner: BluetoothLeScanner? = null
    private var activeCallback: ScanCallback? = null

    fun hasLocationOrWifiPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val nearby = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        return fine || nearby
    }

    fun hasBleScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasBleConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun isStepDetectorAvailable(): Boolean {
        return sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null
    }

    fun sampleActiveWifiSnapshot(): ActiveWifiRfSample {
        if (!hasLocationOrWifiPermission()) {
            return ActiveWifiRfSample(
                ssid = "Permission Required",
                bssid = "--:--:--:--:--:--",
                rssiDbm = -100,
                linkSpeedMbps = 0,
                frequencyMhz = 0,
                hasPermission = false
            )
        }
        return try {
            val info: WifiInfo? = getActiveWifiInfo()
            if (info != null && info.networkId != -1) {
                val rawSsid = info.ssid?.trim()?.removeSurrounding("\"") ?: ""
                ActiveWifiRfSample(
                    ssid = if (rawSsid.isBlank() || rawSsid == "<unknown ssid>") "Wi-Fi AP" else rawSsid,
                    bssid = info.bssid ?: "--:--:--:--:--:--",
                    rssiDbm = info.rssi.coerceIn(-120, -10),
                    linkSpeedMbps = info.linkSpeed.coerceAtLeast(0),
                    frequencyMhz = info.frequency.coerceAtLeast(0),
                    hasPermission = true
                )
            } else {
                ActiveWifiRfSample(
                    ssid = "Wi-Fi Disconnected",
                    bssid = "--:--:--:--:--:--",
                    rssiDbm = -100,
                    linkSpeedMbps = 0,
                    frequencyMhz = 0,
                    hasPermission = true
                )
            }
        } catch (_: SecurityException) {
            ActiveWifiRfSample("Permission Denied", "--:--:--:--:--:--", -100, 0, 0, false)
        } catch (_: Exception) {
            ActiveWifiRfSample("Unavailable", "--:--:--:--:--:--", -100, 0, 0, true)
        }
    }

    private fun getActiveWifiInfo(): WifiInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val active = connectivityManager?.activeNetwork
            val caps = connectivityManager?.getNetworkCapabilities(active)
            val info = caps?.transportInfo as? WifiInfo
            if (info != null) return info
        }
        @Suppress("DEPRECATION")
        return wifiManager?.connectionInfo
    }

    fun stepDetectorFlow(): Flow<Long> = callbackFlow {
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        if (sensor == null) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_STEP_DETECTOR) {
                    trySend(System.currentTimeMillis())
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    suspend fun exportFloorplanHeatmapPng(
        pins: List<FloorplanPin>,
        gridCols: Int = 10,
        gridRows: Int = 8
    ): Result<Pair<File, Uri>> = withContext(Dispatchers.IO) {
        if (pins.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Drop at least 1 RF survey pin on the floorplan before exporting."))
        }
        return@withContext try {
            val width = 1000
            val height = 800
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.rgb(8, 12, 22))

            // Render IDW interpolated heatmap across blocks for smooth, fast rendering
            val block = 16
            val cellPaint = Paint()
            for (py in 0 until height step block) {
                val yNorm = (py + block * 0.5f) / height.toFloat()
                for (px in 0 until width step block) {
                    val xNorm = (px + block * 0.5f) / width.toFloat()
                    val rssi = interpolateIdwRssi(xNorm, yNorm, pins)
                    cellPaint.color = rssiToHeatmapArgb(rssi, alpha = 185)
                    canvas.drawRect(
                        px.toFloat(),
                        py.toFloat(),
                        (px + block).toFloat(),
                        (py + block).toFloat(),
                        cellPaint
                    )
                }
            }

            // Blueprint grid overlay
            val gridPaint = Paint().apply {
                color = android.graphics.Color.argb(70, 148, 163, 184)
                strokeWidth = 1.5f
            }
            for (c in 0..gridCols) {
                val x = (c.toFloat() / gridCols) * width
                canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            }
            for (r in 0..gridRows) {
                val y = (r.toFloat() / gridRows) * height
                canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            }

            // Pin markers + RSSI labels
            val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                color = android.graphics.Color.WHITE
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 24f
                isFakeBoldText = true
            }

            pins.forEachIndexed { idx, pin ->
                val cx = pin.xRatio.coerceIn(0.02f, 0.98f) * width
                val cy = pin.yRatio.coerceIn(0.02f, 0.98f) * height
                circlePaint.color = rssiToHeatmapArgb(pin.rssiDbm.toFloat(), alpha = 255)
                canvas.drawCircle(cx, cy, 18f, circlePaint)
                canvas.drawCircle(cx, cy, 18f, borderPaint)
                canvas.drawText("#${idx + 1} ${pin.rssiDbm} dBm", (cx + 22f).coerceAtMost(width - 180f), cy + 8f, textPaint)
            }

            val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val outFile = File(reportsDir, "enterweb_floorplan_heatmap_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            bitmap.recycle()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
            Result.success(outFile to uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun interpolateIdwRssi(xNorm: Float, yNorm: Float, pins: List<FloorplanPin>): Float {
        if (pins.isEmpty()) return -100f
        var num = 0.0
        var den = 0.0
        for (pin in pins) {
            val dx = (xNorm - pin.xRatio).toDouble()
            val dy = (yNorm - pin.yRatio).toDouble()
            val distSq = dx * dx + dy * dy
            if (distSq < 1e-6) return pin.rssiDbm.toFloat()
            val w = 1.0 / distSq
            num += w * pin.rssiDbm
            den += w
        }
        return if (den > 0.0) (num / den).toFloat() else -100f
    }

    private fun rssiToHeatmapArgb(rssiDbm: Float, alpha: Int): Int {
        // -45 dBm -> Emerald (#10B981), -70 dBm -> Amber (#F59E0B), -95 dBm -> Crimson (#EF4444)
        val normalized = ((rssiDbm + 95f) / 50f).coerceIn(0f, 1f)
        val r: Int
        val g: Int
        val b: Int
        if (normalized >= 0.5f) {
            val t = (normalized - 0.5f) * 2f
            r = (245 + (16 - 245) * t).toInt()
            g = (158 + (185 - 158) * t).toInt()
            b = (11 + (129 - 11) * t).toInt()
        } else {
            val t = normalized * 2f
            r = (239 + (245 - 239) * t).toInt()
            g = (68 + (158 - 68) * t).toInt()
            b = (68 + (11 - 68) * t).toInt()
        }
        return android.graphics.Color.argb(alpha, r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    @SuppressLint("MissingPermission")
    fun startBleScan() {
        _bleError.value = null
        if (!hasBleScanPermission()) {
            _bleError.value = "Bluetooth scan permission (BLUETOOTH_SCAN / ACCESS_FINE_LOCATION) not granted."
            return
        }
        val adapter: BluetoothAdapter? = bluetoothManager?.adapter
        if (adapter == null) {
            _bleError.value = "No Bluetooth hardware adapter detected on this device/emulator."
            return
        }
        if (!adapter.isEnabled) {
            _bleError.value = "Bluetooth radio is turned off. Enable Bluetooth to scan BLE beacons."
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _bleError.value = "BluetoothLeScanner unavailable (device Bluetooth stack idle)."
            return
        }

        stopBleScan()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let { processScanResult(it) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { processScanResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                _isBleScanning.value = false
                _bleError.value = "BLE Scan failed with Android error code $errorCode."
            }
        }

        activeScanner = scanner
        activeCallback = callback
        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(null, settings, callback)
            _isBleScanning.value = true
        } catch (e: SecurityException) {
            _isBleScanning.value = false
            _bleError.value = "BLE permission denied: ${e.message}"
        } catch (e: Exception) {
            _isBleScanning.value = false
            _bleError.value = "BLE scan error: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    fun stopBleScan() {
        val cb = activeCallback
        val scanner = activeScanner
        activeCallback = null
        activeScanner = null
        _isBleScanning.value = false
        if (cb != null && scanner != null && hasBleScanPermission()) {
            runCatching { scanner.stopScan(cb) }
        }
    }

    fun clearBleDevices() {
        _bleDevices.value = emptyList()
    }

    @SuppressLint("MissingPermission")
    private fun processScanResult(result: ScanResult) {
        val address = result.device?.address ?: return
        val deviceName = if (hasBleConnectPermission()) {
            runCatching { result.device?.name }.getOrNull()
        } else null
        val resolvedName = deviceName?.takeIf { it.isNotBlank() }
            ?: result.scanRecord?.deviceName?.takeIf { it.isNotBlank() }
            ?: "Unknown BLE Device"

        val txPower = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && result.txPower != ScanResult.TX_POWER_NOT_PRESENT) {
            result.txPower
        } else {
            val recordTx = result.scanRecord?.txPowerLevel ?: Int.MIN_VALUE
            if (recordTx != Int.MIN_VALUE && recordTx in -100..20) recordTx else null
        }

        val rssi = result.rssi
        val refTx = txPower ?: -59
        val estDist = 10.0.pow((refTx - rssi).toDouble() / (10.0 * 2.2)).coerceIn(0.1, 150.0)

        val uuids = result.scanRecord?.serviceUuids?.map { it.uuid.toString().uppercase() }.orEmpty()
        var mfgHex = ""
        val mfgMap = result.scanRecord?.manufacturerSpecificData
        if (mfgMap != null && mfgMap.size() > 0) {
            val companyId = mfgMap.keyAt(0)
            val data = mfgMap.valueAt(0)
            if (data != null) {
                mfgHex = "0x${companyId.toString(16).uppercase()}: ${NetworkByteUtils.bytesToHex(data, " ").take(24)}"
            }
        }
        val connectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) result.isConnectable else true

        val entry = BleDeviceResult(
            address = address,
            name = resolvedName,
            rssiDbm = rssi,
            txPowerDbm = txPower,
            estimatedDistanceMeters = estDist,
            serviceUuids = uuids,
            isConnectable = connectable,
            manufacturerDataHex = mfgHex,
            lastSeenMs = System.currentTimeMillis()
        )

        val current = _bleDevices.value.toMutableList()
        val idx = current.indexOfFirst { it.address == address }
        if (idx >= 0) {
            current[idx] = entry
        } else {
            current.add(entry)
        }
        _bleDevices.value = current.sortedByDescending { it.rssiDbm }
    }

    fun estimateBleDistance(rssiDbm: Int, txPowerDbm: Int? = null): Double {
        val ref = txPowerDbm ?: -59
        return 10.0.pow(abs(ref - rssiDbm).let { (ref - rssiDbm).toDouble() } / (10.0 * 2.2)).coerceIn(0.1, 150.0)
    }

    fun newPinId(): String = UUID.randomUUID().toString().take(8)
}
