// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/MtuDiscoveryRepository.kt ===
package com.enterwebslinger.network.data.repository

import com.enterwebslinger.network.core.util.NetworkByteUtils
import com.enterwebslinger.network.domain.model.MtuDiscoveryResult
import com.enterwebslinger.network.domain.model.MtuProbeStep
import com.enterwebslinger.network.domain.model.MtuTunnelRecommendation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Pure-Kotlin / JVM Path MTU Discovery (PMTUD) & Fragmentation Auditor.
 * Runs binary search across 576..1500 wire MTU using OS ping DF flag (`-M do`),
 * computes exact IP/ICMP overhead (+28 IPv4, +48 IPv6), and calculates tunnel MTU
 * and TCP MSS clamping recommendations.
 */
class MtuDiscoveryRepository {

    companion object {
        const val IPV4_ICMP_OVERHEAD = 28
        const val IPV6_ICMP_OVERHEAD = 48
        const val MIN_WIRE_MTU = 576
        const val MAX_WIRE_MTU = 1500
    }

    suspend fun discoverPathMtu(
        targetHost: String,
        minWireMtu: Int = MIN_WIRE_MTU,
        maxWireMtu: Int = MAX_WIRE_MTU,
        onStepUpdate: ((MtuProbeStep) -> Unit)? = null
    ): MtuDiscoveryResult = withContext(Dispatchers.IO) {
        val cleanHost = targetHost.trim()
        if (cleanHost.isBlank()) {
            return@withContext MtuDiscoveryResult(
                targetHost = cleanHost,
                resolvedIp = "",
                isIpv6 = false,
                ipIcmpOverheadBytes = IPV4_ICMP_OVERHEAD,
                maxPayloadBytes = 0,
                pathMtuBytes = 0,
                errorMessage = "Target host or IP address is required."
            )
        }

        val resolvedAddr = try {
            InetAddress.getByName(cleanHost)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext MtuDiscoveryResult(
                targetHost = cleanHost,
                resolvedIp = "",
                isIpv6 = false,
                ipIcmpOverheadBytes = IPV4_ICMP_OVERHEAD,
                maxPayloadBytes = 0,
                pathMtuBytes = 0,
                errorMessage = "DNS resolution failed for '$cleanHost': ${e.message}"
            )
        }

        val resolvedIp = resolvedAddr.hostAddress ?: cleanHost
        val isIpv6 = resolvedAddr is Inet6Address
        val overhead = if (isIpv6) IPV6_ICMP_OVERHEAD else IPV4_ICMP_OVERHEAD

        val lowWire = minWireMtu.coerceAtLeast(if (isIpv6) 1280 else 68)
        val highWire = maxWireMtu.coerceAtLeast(lowWire)
        val minPayload = (lowWire - overhead).coerceAtLeast(20)
        val maxPayload = (highWire - overhead).coerceAtLeast(minPayload)

        val steps = mutableListOf<MtuProbeStep>()

        // Baseline check at minimum payload to confirm host responds to DF probes
        val baselineStep = probeDfPayload(resolvedIp, isIpv6, minPayload, overhead)
        steps.add(baselineStep)
        onStepUpdate?.invoke(baselineStep)

        if (!baselineStep.succeeded) {
            // Try probing 1472/1232 once in case minimum DF check was treated strangely, otherwise report failure
            val recs = buildRecommendations(lowWire, isIpv6)
            return@withContext MtuDiscoveryResult(
                targetHost = cleanHost,
                resolvedIp = resolvedIp,
                isIpv6 = isIpv6,
                ipIcmpOverheadBytes = overhead,
                maxPayloadBytes = 0,
                pathMtuBytes = 0,
                minProbedBytes = lowWire,
                maxProbedBytes = highWire,
                steps = steps,
                recommendations = recs,
                errorMessage = "Baseline DF probe ($lowWire B) failed: ${baselineStep.statusMessage}. Target may drop ICMP."
            )
        }

        var lo = minPayload
        var hi = maxPayload
        var bestPayload = minPayload

        // Quick optimistic check at maxPayload first (standard 1500 byte Ethernet MTU)
        val maxStep = probeDfPayload(resolvedIp, isIpv6, maxPayload, overhead)
        steps.add(maxStep)
        onStepUpdate?.invoke(maxStep)

        if (maxStep.succeeded) {
            bestPayload = maxPayload
        } else {
            hi = maxPayload - 1
            lo = minPayload + 1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                val step = probeDfPayload(resolvedIp, isIpv6, mid, overhead)
                steps.add(step)
                onStepUpdate?.invoke(step)
                if (step.succeeded) {
                    bestPayload = mid
                    lo = mid + 1
                } else {
                    hi = mid - 1
                }
            }
        }

