// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/EngineeringUtilitiesRepository.kt ===
package com.enterwebslinger.network.data.repository

import android.content.Context
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.enterwebslinger.network.core.util.NetworkByteUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class ArpNeighborEntry(
    val ipAddress: String,
    val macAddress: String,
    val deviceInterface: String,
    val flags: String,
    val vendor: String,
    val isRandomizedMac: Boolean
)

data class WhoisResult(
    val query: String,
    val serverUsed: String,
    val rawText: String,
    val registrar: String = "",
    val creationDate: String = "",
    val expiryDate: String = "",
    val nameServers: List<String> = emptyList(),
    val asnInfo: String = ""
)

data class SslCertInfo(
    val host: String,
    val port: Int,
    val subjectDn: String,
    val issuerDn: String,
    val subjectAlternativeNames: List<String>,
    val serialNumberHex: String,
    val sha256Fingerprint: String,
    val sha1Fingerprint: String,
    val validFrom: String,
    val validUntil: String,
    val daysUntilExpiry: Long,
    val isExpired: Boolean,
    val negotiatedProtocol: String,
    val negotiatedCipherSuite: String,
    val chainLength: Int
)

data class Ipv4SubnetCalculation(
    val ipAddress: String,
    val cidrPrefix: Int,
    val netmask: String,
    val wildcardMask: String,
    val networkAddress: String,
    val broadcastAddress: String,
    val firstUsableIp: String,
    val lastUsableIp: String,
    val totalHosts: Long,
    val usableHosts: Long,
    val ipClass: String,
    val isPrivateRfc1918: Boolean,
    val binaryIp: String,
    val binaryNetmask: String
)

data class VlsmSubnetAllocation(
    val subnetName: String,
    val requiredHosts: Int,
    val allocatedHosts: Int,
    val cidrPrefix: Int,
    val networkAddress: String,
    val netmask: String,
    val usableRange: String,
    val broadcastAddress: String
)

data class Ipv6Calculation(
    val inputAddress: String,
    val prefixLength: Int,
    val expandedFormat: String,
    val compressedFormat: String,
    val addressScope: String,
    val ipv4MappedEquivalent: String?,
    val totalAddressesEstimate: String
)

data class Iperf3TestProgress(
    val isRunning: Boolean = false,
    val targetHost: String = "",
    val currentMbps: Double = 0.0,
    val totalBytesTransferred: Long = 0,
    val elapsedSeconds: Double = 0.0,
    val finalAverageMbps: Double = 0.0,
    val logOutput: List<String> = emptyList(),
    val error: String? = null
)

data class WifiRoamingEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val oldBssid: String,
    val newBssid: String,
    val ssid: String,
    val rssiDbm: Int,
    val frequencyMhz: Int,
    val linkSpeedMbps: Int,
    val transitionLatencyMs: Long
)

data class SipOptionsResult(
    val targetHost: String,
    val targetPort: Int,
    val protocol: String,
    val rttMs: Long,
    val statusCode: Int,
    val statusText: String,
    val serverHeader: String,
    val allowMethods: String,
    val rawResponse: String
)

class EngineeringUtilitiesRepository(private val context: Context? = null) {

    @Suppress("unused")
    private val wifiManager = context?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val _roamingEvents = MutableStateFlow<List<WifiRoamingEvent>>(emptyList())
    val roamingEvents: Flow<List<WifiRoamingEvent>> = _roamingEvents.asStateFlow()

    private var lastObservedBssid: String? = null
    private var lastBssidChangeTimestamp: Long = 0

