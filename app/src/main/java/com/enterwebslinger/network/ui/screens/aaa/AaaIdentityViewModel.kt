// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/aaa/AaaIdentityViewModel.kt ===
package com.enterwebslinger.network.ui.screens.aaa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterwebslinger.network.EnterwebSlingerApp
import com.enterwebslinger.network.data.repository.EnterpriseIdentityRepository
import com.enterwebslinger.network.data.repository.RadiusAndAaaRepository
import com.enterwebslinger.network.domain.model.AaaProfileKind
import com.enterwebslinger.network.domain.model.CoaListenerEvent
import com.enterwebslinger.network.domain.model.CoaListenerStatus
import com.enterwebslinger.network.domain.model.LdapTestConfig
import com.enterwebslinger.network.domain.model.LdapTestResult
import com.enterwebslinger.network.domain.model.RadiusAccountingConfig
import com.enterwebslinger.network.domain.model.RadiusAuthRequestConfig
import com.enterwebslinger.network.domain.model.RadiusCoaConfig
import com.enterwebslinger.network.domain.model.RadiusWorkbenchResult
import com.enterwebslinger.network.domain.model.SavedAaaServerProfile
import com.enterwebslinger.network.domain.model.TacacsAuthConfig
import com.enterwebslinger.network.domain.model.TacacsFullResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AaaIdentityViewModel @JvmOverloads constructor(
    private val radiusRepo: RadiusAndAaaRepository = EnterwebSlingerApp.getContainer().radiusAndAaaRepository,
    private val identityRepo: EnterpriseIdentityRepository = EnterwebSlingerApp.getContainer().enterpriseIdentityRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    val savedProfiles: StateFlow<List<SavedAaaServerProfile>> = radiusRepo.savedProfilesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedProfile = MutableStateFlow<SavedAaaServerProfile?>(null)
    val selectedProfile: StateFlow<SavedAaaServerProfile?> = _selectedProfile.asStateFlow()

    private val _selectedProfileSecret = MutableStateFlow("")
    val selectedProfileSecret: StateFlow<String> = _selectedProfileSecret.asStateFlow()

    private val _radiusAuthResult = MutableStateFlow<RadiusWorkbenchResult?>(null)
    val radiusAuthResult: StateFlow<RadiusWorkbenchResult?> = _radiusAuthResult.asStateFlow()

    private val _radiusAcctCoaResult = MutableStateFlow<RadiusWorkbenchResult?>(null)
    val radiusAcctCoaResult: StateFlow<RadiusWorkbenchResult?> = _radiusAcctCoaResult.asStateFlow()

    val coaListenerStatus: StateFlow<CoaListenerStatus> = radiusRepo.coaListenerStatus
    val coaEvents: StateFlow<List<CoaListenerEvent>> = radiusRepo.coaEvents

    private val _tacacsResult = MutableStateFlow<TacacsFullResult?>(null)
    val tacacsResult: StateFlow<TacacsFullResult?> = _tacacsResult.asStateFlow()

    private val _ldapResult = MutableStateFlow<LdapTestResult?>(null)
    val ldapResult: StateFlow<LdapTestResult?> = _ldapResult.asStateFlow()

    private var activeJob: Job? = null

    fun setSelectedTab(tabIndex: Int) {
        _selectedTab.value = tabIndex.coerceIn(0, 3)
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    fun selectProfile(profile: SavedAaaServerProfile) {
        _selectedProfile.value = profile
        _selectedProfileSecret.value = radiusRepo.getSecretForProfile(profile.id)
        val tabTarget = when (profile.kind) {
            AaaProfileKind.RADIUS_AUTH -> 0
            AaaProfileKind.RADIUS_ACCT, AaaProfileKind.RADIUS_COA -> 1
            AaaProfileKind.TACACS_PLUS -> 2
            AaaProfileKind.LDAP_AD -> 3
        }
        _selectedTab.value = tabTarget
    }

    fun saveProfile(
        name: String,
        kind: AaaProfileKind,
        host: String,
        port: Int,
        username: String,
        secretOrPassword: String,
        nasIp: String,
        nasIdentifier: String,
        baseDn: String,
        useTls: Boolean,
        allowSelfSignedTls: Boolean
    ) {
        viewModelScope.launch {
            val id = radiusRepo.saveServerProfile(
                name = name,
                kind = kind,
                host = host,
                port = port,
                username = username,
                secretOrPassword = secretOrPassword,
                nasIp = nasIp,
                nasIdentifier = nasIdentifier,
                baseDn = baseDn,
                useTls = useTls,
                allowSelfSignedTls = allowSelfSignedTls
            )
            _statusBanner.value = "Saved AAA/Identity profile #$id to Room & Keystore Vault."
        }
    }

    fun deleteProfile(profile: SavedAaaServerProfile) {
        viewModelScope.launch {
            radiusRepo.deleteServerProfile(profile.id)
            if (_selectedProfile.value?.id == profile.id) {
                _selectedProfile.value = null
                _selectedProfileSecret.value = ""
            }
            _statusBanner.value = "Removed profile '${profile.name}'."
        }
    }

    fun cancelActiveTask() {
        activeJob?.cancel()
        activeJob = null
        _isRunning.value = false
        _statusBanner.value = "Cancelled active AAA/Identity operation."
    }

    fun runRadiusAuth(config: RadiusAuthRequestConfig) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _isRunning.value = true
            try {
                val res = radiusRepo.sendAuthenticationTest(config)
                _radiusAuthResult.value = res
                _statusBanner.value = res.errorMessage
                    ?: "${res.responsePacketCode.label} via ${res.protocolUsed} in ${res.rttMs} ms"
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun runRadiusAccounting(config: RadiusAccountingConfig) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _isRunning.value = true
            try {
                val res = radiusRepo.sendAccountingTest(config)
                _radiusAcctCoaResult.value = res
                _statusBanner.value = res.errorMessage
                    ?: "${res.responsePacketCode.label} in ${res.rttMs} ms"
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun runCoaOrDisconnect(config: RadiusCoaConfig) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _isRunning.value = true
            try {
                val res = radiusRepo.sendCoaOrDisconnect(config)
                _radiusAcctCoaResult.value = res
                _statusBanner.value = res.errorMessage
                    ?: "${res.responsePacketCode.label} in ${res.rttMs} ms"
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun startCoaListener(port: Int, secret: String, autoAck: Boolean = true) {
        radiusRepo.startCoaListener(port, secret, autoAck)
        _statusBanner.value = "Started Local NAS CoA/PoD UDP Listener on port ${port.coerceIn(1024, 65535)}."
    }

    fun stopCoaListener() {
        radiusRepo.stopCoaListener()
        _statusBanner.value = "Stopped Local NAS CoA Listener."
    }

    fun clearCoaEvents() {
        radiusRepo.clearCoaEvents()
    }

    fun runTacacsTest(config: TacacsAuthConfig) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _isRunning.value = true
            try {
                val res = identityRepo.testTacacsPlus(config)
                _tacacsResult.value = res
                _statusBanner.value = res.errorMessage
                    ?: "TACACS+ ${res.mode.label}: ${res.statusLabel} (${res.rttMs} ms)"
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun runLdapTest(config: LdapTestConfig) {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _isRunning.value = true
            try {
                val res = identityRepo.testLdapBindAndSearch(config)
                _ldapResult.value = res
                _statusBanner.value = res.errorMessage
                    ?: "LDAP Bind: ${res.bindResultLabel} (${res.entries.size} entries, ${res.rttMs} ms)"
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isRunning.value = false
            }
        }
    }

    override fun onCleared() {
        cancelActiveTask()
        radiusRepo.stopCoaListener()
        super.onCleared()
    }
}
