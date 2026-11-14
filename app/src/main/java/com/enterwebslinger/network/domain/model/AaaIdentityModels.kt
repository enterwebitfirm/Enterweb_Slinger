// === FILE: app/src/main/java/com/enterwebslinger/network/domain/model/AaaIdentityModels.kt ===
package com.enterwebslinger.network.domain.model

enum class RadiusAuthProtocol(val displayName: String) {
    PAP("PAP (MD5 XOR)"),
    CHAP("CHAP (RFC 1994)"),
    MSCHAPV2("MS-CHAPv2 (RFC 2548)")
}

enum class RadiusPacketCode(val code: Int, val label: String) {
    ACCESS_REQUEST(1, "Access-Request (1)"),
    ACCESS_ACCEPT(2, "Access-Accept (2)"),
    ACCESS_REJECT(3, "Access-Reject (3)"),
    ACCOUNTING_REQUEST(4, "Accounting-Request (4)"),
    ACCOUNTING_RESPONSE(5, "Accounting-Response (5)"),
    ACCESS_CHALLENGE(11, "Access-Challenge (11)"),
    DISCONNECT_REQUEST(40, "Disconnect-Request / PoD (40)"),
    DISCONNECT_ACK(41, "Disconnect-ACK (41)"),
    DISCONNECT_NAK(42, "Disconnect-NAK (42)"),
    COA_REQUEST(43, "CoA-Request (43)"),
    COA_ACK(44, "CoA-ACK (44)"),
    COA_NAK(45, "CoA-NAK (45)"),
    UNKNOWN(-1, "Unknown / Timeout")
}

enum class RadiusAcctStatusType(val code: Int, val label: String) {
    START(1, "Start (1)"),
    STOP(2, "Stop (2)"),
    INTERIM_UPDATE(3, "Interim-Update (3)"),
    ACCOUNTING_ON(7, "Accounting-On (7)"),
    ACCOUNTING_OFF(8, "Accounting-Off (8)")
}

enum class RadiusServiceType(val code: Int, val label: String) {
    LOGIN(1, "Login (1)"),
    FRAMED(2, "Framed (2)"),
    ADMINISTRATIVE(6, "Administrative (6)"),
    AUTHENTICATE_ONLY(8, "Authenticate-Only (8)")
}

enum class RadiusVsaVendor(val vendorId: Long, val label: String) {
    MIKROTIK(14988L, "MikroTik (14988)"),
    CISCO(9L, "Cisco (9)"),
    SOPHOS(2604L, "Sophos (2604)"),
    FORTINET(12356L, "Fortinet (12356)"),
    MICROSOFT(311L, "Microsoft (311)")
}

data class RadiusVsaInput(
    val vendor: RadiusVsaVendor,
    val vendorType: Int,
    val stringValue: String = "",
    val intValue: Long? = null,
    val ipv4Value: String = ""
)

data class RadiusAuthRequestConfig(
    val serverIp: String,
    val port: Int = 1812,
    val sharedSecret: String,
    val username: String,
    val password: String,
    val protocol: RadiusAuthProtocol = RadiusAuthProtocol.PAP,
    val nasIpAddress: String = "127.0.0.1",
    val nasPort: Int = 0,
    val serviceType: RadiusServiceType = RadiusServiceType.FRAMED,
    val framedProtocol: Int = 1, // 1 = PPP
    val callingStationId: String = "",
    val calledStationId: String = "",
    val nasIdentifier: String = "EnterwebSlinger",
    val includeMessageAuthenticator: Boolean = true,
    val customVlanId: String = "",
    val extraVsas: List<RadiusVsaInput> = emptyList()
)

data class RadiusAccountingConfig(
    val serverIp: String,
    val port: Int = 1813,
    val sharedSecret: String,
    val username: String,
    val statusType: RadiusAcctStatusType = RadiusAcctStatusType.START,
    val sessionId: String = "sess-0001",
    val sessionTimeSec: Long = 0L,
    val inputOctets: Long = 0L,
    val outputOctets: Long = 0L,
    val inputGigawords: Long = 0L,
    val outputGigawords: Long = 0L,
    val terminateCause: Int? = null,
    val nasIpAddress: String = "127.0.0.1",
    val nasIdentifier: String = "EnterwebSlinger",
    val framedIpAddress: String = ""
)

data class RadiusCoaConfig(
    val targetIp: String,
    val port: Int = 3799,
    val sharedSecret: String,
    val isDisconnect: Boolean = true,
    val username: String = "",
    val sessionId: String = "",
    val callingStationId: String = "",
    val nasIpAddress: String = "",
    val filterIdOrRateLimit: String = "",
    val sessionTimeoutSec: Long? = null,
    val vsaVendor: RadiusVsaVendor = RadiusVsaVendor.MIKROTIK,
    val vsaType: Int = 8,
    val vsaValue: String = ""
)

data class RadiusDecodedVsa(
    val vendorId: Long,
    val vendorName: String,
    val vendorType: Int,
    val attributeName: String,
    val decodedValue: String,
    val rawHex: String
)

data class Radius8021xSummary(
    val vlanId: String = "",
    val tunnelType: String = "",
    val tunnelMediumType: String = "",
    val filterId: String = "",
    val sessionTimeoutSec: Long? = null,
    val framedPool: String = "",
    val mikrotikRateLimit: String = "",
    val mikrotikGroup: String = "",
    val fortinetGroup: String = "",
    val ciscoAvPairs: List<String> = emptyList()
)