    // Offline IEEE OUI Vendor Database
    private val ouiMap = mapOf(
        "00:00:0C" to "Cisco Systems",
        "00:01:42" to "Cisco Systems",
        "00:01:43" to "Cisco Systems",
        "00:01:C7" to "Cisco Systems",
        "00:01:C9" to "Cisco Systems",
        "00:03:6B" to "Cisco Systems",
        "00:07:0D" to "Cisco Systems",
        "00:08:20" to "Cisco Systems",
        "00:08:A3" to "Cisco Systems",
        "00:08:A4" to "Cisco Systems",
        "00:03:7F" to "Atheros Communications",
        "00:0C:29" to "VMware, Inc.",
        "00:50:56" to "VMware, Inc.",
        "00:05:69" to "VMware, Inc.",
        "00:1C:42" to "Parallels, Inc.",
        "00:15:5D" to "Microsoft Corporation (Hyper-V)",
        "00:1A:11" to "Google, Inc.",
        "F4:F5:DB" to "Google, Inc.",
        "3C:5A:37" to "Google, Inc.",
        "54:60:09" to "Google, Inc.",
        "00:17:88" to "Philips Lighting",
        "00:1A:79" to "Ubiquiti Networks",
        "04:18:D6" to "Ubiquiti Networks",
        "24:A4:3C" to "Ubiquiti Networks",
        "68:D7:9A" to "Ubiquiti Networks",
        "74:83:C2" to "Ubiquiti Networks",
        "78:8A:20" to "Ubiquiti Networks",
        "80:2A:A8" to "Ubiquiti Networks",
        "B4:FB:E4" to "Ubiquiti Networks",
        "DC:9F:DB" to "Ubiquiti Networks",
        "F0:9F:C2" to "Ubiquiti Networks",
        "00:1D:AA" to "Apple, Inc.",
        "00:1E:52" to "Apple, Inc.",
        "00:23:12" to "Apple, Inc.",
        "00:25:00" to "Apple, Inc.",
        "00:26:BB" to "Apple, Inc.",
        "18:AF:61" to "Apple, Inc.",
        "3C:07:54" to "Apple, Inc.",
        "70:3E:AC" to "Apple, Inc.",
        "88:66:5A" to "Apple, Inc.",
        "A4:83:E7" to "Apple, Inc.",
        "AC:BC:32" to "Apple, Inc.",
        "BC:92:6B" to "Apple, Inc.",
        "DC:2B:61" to "Apple, Inc.",
        "F0:18:98" to "Apple, Inc.",
        "00:1E:8C" to "Samsung Electronics",
        "00:26:37" to "Samsung Electronics",
        "08:EE:8B" to "Samsung Electronics",
        "34:BE:00" to "Samsung Electronics",
        "50:01:D9" to "Samsung Electronics",
        "A8:9F:BA" to "Samsung Electronics",
        "B8:B9:8E" to "Samsung Electronics",
        "00:09:5B" to "Netgear Inc.",
        "00:14:6C" to "Netgear Inc.",
        "00:1F:33" to "Netgear Inc.",
        "20:4E:7F" to "Netgear Inc.",
        "A0:04:60" to "Netgear Inc.",
        "C0:3F:0E" to "Netgear Inc.",
        "00:1D:0F" to "TP-Link Technologies",
        "14:CC:20" to "TP-Link Technologies",
        "50:C7:BF" to "TP-Link Technologies",
        "60:32:B1" to "TP-Link Technologies",
        "70:4F:57" to "TP-Link Technologies",
        "98:DA:C4" to "TP-Link Technologies",
        "B0:4E:26" to "TP-Link Technologies",
        "C0:25:E9" to "TP-Link Technologies",
        "E8:48:B8" to "TP-Link Technologies",
        "00:15:6D" to "Ubiquiti Networks",
        "00:27:22" to "Ubiquiti Networks",
        "00:0C:42" to "MikroTik",
        "08:55:31" to "MikroTik",
        "2C:C8:1B" to "MikroTik",
        "48:8F:5A" to "MikroTik",
        "64:D1:54" to "MikroTik",
        "B8:69:F4" to "MikroTik",
        "C4:AD:34" to "MikroTik",
        "D4:CA:6D" to "MikroTik",
        "E4:8D:8C" to "MikroTik",
        "00:04:96" to "Extreme Networks",
        "00:05:33" to "Brocade Communications",
        "00:10:18" to "Broadcom Corp",
        "00:1B:21" to "Intel Corporation",
        "00:21:6A" to "Intel Corporation",
        "00:24:D7" to "Intel Corporation",
        "34:13:E8" to "Intel Corporation",
        "3C:FD:FE" to "Intel Corporation",
        "68:05:CA" to "Intel Corporation",
        "80:86:F2" to "Intel Corporation",
        "00:14:22" to "Dell Inc.",
        "00:18:8B" to "Dell Inc.",
        "00:21:9B" to "Dell Inc.",
        "14:FE:B5" to "Dell Inc.",
        "18:66:DA" to "Dell Inc.",
        "24:B6:FD" to "Dell Inc.",
        "F8:DB:88" to "Dell Inc.",
        "00:08:02" to "Hewlett Packard Enterprise",
        "00:18:71" to "Hewlett Packard Enterprise",
        "00:24:81" to "Hewlett Packard Enterprise",
        "3C:D9:2B" to "Hewlett Packard Enterprise",
        "00:1C:73" to "Arista Networks",
        "28:99:3A" to "Arista Networks",
        "44:4C:A8" to "Arista Networks",
        "74:83:EF" to "Arista Networks",
        "00:1F:12" to "Juniper Networks",
        "00:26:88" to "Juniper Networks",
        "2C:6B:F5" to "Juniper Networks",
        "40:B4:F0" to "Juniper Networks",
        "54:E0:32" to "Juniper Networks",
        "84:18:88" to "Juniper Networks",
        "B8:C2:53" to "Raspberry Pi Trading Ltd",
        "DC:A6:32" to "Raspberry Pi Trading Ltd",
        "E4:5F:01" to "Raspberry Pi Trading Ltd",
        "28:CD:C1" to "Raspberry Pi Trading Ltd",
        "D8:3A:DD" to "Raspberry Pi Trading Ltd",
        "18:FE:34" to "Espressif Inc.",
        "24:0A:C4" to "Espressif Inc.",
        "24:6F:28" to "Espressif Inc.",
        "30:AE:A4" to "Espressif Inc.",
        "5C:CF:7F" to "Espressif Inc.",
        "84:F3:EB" to "Espressif Inc.",
        "A4:CF:12" to "Espressif Inc.",
        "EC:FA:BC" to "Espressif Inc."
    )

