// === FILE: app/src/main/java/com/enterwebslinger/network/domain/model/CiscoUcModels.kt ===
package com.enterwebslinger.network.domain.model

enum class EndpointRegistrationStatus {
    REGISTERED,
    UNREGISTERED,
    REJECTED,
    PARTIALLY_REGISTERED,
    UNKNOWN
}

data class PhoneEndpoint(
    val name: String,
    val model: String,
    val directoryNumber: String,
    val status: EndpointRegistrationStatus,
    val ipAddress: String,
    val activeLoadId: String,
    val primaryUser: String,
    val protocol: String = "SIP"
)

data class SipTrunk(
    val name: String,
    val destinationAddress: String,
    val port: Int = 5060,
    val status: String = "Unknown",
    val serviceState: String = "Unknown",
    val activeCallCount: Int = 0
)

data class CallQualityMetric(
    val callId: String,
    val caller: String,
    val callee: String,
    val mosScore: Double,
    val jitterMs: Double,
    val latencyMs: Double,
    val packetLossPercent: Double,
    val codec: String = "G.711u"
) {
    val isGoodQuality: Boolean
        get() = mosScore >= 4.0 && jitterMs <= 30.0 && packetLossPercent <= 1.0
}

data class VoiceGatewayDsp(
    val gatewayName: String,
    val activeChannels: Int,
    val totalChannels: Int,
    val activeCodec: String,
    val utilizationPercent: Float
)
