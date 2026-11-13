// === FILE: app/src/main/java/com/enterwebslinger/network/core/di/AppContainer.kt ===
package com.enterwebslinger.network.core.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.enterwebslinger.network.core.security.CredentialVault
import com.enterwebslinger.network.data.local.datastore.UserPreferencesDataStore
import com.enterwebslinger.network.data.local.db.OmniNetDatabase
import com.enterwebslinger.network.data.local.db.dao.DeviceDao
import com.enterwebslinger.network.data.local.db.dao.EndpointProfileDao
import com.enterwebslinger.network.data.local.db.dao.HostWatchDao
import com.enterwebslinger.network.data.local.db.dao.ScanHistoryDao
import com.enterwebslinger.network.data.local.db.dao.SyslogDao
import com.enterwebslinger.network.data.repository.CiscoCollaborationRepository
import com.enterwebslinger.network.data.repository.CoreProtocolsRepository
import com.enterwebslinger.network.data.repository.DiagnosticsRepository
import com.enterwebslinger.network.data.repository.EngineeringUtilitiesRepository
import com.enterwebslinger.network.data.repository.FileTransferRepository
import com.enterwebslinger.network.data.repository.FlowCollectorRepository
import com.enterwebslinger.network.data.repository.GnmiRepository
import com.enterwebslinger.network.data.repository.IotProtocolsRepository
import com.enterwebslinger.network.data.repository.LanScannerRepository
import com.enterwebslinger.network.data.repository.LibreNmsRepository
import com.enterwebslinger.network.data.repository.NetworkInfoRepository
import com.enterwebslinger.network.data.repository.NetworkTelemetryAggregator
import com.enterwebslinger.network.data.repository.NetworkTopologyRepository
import com.enterwebslinger.network.data.repository.NmapFingerprintEngine
import com.enterwebslinger.network.data.repository.OobHardwareRepository
import com.enterwebslinger.network.data.repository.SecurityRepository
import com.enterwebslinger.network.data.repository.SnmpRepository
import com.enterwebslinger.network.data.repository.SyslogRepository
import com.enterwebslinger.network.data.repository.TerminalAndNetOsRepository
import com.enterwebslinger.network.data.repository.VirtualizationRepository
import com.enterwebslinger.network.data.repository.VpnRepository
import com.enterwebslinger.network.data.repository.WebServicesRepository
import com.enterwebslinger.network.data.repository.WifiSpeedRepository
import com.enterwebslinger.network.service.AiDiagnosticService
import com.enterwebslinger.network.ui.screens.ai.AiAssistantViewModel
import com.enterwebslinger.network.ui.screens.dashboard.DashboardViewModel
import com.enterwebslinger.network.ui.screens.datacenter.DataCenterViewModel
import com.enterwebslinger.network.ui.screens.diagnostics.DiagnosticsViewModel
import com.enterwebslinger.network.ui.screens.engineering.EngineeringViewModel
import com.enterwebslinger.network.ui.screens.enterprise.EnterpriseViewModel
import com.enterwebslinger.network.ui.screens.iot.IotApiWorkbenchViewModel
import com.enterwebslinger.network.ui.screens.lanscanner.LanScannerViewModel
import com.enterwebslinger.network.ui.screens.security.SecurityViewModel
import com.enterwebslinger.network.ui.screens.vpn.VpnViewModel
import com.enterwebslinger.network.ui.screens.wifi.WifiSpeedViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(val context: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val userPreferencesDataStore: UserPreferencesDataStore by lazy {
        UserPreferencesDataStore(context)
    }

    val credentialVault: CredentialVault by lazy {
        CredentialVault(context)
    }

    val omniNetDatabase: OmniNetDatabase by lazy {
        OmniNetDatabase.getInstance(context)
    }

    val deviceDao: DeviceDao by lazy {
        omniNetDatabase.deviceDao()
    }

    val scanHistoryDao: ScanHistoryDao by lazy {
        omniNetDatabase.scanHistoryDao()
    }

    val hostWatchDao: HostWatchDao by lazy {
        omniNetDatabase.hostWatchDao()
    }

    val syslogDao: SyslogDao by lazy {
        omniNetDatabase.syslogDao()
    }

    val endpointProfileDao: EndpointProfileDao by lazy {
        omniNetDatabase.endpointProfileDao()
    }

    val networkTopologyRepository: NetworkTopologyRepository by lazy {
        NetworkTopologyRepository(context, applicationScope)
    }

    val networkInfoRepository: NetworkInfoRepository by lazy {
        NetworkInfoRepository(context, applicationScope)
    }

    val lanScannerRepository: LanScannerRepository by lazy {
        LanScannerRepository(context, networkInfoRepository, userPreferencesDataStore)
    }

    val diagnosticsRepository: DiagnosticsRepository by lazy {
        DiagnosticsRepository()
    }

    val securityRepository: SecurityRepository by lazy {
        SecurityRepository(
            scope = applicationScope,
            userPreferencesDataStore = userPreferencesDataStore,
            endpointProfileDao = endpointProfileDao,
            networkInfoRepository = networkInfoRepository
        )
    }

    val wifiSpeedRepository: WifiSpeedRepository by lazy {
        WifiSpeedRepository(context, networkInfoRepository)
    }

    val snmpRepository: SnmpRepository by lazy {
        SnmpRepository(applicationScope)
    }

    val flowCollectorRepository: FlowCollectorRepository by lazy {
        FlowCollectorRepository(applicationScope)
    }

    val syslogRepository: SyslogRepository by lazy {
        SyslogRepository(applicationScope)
    }

    val libreNmsRepository: LibreNmsRepository by lazy {
        LibreNmsRepository()
    }

    val gnmiRepository: GnmiRepository by lazy {
        GnmiRepository()
    }

    val nmapFingerprintEngine: NmapFingerprintEngine by lazy {
        NmapFingerprintEngine()
    }

    val fileTransferRepository: FileTransferRepository by lazy {
        FileTransferRepository(context, applicationScope)
    }

    val terminalAndNetOsRepository: TerminalAndNetOsRepository by lazy {
        TerminalAndNetOsRepository(applicationScope)
    }

    val coreProtocolsRepository: CoreProtocolsRepository by lazy {
        CoreProtocolsRepository()
    }

    val oobHardwareRepository: OobHardwareRepository by lazy {
        OobHardwareRepository()
    }

    val virtualizationRepository: VirtualizationRepository by lazy {
        VirtualizationRepository()
    }

    val ciscoCollaborationRepository: CiscoCollaborationRepository by lazy {
        CiscoCollaborationRepository()
    }

    val vpnRepository: VpnRepository by lazy {
        VpnRepository(context)
    }

    val iotProtocolsRepository: IotProtocolsRepository by lazy {
        IotProtocolsRepository(applicationScope)
    }

    val webServicesRepository: WebServicesRepository by lazy {
        WebServicesRepository()
    }

    val engineeringUtilitiesRepository: EngineeringUtilitiesRepository by lazy {
        EngineeringUtilitiesRepository(context)
    }

    val telemetryAggregator: NetworkTelemetryAggregator by lazy {
        NetworkTelemetryAggregator(
            networkInfoRepository = networkInfoRepository,
            lanScannerRepository = lanScannerRepository,
            diagnosticsRepository = diagnosticsRepository,
            wifiSpeedRepository = wifiSpeedRepository,
            snmpRepository = snmpRepository,
            flowCollectorRepository = flowCollectorRepository,
            syslogRepository = syslogRepository,
            vpnRepository = vpnRepository,
            iotProtocolsRepository = iotProtocolsRepository
        )
    }

    val aiDiagnosticService: AiDiagnosticService by lazy {
        AiDiagnosticService(
            context = context,
            telemetryAggregator = telemetryAggregator,
            networkInfoRepository = networkInfoRepository,
            lanScannerRepository = lanScannerRepository,
            diagnosticsRepository = diagnosticsRepository,
            securityRepository = securityRepository,
            wifiSpeedRepository = wifiSpeedRepository,
            snmpRepository = snmpRepository,
            flowCollectorRepository = flowCollectorRepository,
            oobHardwareRepository = oobHardwareRepository,
            nmapFingerprintEngine = nmapFingerprintEngine,
            terminalAndNetOsRepository = terminalAndNetOsRepository,
            iotProtocolsRepository = iotProtocolsRepository,
            vpnRepository = vpnRepository,
            webServicesRepository = webServicesRepository,
            coreProtocolsRepository = coreProtocolsRepository
        )
    }

    val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return when {
                modelClass.isAssignableFrom(DashboardViewModel::class.java) -> {
                    DashboardViewModel(networkInfoRepository) as T
                }
                modelClass.isAssignableFrom(LanScannerViewModel::class.java) -> {
                    LanScannerViewModel(lanScannerRepository, networkInfoRepository, userPreferencesDataStore) as T
                }
                modelClass.isAssignableFrom(DiagnosticsViewModel::class.java) -> {
                    DiagnosticsViewModel(diagnosticsRepository, networkInfoRepository) as T
                }
                modelClass.isAssignableFrom(SecurityViewModel::class.java) -> {
                    SecurityViewModel(securityRepository, networkInfoRepository) as T
                }
                modelClass.isAssignableFrom(WifiSpeedViewModel::class.java) -> {
                    WifiSpeedViewModel(wifiSpeedRepository) as T
                }
                modelClass.isAssignableFrom(AiAssistantViewModel::class.java) -> {
                    AiAssistantViewModel(aiDiagnosticService) as T
                }
                modelClass.isAssignableFrom(EnterpriseViewModel::class.java) -> {
                    EnterpriseViewModel(
                        snmpRepository = snmpRepository,
                        flowCollectorRepository = flowCollectorRepository,
                        syslogRepository = syslogRepository,
                        libreNmsRepository = libreNmsRepository,
                        gnmiRepository = gnmiRepository,
                        nmapFingerprintEngine = nmapFingerprintEngine,
                        networkInfoRepository = networkInfoRepository,
                        fileTransferRepository = fileTransferRepository,
                        terminalAndNetOsRepository = terminalAndNetOsRepository,
                        coreProtocolsRepository = coreProtocolsRepository
                    ) as T
                }
                modelClass.isAssignableFrom(DataCenterViewModel::class.java) -> {
                    DataCenterViewModel(
                        oobRepository = oobHardwareRepository,
                        virtualizationRepository = virtualizationRepository,
                        ciscoUcRepository = ciscoCollaborationRepository
                    ) as T
                }
                modelClass.isAssignableFrom(VpnViewModel::class.java) -> {
                    VpnViewModel(vpnRepository) as T
                }
                modelClass.isAssignableFrom(IotApiWorkbenchViewModel::class.java) -> {
                    IotApiWorkbenchViewModel(
                        iotRepository = iotProtocolsRepository,
                        webServicesRepository = webServicesRepository
                    ) as T
                }
                modelClass.isAssignableFrom(EngineeringViewModel::class.java) -> {
                    EngineeringViewModel(engineeringUtilitiesRepository) as T
                }
                else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}