    // =========================================================================
    // 1. MAC Vendor Resolution & Randomized MAC Detection
    // =========================================================================

    fun isRandomizedMac(macAddress: String): Boolean {
        val clean = macAddress.replace(":", "").replace("-", "")
        if (clean.length < 2) return false
        val firstOctet = clean.substring(0, 2).toIntOrNull(16) ?: return false
        return (firstOctet and 0x02) != 0
    }

    fun resolveVendor(macAddress: String): String {
        if (macAddress.isBlank()) return ""
        if (isRandomizedMac(macAddress)) {
            return "Randomized / Private MAC (Locally Administered)"
        }
        val clean = macAddress.uppercase().replace("-", ":")
        val parts = clean.split(":")
        if (parts.size >= 3) {
            val prefix = "${parts[0]}:${parts[1]}:${parts[2]}"
            ouiMap[prefix]?.let { return it }
        }
        return "Unknown Hardware Vendor"
    }

    suspend fun getArpNeighbors(): List<ArpNeighborEntry> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<ArpNeighborEntry>()
        try {
            val arpFile = File("/proc/net/arp")
            if (arpFile.exists() && arpFile.canRead()) {
                arpFile.bufferedReader().useLines { lines ->
                    lines.drop(1).forEach { line ->
                        val tokens = line.trim().split(Regex("\\s+"))
                        if (tokens.size >= 6) {
                            val ip = tokens[0]
                            val flags = tokens[2]
                            val mac = tokens[3]
                            val iface = tokens[5]
                            if (mac != "00:00:00:00:00:00") {
                                entries.add(
                                    ArpNeighborEntry(
                                        ipAddress = ip,
                                        macAddress = mac,
                                        deviceInterface = iface,
                                        flags = flags,
                                        vendor = resolveVendor(mac),
                                        isRandomizedMac = isRandomizedMac(mac)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("EngineeringUtils", "Error reading /proc/net/arp: ${e.message}")
        }
        entries
    }

    // =========================================================================
    // 2. Raw Whois Client (TCP port 43 with IANA referral following)
    // =========================================================================

    suspend fun queryWhois(query: String): WhoisResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return@withContext WhoisResult(
                query = "",
                serverUsed = "",
                rawText = "Enter a domain name, IP address, or ASN to query."
            )
        }
        var server = "whois.iana.org"
        var rawResult = ""
        var hops = 0

        try {
            while (hops < 3) {
                hops++
                val response = queryWhoisSocket(server, 43, cleanQuery)
                rawResult = response

                val referralRegex = Regex("(?i)(?:refer|whois server|whois):\\s*([a-zA-Z0-9.-]+)")
                val match = referralRegex.find(response)
                if (match != null) {
                    val nextServer = match.groupValues[1].trim()
                    if (nextServer.isNotEmpty() && nextServer != server) {
                        server = nextServer
                        continue
                    }
                }
                break
            }

            val registrar = Regex("(?i)Registrar:\\s*(.*)").find(rawResult)?.groupValues?.get(1)?.trim() ?: ""
            val creationDate = Regex("(?i)(?:Creation Date|Created):\\s*(.*)").find(rawResult)?.groupValues?.get(1)?.trim() ?: ""
            val expiryDate = Regex("(?i)(?:Registry Expiry Date|Expir(?:y|ation) Date):\\s*(.*)").find(rawResult)?.groupValues?.get(1)?.trim() ?: ""
            val nameServers = Regex("(?i)Name Server:\\s*(.*)").findAll(rawResult).map { it.groupValues[1].trim() }.toList()

            val asnInfo = if (cleanQuery.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                fetchBgpNetworkInfo(cleanQuery)
            } else {
                Regex("(?i)(?:origin|aut-num):\\s*(.*)").find(rawResult)?.groupValues?.get(1)?.trim() ?: ""
            }

            WhoisResult(
                query = cleanQuery,
                serverUsed = server,
                rawText = rawResult,
                registrar = registrar,
                creationDate = creationDate,
                expiryDate = expiryDate,
                nameServers = nameServers,
                asnInfo = asnInfo
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            WhoisResult(
                query = cleanQuery,
                serverUsed = server,
                rawText = "Whois query failed: ${e.message}"
            )
        }
    }

    private fun queryWhoisSocket(host: String, port: Int, query: String): String {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 6000)
            socket.soTimeout = 8000
            val writer = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            writer.write("$query\r\n")
            writer.flush()
            val sb = java.lang.StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
            }
            return sb.toString()
        }
    }

    private fun fetchBgpNetworkInfo(ip: String): String {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://stat.ripe.net/data/network-info/data.json?resource=$ip")
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.inputStream.bufferedReader().use { reader ->
                val json = JSONObject(reader.readText())
                val data = json.optJSONObject("data")
                val asns = data?.optJSONArray("asns")
                val prefix = data?.optString("prefix", "") ?: ""
                val asn = if (asns != null && asns.length() > 0) "AS${asns.getInt(0)}" else "Unknown ASN"
                "$asn (Prefix: $prefix)"
            }
        } catch (_: Exception) {
            "BGP ASN Lookup Unavailable"
        } finally {
            conn?.disconnect()
        }
    }

    // =========================================================================
    // 3. SSL/TLS Certificate & Cipher Suite Inspector
    // =========================================================================

    suspend fun inspectTlsCertificate(host: String, port: Int = 443): SslCertInfo = withContext(Dispatchers.IO) {
        if (host.isBlank()) throw IllegalArgumentException("Target host is required")
        var capturedCerts: Array<X509Certificate>? = null

        val trustAllManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                if (chain != null && chain.isNotEmpty()) {
                    @Suppress("UNCHECKED_CAST")
                    capturedCerts = chain as Array<X509Certificate>
                }
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
        }

        val socketFactory = sslContext.socketFactory
        (socketFactory.createSocket() as SSLSocket).use { socket ->
            socket.connect(InetSocketAddress(host.trim(), port), 7000)
            socket.soTimeout = 7000
            socket.startHandshake()

            val session = socket.session
            val protocol = session.protocol ?: "Unknown TLS"
            val cipher = session.cipherSuite ?: "Unknown Cipher"

            val certs = capturedCerts ?: session.peerCertificates?.filterIsInstance<X509Certificate>()?.toTypedArray()
                ?: throw IllegalStateException("No peer certificates returned by server")

            val leaf = certs[0]
            val subjectDn = leaf.subjectDN?.name ?: "Unknown Subject"
            val issuerDn = leaf.issuerDN?.name ?: "Unknown Issuer"
            val serialHex = leaf.serialNumber.toString(16).uppercase()

            val sha256 = getFingerprint(leaf, "SHA-256")
            val sha1 = getFingerprint(leaf, "SHA-1")

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
            val notBefore = leaf.notBefore
            val notAfter = leaf.notAfter
            val now = Date()

            val daysRemaining = ((notAfter.time - now.time) / (1000 * 60 * 60 * 24)).coerceAtLeast(-9999)
            val isExpired = now.after(notAfter) || now.before(notBefore)

            val sans = mutableListOf<String>()
            try {
                val altNames = leaf.subjectAlternativeNames
                if (altNames != null) {
                    for (item in altNames) {
                        if (item.size >= 2) {
                            sans.add(item[1].toString())
                        }
                    }
                }
            } catch (_: Exception) {}

            SslCertInfo(
                host = host.trim(),
                port = port,
                subjectDn = subjectDn,
                issuerDn = issuerDn,
                subjectAlternativeNames = sans,
                serialNumberHex = serialHex,
                sha256Fingerprint = sha256,
                sha1Fingerprint = sha1,
                validFrom = sdf.format(notBefore),
                validUntil = sdf.format(notAfter),
                daysUntilExpiry = daysRemaining,
                isExpired = isExpired,
                negotiatedProtocol = protocol,
                negotiatedCipherSuite = cipher,
                chainLength = certs.size
            )
        }
    }

