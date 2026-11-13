// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/GnmiRepository.kt ===
package com.enterwebslinger.network.data.repository

import com.enterwebslinger.network.domain.model.GnmiSessionConfig
import com.enterwebslinger.network.domain.model.GnmiTelemetryNode
import com.enterwebslinger.network.domain.model.GrpcHealthCheckResult
import com.enterwebslinger.network.domain.model.GrpcServingStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class GnmiRepository {

    private fun buildGrpcHttpClient(useTls: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)

        if (useTls) {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())
            builder.protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
        }
        return builder.build()
    }

    suspend fun checkGrpcHealth(host: String, port: Int, serviceName: String, useTls: Boolean = false): GrpcHealthCheckResult {
        if (host.isBlank()) {
            return GrpcHealthCheckResult(
                target = "",
                serviceName = serviceName.ifBlank { "overall" },
                isReachable = false,
                status = GrpcServingStatus.UNKNOWN,
                responseTimeMs = 0L,
                errorMessage = "Target host is required"
            )
        }
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val scheme = if (useTls) "https" else "http"
            val url = "$scheme://$host:$port/grpc.health.v1.Health/Check"

            try {
                val client = buildGrpcHttpClient(useTls)

                val protoStream = ByteArrayOutputStream()
                if (serviceName.isNotEmpty()) {
                    val serviceBytes = serviceName.toByteArray(Charsets.UTF_8)
                    protoStream.write(0x0A)
                    encodeVarint(protoStream, serviceBytes.size.toLong())
                    protoStream.write(serviceBytes)
                }
                val frame = wrapGrpcFrame(protoStream.toByteArray())

                val body = frame.toRequestBody("application/grpc".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .header("te", "trailers")
                    .header("content-type", "application/grpc")
                    .header("grpc-encoding", "identity")
                    .header("user-agent", "Enterweb-Slinger-gRPC/1.0")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    val duration = System.currentTimeMillis() - start
                    val grpcStatusHeader = response.header("grpc-status")
                        ?: runCatching { response.trailers().get("grpc-status") }.getOrNull()
                        ?: if (response.isSuccessful) "0" else "2"
                    val grpcStatusCode = grpcStatusHeader.toIntOrNull() ?: if (response.isSuccessful) 0 else 2

                    if (grpcStatusCode == 0 && response.isSuccessful) {
                        val respBytes = response.body?.bytes()
                        var servingStatus = GrpcServingStatus.SERVING
                        if (respBytes != null && respBytes.size >= 5) {
                            val payload = respBytes.copyOfRange(5, respBytes.size)
                            if (payload.isNotEmpty() && payload[0].toInt() == 0x08) {
                                val statusInt = if (payload.size > 1) payload[1].toInt() else 1
                                servingStatus = GrpcServingStatus.entries.firstOrNull { it.code == statusInt }
                                    ?: GrpcServingStatus.SERVING
                            }
                        }
                        GrpcHealthCheckResult(
                            target = "$host:$port",
                            serviceName = serviceName.ifBlank { "overall" },
                            isReachable = true,
                            status = servingStatus,
                            responseTimeMs = duration,
                            grpcStatusCode = grpcStatusCode
                        )
                    } else {
                        val grpcMessage = response.header("grpc-message")
                            ?: runCatching { response.trailers().get("grpc-message") }.getOrNull()
                        GrpcHealthCheckResult(
                            target = "$host:$port",
                            serviceName = serviceName.ifBlank { "overall" },
                            isReachable = response.isSuccessful,
                            status = if (grpcStatusCode == 5) GrpcServingStatus.SERVICE_UNKNOWN else GrpcServingStatus.NOT_SERVING,
                            responseTimeMs = duration,
                            grpcStatusCode = grpcStatusCode,
                            errorMessage = grpcMessage ?: "HTTP ${response.code} / gRPC status $grpcStatusCode"
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                GrpcHealthCheckResult(
                    target = "$host:$port",
                    serviceName = serviceName.ifBlank { "overall" },
                    isReachable = false,
                    status = GrpcServingStatus.UNKNOWN,
                    responseTimeMs = System.currentTimeMillis() - start,
                    errorMessage = e.message ?: "Connection failed"
                )
            }
        }
    }

    suspend fun fetchGnmiTelemetry(config: GnmiSessionConfig): Result<List<GnmiTelemetryNode>> {
        if (config.host.isBlank()) return Result.failure(IllegalArgumentException("gNMI target host is required"))
        return withContext(Dispatchers.IO) {
            try {
                val health = checkGrpcHealth(config.host, config.port, "gnmi.gNMI", config.useTls)
                val nowNs = System.currentTimeMillis() * 1_000_000L
                val scheme = if (config.useTls) "https" else "http"
                val client = buildGrpcHttpClient(config.useTls)
                val results = mutableListOf<GnmiTelemetryNode>()
                var anyGrpcSuccess = false
                var lastHttpOrGrpcError: String? = null

                results.add(
                    GnmiTelemetryNode(
                        path = "/grpc/health.v1.Health/status",
                        value = "${health.status.name} (code=${health.grpcStatusCode}${health.errorMessage?.let { ": $it" } ?: ""})",
                        timestampNs = nowNs,
                        dataType = "grpc-health"
                    )
                )

                // 1. Capabilities RPC
                runCatching {
                    val capUrl = "$scheme://${config.host}:${config.port}/gnmi.gNMI/Capabilities"
                    val capReq = buildGnmiRequest(capUrl, wrapGrpcFrame(ByteArray(0)), config)
                    client.newCall(capReq).execute().use { response ->
                        val grpcStatus = response.header("grpc-status")
                            ?: runCatching { response.trailers().get("grpc-status") }.getOrNull()
                            ?: if (response.isSuccessful) "0" else "2"
                        val grpcMsg = response.header("grpc-message")
                            ?: runCatching { response.trailers().get("grpc-message") }.getOrNull()
                        val bytes = response.body?.bytes()
                        val printableStrings = bytes?.let { extractPrintableUtf8Strings(it) } ?: emptyList()

                        results.add(
                            GnmiTelemetryNode(
                                path = "/gnmi/capabilities/status",
                                value = "HTTP ${response.code} / grpc-status=$grpcStatus${grpcMsg?.let { " ($it)" } ?: ""}",
                                timestampNs = nowNs,
                                dataType = "grpc-meta",
                                deltaRate = health.responseTimeMs.toDouble()
                            )
                        )
                        if (response.isSuccessful && grpcStatus == "0") {
                            anyGrpcSuccess = true
                            printableStrings.forEachIndexed { idx, token ->
                                results.add(
                                    GnmiTelemetryNode(
                                        path = "/gnmi/capabilities/supported-model[$idx]",
                                        value = token,
                                        timestampNs = nowNs,
                                        dataType = "protobuf-utf8"
                                    )
                                )
                            }
                        } else {
                            lastHttpOrGrpcError = "Capabilities HTTP ${response.code}, gRPC $grpcStatus ${grpcMsg ?: ""}".trim()
                        }
                    }
                }.onFailure { e ->
                    if (e is CancellationException) throw e
                    lastHttpOrGrpcError = e.message
                }

                // 2. gNMI Get RPC for requested YANG paths
                val queryPaths = config.targetPaths.filter { it.isNotBlank() }.ifEmpty {
                    listOf("/interfaces/interface/state/counters", "/system/state")
                }
                runCatching {
                    val getUrl = "$scheme://${config.host}:${config.port}/gnmi.gNMI/Get"
                    val getProto = buildGnmiGetRequestProto(queryPaths)
                    val getReq = buildGnmiRequest(getUrl, wrapGrpcFrame(getProto), config)
                    client.newCall(getReq).execute().use { response ->
                        val grpcStatus = response.header("grpc-status")
                            ?: runCatching { response.trailers().get("grpc-status") }.getOrNull()
                            ?: if (response.isSuccessful) "0" else "2"
                        val grpcMsg = response.header("grpc-message")
                            ?: runCatching { response.trailers().get("grpc-message") }.getOrNull()
                        val bytes = response.body?.bytes()
                        val tokens = bytes?.let { extractPrintableUtf8Strings(it) } ?: emptyList()

                        results.add(
                            GnmiTelemetryNode(
                                path = "/gnmi/get/status",
                                value = "HTTP ${response.code} / grpc-status=$grpcStatus (${queryPaths.joinToString(", ")})",
                                timestampNs = nowNs,
                                dataType = "grpc-meta"
                            )
                        )
                        if (response.isSuccessful && grpcStatus == "0") {
                            anyGrpcSuccess = true
                            tokens.forEachIndexed { idx, token ->
                                results.add(
                                    GnmiTelemetryNode(
                                        path = "/gnmi/get/update[$idx]",
                                        value = token,
                                        timestampNs = nowNs,
                                        dataType = "yang-json-ietf"
                                    )
                                )
                            }
                        } else {
                            lastHttpOrGrpcError = "Get HTTP ${response.code}, gRPC $grpcStatus ${grpcMsg ?: ""}".trim()
                        }
                    }
                }.onFailure { e ->
                    if (e is CancellationException) throw e
                    lastHttpOrGrpcError = lastHttpOrGrpcError ?: e.message
                }

                // 3. gNMI Subscribe (ONCE) fallback/probe if Get did not yield leaf data
                if (!anyGrpcSuccess) {
                    runCatching {
                        val subUrl = "$scheme://${config.host}:${config.port}/gnmi.gNMI/Subscribe"
                        val subProto = buildGnmiSubscribeOnceProto(queryPaths)
                        val subReq = buildGnmiRequest(subUrl, wrapGrpcFrame(subProto), config)
                        client.newCall(subReq).execute().use { response ->
                            val grpcStatus = response.header("grpc-status")
                                ?: runCatching { response.trailers().get("grpc-status") }.getOrNull()
                                ?: if (response.isSuccessful) "0" else "2"
                            val bytes = response.body?.bytes()
                            val tokens = bytes?.let { extractPrintableUtf8Strings(it) } ?: emptyList()
                            if (response.isSuccessful && grpcStatus == "0") {
                                anyGrpcSuccess = true
                                tokens.forEachIndexed { idx, token ->
                                    results.add(
                                        GnmiTelemetryNode(
                                            path = "/gnmi/subscribe/once[$idx]",
                                            value = token,
                                            timestampNs = nowNs,
                                            dataType = "yang-subscribe"
                                        )
                                    )
                                }
                            } else {
                                lastHttpOrGrpcError = "Subscribe HTTP ${response.code}, gRPC $grpcStatus"
                            }
                        }
                    }.onFailure { e ->
                        if (e is CancellationException) throw e
                    }
                }

                if (!anyGrpcSuccess && !health.isReachable) {
                    // Verify raw TCP reachability and return failure if completely unreachable
                    val t0 = System.currentTimeMillis()
                    Socket().use { s ->
                        s.connect(InetSocketAddress(config.host, config.port), 2000)
                    }
                    val tcpMs = System.currentTimeMillis() - t0
                    results.add(
                        GnmiTelemetryNode(
                            path = "/transport/tcp/${config.host}:${config.port}",
                            value = "TCP Connected (${tcpMs}ms), gRPC error: ${lastHttpOrGrpcError ?: health.errorMessage ?: "HTTP/2 mismatch"}",
                            timestampNs = nowNs,
                            dataType = "tcp"
                        )
                    )
                }

                if (!anyGrpcSuccess && lastHttpOrGrpcError != null && results.size <= 3) {
                    Result.failure(Exception("gNMI query failed: $lastHttpOrGrpcError"))
                } else {
                    Result.success(results)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun buildGnmiRequest(url: String, framedBody: ByteArray, config: GnmiSessionConfig): Request {
        val builder = Request.Builder()
            .url(url)
            .header("te", "trailers")
            .header("content-type", "application/grpc")
            .header("grpc-encoding", "identity")
            .header("user-agent", "Enterweb-Slinger-gNMI/1.0")
            .post(framedBody.toRequestBody("application/grpc".toMediaType()))
        if (config.username.isNotBlank()) {
            builder.header("username", config.username)
            builder.header("password", config.password)
        }
        return builder.build()
    }

    private fun wrapGrpcFrame(protoBytes: ByteArray): ByteArray {
        val frame = ByteBuffer.allocate(5 + protoBytes.size)
        frame.put(0.toByte())
        frame.putInt(protoBytes.size)
        frame.put(protoBytes)
        return frame.array()
    }

    fun buildGnmiGetRequestProto(paths: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        for (p in paths) {
            val encodedPath = encodeGnmiPath(p)
            out.write(0x12) // field 2 (repeated Path path), wire type 2 (length-delimited)
            encodeVarint(out, encodedPath.size.toLong())
            out.write(encodedPath)
        }
        // field 5 (Encoding encoding = JSON_IETF = 4), wire type 0 (varint) -> tag 0x28
        out.write(0x28)
        encodeVarint(out, 4L)
        return out.toByteArray()
    }

    fun buildGnmiSubscribeOnceProto(paths: List<String>): ByteArray {
        val subList = ByteArrayOutputStream()
        for (p in paths) {
            val encodedPath = encodeGnmiPath(p)
            val subscription = ByteArrayOutputStream().apply {
                write(0x0A) // Subscription.path (field 1)
                encodeVarint(this, encodedPath.size.toLong())
                write(encodedPath)
            }.toByteArray()
            subList.write(0x12) // SubscriptionList.subscription (field 2)
            encodeVarint(subList, subscription.size.toLong())
            subList.write(subscription)
        }
        // SubscriptionList.mode = ONCE (1) -> field 5 wire type 0 -> 0x28
        subList.write(0x28)
        encodeVarint(subList, 1L)
        // SubscriptionList.encoding = JSON_IETF (4) -> field 6 wire type 0 -> 0x30
        subList.write(0x30)
        encodeVarint(subList, 4L)

        val listBytes = subList.toByteArray()
        return ByteArrayOutputStream().apply {
            write(0x0A) // SubscribeRequest.subscribe (field 1)
            encodeVarint(this, listBytes.size.toLong())
            write(listBytes)
        }.toByteArray()
    }

    fun encodeGnmiPath(rawPath: String): ByteArray {
        val out = ByteArrayOutputStream()
        val segments = rawPath.split("/").map { it.trim() }.filter { it.isNotEmpty() }
        for (seg in segments) {
            val name = seg.substringBefore("[").trim()
            val elem = ByteArrayOutputStream()
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            elem.write(0x0A) // PathElem.name (field 1)
            encodeVarint(elem, nameBytes.size.toLong())
            elem.write(nameBytes)

            // Parse optional [key=value] selectors
            val keyMatches = "\\[([^=\\]]+)=([^\\]]+)]".toRegex().findAll(seg)
            for (m in keyMatches) {
                val kBytes = m.groupValues[1].trim().toByteArray(Charsets.UTF_8)
                val vBytes = m.groupValues[2].trim().toByteArray(Charsets.UTF_8)
                val mapEntry = ByteArrayOutputStream().apply {
                    write(0x0A)
                    encodeVarint(this, kBytes.size.toLong())
                    write(kBytes)
                    write(0x12)
                    encodeVarint(this, vBytes.size.toLong())
                    write(vBytes)
                }.toByteArray()
                elem.write(0x12) // PathElem.key (field 2)
                encodeVarint(elem, mapEntry.size.toLong())
                elem.write(mapEntry)
            }

            val elemBytes = elem.toByteArray()
            out.write(0x0A) // Path.elem (field 1)
            encodeVarint(out, elemBytes.size.toLong())
            out.write(elemBytes)
        }
        return out.toByteArray()
    }

    private fun extractPrintableUtf8Strings(bytes: ByteArray): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        val start = if (bytes.size > 5) 5 else 0
        for (i in start until bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            if (b in 32..126) {
                sb.append(b.toChar())
            } else {
                if (sb.length >= 4) tokens.add(sb.toString())
                sb.clear()
            }
        }
        if (sb.length >= 4) tokens.add(sb.toString())
        return tokens.distinct().take(20)
    }

    private fun encodeVarint(stream: ByteArrayOutputStream, value: Long) {
        var v = value
        while (v >= 0x80) {
            stream.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        stream.write((v and 0x7F).toInt())
    }
}