data class RadiusWorkbenchResult(
    val serverIp: String,
    val port: Int,
    val requestCode: RadiusPacketCode,
    val responsePacketCode: RadiusPacketCode,
    val responseCode: RadiusResponseCode,
    val protocolUsed: String = "PAP",
    val rttMs: Long = 0L,
    val isSuccess: Boolean = false,
    val responseAuthenticatorValid: Boolean = false,
    val messageAuthenticatorPresent: Boolean = false,
    val messageAuthenticatorValid: Boolean = false,
    val replyMessage: String = "",
    val errorCauseCode: Int? = null,
    val errorCauseText: String = "",
    val dot1xSummary: Radius8021xSummary = Radius8021xSummary(),
    val attributes: List<RadiusAttributeEntry> = emptyList(),
    val decodedVsas: List<RadiusDecodedVsa> = emptyList(),
    val rawRequestHex: String = "",
    val rawResponseHex: String = "",
    val errorMessage: String? = null
)

data class CoaListenerEvent(
    val id: Long = System.currentTimeMillis(),
    val timestampMs: Long = System.currentTimeMillis(),
    val sourceIp: String,
    val sourcePort: Int,
    val requestCode: RadiusPacketCode,
    val replySentCode: RadiusPacketCode,
    val authenticatorValid: Boolean,
    val messageAuthenticatorValid: Boolean?,
    val username: String = "",
    val sessionId: String = "",
    val attributes: List<RadiusAttributeEntry> = emptyList(),
    val decodedVsas: List<RadiusDecodedVsa> = emptyList(),
    val rawPacketHex: String = ""
)

data class CoaListenerStatus(
    val isListening: Boolean = false,
    val bindPort: Int = 3799,
    val packetsReceived: Int = 0,
    val errorMessage: String? = null
)

enum class AaaProfileKind(val label: String, val defaultPort: Int) {
    RADIUS_AUTH("RADIUS Auth", 1812),
    RADIUS_ACCT("RADIUS Acct", 1813),
    RADIUS_COA("RADIUS CoA", 3799),
    TACACS_PLUS("TACACS+", 49),
    LDAP_AD("LDAP / AD", 389)
}

data class SavedAaaServerProfile(
    val id: Long = 0L,
    val name: String,
    val kind: AaaProfileKind,
    val host: String,
    val port: Int,
    val username: String = "",
    val nasIp: String = "127.0.0.1",
    val nasIdentifier: String = "EnterwebSlinger",
    val baseDn: String = "",
    val useTls: Boolean = false,
    val allowSelfSignedTls: Boolean = true,
    val lastUsedTimestamp: Long = System.currentTimeMillis()
)

// --- TACACS+ (RFC 8907) ---

enum class TacacsOperationMode(val label: String) {
    AUTHENTICATION("Authentication (0x01)"),
    COMMAND_AUTHORIZATION("Cmd Authorization (0x02)")
}

enum class TacacsAuthenMethod(val byteCode: Byte, val label: String) {
    ASCII(0x01, "ASCII"),
    PAP(0x02, "PAP")
}

data class TacacsAuthConfig(
    val serverIp: String,
    val port: Int = 49,
    val sharedSecret: String = "",
    val mode: TacacsOperationMode = TacacsOperationMode.AUTHENTICATION,
    val authenMethod: TacacsAuthenMethod = TacacsAuthenMethod.PAP,
    val username: String,
    val password: String = "",
    val privilegeLevel: Int = 15,
    val commandToAuthorize: String = "show",
    val commandArgs: String = "running-config",
    val remoteAddress: String = "127.0.0.1"
)

data class TacacsFullResult(
    val serverIp: String,
    val port: Int,
    val mode: TacacsOperationMode,
    val isReachable: Boolean,
    val isAuthorizedOrPassed: Boolean,
    val versionByte: Int? = null,
    val flagsByte: Int? = null,
    val sessionIdHex: String = "",
    val obfuscatedBody: Boolean = false,
    val statusCode: Int = -1,
    val statusLabel: String = "",
    val serverMessage: String = "",
    val returnedAvPairs: List<String> = emptyList(),
    val rttMs: Long = 0L,
    val rawResponseHex: String = "",
    val errorMessage: String? = null
)

// --- LDAP / Active Directory (RFC 4511) ---

data class LdapTestConfig(
    val host: String,
    val port: Int = 389,
    val useLdaps: Boolean = false,
    val allowSelfSignedTls: Boolean = true,
    val bindDnOrUpn: String,
    val bindPassword: String,
    val baseDn: String = "",
    val searchFilter: String = "(objectClass=*)",
    val requestedAttributes: List<String> = listOf("cn", "sAMAccountName", "distinguishedName", "memberOf", "mail")
)

data class LdapEntryRecord(
    val dn: String,
    val attributes: Map<String, List<String>> = emptyMap()
)

data class LdapTestResult(
    val host: String,
    val port: Int,
    val useLdaps: Boolean,
    val isBindSuccess: Boolean,
    val bindResultCode: Int = -1,
    val bindResultLabel: String = "",
    val bindDiagnosticMessage: String = "",
    val searchResultCode: Int? = null,
    val searchDiagnosticMessage: String = "",
    val tlsProtocol: String = "",
    val entries: List<LdapEntryRecord> = emptyList(),
    val rttMs: Long = 0L,
    val errorMessage: String? = null
)