    private fun getFingerprint(cert: X509Certificate, algorithm: String): String {
        val md = MessageDigest.getInstance(algorithm)
        val digest = md.digest(cert.encoded)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    // =========================================================================
    // 4. Offline Subnet / CIDR / VLSM & IPv6 Calculator
    // =========================================================================

    fun calculateIpv4Subnet(ipStr: String, prefixLength: Int): Ipv4SubnetCalculation? {
        val parts = ipStr.trim().split(".")
        if (parts.size != 4 || parts.any { it.toIntOrNull() !in 0..255 }) return null

        val prefix = prefixLength.coerceIn(0, 32)
        val ipParts = parts.map { it.toInt() }
        val ipNum = NetworkByteUtils.stringToIpLong(ipStr)

        val maskNum = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        val wildcardNum = maskNum.inv() and 0xFFFFFFFFL

        val networkNum = ipNum and maskNum
        val broadcastNum = ipNum or wildcardNum

        val totalHosts = if (prefix >= 32) 1L else (1L shl (32 - prefix))
        val usableHosts = if (prefix >= 31) 0L else totalHosts - 2

        val firstUsableNum = if (prefix >= 31) networkNum else networkNum + 1
        val lastUsableNum = if (prefix >= 31) broadcastNum else broadcastNum - 1

        val ipClass = when (ipParts[0]) {
            in 1..126 -> "Class A"
            127 -> "Loopback (127.0.0.0/8)"
            in 128..191 -> "Class B"
            in 192..223 -> "Class C"
            in 224..239 -> "Class D (Multicast)"
            else -> "Class E (Experimental)"
        }

        val octet1 = ipParts[0]
        val octet2 = ipParts[1]
        val isPrivate = (octet1 == 10) ||
            (octet1 == 172 && octet2 in 16..31) ||
            (octet1 == 192 && octet2 == 168) ||
            (octet1 == 100 && octet2 in 64..127)

        return Ipv4SubnetCalculation(
            ipAddress = NetworkByteUtils.ipLongToString(ipNum),
            cidrPrefix = prefix,
            netmask = NetworkByteUtils.ipLongToString(maskNum),
            wildcardMask = NetworkByteUtils.ipLongToString(wildcardNum),
            networkAddress = NetworkByteUtils.ipLongToString(networkNum),
            broadcastAddress = NetworkByteUtils.ipLongToString(broadcastNum),
            firstUsableIp = NetworkByteUtils.ipLongToString(firstUsableNum),
            lastUsableIp = NetworkByteUtils.ipLongToString(lastUsableNum),
            totalHosts = totalHosts,
            usableHosts = usableHosts,
            ipClass = ipClass,
            isPrivateRfc1918 = isPrivate,
            binaryIp = toBinaryString(ipNum),
            binaryNetmask = toBinaryString(maskNum)
        )
    }

    private fun toBinaryString(value: Long): String {
        val s = java.lang.Long.toBinaryString(value)
        val padded = s.padStart(32, '0')
        return "${padded.substring(0, 8)}.${padded.substring(8, 16)}.${padded.substring(16, 24)}.${padded.substring(24, 32)}"
    }

    fun calculateVlsm(parentCidr: String, subnetHostRequirements: List<Pair<String, Int>>): List<VlsmSubnetAllocation> {
        if (parentCidr.isBlank() || subnetHostRequirements.isEmpty()) return emptyList()
        val parts = parentCidr.trim().split("/")
        val baseIp = parts[0]
        val basePrefix = parts.getOrNull(1)?.toIntOrNull() ?: 24
        val calc = calculateIpv4Subnet(baseIp, basePrefix) ?: return emptyList()

        var currentIpNum = NetworkByteUtils.stringToIpLong(calc.networkAddress)
        val sortedReqs = subnetHostRequirements.sortedByDescending { it.second }

        val allocations = mutableListOf<VlsmSubnetAllocation>()
        for ((name, hostsReq) in sortedReqs) {
            var prefix = 30
            while (prefix >= basePrefix) {
                val capacity = (1L shl (32 - prefix)) - 2
                if (capacity >= hostsReq) break
                prefix--
            }
            if (prefix < basePrefix) prefix = basePrefix

            val maskNum = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
            val totalSize = 1L shl (32 - prefix)
            val netNum = currentIpNum
            val bcastNum = netNum + totalSize - 1
            val firstUsable = netNum + 1
            val lastUsable = bcastNum - 1

            allocations.add(
                VlsmSubnetAllocation(
                    subnetName = name,
                    requiredHosts = hostsReq,
                    allocatedHosts = (totalSize - 2).toInt().coerceAtLeast(0),
                    cidrPrefix = prefix,
                    networkAddress = NetworkByteUtils.ipLongToString(netNum),
                    netmask = NetworkByteUtils.ipLongToString(maskNum),
                    usableRange = "${NetworkByteUtils.ipLongToString(firstUsable)} - ${NetworkByteUtils.ipLongToString(lastUsable)}",
                    broadcastAddress = NetworkByteUtils.ipLongToString(bcastNum)
                )
            )

            currentIpNum += totalSize
        }
        return allocations
    }

    fun calculateIpv6(ipv6Str: String, prefixLength: Int = 64): Ipv6Calculation? {
        val clean = ipv6Str.trim().split("/")[0]
        if (clean.isBlank() || !clean.contains(":")) return null
        val inet = try {
            InetAddress.getByName(clean)
        } catch (_: Exception) {
            return null
        }

        val rawBytes = inet.address
        if (rawBytes.size != 16) return null

        val expandedSb = java.lang.StringBuilder()
        for (i in 0 until rawBytes.size step 2) {
            val high = rawBytes[i].toInt() and 0xFF
            val low = rawBytes[i + 1].toInt() and 0xFF
            val block = (high shl 8) or low
            expandedSb.append("%04x".format(block))
            if (i < 14) expandedSb.append(":")
        }
        val expanded = expandedSb.toString()
        val compressed = inet.hostAddress ?: clean

        val scope = when {
            expanded.startsWith("fe8") || expanded.startsWith("fe9") || expanded.startsWith("fea") || expanded.startsWith("feb") -> "Link-Local Unicast (fe80::/10)"
            expanded.startsWith("fc") || expanded.startsWith("fd") -> "Unique Local Unicast (ULA, fc00::/7)"
            expanded.startsWith("ff") -> "Multicast (ff00::/8)"
            expanded == "0000:0000:0000:0000:0000:0000:0000:0001" -> "Loopback (::1/128)"
            expanded.startsWith("2") || expanded.startsWith("3") -> "Global Unicast (2000::/3)"
            else -> "Reserved / Other"
        }

        val ipv4Mapped = if (expanded.startsWith("0000:0000:0000:0000:0000:ffff:")) {
            val b1 = rawBytes[12].toInt() and 0xFF
            val b2 = rawBytes[13].toInt() and 0xFF
            val b3 = rawBytes[14].toInt() and 0xFF
            val b4 = rawBytes[15].toInt() and 0xFF
            "$b1.$b2.$b3.$b4"
        } else null

        val totalAddresses = when (prefixLength) {
            64 -> "18,446,744,073,709,551,616 (2^64 standard /64 subnet)"
            48 -> "1.208 x 10^24 (2^80 ISP site allocation /48)"
            128 -> "1 host (single address)"
            else -> "2^${128 - prefixLength} addresses"
        }

        return Ipv6Calculation(
            inputAddress = clean,
            prefixLength = prefixLength,
            expandedFormat = expanded,
            compressedFormat = compressed,
            addressScope = scope,
            ipv4MappedEquivalent = ipv4Mapped,
            totalAddressesEstimate = totalAddresses
        )
    }

    // =========================================================================
    // 5. iPerf3-Compatible LAN Throughput Client (Port 5201)
    // =========================================================================

    suspend fun runIperf3Client(
        serverHost: String,
        serverPort: Int = 5201,
        durationSeconds: Int = 5,
        onProgress: (Iperf3TestProgress) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (serverHost.isBlank()) {
            onProgress(Iperf3TestProgress(isRunning = false, error = "iPerf3 server host is required"))
            return@withContext
        }
        val logs = mutableListOf<String>()
        fun addLog(msg: String) {
            logs.add(msg)
            onProgress(Iperf3TestProgress(isRunning = true, targetHost = serverHost, logOutput = logs.toList()))
        }

        addLog("Connecting to iPerf3 server at $serverHost:$serverPort...")
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(serverHost.trim(), serverPort), 5000)
                socket.tcpNoDelay = true
                socket.sendBufferSize = 128 * 1024
                socket.receiveBufferSize = 128 * 1024
                addLog("Connected. Sending iPerf3 handshake protocol...")

                val out = socket.getOutputStream()

                val cookie = UUID.randomUUID().toString().replace("-", "").take(36) + "\u0000"
                out.write(cookie.toByteArray(StandardCharsets.US_ASCII))
                out.flush()

                val paramsJson = JSONObject().apply {
                    put("tcp", true)
                    put("omit", 0)
                    put("time", durationSeconds)
                    put("num", 0)
                    put("blockcount", 0)
                }.toString()

                val paramBytes = paramsJson.toByteArray(StandardCharsets.UTF_8)
                val lenHeader = byteArrayOf(
                    ((paramBytes.size shr 24) and 0xFF).toByte(),
                    ((paramBytes.size shr 16) and 0xFF).toByte(),
                    ((paramBytes.size shr 8) and 0xFF).toByte(),
                    (paramBytes.size and 0xFF).toByte()
                )
                out.write(lenHeader)
                out.write(paramBytes)
                out.flush()
                addLog("Sent test parameters: $durationSeconds seconds TCP stream")

                val buffer = ByteArray(64 * 1024)
                for (i in buffer.indices) buffer[i] = (i % 256).toByte()

                val startTime = System.currentTimeMillis()
                val targetDurationMs = durationSeconds * 1000L
                var totalBytesSent = 0L
                var lastReportTime = startTime

                while (System.currentTimeMillis() - startTime < targetDurationMs) {
                    out.write(buffer)
                    totalBytesSent += buffer.size

                    val now = System.currentTimeMillis()
                    if (now - lastReportTime >= 1000) {
                        val elapsedSec = (now - startTime) / 1000.0
                        val currentRateMbps = (totalBytesSent * 8.0) / (elapsedSec * 1_000_000.0)
                        val logLine = "Interval [${elapsedSec.toInt()}s]: ${"%.2f".format(currentRateMbps)} Mbps | ${(totalBytesSent / (1024 * 1024))} MB transferred"
                        logs.add(logLine)
                        onProgress(
                            Iperf3TestProgress(
                                isRunning = true,
                                targetHost = serverHost,
                                currentMbps = currentRateMbps,
                                totalBytesTransferred = totalBytesSent,
                                elapsedSeconds = elapsedSec,
                                logOutput = logs.toList()
                            )
                        )
                        lastReportTime = now
                    }
                }
                out.flush()

                val totalElapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.001)
                val avgMbps = (totalBytesSent * 8.0) / (totalElapsedSec * 1_000_000.0)
                addLog("Test Complete! Average Throughput: ${"%.2f".format(avgMbps)} Mbps (${totalBytesSent / (1024 * 1024)} MB transferred)")