        val discoveredPathMtu = bestPayload + overhead
        val recommendations = buildRecommendations(discoveredPathMtu, isIpv6)

        MtuDiscoveryResult(
            targetHost = cleanHost,
            resolvedIp = resolvedIp,
            isIpv6 = isIpv6,
            ipIcmpOverheadBytes = overhead,
            maxPayloadBytes = bestPayload,
            pathMtuBytes = discoveredPathMtu,
            minProbedBytes = lowWire,
            maxProbedBytes = highWire,
            steps = steps,
            recommendations = recommendations
        )
    }

    private fun probeDfPayload(
        targetIp: String,
        isIpv6: Boolean,
        payloadSize: Int,
        overhead: Int
    ): MtuProbeStep {
        val wireSize = payloadSize + overhead
        val cmd = if (isIpv6) {
            listOf("ping6", "-c", "1", "-W", "1", "-M", "do", "-s", payloadSize.toString(), targetIp)
        } else {
            listOf("ping", "-c", "1", "-W", "1", "-M", "do", "-s", payloadSize.toString(), targetIp)
        }

        val startMs = System.currentTimeMillis()
        var process: Process? = null
        try {
            process = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val outputBuilder = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    outputBuilder.append(line).append('\n')
                }
            }
            process.waitFor(3, TimeUnit.SECONDS)
            val exitCode = runCatching { process.exitValue() }.getOrDefault(-1)
            val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            val output = outputBuilder.toString()

            val fragDetected = output.contains("Frag needed", ignoreCase = true) ||
                output.contains("Message too long", ignoreCase = true) ||
                output.contains("packet too big", ignoreCase = true) ||
                output.contains("DF set", ignoreCase = true)

            val receivedReply = exitCode == 0 && (
                output.contains("1 received", ignoreCase = true) ||
                    output.contains("bytes from", ignoreCase = true)
                ) && !fragDetected

            val parsedRtt = Regex("""time[=<](\d+(?:\.\d+)?)\s*ms""")
                .find(output)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toLong()?.coerceAtLeast(1L)
                ?: elapsedMs

            val statusMsg = when {
                receivedReply -> "OK ($wireSize B wire, DF honored)"
                fragDetected -> "Frag needed and DF set (Message too long at $wireSize B)"
                output.contains("not permitted", ignoreCase = true) && isIpv6 -> {
                    // Fallback to `ping -6` if ping6 binary is unavailable
                    return probeDfIpv6Fallback(targetIp, payloadSize, overhead)
                }
                output.isNotBlank() -> output.lines().firstOrNull { it.isNotBlank() }?.take(96) ?: "No reply"
                else -> "Timeout / ICMP blocked (exit $exitCode)"
            }

            return MtuProbeStep(
                payloadSizeBytes = payloadSize,
                totalWireSizeBytes = wireSize,
                succeeded = receivedReply,
                statusMessage = statusMsg,
                rttMs = parsedRtt
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isIpv6) {
                return probeDfIpv6Fallback(targetIp, payloadSize, overhead)
            }
            return MtuProbeStep(
                payloadSizeBytes = payloadSize,
                totalWireSizeBytes = wireSize,
                succeeded = false,
                statusMessage = "Probe error: ${e.message ?: "Command failed"}",
                rttMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            )
        } finally {
            NetworkByteUtils.destroyProcess(process)
        }
    }

    private fun probeDfIpv6Fallback(targetIp: String, payloadSize: Int, overhead: Int): MtuProbeStep {
        val wireSize = payloadSize + overhead
        val startMs = System.currentTimeMillis()
        var process: Process? = null
        return try {
            process = ProcessBuilder(
                "ping", "-6", "-c", "1", "-W", "1", "-M", "do", "-s", payloadSize.toString(), targetIp
            ).redirectErrorStream(true).start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            process.waitFor(3, TimeUnit.SECONDS)
            val exitCode = runCatching { process.exitValue() }.getOrDefault(-1)
            val frag = output.contains("Message too long", true) || output.contains("Frag needed", true)
            val ok = exitCode == 0 && !frag && output.contains("bytes from", true)
            val parsedRtt = Regex("""time[=<](\d+(?:\.\d+)?)\s*ms""")
                .find(output)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toLong()
                ?: (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            MtuProbeStep(
                payloadSizeBytes = payloadSize,
                totalWireSizeBytes = wireSize,
                succeeded = ok,
                statusMessage = when {
                    ok -> "OK ($wireSize B IPv6 wire)"
                    frag -> "Packet too big / Frag needed ($wireSize B)"
                    else -> output.lines().firstOrNull { it.isNotBlank() }?.take(96) ?: "Timeout"
                },
                rttMs = parsedRtt
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MtuProbeStep(
                payloadSizeBytes = payloadSize,
                totalWireSizeBytes = wireSize,
                succeeded = false,
                statusMessage = e.message ?: "IPv6 probe unavailable",
                rttMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            )
        } finally {
            NetworkByteUtils.destroyProcess(process)
        }
    }

    fun buildRecommendations(pathMtuBytes: Int, isIpv6: Boolean): List<MtuTunnelRecommendation> {
        val clampedPath = pathMtuBytes.coerceIn(576, 9000)
        val tcpHeaderOverhead = if (isIpv6) 60 else 40 // IPv4(20)+TCP(20) or IPv6(40)+TCP(20)

        val pppoeMtu = minOf(clampedPath, 1492)
        val wireGuardMtu = minOf(clampedPath - 80, 1420).coerceAtLeast(1280)
        val ipsecMtu = minOf(clampedPath - 100, 1380).coerceIn(1280, 1400)
        val vxlanMtu = minOf(clampedPath - 50, 1450).coerceAtLeast(1280)

        return listOf(
            MtuTunnelRecommendation(
                tunnelName = "PPPoE DSL / Fiber (RFC 4638)",
                recommendedMtu = pppoeMtu,
                recommendedTcpMss = (pppoeMtu - tcpHeaderOverhead).coerceAtLeast(536),
                notes = "8-byte PPPoE+PPP framing overhead (cap 1492 on 1500 Ethernet)"
            ),
            MtuTunnelRecommendation(
                tunnelName = "WireGuard (UDP Noise)",
                recommendedMtu = wireGuardMtu,
                recommendedTcpMss = (wireGuardMtu - tcpHeaderOverhead).coerceAtLeast(536),
                notes = "60-80 byte UDP/ChaCha20-Poly1305 header allowance (target 1420)"
            ),
            MtuTunnelRecommendation(
                tunnelName = "IPsec XFRM / IKEv2 ESP",
                recommendedMtu = ipsecMtu,
                recommendedTcpMss = (ipsecMtu - tcpHeaderOverhead).coerceAtLeast(536),
                notes = "ESP NAT-T + AES-GCM trailer/padding range 1360–1400"
            ),
            MtuTunnelRecommendation(
                tunnelName = "VXLAN L2-over-UDP Overlay",
                recommendedMtu = vxlanMtu,
                recommendedTcpMss = (vxlanMtu - tcpHeaderOverhead).coerceAtLeast(536),
                notes = "50-byte VXLAN+UDP+outer IP header overhead (target 1450)"
            )
        )
    }
}
