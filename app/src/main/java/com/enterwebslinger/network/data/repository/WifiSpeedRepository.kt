// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/WifiSpeedRepository.kt ===
package com.enterwebslinger.network.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import com.enterwebslinger.network.core.util.NetworkUtils
import com.enterwebslinger.network.domain.model.ChannelCongestionInfo
import com.enterwebslinger.network.domain.model.SpeedPhase
import com.enterwebslinger.network.domain.model.SpeedTestState
import com.enterwebslinger.network.domain.model.UiState
import com.enterwebslinger.network.domain.model.WifiScanEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class WifiSpeedRepository(
    context: Context,
    private val networkInfoRepository: NetworkInfoRepository
) {
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _speedState = MutableStateFlow(SpeedTestState())
    val speedState: StateFlow<SpeedTestState> = _speedState.asStateFlow()

    private val _speedUiState = MutableStateFlow<UiState<SpeedTestState>>(UiState.Idle)
    val speedUiState: StateFlow<UiState<SpeedTestState>> = _speedUiState.asStateFlow()

    private val _wifiScanList = MutableStateFlow<List<WifiScanEntry>>(emptyList())
    val wifiScanList: StateFlow<List<WifiScanEntry>> = _wifiScanList.asStateFlow()

    private val _channelCongestion = MutableStateFlow<List<ChannelCongestionInfo>>(emptyList())
    val channelCongestion: StateFlow<List<ChannelCongestionInfo>> = _channelCongestion.asStateFlow()

    private val _wifiUiState = MutableStateFlow<UiState<List<WifiScanEntry>>>(UiState.Idle)
    val wifiUiState: StateFlow<UiState<List<WifiScanEntry>>> = _wifiUiState.asStateFlow()

    private var speedJob: Job? = null
    private var wifiScanJob: Job? = null

    fun dismissSpeedError() {
        _speedState.value = _speedState.value.copy(
            phase = if (_speedState.value.downloadSpeedMbps > 0f) SpeedPhase.COMPLETE else SpeedPhase.IDLE,
            statusText = if (_speedState.value.downloadSpeedMbps > 0f) "Speed test complete" else "Ready to Test"
        )
        _speedUiState.value = UiState.Idle
    }

    fun dismissWifiError() {
        val current = _wifiScanList.value
        _wifiUiState.value = if (current.isEmpty()) UiState.Idle else UiState.Success(current)
    }

    fun startSpeedTest(scope: CoroutineScope) {
        if (_speedState.value.phase != SpeedPhase.IDLE &&
            _speedState.value.phase != SpeedPhase.COMPLETE &&
            _speedState.value.phase != SpeedPhase.ERROR
        ) {
            return
        }
        speedJob?.cancel()
        _speedUiState.value = UiState.Loading

        speedJob = scope.launch(Dispatchers.IO) {
            try {
                _speedState.value = SpeedTestState(
                    phase = SpeedPhase.PING,
                    progress = 0.08f,
                    statusText = "Measuring baseline unloaded latency..."
                )

                val unloadedLatency = measureUnloadedLatency(sampleCount = 4)
                if (unloadedLatency <= 0f) {
                    val err = IllegalStateException("Speed test server unreachable (https://speed.cloudflare.com). Check Internet connectivity.")
                    _speedState.value = _speedState.value.copy(
                        phase = SpeedPhase.ERROR,
                        statusText = err.message!!
                    )
                    _speedUiState.value = UiState.Error(err.message!!, err)
                    return@launch
                }

                _speedState.value = _speedState.value.copy(
                    unloadedPingMs = unloadedLatency,
                    progress = 0.16f
                )
                delay(200L)

                _speedState.value = _speedState.value.copy(
                    phase = SpeedPhase.DOWNLOAD,
                    statusText = "Running 3-stream download & loaded bufferbloat probes..."
                )

                val (downloadSpeed, loadedLatency) = measureMultiStreamDownloadWithLoadedPing()
                val bufferbloat = if (loadedLatency > 0f) {
                    (loadedLatency - unloadedLatency).coerceAtLeast(0f)
                } else {
                    0f
                }

                _speedState.value = _speedState.value.copy(
                    downloadSpeedMbps = downloadSpeed,
                    currentRateMbps = downloadSpeed,
                    loadedPingMs = loadedLatency,
                    bufferbloatDeltaMs = bufferbloat,
                    progress = 0.65f
                )
                delay(200L)

                _speedState.value = _speedState.value.copy(
                    phase = SpeedPhase.UPLOAD,
                    statusText = "Measuring 3-stream upload throughput..."
                )

                val uploadSpeed = measureMultiStreamUpload()
                val finalState = _speedState.value.copy(
                    phase = SpeedPhase.COMPLETE,
                    uploadSpeedMbps = uploadSpeed,
                    currentRateMbps = downloadSpeed,
                    progress = 1.0f,
                    statusText = "Speed test complete"
                )
                _speedState.value = finalState
                _speedUiState.value = UiState.Success(finalState)
            } catch (e: CancellationException) {
                val idle = SpeedTestState(phase = SpeedPhase.IDLE, statusText = "Speed test cancelled")
                _speedState.value = idle
                _speedUiState.value = UiState.Idle
                throw e
            } catch (e: Exception) {
                val msg = "Speed test failed: ${e.message ?: "Network error"}"
                _speedState.value = _speedState.value.copy(
                    phase = SpeedPhase.ERROR,
                    statusText = msg
                )
                _speedUiState.value = UiState.Error(msg, e)
            }
        }
    }

    fun stopSpeedTest() {
        speedJob?.cancel()
        speedJob = null
        val idle = SpeedTestState(
            phase = SpeedPhase.IDLE,
            statusText = "Ready to Test"
        )
        _speedState.value = idle
        _speedUiState.value = UiState.Idle
    }

    private suspend fun measureUnloadedLatency(sampleCount: Int = 4): Float = withContext(Dispatchers.IO) {
        val samples = mutableListOf<Float>()
        for (i in 0 until sampleCount) {
            val t0 = System.nanoTime()
            try {
                val req = Request.Builder().url("https://speed.cloudflare.com/__down?bytes=0").build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val deltaMs = ((System.nanoTime() - t0) / 1_000_000f).coerceAtLeast(1f)
                        samples.add(deltaMs)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
            delay(60L)
        }
        if (samples.isNotEmpty()) samples.average().toFloat() else 0f
    }

    private suspend fun measureMultiStreamDownloadWithLoadedPing(): Pair<Float, Float> = coroutineScope {
        val streamCount = 3
        val bytesPerStream = 25_000_000L
        val totalExpectedBytes = streamCount * bytesPerStream
        val downloadUrl = "https://speed.cloudflare.com/__down?bytes=$bytesPerStream"

        val totalBytes = AtomicLong(0L)
        val isDownloading = AtomicBoolean(true)
        val startTime = System.currentTimeMillis()
        val rates = Collections.synchronizedList(mutableListOf<Float>())
        val loadedPingSamples = Collections.synchronizedList(mutableListOf<Float>())

        // Loaded latency probe runs CONCURRENTLY with multi-stream download load to measure true bufferbloat
        val loadedPingJob = async(Dispatchers.IO) {
            while (isActive && isDownloading.get()) {
                val t0 = System.nanoTime()
                try {
                    val req = Request.Builder().url("https://speed.cloudflare.com/__down?bytes=0").build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val rttMs = ((System.nanoTime() - t0) / 1_000_000f).coerceAtLeast(1f)
                            loadedPingSamples.add(rttMs)
                            _speedState.value = _speedState.value.copy(loadedPingMs = rttMs)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
                delay(250L)
            }
        }

        val samplerJob = async(Dispatchers.IO) {
            while (isActive && isDownloading.get()) {
                delay(200L)
                val elapsed = System.currentTimeMillis() - startTime
                val bytes = totalBytes.get()
                if (elapsed > 0 && bytes > 0) {
                    val instantMbps = (bytes * 8f) / (elapsed * 1000f)
                    rates.add(instantMbps)
                    _speedState.value = _speedState.value.copy(
                        currentRateMbps = instantMbps,
                        progress = 0.16f + (bytes.toFloat() / totalExpectedBytes.toFloat() * 0.49f).coerceIn(0f, 0.49f),
                        liveRateHistory = rates.takeLast(20)
                    )
                }
            }
        }

        val downloadStreams = (0 until streamCount).map {
            async(Dispatchers.IO) {
                try {
                    val req = Request.Builder().url(downloadUrl).build()
                    okHttpClient.newCall(req).execute().use { response ->
                        val body = response.body ?: return@async
                        val input: InputStream = body.byteStream()
                        val buffer = ByteArray(16384)
                        while (isActive) {
                            val bytesRead = input.read(buffer)
                            if (bytesRead == -1) break
                            totalBytes.addAndGet(bytesRead.toLong())
                            if (System.currentTimeMillis() - startTime > 9000L) {
                                // Cap test duration at 9s on fast links cleanly
                                break
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
            }
        }

        downloadStreams.awaitAll()
        isDownloading.set(false)
        samplerJob.cancel()
        loadedPingJob.cancel()

        val downloaded = totalBytes.get()
        if (downloaded <= 0L) {
            throw IllegalStateException("Download stream transferred 0 bytes. Check Internet connectivity.")
        }

        val totalTimeMs = (System.currentTimeMillis() - startTime).coerceAtLeast(100L)
        val aggregateMbps = (downloaded * 8f) / (totalTimeMs * 1000f)
        val avgLoadedPing = if (loadedPingSamples.isNotEmpty()) {
            loadedPingSamples.average().toFloat()
        } else {
            _speedState.value.unloadedPingMs
        }

        aggregateMbps to avgLoadedPing
    }

    private suspend fun measureMultiStreamUpload(): Float = coroutineScope {
        val uploadUrl = "https://speed.cloudflare.com/__up"
        val streamCount = 3
        val payloadSizePerStream = 3 * 1024 * 1024
        val payloadBytes = ByteArray(payloadSizePerStream) { idx -> (idx * 31).toByte() }
        val uploadedBytes = AtomicLong(0L)
        val startTime = System.currentTimeMillis()

        val uploadTasks = (0 until streamCount).map {
            async(Dispatchers.IO) {
                try {
                    val body = payloadBytes.toRequestBody("application/octet-stream".toMediaType())
                    val req = Request.Builder().url(uploadUrl).post(body).build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val totalNow = uploadedBytes.addAndGet(payloadSizePerStream.toLong())
                            val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(100L)
                            val mbps = (totalNow * 8f) / (elapsed * 1000f)
                            _speedState.value = _speedState.value.copy(
                                currentRateMbps = mbps,
                                progress = 0.65f + (totalNow.toFloat() / (streamCount * payloadSizePerStream).toFloat() * 0.35f).coerceIn(0f, 0.35f)
                            )
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
            }
        }

        uploadTasks.awaitAll()
        val totalUploaded = uploadedBytes.get()
        if (totalUploaded <= 0L) return@coroutineScope 0f
        val totalTimeMs = (System.currentTimeMillis() - startTime).coerceAtLeast(100L)
        (totalUploaded * 8f) / (totalTimeMs * 1000f)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun refreshWifiScan(scope: CoroutineScope? = null) {
        val targetScope = scope ?: return executeWifiScanSync()
        wifiScanJob?.cancel()
        wifiScanJob = targetScope.launch(Dispatchers.IO) {
            executeWifiScanSync()
        }
    }

    fun cancelWifiScan() {
        wifiScanJob?.cancel()
        wifiScanJob = null
        val current = _wifiScanList.value
        _wifiUiState.value = if (current.isEmpty()) UiState.Idle else UiState.Success(current)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun executeWifiScanSync() {
        val wm = wifiManager
        if (wm == null) {
            _wifiScanList.value = emptyList()
            _channelCongestion.value = emptyList()
            _wifiUiState.value = UiState.Error("Wi-Fi hardware service unavailable on this device.")
            return
        }

        _wifiUiState.value = UiState.Loading
        val currentTopology = networkInfoRepository.topology.value

        try {
            if (!wm.isWifiEnabled) {
                _wifiScanList.value = emptyList()
                _channelCongestion.value = emptyList()
                _wifiUiState.value = UiState.Error("Wi-Fi is disabled. Enable Wi-Fi to scan nearby access points.")
                return
            }

            // Trigger OS Wi-Fi scan (respecting Android 9+ system throttling gracefully)
            runCatching { wm.startScan() }

            val results: List<ScanResult>? = wm.scanResults
            if (results != null) {
                val entries = results.map { sr ->
                    val freq = sr.frequency
                    val channel = NetworkUtils.frequencyToChannel(freq)
                    val band = NetworkUtils.frequencyToBand(freq)
                    val isCurrent = (sr.BSSID != null && sr.BSSID.equals(currentTopology.bssid, ignoreCase = true)) ||
                        (sr.SSID != null && sr.SSID.isNotBlank() && sr.SSID == currentTopology.ssid)

                    WifiScanEntry(
                        ssid = if (sr.SSID.isNullOrBlank()) "<Hidden SSID>" else sr.SSID,
                        bssid = sr.BSSID ?: "--:--:--:--:--:--",
                        rssiDbm = sr.level,
                        frequencyMhz = freq,
                        band = band,
                        channel = channel,
                        channelWidthMhz = decodeChannelWidthMhz(sr),
                        security = sr.capabilities ?: "Open",
                        isCurrentNetwork = isCurrent
                    )
                }.sortedByDescending { it.rssiDbm }

                _wifiScanList.value = entries
                _channelCongestion.value = computeChannelCongestion(entries)
                _wifiUiState.value = UiState.Success(entries)
            } else {
                _wifiScanList.value = emptyList()
                _channelCongestion.value = emptyList()
                _wifiUiState.value = UiState.Error("No Wi-Fi scan results returned. Ensure Location Services are enabled.")
            }
        } catch (e: SecurityException) {
            _wifiScanList.value = emptyList()
            _channelCongestion.value = emptyList()
            _wifiUiState.value = UiState.Error("Permission denied: Grant Location and Nearby Wi-Fi Devices permissions.", e)
        } catch (e: Exception) {
            _wifiUiState.value = UiState.Error("Wi-Fi scan failed: ${e.message ?: "Unknown error"}", e)
        }
    }

    private fun decodeChannelWidthMhz(sr: ScanResult): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return when (sr.channelWidth) {
                ScanResult.CHANNEL_WIDTH_20MHZ -> 20
                ScanResult.CHANNEL_WIDTH_40MHZ -> 40
                ScanResult.CHANNEL_WIDTH_80MHZ -> 80
                ScanResult.CHANNEL_WIDTH_160MHZ -> 160
                ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160
                else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && sr.channelWidth == 5) 320 else 20
            }
        }
        return 20
    }

    /**
     * Computes channel congestion score (0..100%) per active channel, accounting for
     * co-channel and adjacent-channel overlap in 2.4 GHz (channels 1, 6, 11) and 5/6 GHz.
     */
    internal fun computeChannelCongestion(entries: List<WifiScanEntry>): List<ChannelCongestionInfo> {
        val result = mutableListOf<ChannelCongestionInfo>()

        // 2.4 GHz non-overlapping canonical channels: 1, 6, 11
        val aps24 = entries.filter { it.band == "2.4 GHz" && it.channel > 0 }
        if (aps24.isNotEmpty() || entries.isEmpty()) {
            val candidate24 = listOf(1, 6, 11)
            val scores24 = candidate24.map { ch ->
                val coAndAdj = aps24.filter { kotlin.math.abs(it.channel - ch) <= 2 }
                val score = coAndAdj.sumOf { ap ->
                    val rssiWeight = ((ap.rssiDbm + 100).coerceIn(5, 60))
                    val overlapFactor = if (ap.channel == ch) 1.0 else 0.55
                    (rssiWeight * overlapFactor).toInt()
                }.coerceIn(0, 100)
                val maxRssi = coAndAdj.maxOfOrNull { it.rssiDbm } ?: -100
                Triple(ch, coAndAdj.count { it.channel == ch }, score to maxRssi)
            }
            val minScore24 = scores24.minOfOrNull { it.third.first } ?: 0
            scores24.forEach { (ch, count, scorePair) ->
                result.add(
                    ChannelCongestionInfo(
                        channel = ch,
                        band = "2.4 GHz",
                        apCount = count,
                        congestionPercent = scorePair.first,
                        strongestRssiDbm = scorePair.second,
                        isRecommended = scorePair.first == minScore24
                    )
                )
            }
        }

        // 5 GHz & 6 GHz channels observed + common 5 GHz candidates
        val aps5 = entries.filter { it.band == "5 GHz" && it.channel > 0 }
        if (aps5.isNotEmpty()) {
            val candidate5 = (listOf(36, 40, 44, 48, 149, 153, 157, 161) + aps5.map { it.channel }).distinct().sorted()
            val scores5 = candidate5.map { ch ->
                val onChannel = aps5.filter { it.channel == ch }
                val score = onChannel.sumOf { (it.rssiDbm + 100).coerceIn(5, 55) }.coerceIn(0, 100)
                val maxRssi = onChannel.maxOfOrNull { it.rssiDbm } ?: -100
                Triple(ch, onChannel.size, score to maxRssi)
            }
            val minScore5 = scores5.minOfOrNull { it.third.first } ?: 0
            scores5.forEach { (ch, count, scorePair) ->
                result.add(
                    ChannelCongestionInfo(
                        channel = ch,
                        band = "5 GHz",
                        apCount = count,
                        congestionPercent = scorePair.first,
                        strongestRssiDbm = scorePair.second,
                        isRecommended = scorePair.first == minScore5 && count <= 1
                    )
                )
            }
        }

        val aps6 = entries.filter { it.band == "6 GHz" && it.channel > 0 }
        if (aps6.isNotEmpty()) {
            val grouped6 = aps6.groupBy { it.channel }
            val minCount = grouped6.values.minOfOrNull { it.size } ?: 0
            grouped6.keys.sorted().forEach { ch ->
                val list = grouped6[ch].orEmpty()
                val score = list.sumOf { (it.rssiDbm + 100).coerceIn(5, 50) }.coerceIn(0, 100)
                val maxRssi = list.maxOfOrNull { it.rssiDbm } ?: -100
                result.add(
                    ChannelCongestionInfo(
                        channel = ch,
                        band = "6 GHz",
                        apCount = list.size,
                        congestionPercent = score,
                        strongestRssiDbm = maxRssi,
                        isRecommended = list.size == minCount
                    )
                )
            }
        }

        return result
    }
}
