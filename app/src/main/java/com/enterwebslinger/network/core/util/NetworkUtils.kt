// === FILE: app/src/main/java/com/enterwebslinger/network/core/util/NetworkUtils.kt ===
package com.enterwebslinger.network.core.util

import java.nio.ByteBuffer

object NetworkUtils {

    val WELL_KNOWN_PORTS = mapOf(
        20 to "FTP Data",
        21 to "FTP Control",
        22 to "SSH",
        23 to "Telnet",
        25 to "SMTP",
        53 to "DNS",
        67 to "DHCP Server",
        68 to "DHCP Client",
        80 to "HTTP",
        110 to "POP3",
        123 to "NTP",
        135 to "MS RPC",
        137 to "NetBIOS-NS",
        138 to "NetBIOS-DGM",
        139 to "NetBIOS-SSN",
        143 to "IMAP",
        161 to "SNMP",
        443 to "HTTPS",
        445 to "SMB / Samba",
        465 to "SMTPS",
        548 to "AFP (Apple)",
        554 to "RTSP Streaming",
        587 to "SMTP Submission",
        993 to "IMAPS",
        995 to "POP3S",
        1433 to "MS SQL",
        1521 to "Oracle DB",
        1883 to "MQTT Broker",
        1900 to "SSDP / UPnP",
        2049 to "NFS",
        3306 to "MySQL DB",
        3389 to "RDP Remote Desktop",
        5000 to "UPnP / Synology",
        5353 to "mDNS",
        5432 to "PostgreSQL",
        5900 to "VNC Server",
        6379 to "Redis Cache",
        8000 to "HTTP Alt / Dev",
        8080 to "HTTP Proxy / Web",
        8443 to "HTTPS Alt",
        8888 to "HTTP Alt",
        9000 to "SonarQube / Portainer",
        9090 to "Prometheus / Cockpit",
        9100 to "Raw JetDirect Printer"
    )

    val COMMON_TOP_100_PORTS = listOf(
        21, 22, 23, 25, 53, 80, 110, 111, 135, 139, 143, 443, 445, 993, 995,
        1433, 1521, 1723, 1883, 2049, 2082, 2083, 2181, 2222, 3000, 3128, 3306,
        3389, 4200, 4443, 5000, 5060, 51413, 5432, 5900, 5985, 6379, 6667, 7000,
        7070, 7443, 8000, 8008, 8080, 8081, 8086, 8088, 8090, 8443, 8500, 8888,
        9000, 9090, 9092, 9100, 9200, 9300, 9443, 9999, 27017
    )

    val WEB_ADMIN_PORTS = listOf(
        80, 443, 8080, 8443, 22, 21, 3389, 5900, 8000, 8888
    )

    private val OUI_MAP = mapOf(
        "00:1A:11" to "Google",
        "3C:5A:B4" to "Google",
        "F4:F5:DB" to "Google",
        "00:03:93" to "Apple",
        "00:17:F2" to "Apple",
        "F0:18:98" to "Apple",
        "BC:92:6B" to "Apple",
        "AC:DE:48" to "Apple",
        "70:3E:AC" to "Apple",
        "B8:27:EB" to "Raspberry Pi",
        "DC:A6:32" to "Raspberry Pi",
        "E4:5F:01" to "Raspberry Pi",
        "24:0A:C4" to "Espressif",
        "30:AE:A4" to "Espressif",
        "84:CC:A8" to "Espressif",
        "00:0C:29" to "VMware",
        "00:50:56" to "VMware",
        "08:00:27" to "VirtualBox",
        "52:54:00" to "QEMU/KVM",
        "00:1E:06" to "Wistron (Android/PC)",
        "00:E0:4C" to "Realtek",
        "00:1B:21" to "Intel",
        "00:15:00" to "Intel",
        "60:A4:4C" to "TP-Link",
        "50:C7:BF" to "TP-Link",
        "98:DA:C4" to "TP-Link",
        "00:14:BF" to "Cisco-Linksys",
        "00:27:22" to "Ubiquiti",
        "78:8A:20" to "Ubiquiti",
        "24:5A:4C" to "Ubiquiti",
        "00:11:32" to "Synology",
        "00:08:9B" to "QNAP",
        "FC:65:DE" to "Amazon Tech",
        "74:C2:46" to "Amazon Echo",
        "44:65:0D" to "Amazon FireTV",
        "00:1D:BA" to "Sony Interactive",
        "70:9E:29" to "Sony PlayStation",
        "28:11:A5" to "Microsoft Xbox",
        "00:15:5D" to "Microsoft Hyper-V",
        "F0:8A:76" to "Samsung",
        "50:01:D9" to "Samsung",
        "88:36:5F" to "Xiaomi"
    )

