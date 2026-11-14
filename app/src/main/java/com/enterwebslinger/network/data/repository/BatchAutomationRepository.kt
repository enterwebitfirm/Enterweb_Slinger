// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/BatchAutomationRepository.kt ===
package com.enterwebslinger.network.data.repository

import android.net.TrafficStats
import com.enterwebslinger.network.core.security.CredentialVault
import com.enterwebslinger.network.data.local.db.dao.EndpointProfileDao
import com.enterwebslinger.network.domain.model.DiffLine
import com.enterwebslinger.network.domain.model.DiffLineType
import com.enterwebslinger.network.domain.model.SshBatchResult
import com.enterwebslinger.network.domain.model.SshTargetHost
import com.enterwebslinger.network.domain.model.TrafficRateSample
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.util.concurrent.TimeUnit

/**
 * Pure-Kotlin / JVM Multi-Host SSH Batch Automation Runner & Live Android TrafficStats Rate Monitor.
 */
class BatchAutomationRepository(
    private val scope: CoroutineScope,
    private val endpointProfileDao: EndpointProfileDao,
    private val credentialVault: CredentialVault
) {

    val savedSshTargetsFlow: Flow<List<SshTargetHost>> = endpointProfileDao.getAllProfilesFlow().map { profiles ->
        profiles.filter {
            it.profileType.contains("SSH", ignoreCase = true) ||
                it.profileType.contains("NETOS", ignoreCase = true) ||
                it.profileType.contains("TERMINAL", ignoreCase = true)
        }.map { entity ->
            val decryptedPwd = entity.credentialVaultKey.takeIf { it.isNotBlank() }
                ?.let { credentialVault.getSecret(it) }.orEmpty()
            SshTargetHost(
                id = "room_${entity.id}",
                name = entity.name,
                host = entity.host,
                port = entity.port,
                username = entity.username,
                password = decryptedPwd,
                vendorType = entity.profileType,
                isSelected = true
            )
        }
    }

    private val _trafficSamples = MutableStateFlow<List<TrafficRateSample>>(emptyList())
    val trafficSamples: StateFlow<List<TrafficRateSample>> = _trafficSamples.asStateFlow()

    private val _latestTrafficSample = MutableStateFlow(TrafficRateSample())
    val latestTrafficSample: StateFlow<TrafficRateSample> = _latestTrafficSample.asStateFlow()

    private var trafficMonitorJob: Job? = null

    fun startTrafficStatsMonitor() {
        if (trafficMonitorJob?.isActive == true) return
        trafficMonitorJob = scope.launch(Dispatchers.IO) {
            var prevRxBytes = TrafficStats.getTotalRxBytes()
            var prevTxBytes = TrafficStats.getTotalTxBytes()
            var prevMobRx = TrafficStats.getMobileRxBytes()
            var prevMobTx = TrafficStats.getMobileTxBytes()
            var prevRxPkts = TrafficStats.getTotalRxPackets()
            var prevTxPkts = TrafficStats.getTotalTxPackets()
            var lastTs = System.currentTimeMillis()

            val supported = prevRxBytes != TrafficStats.UNSUPPORTED.toLong() &&
                prevTxBytes != TrafficStats.UNSUPPORTED.toLong()

            while (isActive) {
                delay(1000L)
                val now = System.currentTimeMillis()
                val elapsedSec = ((now - lastTs) / 1000.0).coerceAtLeast(0.5)

                val curRxBytes = TrafficStats.getTotalRxBytes()
                val curTxBytes = TrafficStats.getTotalTxBytes()
                val curMobRx = TrafficStats.getMobileRxBytes()
                val curMobTx = TrafficStats.getMobileTxBytes()
                val curRxPkts = TrafficStats.getTotalRxPackets()
                val curTxPkts = TrafficStats.getTotalTxPackets()

                val sample = if (supported && curRxBytes >= 0 && curTxBytes >= 0) {
                    val rxBps = (((curRxBytes - prevRxBytes).coerceAtLeast(0L)) / elapsedSec).toLong()
                    val txBps = (((curTxBytes - prevTxBytes).coerceAtLeast(0L)) / elapsedSec).toLong()
                    val mRxBps = if (curMobRx >= 0 && prevMobRx >= 0) (((curMobRx - prevMobRx).coerceAtLeast(0L)) / elapsedSec).toLong() else 0L
                    val mTxBps = if (curMobTx >= 0 && prevMobTx >= 0) (((curMobTx - prevMobTx).coerceAtLeast(0L)) / elapsedSec).toLong() else 0L
                    val rxPps = if (curRxPkts >= 0 && prevRxPkts >= 0) (((curRxPkts - prevRxPkts).coerceAtLeast(0L)) / elapsedSec).toLong() else 0L
                    val txPps = if (curTxPkts >= 0 && prevTxPkts >= 0) (((curTxPkts - prevTxPkts).coerceAtLeast(0L)) / elapsedSec).toLong() else 0L

                    TrafficRateSample(
                        timestampMs = now,
                        totalRxBps = rxBps,
                        totalTxBps = txBps,
                        mobileRxBps = mRxBps,
                        mobileTxBps = mTxBps,
                        totalRxPps = rxPps,
                        totalTxPps = txPps,
                        cumulativeRxBytes = curRxBytes,
                        cumulativeTxBytes = curTxBytes,
                        isSupported = true
                    )
                } else {
                    TrafficRateSample(timestampMs = now, isSupported = false)
                }

                prevRxBytes = curRxBytes
                prevTxBytes = curTxBytes
                prevMobRx = curMobRx
                prevMobTx = curMobTx
                prevRxPkts = curRxPkts
                prevTxPkts = curTxPkts
                lastTs = now

                _latestTrafficSample.value = sample
                _trafficSamples.value = (_trafficSamples.value + sample).takeLast(40)
            }
        }
    }

    fun stopTrafficStatsMonitor() {
        trafficMonitorJob?.cancel()
        trafficMonitorJob = null
    }

    suspend fun runBatchSshCommand(
        targets: List<SshTargetHost>,
        command: String,
        goldenBaselineOutput: String = ""
    ): List<SshBatchResult> = withContext(Dispatchers.IO) {
        val activeTargets = targets.filter { it.isSelected && it.host.isNotBlank() }
        if (activeTargets.isEmpty() || command.isBlank()) return@withContext emptyList()

        val sem = Semaphore(6)
        val rawResults = coroutineScope {
            activeTargets.map { target ->
                async {
                    sem.withPermit {
                        executeSingleSsh(target, command)
                    }
                }
            }.awaitAll()
        }

        val effectiveBaseline = goldenBaselineOutput.takeIf { it.isNotBlank() }
            ?: rawResults.firstOrNull { it.isSuccess && it.output.isNotBlank() }?.output.orEmpty()

        rawResults.map { res ->
            if (!res.isSuccess || effectiveBaseline.isBlank()) {
                res
            } else {
                val (diffLines, summary) = computeOutputDiff(res.output, effectiveBaseline)
                res.copy(diffLines = diffLines, diffSummary = summary)
            }
        }
    }

    private fun executeSingleSsh(target: SshTargetHost, command: String): SshBatchResult {
        val t0 = System.currentTimeMillis()
        val ssh = SSHClient()
        ssh.connectTimeout = 8000
        ssh.timeout = 12000
        ssh.addHostKeyVerifier(PromiscuousVerifier())
        return try {
            ssh.connect(target.host.trim(), target.port)
            ssh.authPassword(target.username.trim(), target.password)
            ssh.startSession().use { session ->
                val cmd = session.exec(command)
                val out = cmd.inputStream.bufferedReader().use { it.readText() }
                cmd.join(8, TimeUnit.SECONDS)
                SshBatchResult(
                    targetName = target.name,
                    host = "${target.host}:${target.port}",
                    output = out,
                    durationMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L),
                    isSuccess = true
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SshBatchResult(
                targetName = target.name,
                host = "${target.host}:${target.port}",
                output = "",
                durationMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L),
                isSuccess = false,
                errorMessage = e.message ?: "SSH failed"
            )
        } finally {
            runCatching { ssh.disconnect() }
        }
    }

    private fun computeOutputDiff(output: String, baseline: String): Pair<List<DiffLine>, String> {
        val outLines = output.lines()
        val baseLines = baseline.lines()
        val baseSet = baseLines.map { it.trim() }.toSet()
        val outSet = outLines.map { it.trim() }.toSet()

        val diffs = mutableListOf<DiffLine>()
        var added = 0
        var removed = 0
        var lineNum = 1

        for (line in outLines) {
            val t = line.trim()
            if (t.isNotEmpty() && t !in baseSet) {
                diffs.add(DiffLine(DiffLineType.ADDED, lineNum, "+ $line"))
                added++
            } else {
                diffs.add(DiffLine(DiffLineType.UNCHANGED, lineNum, "  $line"))
            }
            lineNum++
        }
        for (line in baseLines) {
            val t = line.trim()
            if (t.isNotEmpty() && t !in outSet) {
                diffs.add(DiffLine(DiffLineType.REMOVED, lineNum, "- $line"))
                removed++
                lineNum++
            }
        }
        val summary = if (added == 0 && removed == 0) "Matches baseline" else "+$added / -$removed diff lines"
        return diffs to summary
    }
}
