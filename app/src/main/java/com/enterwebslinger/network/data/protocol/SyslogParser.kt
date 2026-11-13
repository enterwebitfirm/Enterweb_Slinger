// === FILE: app/src/main/java/com/enterwebslinger/network/data/protocol/SyslogParser.kt ===
package com.enterwebslinger.network.data.protocol

import com.enterwebslinger.network.domain.model.SyslogFacility
import com.enterwebslinger.network.domain.model.SyslogMessage
import com.enterwebslinger.network.domain.model.SyslogSeverity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

object SyslogParser {

    private val RFC5424_PATTERN = Pattern.compile(
        "^<(\\d{1,3})>(\\d)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(?:\\[(.*?)\\])?\\s*(.*)$"
    )

    private val RFC3164_PATTERN = Pattern.compile(
        "^<(\\d{1,3})>([A-Za-z]{3}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2})\\s+(\\S+)\\s+([^:\\[\\s]+)(?:\\[(\\d+)\\])?:?\\s*(.*)$"
    )

    private val GENERIC_PRI_PATTERN = Pattern.compile("^<(\\d{1,3})>(.*)$")

    fun parse(rawText: String, sourceIp: String, sourcePort: Int, transport: String = "UDP"): SyslogMessage {
        val trimmed = rawText.trim()

        val rfc5424Matcher = RFC5424_PATTERN.matcher(trimmed)
        if (rfc5424Matcher.matches()) {
            val pri = rfc5424Matcher.group(1)?.toIntOrNull() ?: 13
            val version = rfc5424Matcher.group(2) ?: "1"
            val hostname = rfc5424Matcher.group(4) ?: sourceIp
            val appName = rfc5424Matcher.group(5)?.takeIf { it != "-" } ?: ""
            val procId = rfc5424Matcher.group(6)?.takeIf { it != "-" } ?: ""
            val msgId = rfc5424Matcher.group(7)?.takeIf { it != "-" } ?: ""
            val structuredDataStr = rfc5424Matcher.group(8) ?: ""
            val messageContent = rfc5424Matcher.group(9) ?: ""

            val facility = SyslogFacility.fromCode(pri / 8)
            val severity = SyslogSeverity.fromCode(pri % 8)
            val structuredMap = parseStructuredData(structuredDataStr)

            return SyslogMessage(
                sourceIp = sourceIp,
                sourcePort = sourcePort,
                transport = transport,
                rfcVersion = "RFC 5424 (v$version)",
                pri = pri,
                facility = facility,
                severity = severity,
                hostname = hostname,
                appName = appName,
                procId = procId,
                msgId = msgId,
                structuredData = structuredMap,
                messageContent = messageContent,
                rawString = trimmed
            )
        }

        val rfc3164Matcher = RFC3164_PATTERN.matcher(trimmed)
        if (rfc3164Matcher.matches()) {
            val pri = rfc3164Matcher.group(1)?.toIntOrNull() ?: 13
            val hostname = rfc3164Matcher.group(3) ?: sourceIp
            val appName = rfc3164Matcher.group(4) ?: ""
            val procId = rfc3164Matcher.group(5) ?: ""
            val messageContent = rfc3164Matcher.group(6) ?: ""

            val facility = SyslogFacility.fromCode(pri / 8)
            val severity = SyslogSeverity.fromCode(pri % 8)

            return SyslogMessage(
                sourceIp = sourceIp,
                sourcePort = sourcePort,
                transport = transport,
                rfcVersion = "RFC 3164",
                pri = pri,
                facility = facility,
                severity = severity,
                hostname = hostname,
                appName = appName,
                procId = procId,
                messageContent = messageContent,
                rawString = trimmed
            )
        }

        val genericMatcher = GENERIC_PRI_PATTERN.matcher(trimmed)
        if (genericMatcher.matches()) {
            val pri = genericMatcher.group(1)?.toIntOrNull() ?: 13
            val body = genericMatcher.group(2) ?: ""
            val facility = SyslogFacility.fromCode(pri / 8)
            val severity = SyslogSeverity.fromCode(pri % 8)

            return SyslogMessage(
                sourceIp = sourceIp,
                sourcePort = sourcePort,
                transport = transport,
                rfcVersion = "Syslog",
                pri = pri,
                facility = facility,
                severity = severity,
                hostname = sourceIp,
                messageContent = body,
                rawString = trimmed
            )
        }

        return SyslogMessage(
            sourceIp = sourceIp,
            sourcePort = sourcePort,
            transport = transport,
            rfcVersion = "Raw",
            pri = 13,
            facility = SyslogFacility.USER,
            severity = SyslogSeverity.NOTICE,
            hostname = sourceIp,
            messageContent = trimmed,
            rawString = trimmed
        )
    }

    private fun parseStructuredData(sd: String): Map<String, String> {
        if (sd.isBlank()) return emptyMap()
        val map = mutableMapOf<String, String>()
        val paramPattern = Pattern.compile("(\\w+)=\"([^\"]*)\"")
        val matcher = paramPattern.matcher(sd)
        while (matcher.find()) {
            val key = matcher.group(1) ?: continue
            val value = matcher.group(2) ?: ""
            map[key] = value
        }
        return map
    }

    fun generateRfc5424(
        facility: SyslogFacility,
        severity: SyslogSeverity,
        hostname: String,
        appName: String,
        procId: String,
        msgId: String,
        structuredDataId: String,
        sdParams: Map<String, String>,
        message: String
    ): String {
        val pri = (facility.code * 8) + severity.code
        val isoTimestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val sdString = if (sdParams.isNotEmpty()) {
            val params = sdParams.entries.joinToString(" ") { "${it.key}=\"${it.value}\"" }
            "[$structuredDataId $params]"
        } else "-"

        return "<$pri>1 $isoTimestamp $hostname ${appName.ifBlank { "-" }} ${procId.ifBlank { "-" }} ${msgId.ifBlank { "-" }} $sdString $message"
    }

    fun generateRfc3164(
        facility: SyslogFacility,
        severity: SyslogSeverity,
        hostname: String,
        tag: String,
        pid: String,
        message: String
    ): String {
        val pri = (facility.code * 8) + severity.code
        val bsdTimestamp = SimpleDateFormat("MMM d HH:mm:ss", Locale.US).format(Date())
        val tagWithPid = if (pid.isNotBlank()) "$tag[$pid]" else tag
        return "<$pri>$bsdTimestamp $hostname $tagWithPid: $message"
    }

    suspend fun sendUdpSyslog(host: String, port: Int, message: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                DatagramSocket().use { socket ->
                    val bytes = message.toByteArray(Charsets.UTF_8)
                    val address = InetAddress.getByName(host)
                    val packet = DatagramPacket(bytes, bytes.size, address, port)
                    socket.send(packet)
                    true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        }
    }
}
