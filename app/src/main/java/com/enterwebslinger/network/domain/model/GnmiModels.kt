// === FILE: app/src/main/java/com/enterwebslinger/network/domain/model/GnmiModels.kt ===
package com.enterwebslinger.network.domain.model

enum class GrpcServingStatus(val code: Int, val description: String) {
    UNKNOWN(0, "Status Unknown"),
    SERVING(1, "Healthy / Serving"),
    NOT_SERVING(2, "Not Serving / Degraded"),
    SERVICE_UNKNOWN(3, "Service Not Found")
}

data class GrpcHealthCheckResult(
    val target: String,
    val serviceName: String,
    val isReachable: Boolean,
    val status: GrpcServingStatus,
    val responseTimeMs: Long,
    val grpcStatusCode: Int = 0,
    val errorMessage: String? = null
)

data class GnmiTelemetryNode(
    val path: String,
    val value: String,
    val timestampNs: Long = System.currentTimeMillis() * 1_000_000L,
    val dataType: String = "string",
    val deltaRate: Double? = null
)

data class GnmiSessionConfig(
    val host: String = "",
    val port: Int = 50051,
    val useTls: Boolean = false,
    val username: String = "",
    val password: String = "",
    val sampleIntervalMs: Long = 2000L,
    val targetPaths: List<String> = listOf("/interfaces/interface/state/counters", "/system/state")
)
