// === FILE: app/src/main/java/com/enterwebslinger/network/domain/model/SyslogModels.kt ===
package com.enterwebslinger.network.domain.model

enum class SyslogSeverity(val code: Int, val label: String, val hexColor: Long) {
    EMERGENCY(0, "Emergency", 0xFFFF0055),
    ALERT(1, "Alert", 0xFFFF2244),
    CRITICAL(2, "Critical", 0xFFFF4433),
    ERROR(3, "Error", 0xFFEF4444),
    WARNING(4, "Warning", 0xFFF59E0B),
    NOTICE(5, "Notice", 0xFF3B82F6),
    INFORMATIONAL(6, "Info", 0xFF10B981),
    DEBUG(7, "Debug", 0xFF6B7280);

    companion object {
        fun fromCode(code: Int): SyslogSeverity = entries.firstOrNull { it.code == code } ?: DEBUG
    }
}

enum class SyslogFacility(val code: Int, val label: String) {
    KERN(0, "kern"),
    USER(1, "user"),
    MAIL(2, "mail"),
    DAEMON(3, "daemon"),
    AUTH(4, "auth"),
    SYSLOG(5, "syslog"),
    LPR(6, "lpr"),
    NEWS(7, "news"),
    UUCP(8, "uucp"),
    CRON(9, "cron"),
    AUTHPRIV(10, "authpriv"),
    FTP(11, "ftp"),
    NTP(12, "ntp"),
    AUDIT(13, "audit"),
    CONSOLE(14, "console"),
    CRON2(15, "cron2"),
    LOCAL0(16, "local0"),
    LOCAL1(17, "local1"),
    LOCAL2(18, "local2"),
    LOCAL3(19, "local3"),
    LOCAL4(20, "local4"),
    LOCAL5(21, "local5"),
    LOCAL6(22, "local6"),
    LOCAL7(23, "local7");

    companion object {
        fun fromCode(code: Int): SyslogFacility = entries.firstOrNull { it.code == code } ?: USER
    }
}

data class SyslogMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val sourceIp: String,
    val sourcePort: Int,
    val transport: String = "UDP",
    val rfcVersion: String = "RFC 3164",
    val pri: Int = 13,
    val facility: SyslogFacility = SyslogFacility.USER,
    val severity: SyslogSeverity = SyslogSeverity.NOTICE,
    val hostname: String = "unknown",
    val appName: String = "",
    val procId: String = "",
    val msgId: String = "",
    val structuredData: Map<String, String> = emptyMap(),
    val messageContent: String = "",
    val rawString: String = ""
)

data class SyslogReceiverConfig(
    val udpEnabled: Boolean = true,
    val udpPort: Int = 1514,
    val tcpEnabled: Boolean = true,
    val tcpPort: Int = 6514,
    val tlsEnabled: Boolean = false
)
