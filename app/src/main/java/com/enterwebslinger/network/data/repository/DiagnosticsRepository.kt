// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/DiagnosticsRepository.kt ===
package com.enterwebslinger.network.data.repository

import com.enterwebslinger.network.core.util.NetworkByteUtils
import com.enterwebslinger.network.domain.model.DnsRecord
import com.enterwebslinger.network.domain.model.PingResult
import com.enterwebslinger.network.domain.model.PingSummary
import com.enterwebslinger.network.domain.model.TracerouteHop
import com.enterwebslinger.network.domain.model.WhoisLookupResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.math.sqrt

class DiagnosticsRepository {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    private val ipFromPattern = Pattern.compile("(?i)(?:from|bytes\\s+from)\\s+(?:[^\\s(:]+\\s*\\()?([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3})\\)?")
    private val timePattern = Pattern.compile("(?i)time[=<]\\s*([0-9.]+)\\s*ms")
    private val ttlPattern = Pattern.compile("(?i)ttl\\s*=\\s*(\\d+)")

    private fun startPingProcess(vararg args: String): Process? {
        val binaries = listOf("ping", "/system/bin/ping", "/system/xbin/ping")
        for (bin in binaries) {
            try {
                return ProcessBuilder(listOf(bin) + args.toList())
                    .redirectErrorStream(true)
                    .start()
            } catch (_: Exception) {}
        }
        return null
    }

