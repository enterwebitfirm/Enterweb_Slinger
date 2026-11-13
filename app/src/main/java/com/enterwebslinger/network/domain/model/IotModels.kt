// === FILE: app/src/main/java/com/enterwebslinger/network/domain/model/IotModels.kt ===
package com.enterwebslinger.network.domain.model

import java.util.UUID

enum class MqttVersion(val protocolName: String, val protocolLevel: Byte) {
    MQTT_3_1_1("MQTT", 4),
    MQTT_5_0("MQTT", 5)
}

enum class MqttQos(val value: Int) {
    AT_MOST_ONCE(0),
    AT_LEAST_ONCE(1),
    EXACTLY_ONCE(2)
}

data class MqttUserProperty(val key: String, val value: String)

data class MqttMessageItem(
    val id: String,
    val topic: String,
    val payloadString: String,
    val qos: MqttQos,
    val isRetained: Boolean,
    val contentType: String? = null,
    val responseTopic: String? = null,
    val userProperties: List<MqttUserProperty> = emptyList(),
    val timestampMillis: Long = System.currentTimeMillis()
)

data class MqttSessionConfig(
    val brokerHost: String = "",
    val port: Int = 1883,
    val clientId: String = "EnterwebSlinger-${UUID.randomUUID().toString().take(6)}",
    val username: String = "",
    val password: String = "",
    val useTls: Boolean = false,
    val version: MqttVersion = MqttVersion.MQTT_5_0,
    val cleanStart: Boolean = true,
    val keepAliveSeconds: Int = 60
)

enum class CoapType(val code: Int) {
    CON(0),
    NON(1),
    ACK(2),
    RST(3)
}

enum class CoapMethod(val code: Int) {
    GET(1),
    POST(2),
    PUT(3),
    DELETE(4)
}

data class CoapResource(
    val path: String,
    val resourceType: String,
    val interfaceDescription: String,
    val title: String,
    val isObservable: Boolean = false
)

data class CoapResponse(
    val type: CoapType,
    val responseCode: String,
    val messageId: Int,
    val tokenHex: String,
    val contentType: String,
    val observeSequence: Int? = null,
    val payload: String,
    val roundTripTimeMs: Long,
    val isSuccess: Boolean
)