                onProgress(
                    Iperf3TestProgress(
                        isRunning = false,
                        targetHost = serverHost,
                        currentMbps = 0.0,
                        totalBytesTransferred = totalBytesSent,
                        elapsedSeconds = totalElapsedSec,
                        finalAverageMbps = avgMbps,
                        logOutput = logs.toList()
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errMsg = "iPerf3 test failed: ${e.message}"
            addLog(errMsg)
            onProgress(
                Iperf3TestProgress(
                    isRunning = false,
                    targetHost = serverHost,
                    error = errMsg,
                    logOutput = logs.toList()
                )
            )
        }
    }

    // =========================================================================
    // 6. Wi-Fi Roaming Monitor (802.11k/v/r BSSID Transitions)
    // =========================================================================

    fun recordWifiTelemetry(wifiInfo: WifiInfo?) {
        if (wifiInfo == null) return
        val currentBssid = wifiInfo.bssid ?: return
        if (currentBssid == "02:00:00:00:00:00") return

        val now = System.currentTimeMillis()
        if (lastObservedBssid != null && lastObservedBssid != currentBssid) {
            val latency = if (lastBssidChangeTimestamp > 0) now - lastBssidChangeTimestamp else 0L
            val event = WifiRoamingEvent(
                timestamp = now,
                oldBssid = lastObservedBssid!!,
                newBssid = currentBssid,
                ssid = wifiInfo.ssid?.replace("\"", "") ?: "Wi-Fi Network",
                rssiDbm = wifiInfo.rssi,
                frequencyMhz = wifiInfo.frequency,
                linkSpeedMbps = wifiInfo.linkSpeed,
                transitionLatencyMs = latency
            )
            val updated = listOf(event) + _roamingEvents.value.take(49)
            _roamingEvents.value = updated
        }
        lastObservedBssid = currentBssid
        lastBssidChangeTimestamp = now
    }

