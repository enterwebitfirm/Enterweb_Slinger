// === FILE: app/src/main/java/com/enterwebslinger/network/domain/model/WebServicesModels.kt ===
package com.enterwebslinger.network.domain.model

enum class HttpMethod {
    GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS
}

enum class AuthType {
    NONE, BEARER_TOKEN, BASIC_AUTH
}

data class HttpHeader(val name: String, val value: String)

data class TimingWaterfall(
    val dnsLookupMs: Long = 0L,
    val tcpHandshakeMs: Long = 0L,
    val tlsHandshakeMs: Long = 0L,
    val ttfbMs: Long = 0L,
    val downloadDurationMs: Long = 0L,
    val totalDurationMs: Long = 0L
)

data class RestResponse(
    val statusCode: Int,
    val statusMessage: String,
    val headers: List<HttpHeader>,
    val body: String,
    val contentLengthBytes: Long,
    val timing: TimingWaterfall,
    val isSuccess: Boolean
)

data class SoapOperation(
    val name: String,
    val soapAction: String,
    val targetNamespace: String,
    val inputMessage: String,
    val outputMessage: String,
    val envelopeTemplateXml: String
)

data class WsdlService(
    val serviceName: String,
    val portName: String,
    val endpointAddress: String,
    val operations: List<SoapOperation>
)

data class SoapExecutionResult(
    val statusCode: Int,
    val responseXml: String,
    val executionTimeMs: Long,
    val isSuccess: Boolean,
    val xpathMatches: List<String> = emptyList(),
    val errorMessage: String? = null
)