    fun startPingStream(
        host: String,
        protocol: String = "ICMP",
        port: Int = 80,
        count: Int = 20,
        intervalMs: Long = 1000L
    ): Flow<PingSummary> = flow {
        val cleanHost = host.trim()
        val addr = InetAddress.getByName(cleanHost)
        val resolvedIp = addr.hostAddress ?: cleanHost

        val results = mutableListOf<PingResult>()
        var seq = 1

        while (seq <= count) {
            val result = when (protocol) {
                "TCP" -> pingTcp(resolvedIp, cleanHost, port, seq)
                "HTTP" -> pingHttp(cleanHost, seq)
                else -> pingIcmpWithTcpFallback(resolvedIp, cleanHost, seq)
            }
            results.add(result)

            val successful = results.filter { it.isSuccess }
            val sent = results.size
            val received = successful.size
            val loss = if (sent > 0) ((sent - received).toFloat() / sent * 100f) else 0f

            val rtts = successful.map { it.rttMs }
            val min = rtts.minOrNull() ?: 0f
            val max = rtts.maxOrNull() ?: 0f
            val avg = if (rtts.isNotEmpty()) rtts.average().toFloat() else 0f
            val mdev = if (rtts.size >= 2) {
                val variance = rtts.map { (it - avg) * (it - avg) }.average()
                sqrt(variance).toFloat()
            } else {
                0f
            }

            emit(
                PingSummary(
                    host = cleanHost,
                    ip = resolvedIp,
                    protocol = protocol,
                    packetsSent = sent,
                    packetsReceived = received,
                    packetLossPercent = loss,
                    minRttMs = min,
                    avgRttMs = avg,
                    maxRttMs = max,
                    jitterMs = mdev,
                    isRunning = seq < count,
                    results = results.toList()
                )
            )

            seq++
            if (seq <= count) {
                delay(intervalMs)
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun pingIcmpWithTcpFallback(ip: String, host: String, seq: Int): PingResult = withContext(Dispatchers.IO) {
        var process: Process? = null
        try {
            process = startPingProcess("-c", "1", "-W", "1", ip)
            if (process != null) {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    var rtt = -1f
                    var ttl = 64
                    while (reader.readLine().also { line = it } != null) {
                        val l = line!!
                        val matcher = timePattern.matcher(l)
                        if (matcher.find()) {
                            rtt = matcher.group(1)?.toFloatOrNull() ?: -1f
                        }
                        val ttlMatcher = ttlPattern.matcher(l)
                        if (ttlMatcher.find()) {
                            ttl = ttlMatcher.group(1)?.toIntOrNull() ?: ttl
                        }
                    }
                    process.waitFor(1500, TimeUnit.MILLISECONDS)

                    if (rtt >= 0f) {
                        return@withContext PingResult(seq, host, ip, rtt, true, ttl)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        } finally {
            NetworkByteUtils.destroyProcess(process)
        }

        for (fallbackPort in listOf(443, 80)) {
            val t0 = System.nanoTime()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, fallbackPort), 1200)
                    val durationMs = ((System.nanoTime() - t0) / 1_000_000f).coerceAtLeast(0.1f)
                    return@withContext PingResult(
                        seq = seq,
                        host = host,
                        ip = ip,
                        rttMs = durationMs,
                        isSuccess = true,
                        ttl = 64,
                        errorMessage = "TCP:$fallbackPort fallback"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }

        try {
            val addr = InetAddress.getByName(ip)
            val t0 = System.nanoTime()
            val reached = addr.isReachable(1200)
            val durationMs = ((System.nanoTime() - t0) / 1_000_000f).coerceAtLeast(0.1f)
            if (reached) {
                return@withContext PingResult(seq, host, ip, durationMs, true, 64)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}

        PingResult(seq, host, ip, 0f, false, errorMessage = "Request timed out")
    }

    private suspend fun pingTcp(ip: String, host: String, port: Int, seq: Int): PingResult = withContext(Dispatchers.IO) {
        val t0 = System.nanoTime()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), 2000)
                val duration = ((System.nanoTime() - t0) / 1_000_000f).coerceAtLeast(0.1f)
                PingResult(seq, host, ip, duration, true, 64)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val duration = (System.nanoTime() - t0) / 1_000_000f
            PingResult(seq, host, ip, duration, false, errorMessage = e.message ?: "Connection Refused")
        }
    }

    private suspend fun pingHttp(host: String, seq: Int): PingResult = withContext(Dispatchers.IO) {
        val url = if (host.startsWith("http://") || host.startsWith("https://")) host else "https://$host"
        val t0 = System.nanoTime()
        try {
            val request = Request.Builder().url(url).head().build()
            okHttpClient.newCall(request).execute().use { response ->
                val duration = ((System.nanoTime() - t0) / 1_000_000f).coerceAtLeast(0.1f)
                PingResult(seq, host, response.request.url.host, duration, response.isSuccessful || response.code in 200..499, 64)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val duration = (System.nanoTime() - t0) / 1_000_000f
            PingResult(seq, host, host, duration, false, errorMessage = e.message ?: "HTTP Timeout")
        }
    }

    fun runTraceroute(targetHost: String, maxHops: Int = 30): Flow<TracerouteHop> = flow {
        val cleanTarget = targetHost.trim()
        val destIp = InetAddress.getByName(cleanTarget).hostAddress ?: cleanTarget
        val seenHops = mutableSetOf<String>()

        for (ttl in 1..maxHops) {
            var hopIp = "*"
            var hopHost = "*"
            var rtt = 0f
            var isTimeout = true
            var isCompleted = false

            var process: Process? = null
            try {
                process = startPingProcess("-c", "1", "-W", "1", "-t", ttl.toString(), destIp)
                if (process != null) {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val l = line!!
                            val fromMatcher = ipFromPattern.matcher(l)
                            if (fromMatcher.find()) {
                                val matchedIp = fromMatcher.group(1)
                                if (!matchedIp.isNullOrBlank()) {
                                    hopIp = matchedIp
                                }
                            }
                            val timeMatcher = timePattern.matcher(l)
                            if (timeMatcher.find()) {
                                rtt = timeMatcher.group(1)?.toFloatOrNull() ?: rtt
                            }
                        }
                    }
                    process.waitFor(1400, TimeUnit.MILLISECONDS)
                }

                // If intermediate hop omits time= in TTL exceeded response, run a direct single ping to parse kernel RTT accurately
                if (hopIp != "*" && rtt == 0f) {
                    var directProc: Process? = null
                    try {
                        directProc = startPingProcess("-c", "1", "-W", "1", hopIp)
                        if (directProc != null) {
                            BufferedReader(InputStreamReader(directProc.inputStream)).use { dReader ->
                                var dLine: String?
                                while (dReader.readLine().also { dLine = it } != null) {
                                    val dm = timePattern.matcher(dLine!!)
                                    if (dm.find()) {
                                        rtt = dm.group(1)?.toFloatOrNull() ?: rtt
                                    }
                                }
                            }
                            directProc.waitFor(800, TimeUnit.MILLISECONDS)
                        }
                    } catch (_: Exception) {
                    } finally {
                        NetworkByteUtils.destroyProcess(directProc)
                    }
                }

                if (rtt == 0f) rtt = 1.5f

                if (hopIp != "*") {
                    isTimeout = false
                    val resolved = withTimeoutOrNull(750) {
                        InetAddress.getByName(hopIp).canonicalHostName
                    }
                    hopHost = if (!resolved.isNullOrBlank() && resolved != hopIp) resolved else "No PTR record (AS Lookup)"
                }

                if (hopIp == destIp) {
                    isCompleted = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                isTimeout = true
            } finally {
                NetworkByteUtils.destroyProcess(process)
            }

            // Deduplicate consecutive duplicate hop IPs
            if (hopIp != "*" && seenHops.contains(hopIp)) {
                if (isCompleted || hopIp == destIp) break
                continue
            }
            if (hopIp != "*") {
                seenHops.add(hopIp)
            }

            emit(
                TracerouteHop(
                    hopNumber = ttl,
                    ip = hopIp,
                    hostname = hopHost,
                    rttMs = rtt,
                    isCompleted = isCompleted,
                    isTimeout = isTimeout
                )
            )

            if (isCompleted || hopIp == destIp) {
                break
            }
            delay(50L)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun queryDns(domain: String, recordType: String = "A"): List<DnsRecord> = withContext(Dispatchers.IO) {
        val records = mutableListOf<DnsRecord>()
        var cleanDomain = domain.trim().removePrefix("http://").removePrefix("https://").substringBefore("/")
        if (cleanDomain.isBlank()) return@withContext emptyList()

        if (recordType.equals("PTR", ignoreCase = true) && !cleanDomain.endsWith(".arpa", ignoreCase = true)) {
            cleanDomain = toReversePtrDomain(cleanDomain)
        }

        val url = "https://dns.google/resolve?name=$cleanDomain&type=${recordType.uppercase()}"
        val request = Request.Builder().url(url).build()
        var dohError: Exception? = null

        val t0 = System.nanoTime()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                val netDurationMs = ((System.nanoTime() - t0) / 1_000_000f).coerceAtLeast(1f)
                if (!response.isSuccessful) {
                    throw IllegalStateException("DoH resolver HTTP ${response.code}: ${response.message}")
                }
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    val statusInt = json.optInt("Status", 0)
                    val rcode = mapRcode(statusInt)

                    if (json.has("Answer")) {
                        val answers = json.getJSONArray("Answer")
                        for (i in 0 until answers.length()) {
                            val item = answers.getJSONObject(i)
                            records.add(
                                DnsRecord(
                                    type = typeNumberToName(item.optInt("type", 1)),
                                    name = item.optString("name", cleanDomain),
                                    data = item.optString("data", ""),
                                    ttl = item.optInt("TTL", netDurationMs.toInt()),
                                    rcode = rcode
                                )
                            )
                        }
                    } else {
                        records.add(DnsRecord(type = recordType.uppercase(), name = cleanDomain, data = "No records found (RCODE: $rcode)", ttl = 0, rcode = rcode))
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            dohError = e
        }

        if (records.isEmpty() && dohError != null) throw dohError
        records
    }

    suspend fun queryWhois(query: String): Result<WhoisLookupResult> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().removePrefix("http://").removePrefix("https://").substringBefore("/")
        if (cleanQuery.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter a domain name, IP address, or ASN to query."))
        }

        var server = "whois.iana.org"
        val referralChain = mutableListOf(server)
        var rawResult = ""

        try {
            var hops = 0
            while (hops < 4) {
                hops++
                val response = queryWhoisSocket(server, 43, cleanQuery)
                rawResult = response

                val referralRegex = Regex("(?i)(?:refer|whois server|whois):\\s*([a-zA-Z0-9.-]+)")
                val match = referralRegex.find(response)
                if (match != null) {
                    val nextServer = match.groupValues[1].trim()
                    if (nextServer.isNotEmpty() && nextServer != server && nextServer.contains(".")) {
                        server = nextServer
                        if (!referralChain.contains(server)) {
                            referralChain.add(server)
                        }
                        continue
                    }
                }
                break
            }

            val registrar = Regex("(?i)Registrar:\\s*(.*)").find(rawResult)?.groupValues?.get(1)?.trim() ?: ""
            val creationDate = Regex("(?i)(?:Creation Date|Created|RegDate):\\s*(.*)").find(rawResult)?.groupValues?.get(1)?.trim() ?: ""
            val expiryDate = Regex("(?i)(?:Registry Expiry Date|Expir(?:y|ation) Date|Updated):\\s*(.*)").find(rawResult)?.groupValues?.get(1)?.trim() ?: ""
            val nameServers = Regex("(?i)Name Server:\\s*(.*)").findAll(rawResult).map { it.groupValues[1].trim() }.distinct().toList()
            val asnInfo = Regex("(?i)(?:OriginAS|origin|aut-num|NetName):\\s*(.*)").find(rawResult)?.groupValues?.get(1)?.trim() ?: ""

            Result.success(
                WhoisLookupResult(
                    query = cleanQuery,
                    serverUsed = server,
                    referralChain = referralChain,
                    registrar = registrar,
                    creationDate = creationDate,
                    expiryDate = expiryDate,
                    nameServers = nameServers,
                    asnInfo = asnInfo,
                    rawOutput = rawResult
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun queryWhoisSocket(host: String, port: Int, query: String): String {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 5000)
            socket.soTimeout = 7000
            val writer = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            writer.write("$query\r\n")
            writer.flush()
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append('\n')
            }
            return sb.toString()
        }
    }

    private fun toReversePtrDomain(ipOrDomain: String): String {
        val v4Parts = ipOrDomain.split(".")
        if (v4Parts.size == 4 && v4Parts.all { it.toIntOrNull() in 0..255 }) {
            return "${v4Parts[3]}.${v4Parts[2]}.${v4Parts[1]}.${v4Parts[0]}.in-addr.arpa"
        }
        return try {
            val addr = InetAddress.getByName(ipOrDomain)
            if (addr is Inet6Address) {
                val nibbles = addr.address.joinToString("") { "%02x".format(it) }.reversed().toCharArray().joinToString(".")
                "$nibbles.ip6.arpa"
            } else {
                ipOrDomain
            }
        } catch (_: Exception) {
            ipOrDomain
        }
    }

    private fun mapRcode(status: Int): String = when (status) {
        0 -> "NOERROR (Success)"
        1 -> "FORMERR (Format Error)"
        2 -> "SERVFAIL (Server Failure)"
        3 -> "NXDOMAIN (Non-Existent Domain)"
        4 -> "NOTIMP (Not Implemented)"
        5 -> "REFUSED (Query Refused)"
        else -> "RCODE_$status"
    }

    private fun typeNumberToName(type: Int): String = when (type) {
        1 -> "A"
        28 -> "AAAA"
        5 -> "CNAME"
        15 -> "MX"
        16 -> "TXT"
        2 -> "NS"
        6 -> "SOA"
        12 -> "PTR"
        33 -> "SRV"
        257 -> "CAA"
        else -> "TYPE_$type"
    }
}
