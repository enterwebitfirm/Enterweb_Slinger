// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/AiDiagnosticRepository.kt ===
package com.enterwebslinger.network.data.repository

import android.content.Context
import android.util.Log
import com.enterwebslinger.network.core.security.CredentialVault
import com.enterwebslinger.network.domain.model.AiAuditFinding
import com.enterwebslinger.network.domain.model.AiAuditReport
import com.enterwebslinger.network.domain.model.DiscoveredDevice
import com.enterwebslinger.network.domain.model.TracerouteHop
import com.enterwebslinger.network.domain.model.WifiScanEntry
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "AiDiagnosticRepository"

class AiDiagnosticRepository(
    private val context: Context,
    private val telemetryAggregator: NetworkTelemetryAggregator,
    private val credentialVault: CredentialVault
) {
    private val okHttpClient = OkHttpClient.Builder().build()

    suspend fun runNetworkHealthAudit(): Result<AiAuditReport> = withContext(Dispatchers.IO) {
        val snapshot = telemetryAggregator.buildSnapshot()
        val prompt = "Analyze this network telemetry snapshot and return a health audit JSON with healthScore, primaryBottleneck, severity, executiveSummary, and findings list:\n${telemetryAggregator.snapshotToJsonPrompt(snapshot)}"

        // Tier 1: Firebase AI
        val firebaseResult = runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel("gemini-2.5-flash")
            val resp = model.generateContent(prompt)
            resp.text
        }

        if (firebaseResult.isSuccess && !firebaseResult.getOrNull().isNullOrBlank()) {
            val text = firebaseResult.getOrNull()!!
            val parsed = parseAuditJson(text)
            if (parsed != null) return@withContext Result.success(parsed)
        }

        // Tier 2: Direct REST API via CredentialVault Gemini key
        val apiKey = credentialVault.getGeminiFallbackApiKey()
        if (!apiKey.isNullOrBlank()) {
            val restResult = runCatching {
                callGeminiRestApi(apiKey, prompt)
            }
            if (restResult.isSuccess && !restResult.getOrNull().isNullOrBlank()) {
                val text = restResult.getOrNull()!!
                val parsed = parseAuditJson(text)
                if (parsed != null) return@withContext Result.success(parsed)
            }
        }

        // Tier 3: Deterministic Local Telemetry Analyzer (Zero App Check failure fallback)
        val localAudit = computeDeterministicAudit(snapshot)
        Result.success(localAudit)
    }

    suspend fun recommendWifiChannel(
        currentChannel: Int,
        currentBand: String,
        aps: List<WifiScanEntry>
    ): String = withContext(Dispatchers.IO) {
        val prompt = "Recommend cleanest Wi-Fi channel for current channel $currentChannel on $currentBand given ${aps.size} scanned APs."

        // Tier 1: Firebase AI
        val fb = runCatching {
            val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel("gemini-2.5-flash")
            model.generateContent(prompt).text
        }.getOrNull()
        if (!fb.isNullOrBlank()) return@withContext fb

        // Tier 2: Direct REST
        val apiKey = credentialVault.getGeminiFallbackApiKey()
        if (!apiKey.isNullOrBlank()) {
            val rest = runCatching { callGeminiRestApi(apiKey, prompt) }.getOrNull()
            if (!rest.isNullOrBlank()) return@withContext rest
        }

        // Tier 3: Deterministic local recommendation
        val recommended = if (currentBand == "2.4 GHz") {
            val channels = listOf(1, 6, 11)
            channels.minByOrNull { ch -> aps.count { it.channel == ch } } ?: 1
        } else {
            val cleanChannels = listOf(36, 40, 44, 48, 149, 153, 157, 161)
            cleanChannels.minByOrNull { ch -> aps.count { it.channel == ch } } ?: 36
        }
        "Wi-Fi Analyzer Recommendation: Based on local RF congestion analysis across ${aps.size} APs, switch to channel $recommended on $currentBand to minimize co-channel interference and maintain optimal link speed."
    }

    suspend fun explainTraceroute(hops: List<TracerouteHop>): String = withContext(Dispatchers.IO) {
        val summary = hops.joinToString("\n") { "Hop ${it.hopNumber}: ${it.ip} - ${if (it.isTimeout) "TIMEOUT" else "${it.rttMs} ms"}" }
        val prompt = "Explain traceroute path:\n$summary"

        val fb = runCatching {
            val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel("gemini-2.5-flash")
            model.generateContent(prompt).text
        }.getOrNull()
        if (!fb.isNullOrBlank()) return@withContext fb

        val apiKey = credentialVault.getGeminiFallbackApiKey()
        if (!apiKey.isNullOrBlank()) {
            val rest = runCatching { callGeminiRestApi(apiKey, prompt) }.getOrNull()
            if (!rest.isNullOrBlank()) return@withContext rest
        }

        val maxHop = hops.maxByOrNull { it.rttMs }
        "Traceroute Analysis (Local Engine): Reached destination across ${hops.size} hops. Highest latency observed at hop ${maxHop?.hopNumber ?: 1} (${maxHop?.rttMs ?: 0f} ms). Intermediate hop timeouts or latency spikes typically reflect carrier ICMP control-plane rate-limiting rather than link congestion."
    }

    suspend fun auditDeviceSecurity(device: DiscoveredDevice): String = withContext(Dispatchers.IO) {
        val prompt = "Audit device ${device.ip} (${device.hostname}, ${device.vendor}) with ports ${device.openPorts}."
        val fb = runCatching {
            val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel("gemini-2.5-flash")
            model.generateContent(prompt).text
        }.getOrNull()
        if (!fb.isNullOrBlank()) return@withContext fb

        val insecure = device.openPorts.filter { it in listOf(21, 23, 80, 445, 1433, 3389) }
        buildString {
            appendLine("Security Audit for ${device.ip} (${device.vendor}):")
            if (insecure.isNotEmpty()) {
                appendLine("- WARNING: Insecure legacy ports detected: ${insecure.joinToString(", ")}")
                appendLine("- Recommendation: Disable unencrypted services (Telnet 23, HTTP 80, SMB 445) and enforce SSHv2 / TLS 1.3.")
            } else {
                appendLine("- Status: No high-risk unencrypted management ports detected in open port set.")
            }
        }
    }

    private fun callGeminiRestApi(apiKey: String, prompt: String): String? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            }))
        }
        val body = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(body).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val responseString = response.body?.string() ?: return null
            val root = JSONObject(responseString)
            val candidates = root.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val candidate = candidates.getJSONObject(0)
            val content = candidate.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            if (parts.length() == 0) return null
            return parts.getJSONObject(0).optString("text", null)
        }
    }

    private fun parseAuditJson(rawText: String): AiAuditReport? {
        return try {
            val clean = rawText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val json = JSONObject(clean)
            val score = json.optInt("healthScore", 85)
            val bottleneck = json.optString("primaryBottleneck", "HEALTHY")
            val severity = json.optString("severity", "OPTIMAL")
            val summary = json.optString("executiveSummary", "Network telemetry operating within normal operational parameters.")
            val findingsArr = json.optJSONArray("findings")
            val findings = mutableListOf<AiAuditFinding>()
            if (findingsArr != null) {
                for (i in 0 until findingsArr.length()) {
                    val obj = findingsArr.getJSONObject(i)
                    findings.add(
                        AiAuditFinding(
                            title = obj.optString("title", "Finding"),
                            technicalEvidence = obj.optString("technicalEvidence", ""),
                            plainEnglishExplanation = obj.optString("plainEnglishExplanation", ""),
                            recommendedFix = obj.optString("recommendedFix", "")
                        )
                    )
                }
            }
            AiAuditReport(score, bottleneck, severity, summary, findings)
        } catch (_: Exception) {
            null
        }
    }

    private fun computeDeterministicAudit(snapshot: com.enterwebslinger.network.domain.model.NetworkDiagnosticSnapshot): AiAuditReport {
        val rssi = snapshot.wifiRfState.rssiDbm
        val linkSpeed = snapshot.wifiRfState.linkSpeedMbps
        val score = when {
            rssi > -65 && linkSpeed > 200 -> 95
            rssi > -75 -> 80
            else -> 65
        }
        val bottleneck = if (rssi < -75) "WIFI_SIGNAL_OR_CONGESTION" else "HEALTHY"
        val severity = if (score > 80) "OPTIMAL" else "WARNING"
        val summary = "Deterministic local telemetry audit: Connected to SSID '${snapshot.wifiRfState.ssid}' at $rssi dBm with link speed ${linkSpeed} Mbps."
        val findings = listOf(
            AiAuditFinding(
                title = "Wi-Fi Signal Assessment",
                technicalEvidence = "RSSI = $rssi dBm, Link Speed = $linkSpeed Mbps",
                plainEnglishExplanation = if (rssi > -70) "Signal strength is strong and stable." else "Signal strength is marginal; proximity to AP is recommended.",
                recommendedFix = "Maintain line of sight to wireless access point."
            )
        )
        return AiAuditReport(score, bottleneck, severity, summary, findings)
    }
}
