// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/ai/AiAssistantViewModel.kt ===
package com.enterwebslinger.network.ui.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterwebslinger.network.EnterwebSlingerApp
import com.enterwebslinger.network.domain.model.AiAuditReport
import com.enterwebslinger.network.domain.model.ChatMessage
import com.enterwebslinger.network.domain.model.DiscoveredDevice
import com.enterwebslinger.network.domain.model.TracerouteHop
import com.enterwebslinger.network.domain.model.WifiScanEntry
import com.enterwebslinger.network.service.AiDiagnosticService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface AuditUiState {
    data object Idle : AuditUiState
    data object Loading : AuditUiState
    data class Success(val report: AiAuditReport) : AuditUiState
    data class Error(val message: String) : AuditUiState
}

class AiAssistantViewModel @JvmOverloads constructor(
    private val aiDiagnosticService: AiDiagnosticService = EnterwebSlingerApp.getContainer().aiDiagnosticService
) : ViewModel() {

    private val _auditState = MutableStateFlow<AuditUiState>(AuditUiState.Idle)
    val auditState: StateFlow<AuditUiState> = _auditState.asStateFlow()

    private val _microDiagnosisMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val microDiagnosisMap: StateFlow<Map<String, String>> = _microDiagnosisMap.asStateFlow()

    private val _microDiagnosisLoading = MutableStateFlow<Set<String>>(emptySet())
    val microDiagnosisLoading: StateFlow<Set<String>> = _microDiagnosisLoading.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = "model",
                text = "Ready for live network engineering diagnostics. Ask me to ping gateways, inspect Wi-Fi channels, audit ports, run SNMP MIB checks, or analyze telemetry."
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _activeToolExecuting = MutableStateFlow<String?>(null)
    val activeToolExecuting: StateFlow<String?> = _activeToolExecuting.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    fun runNetworkHealthAudit() {
        viewModelScope.launch {
            _auditState.value = AuditUiState.Loading
            val result = aiDiagnosticService.runNetworkHealthAudit()
            result.onSuccess { report ->
                _auditState.value = AuditUiState.Success(report)
            }.onFailure { err ->
                _auditState.value = AuditUiState.Error(err.localizedMessage ?: "AI audit failed.")
            }
        }
    }

    fun explainTraceroute(hops: List<TracerouteHop>) {
        val key = "traceroute"
        viewModelScope.launch {
            _microDiagnosisLoading.value = _microDiagnosisLoading.value + key
            try {
                val explanation = aiDiagnosticService.explainTraceroute(hops)
                _microDiagnosisMap.value = _microDiagnosisMap.value + (key to explanation)
            } finally {
                _microDiagnosisLoading.value = _microDiagnosisLoading.value - key
            }
        }
    }

    fun auditDeviceSecurity(device: DiscoveredDevice) {
        val key = "device_${device.ip}"
        viewModelScope.launch {
            _microDiagnosisLoading.value = _microDiagnosisLoading.value + key
            try {
                val audit = aiDiagnosticService.auditDeviceSecurity(device)
                _microDiagnosisMap.value = _microDiagnosisMap.value + (key to audit)
            } finally {
                _microDiagnosisLoading.value = _microDiagnosisLoading.value - key
            }
        }
    }

    fun recommendWifiChannel(currentChannel: Int, band: String, aps: List<WifiScanEntry>) {
        val key = "wifi_channel"
        viewModelScope.launch {
            _microDiagnosisLoading.value = _microDiagnosisLoading.value + key
            try {
                val recommendation = aiDiagnosticService.recommendWifiChannel(currentChannel, band, aps)
                _microDiagnosisMap.value = _microDiagnosisMap.value + (key to recommendation)
            } finally {
                _microDiagnosisLoading.value = _microDiagnosisLoading.value - key
            }
        }
    }

    fun explainTechnicalResult(key: String, contextTitle: String, rawTelemetry: String) {
        if (rawTelemetry.isBlank()) return
        viewModelScope.launch {
            _microDiagnosisLoading.value = _microDiagnosisLoading.value + key
            try {
                val explanation = aiDiagnosticService.explainTechnicalOutput(contextTitle, rawTelemetry)
                _microDiagnosisMap.value = _microDiagnosisMap.value + (key to explanation)
            } finally {
                _microDiagnosisLoading.value = _microDiagnosisLoading.value - key
            }
        }
    }

    fun clearMicroDiagnosis(key: String) {
        _microDiagnosisMap.value = _microDiagnosisMap.value - key
    }

    fun sendChatMessage(userText: String) {
        val cleanText = userText.trim()
        if (cleanText.isBlank()) return
        val userMsg = ChatMessage(sender = "user", text = cleanText)
        _chatMessages.value = _chatMessages.value + userMsg
        _isChatLoading.value = true
        _activeToolExecuting.value = null

        val responseMessageId = UUID.randomUUID().toString()
        val initialAssistantMsg = ChatMessage(id = responseMessageId, sender = "model", text = "")
        _chatMessages.value = _chatMessages.value + initialAssistantMsg

        viewModelScope.launch {
            val accumulated = StringBuilder()
            try {
                aiDiagnosticService.streamChatWithTools(
                    userMessage = cleanText,
                    onToolExecuting = { toolName ->
                        _activeToolExecuting.value = toolName
                    }
                ).collect { chunk ->
                    accumulated.append(chunk)
                    _chatMessages.value = _chatMessages.value.map { msg ->
                        if (msg.id == responseMessageId) {
                            msg.copy(text = accumulated.toString())
                        } else msg
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _chatMessages.value = _chatMessages.value.map { msg ->
                    if (msg.id == responseMessageId) {
                        msg.copy(text = "Diagnosis interrupted: ${e.localizedMessage ?: "Unknown error"}")
                    } else msg
                }
            } finally {
                _isChatLoading.value = false
                _activeToolExecuting.value = null
            }
        }
    }
}
