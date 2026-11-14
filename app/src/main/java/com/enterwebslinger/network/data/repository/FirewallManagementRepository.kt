// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/FirewallManagementRepository.kt ===
package com.enterwebslinger.network.data.repository

import android.content.Context
import androidx.core.content.FileProvider
import com.enterwebslinger.network.core.security.CredentialVault
import com.enterwebslinger.network.data.local.db.dao.EndpointProfileDao
import com.enterwebslinger.network.data.local.db.entities.SavedEndpointProfileEntity
import com.enterwebslinger.network.data.protocol.FirewallParsers
import com.enterwebslinger.network.domain.model.CiscoPacketTracerResult
import com.enterwebslinger.network.domain.model.FirewallApplianceProfile
import com.enterwebslinger.network.domain.model.FirewallBackupResult
import com.enterwebslinger.network.domain.model.FirewallDropLogEntry
import com.enterwebslinger.network.domain.model.FirewallHealthSummary
import com.enterwebslinger.network.domain.model.FirewallHubSnapshot
import com.enterwebslinger.network.domain.model.FirewallVendor
import com.enterwebslinger.network.domain.model.PanOsPolicyMatchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Pure-Kotlin Multi-Vendor Firewall Management & Diagnostic Repository:
 * - Sophos XG/XGS (SFOS XML API + SSH CLI for XFRM/IPsec/drppkt/backup)
 * - Fortinet FortiGate 7.x/8.x (REST API Bearer Token monitor/control/backup)
 * - Palo Alto PAN-OS & Panorama (XML API keygen/op + Policy/NAT match tester)
 * - Cisco ASA / FTD (SSH CLI failover/conn/xlate/vpn/packet-tracer + REST fallback)
 * - pfSense / OPNsense (REST API + FreeBSD SSH CLI dpinger/CARP/pfctl/unbound/dhcpd)
 */
