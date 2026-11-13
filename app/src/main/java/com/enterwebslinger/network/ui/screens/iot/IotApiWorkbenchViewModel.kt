// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/iot/IotApiWorkbenchViewModel.kt ===
package com.enterwebslinger.network.ui.screens.iot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterwebslinger.network.EnterwebSlingerApp
import com.enterwebslinger.network.data.repository.IotProtocolsRepository
import com.enterwebslinger.network.data.repository.WebServicesRepository
import com.enterwebslinger.network.domain.model.AuthType
import com.enterwebslinger.network.domain.model.CoapMethod
import com.enterwebslinger.network.domain.model.CoapResource
import com.enterwebslinger.network.domain.model.CoapResponse
import com.enterwebslinger.network.domain.model.CoapType
import com.enterwebslinger.network.domain.model.HttpHeader
import com.enterwebslinger.network.domain.model.HttpMethod
import com.enterwebslinger.network.domain.model.MqttMessageItem
import com.enterwebslinger.network.domain.model.MqttQos
import com.enterwebslinger.network.domain.model.MqttSessionConfig
import com.enterwebslinger.network.domain.model.MqttUserProperty
import com.enterwebslinger.network.domain.model.MqttVersion
import com.enterwebslinger.network.domain.model.RestResponse
import com.enterwebslinger.network.domain.model.SoapExecutionResult
import com.enterwebslinger.network.domain.model.SoapOperation
import com.enterwebslinger.network.domain.model.WsdlService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class IotApiWorkbenchViewModel @JvmOverloads constructor(
    private val iotRepository: IotProtocolsRepository = EnterwebSlingerApp.getContainer().iotProtocolsRepository,
    private val webServicesRepository: WebServicesRepository = EnterwebSlingerApp.getContainer().webServicesRepository
) : ViewModel() {

    // --- Active Tab (0: MQTT, 1: CoAP, 2: REST, 3: SOAP & WSDL) ---
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab.coerceIn(0, 3)
    }

    // =========================================================================
    // 1. MQTT 3.1.1 & 5.0 State & Actions
    // =========================================================================
    val isMqttConnected: StateFlow<Boolean> = iotRepository.isMqttConnected
    val mqttStatus: StateFlow<String> = iotRepository.mqttStatus
    val mqttMessages: StateFlow<List<MqttMessageItem>> = iotRepository.mqttMessages
    val mqttSubscribedTopics: StateFlow<Set<String>> = iotRepository.mqttSubscribedTopics

    var mqttHost = MutableStateFlow("")
    var mqttPort = MutableStateFlow("1883")
    var mqttClientId = MutableStateFlow("")
    var mqttVersion = MutableStateFlow(MqttVersion.MQTT_5_0)
    var mqttKeepAlive = MutableStateFlow("60")
    var mqttCleanSession = MutableStateFlow(true)
    var mqttUseTls = MutableStateFlow(false)
    var mqttUsername = MutableStateFlow("")
    var mqttPassword = MutableStateFlow("")

    var mqttPublishTopic = MutableStateFlow("")
    var mqttPublishPayload = MutableStateFlow("")
    var mqttPublishQos = MutableStateFlow(MqttQos.AT_LEAST_ONCE)
    var mqttPublishRetain = MutableStateFlow(false)
    var mqttUserProps = MutableStateFlow<List<MqttUserProperty>>(emptyList())

    var mqttNewSubTopic = MutableStateFlow("")

    fun connectMqtt() {
        val host = mqttHost.value.trim()
        if (host.isBlank()) return
        viewModelScope.launch {
            val portInt = mqttPort.value.toIntOrNull() ?: if (mqttUseTls.value) 8883 else 1883
            val keepAliveInt = mqttKeepAlive.value.toIntOrNull() ?: 60
            val effectiveClientId = mqttClientId.value.trim().ifBlank {
                "slinger-" + UUID.randomUUID().toString().take(8)
            }
            val config = MqttSessionConfig(
                brokerHost = host,
                port = portInt,
                clientId = effectiveClientId,
                version = mqttVersion.value,
                cleanStart = mqttCleanSession.value,
                keepAliveSeconds = keepAliveInt,
                username = mqttUsername.value.trim(),
                password = mqttPassword.value,
                useTls = mqttUseTls.value || portInt == 8883
            )
            iotRepository.connectMqtt(config)
        }
    }

    fun disconnectMqtt() {
        viewModelScope.launch {
            iotRepository.disconnectMqtt()
        }
    }

    fun publishMqtt() {
        val topic = mqttPublishTopic.value.trim()
        if (topic.isBlank()) return
        viewModelScope.launch {
            val payload = mqttPublishPayload.value
            iotRepository.publishMqtt(
                topic = topic,
                payload = payload,
                qos = mqttPublishQos.value,
                retain = mqttPublishRetain.value,
                userProperties = mqttUserProps.value
            )
        }
    }

    fun subscribeMqtt(topic: String = mqttNewSubTopic.value) {
        val clean = topic.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            iotRepository.subscribeTopic(clean, MqttQos.AT_LEAST_ONCE)
        }
    }

    fun unsubscribeMqtt(topic: String) {
        viewModelScope.launch {
            iotRepository.unsubscribeTopic(topic)
        }
    }

    fun clearMqttMessages() {
        iotRepository.clearMqttMessages()
    }

    // =========================================================================
    // 2. CoAP RFC 7252 & RFC 7641 Observe State & Actions
    // =========================================================================
    val coapDiscoveredResources: StateFlow<List<CoapResource>> = iotRepository.coapDiscoveredResources
    val lastCoapResponse: StateFlow<CoapResponse?> = iotRepository.lastCoapResponse

    var coapHost = MutableStateFlow("")
    var coapPort = MutableStateFlow("5683")
    var coapMethod = MutableStateFlow(CoapMethod.GET)
    var coapUriPath = MutableStateFlow("")
    var coapPayload = MutableStateFlow("")
    var coapType = MutableStateFlow(CoapType.CON)
    var isCoapObserving = MutableStateFlow(false)

    private val _coapStatus = MutableStateFlow<String?>(null)
    val coapStatus: StateFlow<String?> = _coapStatus.asStateFlow()

    fun executeCoapRequest() {
        val host = coapHost.value.trim()
        if (host.isBlank()) return
        viewModelScope.launch {
            _coapStatus.value = "Sending CoAP ${coapMethod.value} request..."
            val portInt = coapPort.value.toIntOrNull() ?: 5683
            val resp = iotRepository.executeCoapRequest(
                host = host,
                port = portInt,
                method = coapMethod.value,
                uriPath = coapUriPath.value.trim(),
                payload = coapPayload.value.trim(),
                type = coapType.value,
                observe = isCoapObserving.value
            )
            _coapStatus.value = "CoAP Response: ${resp.responseCode} in ${resp.roundTripTimeMs}ms"
        }
    }

    fun discoverCoapResources() {
        val host = coapHost.value.trim()
        if (host.isBlank()) return
        viewModelScope.launch {
            _coapStatus.value = "Discovering /.well-known/core resources..."
            val portInt = coapPort.value.toIntOrNull() ?: 5683
            val resp = iotRepository.executeCoapRequest(
                host = host,
                port = portInt,
                method = CoapMethod.GET,
                uriPath = "/.well-known/core"
            )
            val count = iotRepository.coapDiscoveredResources.value.size
            _coapStatus.value = "Discovered $count CoAP resources (${resp.roundTripTimeMs}ms)"
        }
    }

    // =========================================================================
    // 3. REST HTTP(S) Timing Waterfall Inspector
    // =========================================================================
    var restUrl = MutableStateFlow("")
    var restMethod = MutableStateFlow(HttpMethod.GET)
    var restAuthType = MutableStateFlow(AuthType.NONE)
    var restAuthUser = MutableStateFlow("")
    var restAuthPass = MutableStateFlow("")
    var restAuthToken = MutableStateFlow("")
    var restRequestBody = MutableStateFlow("")
    var restContentType = MutableStateFlow("application/json")
    var restIgnoreSslErrors = MutableStateFlow(true)

    private val _restHeaders = MutableStateFlow(
        listOf(
            HttpHeader("User-Agent", "Enterweb-Slinger/1.0 (Android)"),
            HttpHeader("Accept", "application/json")
        )
    )
    val restHeaders: StateFlow<List<HttpHeader>> = _restHeaders.asStateFlow()

    private val _restResponse = MutableStateFlow<RestResponse?>(null)
    val restResponse: StateFlow<RestResponse?> = _restResponse.asStateFlow()

    private val _isRestLoading = MutableStateFlow(false)
    val isRestLoading: StateFlow<Boolean> = _isRestLoading.asStateFlow()

    fun addRestHeader(key: String, value: String) {
        if (key.isNotBlank()) {
            _restHeaders.value = _restHeaders.value + HttpHeader(key.trim(), value.trim())
        }
    }

    fun removeRestHeader(header: HttpHeader) {
        _restHeaders.value = _restHeaders.value.filter { it != header }
    }

    fun executeRestRequest() {
        val url = restUrl.value.trim()
        if (url.isBlank()) return
        viewModelScope.launch {
            _isRestLoading.value = true
            val resp = webServicesRepository.executeRestRequest(
                url = url,
                method = restMethod.value,
                headers = _restHeaders.value,
                authType = restAuthType.value,
                authTokenOrUser = if (restAuthType.value == AuthType.BEARER_TOKEN) restAuthToken.value.trim() else restAuthUser.value.trim(),
                authPassword = restAuthPass.value,
                requestBody = restRequestBody.value,
                contentType = restContentType.value,
                ignoreSslErrors = restIgnoreSslErrors.value
            )
            _restResponse.value = resp
            _isRestLoading.value = false
        }
    }

    // =========================================================================
    // 4. SOAP 1.1 / 1.2 & WSDL Inspector
    // =========================================================================
    var wsdlUrl = MutableStateFlow("")
    var soapEndpointUrl = MutableStateFlow("")
    var soapActionHeader = MutableStateFlow("")
    var soapVersion12 = MutableStateFlow(false)
    var soapXmlPayload = MutableStateFlow("")
    var soapIgnoreSslErrors = MutableStateFlow(true)

    private val _wsdlServices = MutableStateFlow<List<WsdlService>>(emptyList())
    val wsdlServices: StateFlow<List<WsdlService>> = _wsdlServices.asStateFlow()

    private val _selectedSoapOperation = MutableStateFlow<SoapOperation?>(null)
    val selectedSoapOperation: StateFlow<SoapOperation?> = _selectedSoapOperation.asStateFlow()

    private val _soapResult = MutableStateFlow<SoapExecutionResult?>(null)
    val soapResult: StateFlow<SoapExecutionResult?> = _soapResult.asStateFlow()

    private val _isSoapLoading = MutableStateFlow(false)
    val isSoapLoading: StateFlow<Boolean> = _isSoapLoading.asStateFlow()

    var xpathQuery = MutableStateFlow("")
    private val _xpathMatches = MutableStateFlow<List<String>>(emptyList())
    val xpathMatches: StateFlow<List<String>> = _xpathMatches.asStateFlow()

    fun fetchAndParseWsdl() {
        val url = wsdlUrl.value.trim()
        if (url.isBlank()) return
        viewModelScope.launch {
            _isSoapLoading.value = true
            val result = webServicesRepository.parseWsdl(url, ignoreSslErrors = soapIgnoreSslErrors.value)
            _isSoapLoading.value = false
            result.fold(
                onSuccess = { srv ->
                    _wsdlServices.value = listOf(srv)
                    val firstOp = srv.operations.firstOrNull()
                    if (firstOp != null) {
                        selectSoapOperation(firstOp)
                    }
                    if (soapEndpointUrl.value.isBlank() && srv.endpointAddress.isNotBlank()) {
                        soapEndpointUrl.value = srv.endpointAddress
                    }
                },
                onFailure = { err ->
                    _soapResult.value = SoapExecutionResult(
                        statusCode = 0,
                        responseXml = "Could not parse WSDL at $url: ${err.message}",
                        executionTimeMs = 0L,
                        isSuccess = false,
                        errorMessage = err.message
                    )
                }
            )
        }
    }

    fun selectSoapOperation(operation: SoapOperation) {
        _selectedSoapOperation.value = operation
        soapActionHeader.value = operation.soapAction
        soapXmlPayload.value = if (operation.envelopeTemplateXml.isNotBlank()) {
            operation.envelopeTemplateXml
        } else {
            webServicesRepository.generateSoapEnvelopeSkeleton(operation, emptyMap(), soapVersion12.value)
        }
    }

    fun executeSoapRequest() {
        val endpoint = soapEndpointUrl.value.trim()
        if (endpoint.isBlank()) return
        viewModelScope.launch {
            _isSoapLoading.value = true
            val res = webServicesRepository.executeSoapRequest(
                endpointUrl = endpoint,
                soapAction = soapActionHeader.value.trim(),
                soapEnvelopeXml = soapXmlPayload.value,
                isSoap12 = soapVersion12.value,
                xpathQuery = xpathQuery.value.trim(),
                ignoreSslErrors = soapIgnoreSslErrors.value
            )
            _isSoapLoading.value = false
            _soapResult.value = res
            _xpathMatches.value = res.xpathMatches
        }
    }

    fun runXPathQuery() {
        val xml = _soapResult.value?.responseXml ?: return
        val query = xpathQuery.value.trim()
        if (query.isNotBlank()) {
            val matches = webServicesRepository.evaluateXPath(xml, query)
            _xpathMatches.value = matches
        }
    }
}
