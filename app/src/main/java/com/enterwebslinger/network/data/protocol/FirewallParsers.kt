// === FILE: app/src/main/java/com/enterwebslinger/network/data/protocol/FirewallParsers.kt ===
package com.enterwebslinger.network.data.protocol

import com.enterwebslinger.network.domain.model.CarpVipEntry
import com.enterwebslinger.network.domain.model.CiscoPacketTracerPhase
import com.enterwebslinger.network.domain.model.CiscoPacketTracerResult
import com.enterwebslinger.network.domain.model.FirewallDropLogEntry
import com.enterwebslinger.network.domain.model.FirewallHealthSummary
import com.enterwebslinger.network.domain.model.FirewallIpsecTunnel
import com.enterwebslinger.network.domain.model.FirewallPolicyHitEntry
import com.enterwebslinger.network.domain.model.FirewallVendor
import com.enterwebslinger.network.domain.model.GlobalProtectUserEntry
import com.enterwebslinger.network.domain.model.PanOsPolicyMatchResult
import com.enterwebslinger.network.domain.model.PfStateSummary
import com.enterwebslinger.network.domain.model.WanGatewaySlaEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

/**
 * Pure-Kotlin / JVM protocol parsers for Sophos XG/XGS, Fortinet FortiGate,
 * Palo Alto PAN-OS, Cisco ASA/FTD, and pfSense/OPNsense management APIs & CLI output.
 */
object FirewallParsers {

    // -------------------------------------------------------------------------
    // Sophos Firewall (XG / XGS SFOS) Parsers
    // -------------------------------------------------------------------------