    // =========================================================================
    // 7. SIP OPTIONS VoIP Ping (UDP/TCP 5060)
    // =========================================================================

    private fun resolveLocalIpv4(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress ?: continue
                    }
                }
            }
            "0.0.0.0"
        } catch (_: Exception) {
            "0.0.0.0"
        }
    }

    suspend fun pingSipOptions(targetHost: String, targetPort: Int = 5060, useTcp: Boolean = false): SipOptionsResult = withContext(Dispatchers.IO) {
        if (targetHost.isBlank()) {
            return@withContext SipOptionsResult(
                targetHost = "",
                targetPort = targetPort,
                protocol = if (useTcp) "TCP" else "UDP",
                rttMs = -1,
                statusCode = 0,
                statusText = "Target host is required",
                serverHeader = "",
                allowMethods = "",
                rawResponse = "Enter a valid SIP proxy or registrar host."
            )
        }
        val cleanHost = targetHost.trim()
        val localIp = resolveLocalIpv4()
        val branch = "z9hG4bK" + UUID.randomUUID().toString().take(10)
        val callId = UUID.randomUUID().toString() + "@enterwebslinger.local"
        val tag = UUID.randomUUID().toString().take(8)

        val sipRequest = "OPTIONS sip:$cleanHost:$targetPort SIP/2.0\r\n" +
            "Via: SIP/2.0/${if (useTcp) "TCP" else "UDP"} $localIp:5060;branch=$branch;rport\r\n" +
            "Max-Forwards: 70\r\n" +
            "From: <sip:slinger@enterwebslinger.local>;tag=$tag\r\n" +
            "To: <sip:$cleanHost>\r\n" +
            "Call-ID: $callId\r\n" +
            "CSeq: 1 OPTIONS\r\n" +
            "User-Agent: EnterwebSlinger-VoipPing/1.0\r\n" +
            "Accept: application/sdp\r\n" +
            "Content-Length: 0\r\n\r\n"

        val startTime = System.currentTimeMillis()
        var responseText = ""

        try {
            if (useTcp) {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(cleanHost, targetPort), 4000)
                    socket.soTimeout = 4000
                    val writer = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                    writer.write(sipRequest)
                    writer.flush()

                    val sb = java.lang.StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line).append("\n")
                        if (line.isNullOrEmpty()) break
                    }
                    responseText = sb.toString()
                }
            } else {
                DatagramSocket().use { socket ->
                    socket.soTimeout = 4000
                    val reqBytes = sipRequest.toByteArray(StandardCharsets.UTF_8)
                    val packet = DatagramPacket(reqBytes, reqBytes.size, InetAddress.getByName(cleanHost), targetPort)
                    socket.send(packet)

                    val rxBuf = ByteArray(4096)
                    val rxPacket = DatagramPacket(rxBuf, rxBuf.size)
                    socket.receive(rxPacket)
                    responseText = String(rxPacket.data, 0, rxPacket.length, StandardCharsets.UTF_8)
                }
            }

            val rtt = System.currentTimeMillis() - startTime
            val statusLine = responseText.lines().firstOrNull { it.startsWith("SIP/2.0") } ?: ""
            val statusParts = statusLine.split(" ", limit = 3)
            val statusCode = statusParts.getOrNull(1)?.toIntOrNull() ?: 0
            val statusText = statusParts.getOrNull(2) ?: if (statusCode == 0) "No SIP Status Line" else "OK"

            val serverHeader = Regex("(?i)Server:\\s*(.*)").find(responseText)?.groupValues?.get(1)?.trim() ?: ""
            val allowHeader = Regex("(?i)Allow:\\s*(.*)").find(responseText)?.groupValues?.get(1)?.trim() ?: ""

            SipOptionsResult(
                targetHost = cleanHost,
                targetPort = targetPort,
                protocol = if (useTcp) "TCP" else "UDP",
                rttMs = rtt,
                statusCode = statusCode,
                statusText = statusText,
                serverHeader = serverHeader,
                allowMethods = allowHeader,
                rawResponse = responseText
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SipOptionsResult(
                targetHost = cleanHost,
                targetPort = targetPort,
                protocol = if (useTcp) "TCP" else "UDP",
                rttMs = -1,
                statusCode = 0,
                statusText = "Timeout / Unreachable (${e.message})",
                serverHeader = "",
                allowMethods = "",
                rawResponse = "Error: ${e.message}"
            )
        }
    }
}