    fun resolveVendor(mac: String): String {
        if (mac.isBlank() || mac.length < 8) return "Unknown Vendor"
        val clean = mac.uppercase().replace("-", ":")
        val prefix = clean.take(8)
        return OUI_MAP[prefix] ?: "Network Host"
    }

    fun isHttpPort(port: Int): Boolean {
        return port in listOf(80, 443, 8000, 8080, 8443, 8888, 5000, 9000, 9090)
    }

    /**
     * Build standard NetBIOS Name Query packet for UDP port 137
     */
    fun buildNbnsQueryPacket(): ByteArray {
        val buffer = ByteBuffer.allocate(50)
        buffer.putShort(0x1337.toShort())
        buffer.putShort(0x0000.toShort())
        buffer.putShort(0x0001.toShort())
        buffer.putShort(0x0000.toShort())
        buffer.putShort(0x0000.toShort())
        buffer.putShort(0x0000.toShort())

        buffer.put(0x20.toByte())
        val encodedWildcard = "CKAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".toByteArray(Charsets.US_ASCII)
        buffer.put(encodedWildcard)
        buffer.put(0x00.toByte())

        buffer.putShort(0x0021.toShort())
        buffer.putShort(0x0001.toShort())

        return buffer.array()
    }

    /**
     * Parse NetBIOS name query response
     */
    fun parseNbnsResponse(data: ByteArray, length: Int): String? {
        if (length < 56) return null
        try {
            val numNames = data[56].toInt() and 0xFF
            if (numNames == 0) return null
            var offset = 57
            for (i in 0 until numNames) {
                if (offset + 18 > length) break
                val nameBytes = data.copyOfRange(offset, offset + 15)
                val type = data[offset + 15].toInt() and 0xFF
                val name = String(nameBytes, Charsets.US_ASCII).trim()
                if (name.isNotBlank() && type == 0x00) {
                    return name
                }
                offset += 18
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Build Wake-on-LAN Magic Packet: 6 bytes of 0xFF followed by 16 repetitions of 48-bit MAC
     */
    fun buildMagicPacket(macStr: String): ByteArray {
        val cleanMac = macStr.replace(":", "").replace("-", "")
        require(cleanMac.length == 12) { "Invalid MAC address format: $macStr" }
        val macBytes = ByteArray(6) { i ->
            cleanMac.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        val packet = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) {
            packet[i] = 0xFF.toByte()
        }
        var index = 6
        for (i in 0 until 16) {
            System.arraycopy(macBytes, 0, packet, index, 6)
            index += 6
        }
        return packet
    }

    /**
     * SSDP Discovery packet
     */
    fun buildSsdpDiscoveryPacket(): ByteArray {
        val message = "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1900\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 2\r\n" +
            "ST: ssdp:all\r\n" +
            "\r\n"
        return message.toByteArray(Charsets.UTF_8)
    }

    fun frequencyToChannel(freqMhz: Int): Int {
        return when {
            freqMhz in 2412..2484 -> (freqMhz - 2407) / 5
            freqMhz in 5170..5825 -> (freqMhz - 5000) / 5
            freqMhz in 5925..7125 -> (freqMhz - 5950) / 5
            else -> 0
        }
    }

    fun frequencyToBand(freqMhz: Int): String {
        return when {
            freqMhz in 2400..2500 -> "2.4 GHz"
            freqMhz in 4900..5900 -> "5 GHz"
            freqMhz in 5925..7125 -> "6 GHz"
            else -> "Unknown"
        }
    }
}
