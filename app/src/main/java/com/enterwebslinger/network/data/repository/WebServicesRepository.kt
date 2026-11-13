// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/WebServicesRepository.kt ===
package com.enterwebslinger.network.data.repository

import com.enterwebslinger.network.domain.model.AuthType
import com.enterwebslinger.network.domain.model.HttpHeader
import com.enterwebslinger.network.domain.model.HttpMethod
import com.enterwebslinger.network.domain.model.RestResponse
import com.enterwebslinger.network.domain.model.SoapExecutionResult
import com.enterwebslinger.network.domain.model.SoapOperation
import com.enterwebslinger.network.domain.model.TimingWaterfall
import com.enterwebslinger.network.domain.model.WsdlService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Credentials
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

class WebServicesRepository {

    private val strictHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val unsafeHttpClient: OkHttpClient by lazy {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAll, SecureRandom())
        }

        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAll[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun clientFor(ignoreSslErrors: Boolean): OkHttpClient =
        if (ignoreSslErrors) unsafeHttpClient else strictHttpClient

    // --- 1. REST / HTTP(S) Inspector with Timing Waterfall ---

    suspend fun executeRestRequest(
        url: String,
        method: HttpMethod = HttpMethod.GET,
        headers: List<HttpHeader> = emptyList(),
        authType: AuthType = AuthType.NONE,
        authTokenOrUser: String = "",
        authPassword: String = "",
        requestBody: String = "",
        contentType: String = "application/json",
        ignoreSslErrors: Boolean = true
    ): RestResponse = withContext(Dispatchers.IO) {
        var tDnsStart = 0L
        var tDnsEnd = 0L
        var tConnectStart = 0L
        var tConnectEnd = 0L
        var tTlsStart = 0L
        var tTlsEnd = 0L
        var tRequestSent = 0L
        var tResponseHeaders = 0L
        var tResponseEnd = 0L
        val tGlobalStart = System.currentTimeMillis()

        val timingListener = object : EventListener() {
            override fun dnsStart(call: Call, domainName: String) { tDnsStart = System.currentTimeMillis() }
            override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) { tDnsEnd = System.currentTimeMillis() }
            override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) { tConnectStart = System.currentTimeMillis() }
            override fun secureConnectStart(call: Call) { tTlsStart = System.currentTimeMillis() }
            override fun secureConnectEnd(call: Call, handshake: Handshake?) { tTlsEnd = System.currentTimeMillis() }
            override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: okhttp3.Protocol?) { tConnectEnd = System.currentTimeMillis() }
            override fun requestHeadersEnd(call: Call, request: Request) { tRequestSent = System.currentTimeMillis() }
            override fun responseHeadersStart(call: Call) { tResponseHeaders = System.currentTimeMillis() }
            override fun responseBodyEnd(call: Call, byteCount: Long) { tResponseEnd = System.currentTimeMillis() }
        }

        val client = clientFor(ignoreSslErrors).newBuilder()
            .eventListener(timingListener)
            .build()

        try {
            val reqBuilder = Request.Builder().url(url.trim())

            when (authType) {
                AuthType.BEARER_TOKEN -> {
                    if (authTokenOrUser.isNotBlank()) reqBuilder.header("Authorization", "Bearer $authTokenOrUser")
                }
                AuthType.BASIC_AUTH -> {
                    if (authTokenOrUser.isNotBlank()) reqBuilder.header("Authorization", Credentials.basic(authTokenOrUser, authPassword))
                }
                AuthType.NONE -> {}
            }

            headers.forEach { if (it.name.isNotBlank()) reqBuilder.header(it.name, it.value) }

            val mediaType = contentType.toMediaTypeOrNull()
            when (method) {
                HttpMethod.GET -> reqBuilder.get()
                HttpMethod.POST -> reqBuilder.post(requestBody.toRequestBody(mediaType))
                HttpMethod.PUT -> reqBuilder.put(requestBody.toRequestBody(mediaType))
                HttpMethod.PATCH -> reqBuilder.patch(requestBody.toRequestBody(mediaType))
                HttpMethod.DELETE -> if (requestBody.isNotBlank()) reqBuilder.delete(requestBody.toRequestBody(mediaType)) else reqBuilder.delete()
                HttpMethod.HEAD -> reqBuilder.head()
                HttpMethod.OPTIONS -> reqBuilder.method("OPTIONS", null)
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                val totalTime = System.currentTimeMillis() - tGlobalStart

                val dnsMs = (tDnsEnd - tDnsStart).coerceAtLeast(0)
                val tcpMs = (tConnectEnd - tConnectStart).coerceAtLeast(0)
                val tlsMs = if (tTlsStart > 0 && tTlsEnd > 0) (tTlsEnd - tTlsStart).coerceAtLeast(0) else 0L
                val ttfbMs = if (tResponseHeaders > 0 && tRequestSent > 0) (tResponseHeaders - tRequestSent).coerceAtLeast(0) else 0L
                val dlMs = if (tResponseEnd > 0 && tResponseHeaders > 0) (tResponseEnd - tResponseHeaders).coerceAtLeast(0) else 0L

                val respHeaders = response.headers.names().map { name ->
                    HttpHeader(name, response.header(name) ?: "")
                }

                RestResponse(
                    statusCode = response.code,
                    statusMessage = response.message,
                    headers = respHeaders,
                    body = bodyString,
                    contentLengthBytes = bodyString.toByteArray(Charsets.UTF_8).size.toLong(),
                    timing = TimingWaterfall(
                        dnsLookupMs = dnsMs,
                        tcpHandshakeMs = tcpMs,
                        tlsHandshakeMs = tlsMs,
                        ttfbMs = ttfbMs,
                        downloadDurationMs = dlMs,
                        totalDurationMs = totalTime
                    ),
                    isSuccess = response.isSuccessful
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val totalTime = System.currentTimeMillis() - tGlobalStart
            RestResponse(
                statusCode = 0,
                statusMessage = "Request Failed",
                headers = emptyList(),
                body = e.message ?: "Network error during REST execution",
                contentLengthBytes = 0L,
                timing = TimingWaterfall(totalDurationMs = totalTime),
                isSuccess = false
            )
        }
    }

    // --- 2. SOAP 1.1 / 1.2 & WSDL Inspector ---

    suspend fun parseWsdl(
        wsdlUrl: String,
        ignoreSslErrors: Boolean = true
    ): Result<WsdlService> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(wsdlUrl.trim()).get().build()
            clientFor(ignoreSslErrors).newCall(req).execute().use { resp ->
                val wsdlXml = resp.body?.string() ?: ""
                if (!resp.isSuccessful || wsdlXml.isBlank()) {
                    return@withContext Result.failure(Exception("Failed to fetch WSDL: HTTP ${resp.code}"))
                }

                val db = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
                val doc = db.parse(InputSource(StringReader(wsdlXml)))

                val targetNs = doc.documentElement.getAttribute("targetNamespace")
                val serviceNodes = doc.getElementsByTagNameNS("*", "service")
                val serviceName = if (serviceNodes.length > 0) {
                    serviceNodes.item(0).attributes?.getNamedItem("name")?.nodeValue ?: "WsdlService"
                } else {
                    "WsdlService"
                }

                val operations = mutableListOf<SoapOperation>()
                val opNodes = doc.getElementsByTagNameNS("*", "operation")

                for (i in 0 until opNodes.length) {
                    val opNode = opNodes.item(i)
                    val opName = opNode.attributes?.getNamedItem("name")?.nodeValue ?: continue
                    if (operations.any { it.name == opName }) continue

                    val soapAction = if (targetNs.isNotBlank()) "${targetNs.trimEnd('/')}/$opName" else opName
                    val nsDecl = if (targetNs.isNotBlank()) " xmlns:tem=\"$targetNs\"" else ""
                    val tagPrefix = if (targetNs.isNotBlank()) "tem:" else ""

                    val envelopeTemplate = """
                        <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"$nsDecl>
                           <soapenv:Header/>
                           <soapenv:Body>
                              <$tagPrefix$opName>
                              </$tagPrefix$opName>
                           </soapenv:Body>
                        </soapenv:Envelope>
                    """.trimIndent()

                    operations.add(
                        SoapOperation(
                            name = opName,
                            soapAction = soapAction,
                            targetNamespace = targetNs,
                            inputMessage = "${opName}SoapIn",
                            outputMessage = "${opName}SoapOut",
                            envelopeTemplateXml = envelopeTemplate
                        )
                    )
                }

                Result.success(
                    WsdlService(
                        serviceName = serviceName,
                        portName = "SoapPort",
                        endpointAddress = wsdlUrl.substringBefore("?"),
                        operations = operations
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun executeSoapRequest(
        endpointUrl: String,
        soapAction: String,
        soapEnvelopeXml: String,
        isSoap12: Boolean = false,
        xpathQuery: String = "",
        ignoreSslErrors: Boolean = true
    ): SoapExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val mediaType = if (isSoap12) {
                "application/soap+xml;charset=UTF-8;action=\"$soapAction\"".toMediaType()
            } else {
                "text/xml;charset=UTF-8".toMediaType()
            }

            val reqBuilder = Request.Builder()
                .url(endpointUrl.trim())
                .post(soapEnvelopeXml.toRequestBody(mediaType))

            if (!isSoap12 && soapAction.isNotBlank()) {
                reqBuilder.header("SOAPAction", "\"$soapAction\"")
            }

            clientFor(ignoreSslErrors).newCall(reqBuilder.build()).execute().use { resp ->
                val respXml = resp.body?.string() ?: ""
                val duration = System.currentTimeMillis() - startTime

                val xpathMatches = if (xpathQuery.isNotBlank() && respXml.isNotBlank()) {
                    evaluateXPath(respXml, xpathQuery)
                } else {
                    emptyList()
                }

                val soapFaultMsg = extractSoapFault(respXml)
                val ok = resp.isSuccessful && soapFaultMsg == null

                SoapExecutionResult(
                    statusCode = resp.code,
                    responseXml = respXml,
                    executionTimeMs = duration,
                    isSuccess = ok,
                    xpathMatches = xpathMatches,
                    errorMessage = soapFaultMsg ?: if (!resp.isSuccessful) "HTTP ${resp.code} ${resp.message}" else null
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SoapExecutionResult(
                statusCode = 0,
                responseXml = "",
                executionTimeMs = System.currentTimeMillis() - startTime,
                isSuccess = false,
                errorMessage = e.message ?: "SOAP execution failure"
            )
        }
    }

    internal fun extractSoapFault(xml: String): String? {
        if (xml.isBlank()) return null
        return runCatching {
            val db = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            val doc = db.parse(InputSource(StringReader(xml)))
            val faultNodes = doc.getElementsByTagNameNS("*", "Fault")
            if (faultNodes.length == 0) return@runCatching null

            val faultStringNodes = doc.getElementsByTagName("faultstring")
            if (faultStringNodes.length > 0) {
                val msg = faultStringNodes.item(0).textContent.trim()
                if (msg.isNotBlank()) return@runCatching "SOAP Fault: $msg"
            }
            val reasonTextNodes = doc.getElementsByTagNameNS("*", "Text")
            if (reasonTextNodes.length > 0) {
                val msg = reasonTextNodes.item(0).textContent.trim()
                if (msg.isNotBlank()) return@runCatching "SOAP 1.2 Fault: $msg"
            }
            "SOAP Fault returned by endpoint"
        }.getOrNull()
    }

    fun generateSoapEnvelopeSkeleton(
        operation: SoapOperation,
        parameters: Map<String, String> = emptyMap(),
        isSoap12: Boolean = false
    ): String {
        val soapEnvNs = if (isSoap12) "http://www.w3.org/2003/05/soap-envelope" else "http://schemas.xmlsoap.org/soap/envelope/"
        val paramTags = StringBuilder()
        parameters.forEach { (k, v) ->
            paramTags.append("      <$k>$v</$k>\n")
        }
        val targetNs = operation.targetNamespace
        val nsDecl = if (targetNs.isNotBlank()) " xmlns:tem=\"$targetNs\"" else ""
        val prefix = if (targetNs.isNotBlank()) "tem:" else ""
        return """<?xml version="1.0" encoding="utf-8"?>
<soapenv:Envelope xmlns:soapenv="$soapEnvNs"$nsDecl>
  <soapenv:Header/>
  <soapenv:Body>
    <$prefix${operation.name}>
$paramTags    </$prefix${operation.name}>
  </soapenv:Body>
</soapenv:Envelope>""".trimIndent()
    }

    fun evaluateXPath(xml: String, query: String): List<String> {
        val matches = mutableListOf<String>()
        if (xml.isBlank() || query.isBlank()) return matches
        try {
            val db = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }.newDocumentBuilder()
            val doc = db.parse(InputSource(StringReader(xml)))
            val xpath = XPathFactory.newInstance().newXPath()
            val nodeList = xpath.evaluate(query, doc, XPathConstants.NODESET) as NodeList
            for (i in 0 until nodeList.length) {
                matches.add(nodeList.item(i).textContent.trim())
            }
        } catch (_: Exception) {}
        return matches
    }
}