class FirewallManagementRepository(
    private val context: Context,
    private val endpointProfileDao: EndpointProfileDao,
    private val credentialVault: CredentialVault
) {
    private val baseOkHttpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    val savedAppliancesFlow: Flow<List<FirewallApplianceProfile>> =
        endpointProfileDao.getAllProfilesFlow().map { entities ->
            entities.filter { it.profileType.startsWith("FW_") }
                .mapNotNull { entityToProfile(it) }
                .sortedByDescending { it.lastUsedTimestamp }
        }

    suspend fun saveAppliance(
        existingId: Long = 0L,
        vendor: FirewallVendor,
        name: String,
        host: String,
        apiPort: Int,
        sshPort: Int,
        username: String,
        password: String,
        apiToken: String,
        allowSelfSignedTls: Boolean,
        extraContext: String
    ): Long = withContext(Dispatchers.IO) {
        val extraJson = JSONObject().apply {
            put("sshPort", sshPort)
            put("allowSelfSignedTls", allowSelfSignedTls)
            put("extraContext", extraContext.trim())
        }.toString()

        val initialEntity = SavedEndpointProfileEntity(
            id = existingId,
            profileType = "FW_${vendor.name}",
            name = name.trim().ifBlank { "${vendor.shortCode} (${host.trim()})" },
            host = host.trim(),
            port = apiPort,
            username = username.trim(),
            credentialVaultKey = if (existingId > 0L) "fw_${existingId}_pass" else "",
            extraConfigJson = extraJson,
            lastUsedTimestamp = System.currentTimeMillis()
        )

        val rowId = if (existingId > 0L) {
            endpointProfileDao.updateProfile(initialEntity)
            existingId
        } else {
            val insertedId = endpointProfileDao.insertProfile(initialEntity)
            endpointProfileDao.updateProfile(
                initialEntity.copy(
                    id = insertedId,
                    credentialVaultKey = "fw_${insertedId}_pass"
                )
            )
            insertedId
        }

        val passKey = "fw_${rowId}_pass"
        val tokenKey = "fw_${rowId}_token"
        if (password.isNotEmpty()) credentialVault.putSecret(passKey, password)
        if (apiToken.isNotEmpty()) credentialVault.putSecret(tokenKey, apiToken)
        rowId
    }

    suspend fun deleteAppliance(profile: FirewallApplianceProfile) = withContext(Dispatchers.IO) {
        if (profile.id <= 0L) return@withContext
        credentialVault.removeSecret("fw_${profile.id}_pass")
        credentialVault.removeSecret("fw_${profile.id}_token")
        endpointProfileDao.deleteProfile(
            SavedEndpointProfileEntity(
                id = profile.id,
                profileType = "FW_${profile.vendor.name}",
                name = profile.name,
                host = profile.host,
                port = profile.apiPort
            )
        )
    }

    suspend fun fetchApplianceDashboard(profile: FirewallApplianceProfile): Result<FirewallHubSnapshot> {
        return when (profile.vendor) {
            FirewallVendor.SOPHOS_XGS -> fetchSophosDashboard(profile)
            FirewallVendor.FORTINET_FORTIGATE -> fetchFortiGateDashboard(profile)
            FirewallVendor.PALO_ALTO_PANOS -> fetchPanOsDashboard(profile)
            FirewallVendor.CISCO_ASA_FTD -> fetchCiscoAsaDashboard(profile)
            FirewallVendor.PFSENSE_OPNSENSE -> fetchPfSenseDashboard(profile)
        }
    }

    // -------------------------------------------------------------------------
    // 1. Sophos Firewall (XG / XGS SFOS)
    // -------------------------------------------------------------------------

    private suspend fun fetchSophosDashboard(profile: FirewallApplianceProfile): Result<FirewallHubSnapshot> =
        withContext(Dispatchers.IO) {
            val pass = getPassword(profile)
            val logSb = StringBuilder()

            val ipsecReqXml = buildSophosXmlEnvelope(
                user = profile.username,
                pass = pass,
                operationXml = "<Get><IPSecConnection/></Get>"
            )
            val gwReqXml = buildSophosXmlEnvelope(
                user = profile.username,
                pass = pass,
                operationXml = "<Get><GatewayConfiguration/></Get>"
            )

            val ipsecXml = postSophosXml(profile, ipsecReqXml).getOrElse {
                logSb.appendLine("XML IPSec API: ${it.message}")
                ""
            }
            val gwXml = postSophosXml(profile, gwReqXml).getOrElse { "" }

            var tunnels = FirewallParsers.parseSophosIpsecXml(ipsecXml)
            val gateways = FirewallParsers.parseSophosGatewayXml(gwXml)

            // Also run SSH inspection for live StrongSwan XFRM SAs if password is present
            if (pass.isNotBlank() && profile.username.isNotBlank()) {
                val sshRes = execSshCommands(
                    profile = profile,
                    commands = listOf("ipsec statusall", "ip xfrm state | head -n 60", "uptime")
                )
                sshRes.onSuccess { outputs ->
                    val statusAll = outputs.getOrElse(0) { "" }
                    val xfrm = outputs.getOrElse(1) { "" }
                    val uptime = outputs.getOrElse(2) { "" }.trim()
                    val sshTunnels = FirewallParsers.parseSophosSshIpsecAndXfrm(statusAll, xfrm)
                    if (sshTunnels.isNotEmpty()) tunnels = sshTunnels
                    if (uptime.isNotBlank()) logSb.appendLine("Uptime: $uptime")
                }.onFailure { err ->
                    logSb.appendLine("SSH Diagnostics: ${err.message}")
                }
            }

            if (ipsecXml.isBlank() && tunnels.isEmpty() && gateways.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException(logSb.toString().ifBlank { "Unable to reach Sophos XML API (${profile.host}:${profile.apiPort}) or SSH." })
                )
            }

            val health = FirewallHealthSummary(
                vendor = FirewallVendor.SOPHOS_XGS,
                applianceName = profile.name,
                hostname = profile.host,
                firmwareVersion = "SFOS",
                haRole = "Active",
                haState = "${tunnels.count { it.isUp }}/${tunnels.size} Tunnels UP",
                statusNote = "Sophos XML API + XFRM Monitor"
            )
            Result.success(
                FirewallHubSnapshot(
                    health = health,
                    ipsecTunnels = tunnels,
                    wanGateways = gateways,
                    rawDiagnosticOutput = logSb.toString().ifBlank { ipsecXml.take(1500) }
                )
            )
        }

    suspend fun bounceSophosIpsecTunnel(profile: FirewallApplianceProfile, tunnelName: String): Result<String> =
        withContext(Dispatchers.IO) {
            val cleanName = tunnelName.trim().replace("[^A-Za-z0-9_.-]".toRegex(), "")
            if (cleanName.isBlank()) return@withContext Result.failure(IllegalArgumentException("Tunnel name required"))
            val sshCmd = "ipsec down $cleanName || true; sleep 1; ipsec up $cleanName"
            execSshCommands(profile, listOf(sshCmd)).map { outs ->
                outs.firstOrNull().orEmpty().ifBlank { "Bounce command dispatched for $cleanName." }
            }
        }

    suspend fun fetchSophosDropPackets(
        profile: FirewallApplianceProfile,
        filterIp: String = ""
    ): Result<List<FirewallDropLogEntry>> = withContext(Dispatchers.IO) {
        val cleanIp = filterIp.trim()
        val filterArg = if (cleanIp.isNotBlank()) " 'host $cleanIp'" else ""
        val cmd = "drppkt$filterArg -c 25 2>/dev/null || tail -n 60 /log/syslog.log | grep -iE 'drop|deny|denied' || true"
        execSshCommands(profile, listOf(cmd)).map { outs ->
            FirewallParsers.parseSophosDropPackets(outs.firstOrNull().orEmpty())
        }
    }

    suspend fun triggerSophosConfigBackup(profile: FirewallApplianceProfile): Result<String> =
        execSshCommands(profile, listOf("/bin/opcode system_backup_now -ds nosync")).map { outs ->
            outs.firstOrNull().orEmpty().ifBlank { "Sophos local system_backup_now opcode triggered successfully." }
        }

    private fun buildSophosXmlEnvelope(user: String, pass: String, operationXml: String): String =
        "<Request><Login><Username>${escapeXml(user)}</Username><Password>${escapeXml(pass)}</Password></Login>$operationXml</Request>"

    private fun postSophosXml(profile: FirewallApplianceProfile, reqXml: String): Result<String> = runCatching {
        val url = "https://${profile.host}:${profile.apiPort}/webconsole/APIController"
        val body = FormBody.Builder().add("reqxml", reqXml).build()
        val req = Request.Builder().url(url).post(body).build()
        clientFor(profile).newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}: ${text.take(180)}")
            text
        }
    }

    // -------------------------------------------------------------------------
    // 2. Fortinet FortiGate (7.x / 8.x)
    // -------------------------------------------------------------------------

    private suspend fun fetchFortiGateDashboard(profile: FirewallApplianceProfile): Result<FirewallHubSnapshot> =
        withContext(Dispatchers.IO) {
            val token = getApiToken(profile)
            if (token.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("FortiGate API Bearer token is required in Vault."))
            }
            val vdomQuery = profile.extraContext.takeIf { it.isNotBlank() }?.let { "?vdom=${urlEnc(it)}" } ?: ""

            val resJson = getFortiApi(profile, token, "/api/v2/monitor/system/resource/usage$vdomQuery").getOrElse {
                return@withContext Result.failure(it)
            }
            val statusJson = getFortiApi(profile, token, "/api/v2/monitor/system/status$vdomQuery").getOrDefault("")
            val haJson = getFortiApi(profile, token, "/api/v2/monitor/system/ha-peer$vdomQuery").getOrDefault("")
            val slaJson = getFortiApi(profile, token, "/api/v2/monitor/virtual-wan/health-check$vdomQuery").getOrDefault("")
            val ipsecJson = getFortiApi(profile, token, "/api/v2/monitor/vpn/ipsec$vdomQuery").getOrDefault("")
            val policyMonJson = getFortiApi(profile, token, "/api/v2/monitor/firewall/policy$vdomQuery").getOrDefault("")
            val policyCmdbJson = getFortiApi(profile, token, "/api/v2/cmdb/firewall/policy$vdomQuery").getOrDefault("")

            val health = FirewallParsers.parseFortiGateResourceJson(profile.name, resJson, statusJson, haJson)
            val gateways = FirewallParsers.parseFortiGateSdwanSlaJson(slaJson)
            val tunnels = FirewallParsers.parseFortiGateIpsecJson(ipsecJson)
            val policies = FirewallParsers.parseFortiGatePolicyHitsJson(policyMonJson, policyCmdbJson)

            Result.success(
                FirewallHubSnapshot(
                    health = health,
                    ipsecTunnels = tunnels,
                    wanGateways = gateways,
                    policyHits = policies,
                    rawDiagnosticOutput = "FortiOS REST Monitor synced (${tunnels.size} P2 tunnels, ${gateways.size} SD-WAN SLAs, ${policies.size} policies)"
                )
            )
        }

    suspend fun controlFortiGateIpsecTunnel(
        profile: FirewallApplianceProfile,
        p1Name: String,
        p2Name: String,
        bringUp: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        val token = getApiToken(profile)
        if (token.isBlank()) return@withContext Result.failure(IllegalArgumentException("FortiGate API token missing."))
        val endpoint = if (bringUp) "/api/v2/monitor/vpn/ipsec/tunnel_up" else "/api/v2/monitor/vpn/ipsec/tunnel_down"
        val jsonBody = JSONObject().apply {
            put("p1name", p1Name)
            put("p2name", p2Name)
        }.toString().toRequestBody("application/json".toMediaType())

        runCatching {
            val req = Request.Builder()
                .url("https://${profile.host}:${profile.apiPort}$endpoint")
                .addHeader("Authorization", "Bearer $token")
                .post(jsonBody)
                .build()
            clientFor(profile).newCall(req).execute().use { resp ->
                val txt = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IllegalStateException("FortiGate HTTP ${resp.code}: ${txt.take(180)}")
                "FortiGate IPsec tunnel $p1Name/$p2Name -> ${if (bringUp) "UP" else "DOWN"} (${resp.code})"
            }
        }
    }

    suspend fun downloadFortiGateBackup(profile: FirewallApplianceProfile): Result<FirewallBackupResult> =
        withContext(Dispatchers.IO) {
            val token = getApiToken(profile)
            if (token.isBlank()) return@withContext Result.failure(IllegalArgumentException("FortiGate API token missing."))
            runCatching {
                val req = Request.Builder()
                    .url("https://${profile.host}:${profile.apiPort}/api/v2/monitor/system/config/backup?scope=global")
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()
                clientFor(profile).newCall(req).execute().use { resp ->
                    val bytes = resp.body?.bytes() ?: ByteArray(0)
                    if (!resp.isSuccessful || bytes.isEmpty()) {
                        throw IllegalStateException("FortiGate backup failed: HTTP ${resp.code}")
                    }
                    val dir = File(context.cacheDir, "reports").apply { mkdirs() }
                    val cleanHost = profile.host.replace("[^A-Za-z0-9._-]".toRegex(), "_")
                    val file = File(dir, "fortigate_${cleanHost}_${System.currentTimeMillis()}.conf")
                    file.writeBytes(bytes)
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    FirewallBackupResult(
                        vendor = FirewallVendor.FORTINET_FORTIGATE,
                        fileName = file.name,
                        fileSizeBytes = file.length(),
                        savedPath = file.absolutePath,
                        shareUriString = uri.toString(),
                        summary = "Downloaded FortiOS global config backup (${file.length()} bytes)"
                    )
                }
            }
        }

    private fun getFortiApi(profile: FirewallApplianceProfile, token: String, path: String): Result<String> = runCatching {
        val req = Request.Builder()
            .url("https://${profile.host}:${profile.apiPort}$path")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        clientFor(profile).newCall(req).execute().use { resp ->
            val txt = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}: ${txt.take(180)}")
            txt
        }
    }

    // -------------------------------------------------------------------------
    // 3. Palo Alto PAN-OS & Panorama XML API
    // -------------------------------------------------------------------------

    private suspend fun fetchPanOsDashboard(profile: FirewallApplianceProfile): Result<FirewallHubSnapshot> =
        withContext(Dispatchers.IO) {
            val apiKey = resolvePanOsApiKey(profile).getOrElse { return@withContext Result.failure(it) }

            val sysXml = queryPanOsOp(profile, apiKey, "<show><system><info/></system></show>").getOrDefault("")
            val resXml = queryPanOsOp(profile, apiKey, "<show><system><resources/></system></show>").getOrDefault("")
            val dpXml = queryPanOsOp(profile, apiKey, "<show><running><resource-monitor><minute><last>1</last></minute></resource-monitor></running></show>").getOrDefault("")
            val sessXml = queryPanOsOp(profile, apiKey, "<show><session><info/></system></show>").let {
                if (it.isSuccess) it.getOrDefault("")
                else queryPanOsOp(profile, apiKey, "<show><session><info/></session></show>").getOrDefault("")
            }
            val ipsecXml = queryPanOsOp(profile, apiKey, "<show><vpn><ipsec-sa/></vpn></show>").getOrDefault("")
            val ikeXml = queryPanOsOp(profile, apiKey, "<show><vpn><ike-sa/></vpn></show>").getOrDefault("")
            val gpXml = queryPanOsOp(profile, apiKey, "<show><global-protect-gateway><current-user/></global-protect-gateway></show>").getOrDefault("")

            val health = FirewallParsers.parsePanOsHealthXml(profile.name, sysXml, resXml, dpXml, sessXml)
            val tunnels = FirewallParsers.parsePanOsIpsecSaXml(ipsecXml, ikeXml)
            val gpUsers = FirewallParsers.parsePanOsGlobalProtectUsersXml(gpXml)

            Result.success(
                FirewallHubSnapshot(
                    health = health,
                    ipsecTunnels = tunnels,
                    vpnUsers = gpUsers,
                    rawDiagnosticOutput = "PAN-OS XML API connected (${tunnels.size} IPsec SAs, ${gpUsers.size} GlobalProtect users)"
                )
            )
        }

    suspend fun testPanOsSecurityAndNatMatch(
        profile: FirewallApplianceProfile,
        fromZone: String,
        toZone: String,
        srcIp: String,
        dstIp: String,
        dstPort: Int,
        protocolNumber: Int = 6,
        application: String = "any"
    ): Result<PanOsPolicyMatchResult> = withContext(Dispatchers.IO) {
        val apiKey = resolvePanOsApiKey(profile).getOrElse { return@withContext Result.failure(it) }
        val vsysAttr = profile.extraContext.takeIf { it.isNotBlank() }?.let { "<vsys>${escapeXml(it)}</vsys>" } ?: ""

        val secCmd = """
            <test><security-policy-match>
                ${if (fromZone.isNotBlank()) "<from>${escapeXml(fromZone)}</from>" else ""}
                ${if (toZone.isNotBlank()) "<to>${escapeXml(toZone)}</to>" else ""}
                <source>${escapeXml(srcIp)}</source>
                <destination>${escapeXml(dstIp)}</destination>
                <protocol>$protocolNumber</protocol>
                <destination-port>$dstPort</destination-port>
                ${if (application.isNotBlank() && application != "any") "<application>${escapeXml(application)}</application>" else ""}
                $vsysAttr
            </security-policy-match></test>
        """.trimIndent().replace("\n", "")

        val natCmd = """
            <test><nat-policy-match>
                ${if (fromZone.isNotBlank()) "<from>${escapeXml(fromZone)}</from>" else ""}
                ${if (toZone.isNotBlank()) "<to>${escapeXml(toZone)}</to>" else ""}
                <source>${escapeXml(srcIp)}</source>
                <destination>${escapeXml(dstIp)}</destination>
                <protocol>$protocolNumber</protocol>
                <destination-port>$dstPort</destination-port>
                $vsysAttr
            </nat-policy-match></test>
        """.trimIndent().replace("\n", "")

        val secRes = queryPanOsOp(profile, apiKey, secCmd)
        secRes.map { secXml ->
            val natXml = queryPanOsOp(profile, apiKey, natCmd).getOrDefault("")
            FirewallParsers.parsePanOsSecurityAndNatMatchXml(secXml, natXml)
        }
    }

    private fun resolvePanOsApiKey(profile: FirewallApplianceProfile): Result<String> {
        val storedToken = getApiToken(profile)
        if (storedToken.isNotBlank()) return Result.success(storedToken)
        val pass = getPassword(profile)
        if (profile.username.isBlank() || pass.isBlank()) {
            return Result.failure(IllegalArgumentException("PAN-OS API Key or Username/Password required."))
        }
        return runCatching {
            val url = "https://${profile.host}:${profile.apiPort}/api/?type=keygen&user=${urlEnc(profile.username)}&password=${urlEnc(pass)}"
            val req = Request.Builder().url(url).get().build()
            clientFor(profile).newCall(req).execute().use { resp ->
                val xml = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IllegalStateException("PAN-OS keygen HTTP ${resp.code}")
                val key = FirewallParsers.parsePanOsKeygenXml(xml).getOrThrow()
                credentialVault.putSecret("fw_${profile.id}_token", key)
                key
            }
        }
    }

    private fun queryPanOsOp(profile: FirewallApplianceProfile, apiKey: String, cmdXml: String): Result<String> = runCatching {
        val body = FormBody.Builder()
            .add("type", "op")
            .add("cmd", cmdXml)
            .add("key", apiKey)
            .build()
        val req = Request.Builder()
            .url("https://${profile.host}:${profile.apiPort}/api/")
            .post(body)
            .build()
        clientFor(profile).newCall(req).execute().use { resp ->
            val xml = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("PAN-OS HTTP ${resp.code}: ${xml.take(180)}")
            xml
        }
    }

    // -------------------------------------------------------------------------
    // 4. Cisco ASA & FTD
    // -------------------------------------------------------------------------

    private suspend fun fetchCiscoAsaDashboard(profile: FirewallApplianceProfile): Result<FirewallHubSnapshot> =
        withContext(Dispatchers.IO) {
            val cmds = listOf(
                "show failover",
                "show conn count",
                "show xlate count",
                "show vpn-sessiondb summary",
                "show crypto ipsec sa | include interface|local ident|remote ident|Current_peer"
            )
            val sshRes = execSshCommands(profile, cmds)
            sshRes.map { outs ->
                val failOut = outs.getOrElse(0) { "" }
                val connOut = outs.getOrElse(1) { "" }
                val xlateOut = outs.getOrElse(2) { "" }
                val vpnOut = outs.getOrElse(3) { "" }
                val health = FirewallParsers.parseCiscoAsaSummary(profile.name, failOut, connOut, xlateOut, vpnOut)
                val connCount = Regex("""(\d+)\s+in use""").find(connOut)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                val xlateCount = Regex("""(\d+)\s+in use""").find(xlateOut)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                val acCount = Regex("""AnyConnect Client\s*:\s*(\d+)""", RegexOption.IGNORE_CASE).find(vpnOut)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

                FirewallHubSnapshot(
                    health = health,
                    anyConnectCount = acCount,
                    asaConnCount = connCount,
                    asaXlateCount = xlateCount,
                    rawDiagnosticOutput = failOut.ifBlank { vpnOut }.take(2000)
                )
            }
        }

    suspend fun runCiscoPacketTracer(
        profile: FirewallApplianceProfile,
        inputInterface: String,
        protocol: String,
        srcIp: String,
        srcPort: Int,
        dstIp: String,
        dstPort: Int
    ): Result<CiscoPacketTracerResult> = withContext(Dispatchers.IO) {
        val cleanProto = protocol.lowercase().let { if (it in listOf("tcp", "udp", "icmp")) it else "tcp" }
        val cmd = if (cleanProto == "icmp") {
            "packet-tracer input ${inputInterface.trim()} icmp ${srcIp.trim()} 8 0 ${dstIp.trim()} detailed"
        } else {
            "packet-tracer input ${inputInterface.trim()} $cleanProto ${srcIp.trim()} $srcPort ${dstIp.trim()} $dstPort detailed"
        }
        execSshCommands(profile, listOf(cmd)).map { outs ->
            FirewallParsers.parseCiscoPacketTracerOutput(outs.firstOrNull().orEmpty())
        }
    }

    // -------------------------------------------------------------------------
    // 5. pfSense / OPNsense
    // -------------------------------------------------------------------------

    private suspend fun fetchPfSenseDashboard(profile: FirewallApplianceProfile): Result<FirewallHubSnapshot> =
        withContext(Dispatchers.IO) {
            val token = getApiToken(profile)
            val pass = getPassword(profile)
            var gateways = emptyList<com.enterwebslinger.network.domain.model.WanGatewaySlaEntry>()
            var carpVips = emptyList<com.enterwebslinger.network.domain.model.CarpVipEntry>()
            var pfSummary: com.enterwebslinger.network.domain.model.PfStateSummary? = null
            var rawOut = ""

            // Try REST API first if token/credentials exist
            if (token.isNotBlank() || (profile.username.isNotBlank() && pass.isNotBlank())) {
                runCatching {
                    val reqBuilder = Request.Builder()
                        .url("https://${profile.host}:${profile.apiPort}/api/v1/status/gateway")
                        .get()
                    if (token.isNotBlank()) {
                        reqBuilder.addHeader("Authorization", "Bearer $token")
                        reqBuilder.addHeader("X-API-Key", token)
                    } else {
                        reqBuilder.addHeader("Authorization", Credentials.basic(profile.username, pass))
                    }
                    clientFor(profile).newCall(reqBuilder.build()).execute().use { resp ->
                        if (resp.isSuccessful) {
                            gateways = FirewallParsers.parsePfGatewayStatus(resp.body?.string().orEmpty())
                        }
                    }
                }
            }

            // FreeBSD SSH inspection for dpinger, CARP VIPs, pfctl state table, unbound, DHCP leases
            if (profile.username.isNotBlank() && pass.isNotBlank()) {
                val sshRes = execSshCommands(
                    profile = profile,
                    commands = listOf(
                        "pfctl -si 2>/dev/null | head -n 20",
                        "pfctl -ss 2>/dev/null | head -n 15",
                        "ifconfig -a 2>/dev/null | grep -E '^[a-zA-Z0-9._-]+:|inet .*vhid|carp:'",
                        "cat /var/dhcpd/var/db/dhcpd.leases 2>/dev/null | tail -n 80",
                        "service unbound status 2>/dev/null || pluginctl -s unbound status 2>/dev/null || true",
                        "pluginctl -g 2>/dev/null || pfSsh.php playback gatewaystatus 2>/dev/null || true"
                    )
                )
                sshRes.onSuccess { outs ->
                    val pfInfo = outs.getOrElse(0) { "" }
                    val pfStates = outs.getOrElse(1) { "" }
                    val carpOut = outs.getOrElse(2) { "" }
                    val leasesOut = outs.getOrElse(3) { "" }
                    val unboundOut = outs.getOrElse(4) { "" }
                    val gwCli = outs.getOrElse(5) { "" }

                    if (gateways.isEmpty()) gateways = FirewallParsers.parsePfGatewayStatus(gwCli)
                    carpVips = FirewallParsers.parseCarpVipStatus(carpOut)
                    pfSummary = FirewallParsers.parsePfCtlStatesAndLeases(pfInfo, pfStates, leasesOut, unboundOut)
                    rawOut = pfInfo.ifBlank { gwCli }.take(1500)
                }.onFailure { err ->
                    if (gateways.isEmpty()) return@withContext Result.failure(err)
                    rawOut = "SSH fallback failed: ${err.message}"
                }
            } else if (gateways.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Provide REST API Key or SSH Username/Password for pfSense/OPNsense."))
            }

            val health = FirewallHealthSummary(
                vendor = FirewallVendor.PFSENSE_OPNSENSE,
                applianceName = profile.name,
                hostname = profile.host,
                activeSessions = pfSummary?.totalStates ?: 0L,
                maxSessions = pfSummary?.stateTableLimit ?: 0L,
                haRole = carpVips.firstOrNull()?.status ?: "Standalone",
                haState = "${carpVips.size} CARP VIPs | Unbound: ${pfSummary?.unboundStatus ?: "Checked"}",
                statusNote = "FreeBSD pfctl + dpinger + CARP Monitor"
            )
            Result.success(
                FirewallHubSnapshot(
                    health = health,
                    wanGateways = gateways,
                    carpVips = carpVips,
                    pfStateSummary = pfSummary,
                    rawDiagnosticOutput = rawOut
                )
            )
        }

    suspend fun restartPfService(profile: FirewallApplianceProfile, serviceName: String): Result<String> {
        val safeSvc = serviceName.trim().replace("[^A-Za-z0-9_-]".toRegex(), "")
        if (safeSvc.isBlank()) return Result.failure(IllegalArgumentException("Invalid service name"))
        return execSshCommands(
            profile,
            listOf("service $safeSvc restart 2>/dev/null || configctl $safeSvc restart 2>/dev/null || pfSsh.php playback svc restart $safeSvc")
        ).map { outs ->
            outs.firstOrNull().orEmpty().ifBlank { "Service '$safeSvc' restart dispatched." }
        }
    }

    // -------------------------------------------------------------------------
    // Shared SSHJ / OkHttp / Vault Helpers
    // -------------------------------------------------------------------------

    private suspend fun execSshCommands(
        profile: FirewallApplianceProfile,
        commands: List<String>
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        val pass = getPassword(profile)
        if (profile.host.isBlank() || profile.username.isBlank() || pass.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("SSH host, username, and password are required."))
        }
        val ssh = SSHClient()
        ssh.connectTimeout = 8000
        ssh.timeout = 12000
        ssh.addHostKeyVerifier(PromiscuousVerifier())
        try {
            ssh.connect(profile.host, profile.sshPort)
            ssh.authPassword(profile.username, pass)
            val results = mutableListOf<String>()
            for (cmdText in commands) {
                ssh.startSession().use { session ->
                    val cmd = session.exec(cmdText)
                    val out = cmd.inputStream.bufferedReader().use { it.readText() }
                    val err = cmd.errorStream.bufferedReader().use { it.readText() }
                    cmd.join(8, TimeUnit.SECONDS)
                    results.add(if (out.isNotBlank()) out else err)
                }
            }
            Result.success(results)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { ssh.disconnect() }
        }
    }

    private fun clientFor(profile: FirewallApplianceProfile): OkHttpClient {
        if (!profile.allowSelfSignedTls) return baseOkHttpClient
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAll, SecureRandom())
        }
        return baseOkHttpClient.newBuilder()
            .sslSocketFactory(sslContext.socketFactory, trustAll[0] as X509TrustManager)
            .hostnameVerifier(HostnameVerifier { _, _ -> true })
            .build()
    }

    private fun entityToProfile(entity: SavedEndpointProfileEntity): FirewallApplianceProfile? {
        val vendorName = entity.profileType.removePrefix("FW_")
        val vendor = FirewallVendor.entries.firstOrNull { it.name == vendorName } ?: return null
        val json = runCatching { JSONObject(entity.extraConfigJson) }.getOrNull()
        val passKey = entity.credentialVaultKey.ifBlank { "fw_${entity.id}_pass" }
        val tokenKey = "fw_${entity.id}_token"
        return FirewallApplianceProfile(
            id = entity.id,
            vendor = vendor,
            name = entity.name,
            host = entity.host,
            apiPort = entity.port,
            sshPort = json?.optInt("sshPort", vendor.defaultSshPort) ?: vendor.defaultSshPort,
            username = entity.username,
            allowSelfSignedTls = json?.optBoolean("allowSelfSignedTls", true) ?: true,
            extraContext = json?.optString("extraContext", "").orEmpty(),
            passwordVaultKey = passKey,
            apiTokenVaultKey = tokenKey,
            hasStoredPassword = credentialVault.hasSecret(passKey),
            hasStoredToken = credentialVault.hasSecret(tokenKey),
            lastUsedTimestamp = entity.lastUsedTimestamp
        )
    }

    private fun getPassword(profile: FirewallApplianceProfile): String =
        credentialVault.getSecret(profile.passwordVaultKey.ifBlank { "fw_${profile.id}_pass" }).orEmpty()

    private fun getApiToken(profile: FirewallApplianceProfile): String =
        credentialVault.getSecret(profile.apiTokenVaultKey.ifBlank { "fw_${profile.id}_token" }).orEmpty()

    private fun escapeXml(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun urlEnc(raw: String): String = URLEncoder.encode(raw, "UTF-8")
}