    fun parseSophosIpsecXml(xml: String): List<FirewallIpsecTunnel> {
        val doc = parseXmlDocument(xml) ?: return emptyList()
        val nodes = doc.getElementsByTagName("IPSecConnection")
        val list = mutableListOf<FirewallIpsecTunnel>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            val name = el.textOf("Name").ifBlank { "Tunnel-${i + 1}" }
            val gw = el.textOf("GatewayAddress").ifBlank { el.textOf("RemoteHost") }
            val localNet = el.textOf("LocalSubnet").ifBlank { el.textOf("LocalNetwork") }
            val remoteNet = el.textOf("RemoteSubnet").ifBlank { el.textOf("RemoteNetwork") }
            val statusStr = el.textOf("Status").ifBlank { el.textOf("ConnectionStatus") }
            val isActive = statusStr.contains("Active", true) || statusStr.contains("Up", true) || statusStr == "1"
            list.add(
                FirewallIpsecTunnel(
                    id = name,
                    name = name,
                    remoteGateway = gw.ifBlank { "Peer" },
                    localSubnet = localNet,
                    remoteSubnet = remoteNet,
                    phase1State = if (isActive) "ESTABLISHED" else statusStr.ifBlank { "DOWN" },
                    phase2State = if (isActive) "INSTALLED" else "IDLE",
                    isUp = isActive,
                    detailNote = "Sophos SFOS XML API"
                )
            )
        }
        return list
    }

    fun parseSophosSshIpsecAndXfrm(ipsecStatusOut: String, xfrmStateOut: String): List<FirewallIpsecTunnel> {
        val tunnels = linkedMapOf<String, FirewallIpsecTunnel>()
        val spiCountByDest = mutableMapOf<String, Int>()

        // Parse `ip xfrm state` lines for installed SPIs
        var lastSrcDst = ""
        xfrmStateOut.lines().forEach { raw ->
            val line = raw.trim()
            val srcDstMatch = Regex("""src\s+([\da-fA-F:.]+)\s+dst\s+([\da-fA-F:.]+)""").find(line)
            if (srcDstMatch != null) {
                lastSrcDst = "${srcDstMatch.groupValues[1]}->${srcDstMatch.groupValues[2]}"
            }
            val spiMatch = Regex("""spi\s+(0x[0-9a-fA-F]+)""").find(line)
            if (spiMatch != null && lastSrcDst.isNotBlank()) {
                spiCountByDest[lastSrcDst] = (spiCountByDest[lastSrcDst] ?: 0) + 1
            }
        }

        // Parse StrongSwan `ipsec statusall` / `ipsec status` connections
        ipsecStatusOut.lines().forEach { raw ->
            val line = raw.trim()
            // Example: "SiteA_Tunnel-1[4]: ESTABLISHED 2 hours ago, 1.2.3.4[1.2.3.4]...5.6.7.8[5.6.7.8]"
            val estabMatch = Regex("""^([^\[\s]+)\[(\d+)\]:\s*(ESTABLISHED|CONNECTING|DELETING|PASSIVE)(.*)$""", RegexOption.IGNORE_CASE).find(line)
            if (estabMatch != null) {
                val connName = estabMatch.groupValues[1].substringBefore("-")
                val state = estabMatch.groupValues[3].uppercase()
                val rest = estabMatch.groupValues[4]
                val ips = Regex("""\b\d{1,3}(?:\.\d{1,3}){3}\b""").findAll(rest).map { it.value }.toList()
                val remoteGw = ips.lastOrNull() ?: "Peer"
                val prev = tunnels[connName]
                tunnels[connName] = FirewallIpsecTunnel(
                    id = connName,
                    name = connName,
                    remoteGateway = remoteGw,
                    localSubnet = prev?.localSubnet.orEmpty(),
                    remoteSubnet = prev?.remoteSubnet.orEmpty(),
                    phase1State = state,
                    phase2State = prev?.phase2State ?: if (state == "ESTABLISHED") "Negotiating/Check P2" else "DOWN",
                    isUp = state == "ESTABLISHED",
                    spiOrSelector = "XFRM SPIs=${spiCountByDest.values.sum()}",
                    detailNote = rest.trim().take(80)
                )
            }
            // Example: "SiteA_Tunnel-1{12}:  INSTALLED, TUNNEL, reqid 1, ESP in UDP SPIs: c12a34b1_i d98e76a2_o"
            val childMatch = Regex("""^([^\{\s]+)\{\d+\}:\s*(INSTALLED|REKEYING|ROUTED)([^;]*)$""", RegexOption.IGNORE_CASE).find(line)
            if (childMatch != null) {
                val connName = childMatch.groupValues[1].substringBefore("-")
                val childState = childMatch.groupValues[2].uppercase()
                val prev = tunnels[connName]
                tunnels[connName] = (prev ?: FirewallIpsecTunnel(
                    id = connName,
                    name = connName,
                    remoteGateway = "Peer"
                )).copy(
                    phase2State = childState,
                    isUp = childState == "INSTALLED" || childState == "REKEYING",
                    spiOrSelector = childMatch.groupValues[3].trim().take(64)
                )
            }
            // Child SA subnets: "SiteA_Tunnel-1{12}:   10.10.0.0/16 === 172.16.0.0/16"
            val netMatch = Regex("""^([^\{\s]+)\{\d+\}:\s*([\d./a-fA-F:, ]+)===\s*([\d./a-fA-F:, ]+)$""").find(line)
            if (netMatch != null) {
                val connName = netMatch.groupValues[1].substringBefore("-")
                val prev = tunnels[connName]
                if (prev != null) {
                    tunnels[connName] = prev.copy(
                        localSubnet = netMatch.groupValues[2].trim(),
                        remoteSubnet = netMatch.groupValues[3].trim()
                    )
                }
            }
        }
        return tunnels.values.toList()
    }

    fun parseSophosGatewayXml(xml: String): List<WanGatewaySlaEntry> {
        val doc = parseXmlDocument(xml) ?: return emptyList()
        val entries = mutableListOf<WanGatewaySlaEntry>()
        val nodes = doc.getElementsByTagName("GatewayConfiguration")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            val name = el.textOf("Name").ifBlank { "WAN-${i + 1}" }
            val gwIp = el.textOf("GatewayIP").ifBlank { el.textOf("IPAddress") }
            val ifc = el.textOf("Interface").ifBlank { "Port" }
            val role = el.textOf("Type").ifBlank { el.textOf("Weight").ifBlank { "Active" } }
            val status = el.textOf("Status").ifBlank { "Live" }
            entries.add(
                WanGatewaySlaEntry(
                    name = name,
                    interfaceName = ifc,
                    gatewayIp = gwIp,
                    role = role,
                    latencyMs = el.textOf("Latency").toDoubleOrNull() ?: 0.0,
                    jitterMs = el.textOf("Jitter").toDoubleOrNull() ?: 0.0,
                    packetLossPercent = el.textOf("PacketLoss").toDoubleOrNull() ?: 0.0,
                    status = status
                )
            )
        }
        return entries
    }

    fun parseSophosDropPackets(raw: String): List<FirewallDropLogEntry> {
        val entries = mutableListOf<FirewallDropLogEntry>()
        raw.lines().forEach { line ->
            val t = line.trim()
            if (t.isBlank()) return@forEach
            // Supports Sophos `drppkt` console output & fw log lines
            val src = Regex("""(?:src_ip|src|S)=([\da-fA-F:.]+)""").find(t)?.groupValues?.getOrNull(1)
                ?: Regex("""(\d{1,3}(?:\.\d{1,3}){3})\s*->""").find(t)?.groupValues?.getOrNull(1)
                ?: ""
            val dst = Regex("""(?:dst_ip|dst|D)=([\da-fA-F:.]+)""").find(t)?.groupValues?.getOrNull(1)
                ?: Regex("""->\s*(\d{1,3}(?:\.\d{1,3}){3})""").find(t)?.groupValues?.getOrNull(1)
                ?: ""
            if (src.isBlank() && dst.isBlank()) return@forEach
            val proto = Regex("""(?:proto|PROTO)=([A-Za-z0-9]+)""").find(t)?.groupValues?.getOrNull(1) ?: "IP"
            val dpt = Regex("""(?:dst_port|dpt|DPT)=(\d+)""").find(t)?.groupValues?.getOrNull(1) ?: ""
            val reason = Regex("""(?:reason|message|fw_rule_id|log_subtype)="?([^"\n]+?)"?(?:\s|$)""")
                .find(t)?.groupValues?.getOrNull(1)
                ?: if (t.contains("DROP", true)) "Packet Dropped" else "Firewall Drop"
            val inIfc = Regex("""(?:in_interface|IN)=([A-Za-z0-9._-]+)""").find(t)?.groupValues?.getOrNull(1) ?: ""
            val ts = Regex("""^(\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}|\d{2}:\d{2}:\d{2})""").find(t)?.groupValues?.getOrNull(1) ?: "Live"
            entries.add(
                FirewallDropLogEntry(
                    timestamp = ts,
                    ruleOrReason = reason.take(64),
                    srcIp = src,
                    dstIp = dst,
                    protocolPort = if (dpt.isNotBlank()) "$proto/$dpt" else proto,
                    inInterface = inIfc,
                    rawLine = t.take(220)
                )
            )
        }
        return entries
    }

    // -------------------------------------------------------------------------
    // Fortinet FortiGate REST API JSON Parsers
    // -------------------------------------------------------------------------

    fun parseFortiGateResourceJson(
        applianceName: String,
        resourceJson: String,
        statusJson: String = "",
        haJson: String = ""
    ): FirewallHealthSummary {
        val resRoot = runCatching { JSONObject(resourceJson) }.getOrNull()
        val results = resRoot?.optJSONObject("results") ?: resRoot
        val cpuObj = results?.optJSONObject("cpu")
        val memObj = results?.optJSONObject("mem")
        val sessionObj = results?.optJSONObject("session")

        val cpuPct = when {
            cpuObj?.has("current") == true -> cpuObj.optDouble("current", 0.0).toFloat()
            results?.has("cpu") == true -> results.optDouble("cpu", 0.0).toFloat()
            else -> 0f
        }
        val memPct = when {
            memObj?.has("current") == true -> memObj.optDouble("current", 0.0).toFloat()
            results?.has("mem") == true -> results.optDouble("mem", 0.0).toFloat()
            else -> 0f
        }
        val activeSess = sessionObj?.optLong("current", 0L)
            ?: results?.optJSONObject("session")?.optLong("total", 0L) ?: 0L
        val maxSess = sessionObj?.optLong("max", 0L) ?: 0L

        val statusRoot = runCatching { JSONObject(statusJson) }.getOrNull()
        val stResults = statusRoot?.optJSONObject("results")
        val hostname = stResults?.optString("hostname", "")
            ?.ifBlank { statusRoot?.optString("serial", "").orEmpty() }
            ?: applianceName
        val version = statusRoot?.optString("version", "") ?: stResults?.optString("version", "").orEmpty()

        val haRoot = runCatching { JSONObject(haJson) }.getOrNull()
        val haArr = haRoot?.optJSONArray("results")
        val haRole = if (haArr != null && haArr.length() > 0) {
            val first = haArr.optJSONObject(0)
            first?.optString("role", "Standalone")?.uppercase() ?: "HA Cluster"
        } else "Standalone"

        return FirewallHealthSummary(
            vendor = FirewallVendor.FORTINET_FORTIGATE,
            applianceName = applianceName,
            hostname = hostname,
            firmwareVersion = version,
            cpuPercent = cpuPct,
            memoryPercent = memPct,
            activeSessions = activeSess,
            maxSessions = maxSess,
            haRole = haRole,
            haState = if (haArr != null && haArr.length() > 1) "${haArr.length()} cluster nodes" else "Active",
            statusNote = "FortiOS Monitor REST API v2"
        )
    }

    fun parseFortiGateSdwanSlaJson(json: String): List<WanGatewaySlaEntry> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val resultsObj = root.optJSONObject("results")
        val list = mutableListOf<WanGatewaySlaEntry>()

        if (resultsObj != null) {
            val slaNames = resultsObj.keys()
            while (slaNames.hasNext()) {
                val slaName = slaNames.next()
                val slaBody = resultsObj.optJSONObject(slaName) ?: continue
                val members = slaBody.optJSONObject("members") ?: continue
                val memberKeys = members.keys()
                while (memberKeys.hasNext()) {
                    val mKey = memberKeys.next()
                    val m = members.optJSONObject(mKey) ?: continue
                    list.add(
                        WanGatewaySlaEntry(
                            name = "$slaName (#$mKey)",
                            interfaceName = m.optString("interface", mKey),
                            gatewayIp = m.optString("gateway", slaBody.optString("server", "")),
                            role = if (m.optInt("sla_targets_met", 0) > 0) "SLA Pass" else "SLA Alert",
                            latencyMs = m.optDouble("latency", 0.0),
                            jitterMs = m.optDouble("jitter", 0.0),
                            packetLossPercent = m.optDouble("packet_loss", 0.0),
                            status = m.optString("status", "unknown").uppercase()
                        )
                    )
                }
            }
        }
        return list
    }

    fun parseFortiGateIpsecJson(json: String): List<FirewallIpsecTunnel> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("results") ?: return emptyList()
        val list = mutableListOf<FirewallIpsecTunnel>()
        for (i in 0 until arr.length()) {
            val p1 = arr.optJSONObject(i) ?: continue
            val p1Name = p1.optString("name", "Tunnel-${i + 1}")
            val gw = p1.optString("rgwy", p1.optString("wizard_type", ""))
            val proxies = p1.optJSONArray("proxyid")
            if (proxies != null && proxies.length() > 0) {
                for (j in 0 until proxies.length()) {
                    val p2 = proxies.optJSONObject(j) ?: continue
                    val p2Name = p2.optString("p2name", p1Name)
                    val status = p2.optString("status", "down").lowercase()
                    list.add(
                        FirewallIpsecTunnel(
                            id = "$p1Name::$p2Name",
                            name = if (p2Name == p1Name) p1Name else "$p1Name / $p2Name",
                            remoteGateway = gw,
                            localSubnet = extractFortiSubnets(p2.optJSONArray("proxy_src")),
                            remoteSubnet = extractFortiSubnets(p2.optJSONArray("proxy_dst")),
                            phase1State = "P1=$p1Name",
                            phase2State = status.uppercase(),
                            isUp = status == "up",
                            bytesIn = p2.optLong("incoming_bytes", 0L),
                            bytesOut = p2.optLong("outgoing_bytes", 0L),
                            spiOrSelector = "p1=$p1Name p2=$p2Name",
                            detailNote = "Expire ${p2.optLong("expire", 0L)}s"
                        )
                    )
                }
            } else {
                list.add(
                    FirewallIpsecTunnel(
                        id = p1Name,
                        name = p1Name,
                        remoteGateway = gw,
                        phase1State = p1.optString("status", "unknown").uppercase(),
                        phase2State = "No Phase2",
                        isUp = p1.optString("status", "").equals("up", true),
                        bytesIn = p1.optLong("incoming_bytes", 0L),
                        bytesOut = p1.optLong("outgoing_bytes", 0L)
                    )
                )
            }
        }
        return list
    }

    private fun extractFortiSubnets(arr: JSONArray?): String {
        if (arr == null || arr.length() == 0) return ""
        return (0 until arr.length()).mapNotNull { idx ->
            arr.optJSONObject(idx)?.optString("subnet", "")?.takeIf { it.isNotBlank() }
        }.joinToString(", ")
    }

    fun parseFortiGatePolicyHitsJson(monitorJson: String, cmdbJson: String = ""): List<FirewallPolicyHitEntry> {
        val namesById = mutableMapOf<String, Triple<String, String, String>>()
        runCatching {
            val cRoot = JSONObject(cmdbJson)
            val cArr = cRoot.optJSONArray("results") ?: JSONArray()
            for (i in 0 until cArr.length()) {
                val obj = cArr.optJSONObject(i) ?: continue
                val id = obj.optInt("policyid", -1).toString()
                val name = obj.optString("name", "").ifBlank { "Policy #$id" }
                val srcIntf = obj.optJSONArray("srcintf")?.optJSONObject(0)?.optString("name", "").orEmpty()
                val dstIntf = obj.optJSONArray("dstintf")?.optJSONObject(0)?.optString("name", "").orEmpty()
                namesById[id] = Triple(name, srcIntf, dstIntf)
            }
        }

        val root = runCatching { JSONObject(monitorJson) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("results") ?: return emptyList()
        val list = mutableListOf<FirewallPolicyHitEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optInt("policyid", i).toString()
            val hits = obj.optLong("hit_count", 0L)
            val bytes = obj.optLong("bytes", 0L)
            val meta = namesById[id]
            list.add(
                FirewallPolicyHitEntry(
                    policyId = id,
                    name = meta?.first ?: "Policy #$id",
                    srcZoneOrIfc = meta?.second ?: "--",
                    dstZoneOrIfc = meta?.third ?: "--",
                    action = obj.optString("action", "accept").uppercase(),
                    hitCount = hits,
                    bytesTransferred = bytes,
                    isZeroHit = hits == 0L
                )
            )
        }
        return list.sortedBy { it.hitCount }
    }

    // -------------------------------------------------------------------------
    // Palo Alto Networks PAN-OS XML Parsers
    // -------------------------------------------------------------------------

    fun parsePanOsKeygenXml(xml: String): Result<String> {
        val doc = parseXmlDocument(xml) ?: return Result.failure(IllegalArgumentException("Invalid PAN-OS XML response"))
        val status = doc.documentElement.getAttribute("status")
        val key = doc.getElementsByTagName("key").let { if (it.length > 0) it.item(0).textContent.trim() else "" }
        return if (status.equals("success", true) && key.isNotBlank()) {
            Result.success(key)
        } else {
            val msg = doc.getElementsByTagName("msg").let { if (it.length > 0) it.item(0).textContent.trim() else "Keygen rejected" }
            Result.failure(IllegalStateException(msg))
        }
    }

    fun parsePanOsHealthXml(
        applianceName: String,
        sysInfoXml: String,
        resXml: String,
        dpXml: String,
        sessXml: String
    ): FirewallHealthSummary {
        val sysDoc = parseXmlDocument(sysInfoXml)
        val hostname = sysDoc?.firstText("hostname").orEmpty().ifBlank { applianceName }
        val swVer = sysDoc?.firstText("sw-version").orEmpty()
        val uptime = sysDoc?.firstText("uptime").orEmpty()

        // Management Plane CPU from `show system resources` top line
        val mgmtCpu = Regex("""%Cpu\(s\):\s*([\d.]+)\s*us.*?([\d.]+)\s*id""").find(resXml)?.let { m ->
            val idle = m.groupValues[2].toFloatOrNull() ?: 100f
            (100f - idle).coerceIn(0f, 100f)
        }
        val memTotal = Regex("""MiB Mem\s*:\s*([\d.]+)\s*total,\s*[\d.]+\s*free,\s*([\d.]+)\s*used""").find(resXml)?.let { m ->
            val tot = m.groupValues[1].toFloatOrNull() ?: 1f
            val used = m.groupValues[2].toFloatOrNull() ?: 0f
            ((used / tot.coerceAtLeast(1f)) * 100f).coerceIn(0f, 100f)
        } ?: 0f

        // Data Plane CPU average from resource-monitor XML
        val dpDoc = parseXmlDocument(dpXml)
        val dpCpuAvg = dpDoc?.getElementsByTagName("cpu-load-average")?.let { nodes ->
            val vals = mutableListOf<Float>()
            for (i in 0 until nodes.length) {
                val entry = nodes.item(i) as? Element ?: continue
                entry.textOf("value").split(",").firstOrNull()?.trim()?.toFloatOrNull()?.let { vals.add(it) }
            }
            if (vals.isNotEmpty()) vals.average().toFloat() else null
        }

        val sessDoc = parseXmlDocument(sessXml)
        val activeSessions = sessDoc?.firstText("num-active")?.toLongOrNull() ?: 0L
        val maxSessions = sessDoc?.firstText("num-max")?.toLongOrNull() ?: 0L

        return FirewallHealthSummary(
            vendor = FirewallVendor.PALO_ALTO_PANOS,
            applianceName = applianceName,
            hostname = hostname,
            firmwareVersion = swVer,
            uptimeText = uptime,
            cpuPercent = dpCpuAvg ?: mgmtCpu ?: 0f,
            mgmtCpuPercent = mgmtCpu,
            dataPlaneCpuPercent = dpCpuAvg,
            memoryPercent = memPctClamp(memTotal),
            activeSessions = activeSessions,
            maxSessions = maxSessions,
            haRole = sysDoc?.firstText("operational-mode").orEmpty().ifBlank { "Normal" },
            haState = sysDoc?.firstText("model").orEmpty(),
            statusNote = "PAN-OS Management & Data Plane API"
        )
    }

    private fun memPctClamp(v: Float): Float = v.coerceIn(0f, 100f)

    fun parsePanOsIpsecSaXml(ipsecXml: String, ikeXml: String): List<FirewallIpsecTunnel> {
        val doc = parseXmlDocument(ipsecXml) ?: return emptyList()
        val ikeDoc = parseXmlDocument(ikeXml)
        val ikeStateByGw = mutableMapOf<String, String>()
        ikeDoc?.getElementsByTagName("entry")?.let { entries ->
            for (i in 0 until entries.length) {
                val el = entries.item(i) as? Element ?: continue
                val gwName = el.textOf("name")
                val state = el.textOf("state").ifBlank { el.textOf("status") }
                if (gwName.isNotBlank()) ikeStateByGw[gwName] = state
            }
        }

        val list = mutableListOf<FirewallIpsecTunnel>()
        val entries = doc.getElementsByTagName("entry")
        for (i in 0 until entries.length) {
            val el = entries.item(i) as? Element ?: continue
            val name = el.textOf("name").ifBlank { el.getAttribute("name") }.ifBlank { "IPsec-${i + 1}" }
            val gwName = el.textOf("gw")
            val remoteIp = el.textOf("remote").ifBlank { gwName }
            val encap = el.textOf("encap")
            val inSpi = el.textOf("i-spi")
            val outSpi = el.textOf("o-spi")
            val ikeState = ikeStateByGw[gwName] ?: "IKE-Ready"
            val isUp = inSpi.isNotBlank() || outSpi.isNotBlank() || el.textOf("state").contains("active", true)
            list.add(
                FirewallIpsecTunnel(
                    id = name,
                    name = name,
                    remoteGateway = remoteIp,
                    phase1State = ikeState,
                    phase2State = if (isUp) "ACTIVE" else "DOWN",
                    isUp = isUp,
                    spiOrSelector = "iSPI=$inSpi oSPI=$outSpi $encap".trim(),
                    detailNote = "GW=$gwName"
                )
            )
        }
        return list
    }

    fun parsePanOsGlobalProtectUsersXml(xml: String): List<GlobalProtectUserEntry> {
        val doc = parseXmlDocument(xml) ?: return emptyList()
        val list = mutableListOf<GlobalProtectUserEntry>()
        val entries = doc.getElementsByTagName("entry")
        for (i in 0 until entries.length) {
            val el = entries.item(i) as? Element ?: continue
            val user = el.textOf("username").ifBlank { continue }
            list.add(
                GlobalProtectUserEntry(
                    username = user,
                    virtualIp = el.textOf("virtual-ip").ifBlank { el.textOf("private-ip") },
                    publicIp = el.textOf("public-ip"),
                    clientOsOrVersion = el.textOf("client").ifBlank { el.textOf("client-os") },
                    loginDuration = el.textOf("login-time").ifBlank { el.textOf("host-id") }
                )
            )
        }
        return list
    }

    fun parsePanOsSecurityAndNatMatchXml(secXml: String, natXml: String = ""): PanOsPolicyMatchResult {
        val secDoc = parseXmlDocument(secXml)
        val entry = secDoc?.getElementsByTagName("entry")?.let { if (it.length > 0) it.item(0) as? Element else null }
        val ruleName = entry?.textOf("name")?.ifBlank { entry.getAttribute("name") }.orEmpty()
            .ifBlank { secDoc?.firstText("msg").orEmpty().ifBlank { "No Matching Security Rule" } }
        val action = entry?.textOf("action").orEmpty().uppercase().ifBlank { "NONE" }
        val fromZone = entry?.textOf("from").orEmpty()
        val toZone = entry?.textOf("to").orEmpty()
        val app = entry?.textOf("application").orEmpty()

        val natDoc = parseXmlDocument(natXml)
        val natEntry = natDoc?.getElementsByTagName("entry")?.let { if (it.length > 0) it.item(0) as? Element else null }
        val natRule = natEntry?.textOf("name")?.ifBlank { natEntry.getAttribute("name") }.orEmpty()
        val transSrc = natEntry?.textOf("source-translation").orEmpty()
        val transDst = natEntry?.textOf("destination-translation").orEmpty()

        return PanOsPolicyMatchResult(
            matchedRuleName = ruleName,
            action = action,
            sourceZone = fromZone,
            destZone = toZone,
            application = app,
            natRuleMatched = natRule.ifBlank { "No NAT translation" },
            translatedSrc = transSrc,
            translatedDst = transDst,
            rawXmlResponse = secXml.take(1500)
        )
    }

    // -------------------------------------------------------------------------
    // Cisco ASA / FTD Packet-Tracer & Failover Parsers
    // -------------------------------------------------------------------------

    fun parseCiscoPacketTracerOutput(rawOutput: String): CiscoPacketTracerResult {
        val phases = mutableListOf<CiscoPacketTracerPhase>()
        val phaseBlocks = rawOutput.split(Regex("""(?=Phase:\s*\d+)"""))
        for (block in phaseBlocks) {
            val numMatch = Regex("""Phase:\s*(\d+)""").find(block) ?: continue
            val pNum = numMatch.groupValues[1].toIntOrNull() ?: continue
            val type = Regex("""Type:\s*([^\r\n]+)""").find(block)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val subtype = Regex("""Subtype:\s*([^\r\n]+)""").find(block)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val result = Regex("""Result:\s*([^\r\n]+)""").find(block)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val configLine = Regex("""Config:\s*([\s\S]*?)(?=Additional Information:|Result:|Phase:|$)""")
                .find(block)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val extra = Regex("""Additional Information:\s*([\s\S]*?)(?=Result:|Phase:|$)""")
                .find(block)?.groupValues?.getOrNull(1)?.trim().orEmpty()

            phases.add(
                CiscoPacketTracerPhase(
                    phaseNumber = pNum,
                    phaseType = type,
                    subtype = subtype,
                    configLine = configLine.take(260),
                    result = result.ifBlank { "PROCESSED" },
                    extraInfo = extra.take(260)
                )
            )
        }

        val resultSection = rawOutput.substringAfterLast("Result:", "")
        val inIfc = Regex("""input-interface:\s*([^\r\n]+)""").find(resultSection)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val outIfc = Regex("""output-interface:\s*([^\r\n]+)""").find(resultSection)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val actionMatch = Regex("""Action:\s*([^\r\n]+)""").find(resultSection)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val dropReason = Regex("""Drop-reason:\s*([^\r\n]+)""").find(resultSection)?.groupValues?.getOrNull(1)?.trim().orEmpty()

        val verdict = when {
            actionMatch.isNotBlank() -> actionMatch.uppercase()
            phases.any { it.result.contains("DROP", true) } -> "DROP"
            phases.isNotEmpty() -> "ALLOW"
            else -> "UNKNOWN"
        }

        return CiscoPacketTracerResult(
            verdict = verdict,
            dropReason = dropReason,
            inputInterface = inIfc,
            outputInterface = outIfc,
            phases = phases,
            rawOutput = rawOutput
        )
    }

    fun parseCiscoAsaSummary(
        applianceName: String,
        failoverOut: String,
        connOut: String,
        xlateOut: String,
        vpnOut: String
    ): FirewallHealthSummary {
        val thisHostRole = Regex("""This host:\s*[^-]+-\s*([^\r\n]+)""", RegexOption.IGNORE_CASE)
            .find(failoverOut)?.groupValues?.getOrNull(1)?.trim()
            ?: if (failoverOut.contains("Active", true)) "Active" else "Standalone"
        val failoverState = if (failoverOut.contains("Failover On", true)) "Failover On" else "Failover Off/NA"
        val connCount = Regex("""(\d+)\s+in use""").find(connOut)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
        val xlateCount = Regex("""(\d+)\s+in use""").find(xlateOut)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
        val anyConnectSessions = Regex("""AnyConnect Client\s*:\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(vpnOut)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

        return FirewallHealthSummary(
            vendor = FirewallVendor.CISCO_ASA_FTD,
            applianceName = applianceName,
            hostname = applianceName,
            activeSessions = connCount,
            haRole = thisHostRole,
            haState = failoverState,
            statusNote = "AnyConnect=$anyConnectSessions | XLATE=$xlateCount"
        )
    }

    // -------------------------------------------------------------------------
    // pfSense / OPNsense Parsers
    // -------------------------------------------------------------------------

    fun parsePfGatewayStatus(raw: String): List<WanGatewaySlaEntry> {
        val list = mutableListOf<WanGatewaySlaEntry>()
        val jsonRoot = runCatching { JSONObject(raw) }.getOrNull()
        val dataArr = jsonRoot?.optJSONArray("data") ?: jsonRoot?.optJSONArray("items")
        if (dataArr != null) {
            for (i in 0 until dataArr.length()) {
                val obj = dataArr.optJSONObject(i) ?: continue
                list.add(
                    WanGatewaySlaEntry(
                        name = obj.optString("name", "GW-${i + 1}"),
                        interfaceName = obj.optString("interface", obj.optString("friendlyif", "wan")),
                        gatewayIp = obj.optString("monitorip", obj.optString("gateway", "")),
                        role = if (obj.optBoolean("defaultgw", false)) "Default GW" else "Failover GW",
                        latencyMs = obj.optString("delay", "0").removeSuffix("ms").trim().toDoubleOrNull() ?: 0.0,
                        jitterMs = obj.optString("stddev", "0").removeSuffix("ms").trim().toDoubleOrNull() ?: 0.0,
                        packetLossPercent = obj.optString("loss", "0").removeSuffix("%").trim().toDoubleOrNull() ?: 0.0,
                        status = obj.optString("status", obj.optString("status_translated", "online")).uppercase()
                    )
                )
            }
            return list
        }

        // CLI `dpinger` / `pfSsh.php` / `pluginctl -g` line parser:
        // Example: "WAN_DHCP 1.1.1.1 14.2ms 1.8ms 0.0% online"
        raw.lines().forEach { line ->
            val cols = line.trim().split(Regex("""\s+"""))
            if (cols.size >= 5 && cols.any { it.contains("ms", true) || it.contains("%") }) {
                val delay = cols.firstOrNull { it.endsWith("ms", true) }?.removeSuffix("ms")?.toDoubleOrNull() ?: 0.0
                val msCols = cols.filter { it.endsWith("ms", true) }
                val stddev = msCols.getOrNull(1)?.removeSuffix("ms")?.toDoubleOrNull() ?: 0.0
                val loss = cols.firstOrNull { it.endsWith("%") }?.removeSuffix("%")?.toDoubleOrNull() ?: 0.0
                val st = cols.lastOrNull()?.uppercase() ?: "ONLINE"
                list.add(
                    WanGatewaySlaEntry(
                        name = cols[0],
                        interfaceName = "WAN",
                        gatewayIp = cols.getOrNull(1).orEmpty(),
                        role = "dpinger",
                        latencyMs = delay,
                        jitterMs = stddev,
                        packetLossPercent = loss,
                        status = st
                    )
                )
            }
        }
        return list
    }

    fun parseCarpVipStatus(raw: String): List<CarpVipEntry> {
        val list = mutableListOf<CarpVipEntry>()
        val jsonObj = runCatching { JSONObject(raw) }.getOrNull()
        val arr = jsonObj?.optJSONArray("data") ?: jsonObj?.optJSONArray("rows")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(
                    CarpVipEntry(
                        vhid = obj.optString("vhid", "${i + 1}"),
                        interfaceName = obj.optString("interface", ""),
                        vipAddress = obj.optString("subnet", obj.optString("vip", "")),
                        status = obj.optString("status", "MASTER").uppercase(),
                        advskew = obj.optString("advskew", "0")
                    )
                )
            }
            return list
        }

        // Parse FreeBSD `ifconfig` CARP lines:
        // "carp: MASTER vhid 10 advbase 1 advskew 0"
        var currentIfc = ""
        var currentInet = ""
        raw.lines().forEach { line ->
            val ifcMatch = Regex("""^([a-zA-Z0-9._-]+):\s+flags=""").find(line)
            if (ifcMatch != null) {
                currentIfc = ifcMatch.groupValues[1]
                currentInet = ""
            }
            val inetMatch = Regex("""inet\s+(\d{1,3}(?:\.\d{1,3}){3}).*vhid\s+(\d+)""").find(line)
            if (inetMatch != null) {
                currentInet = inetMatch.groupValues[1]
            }
            val carpMatch = Regex("""carp:\s*(MASTER|BACKUP|INIT)\s+vhid\s+(\d+)(?:.*advskew\s+(\d+))?""", RegexOption.IGNORE_CASE).find(line)
            if (carpMatch != null) {
                list.add(
                    CarpVipEntry(
                        vhid = carpMatch.groupValues[2],
                        interfaceName = currentIfc.ifBlank { "carp" },
                        vipAddress = currentInet.ifBlank { "VIP-${carpMatch.groupValues[2]}" },
                        status = carpMatch.groupValues[1].uppercase(),
                        advskew = carpMatch.groupValues[3]
                    )
                )
            }
        }
        return list
    }

    fun parsePfCtlStatesAndLeases(pfInfoOut: String, pfStatesOut: String, leasesOut: String, unboundOut: String): PfStateSummary {
        val currentStates = Regex("""current entries\s+(\d+)""", RegexOption.IGNORE_CASE)
            .find(pfInfoOut)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
        val limit = Regex("""limit\s+(\d+)""", RegexOption.IGNORE_CASE)
            .find(pfInfoOut)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
        val topFlows = pfStatesOut.lines().map { it.trim() }.filter { it.isNotBlank() }.take(12)

        val leaseList = mutableListOf<String>()
        var currentIp = ""
        leasesOut.lines().forEach { raw ->
            val line = raw.trim()
            val ipM = Regex("""^lease\s+(\d{1,3}(?:\.\d{1,3}){3})\s*\{""").find(line)
            if (ipM != null) currentIp = ipM.groupValues[1]
            val macM = Regex("""hardware ethernet\s+([0-9a-fA-F:]+);""").find(line)
            if (macM != null && currentIp.isNotBlank()) {
                leaseList.add("$currentIp -> ${macM.groupValues[1].uppercase()}")
                currentIp = ""
            }
        }
        val unboundStatus = when {
            unboundOut.contains("running", true) || unboundOut.contains("is running", true) -> "Running"
            unboundOut.contains("stopped", true) || unboundOut.contains("not running", true) -> "Stopped"
            unboundOut.isNotBlank() -> unboundOut.lines().firstOrNull { it.isNotBlank() }?.take(48) ?: "Active"
            else -> "Checked"
        }

        return PfStateSummary(
            totalStates = if (currentStates > 0) currentStates else topFlows.size.toLong(),
            stateTableLimit = limit,
            topProtocolFlows = topFlows,
            dhcpLeaseCount = leaseList.size,
            unboundStatus = unboundStatus,
            recentLeases = leaseList.takeLast(12)
        )
    }

    private fun parseXmlDocument(xml: String) = runCatching {
        val trimmed = xml.trim()
        if (!trimmed.startsWith("<")) return@runCatching null
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        factory.newDocumentBuilder().parse(InputSource(StringReader(trimmed)))
    }.getOrNull()

    private fun Element.textOf(tag: String): String {
        val nodes = getElementsByTagName(tag)
        if (nodes.length == 0) return ""
        val node = nodes.item(0)
        return if (node.nodeType == Node.ELEMENT_NODE) node.textContent?.trim().orEmpty() else ""
    }

    private fun org.w3c.dom.Document.firstText(tag: String): String {
        val nodes = getElementsByTagName(tag)
        return if (nodes.length > 0) nodes.item(0).textContent?.trim().orEmpty() else ""
    }
}
