// === FILE: app/src/main/java/com/enterwebslinger/network/ui/navigation/AppNavHost.kt ===
package com.enterwebslinger.network.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.enterwebslinger.network.ui.screens.aaa.AaaIdentityScreen
import com.enterwebslinger.network.ui.screens.aaa.AaaIdentityViewModel
import com.enterwebslinger.network.ui.screens.ai.AiAssistantScreen
import com.enterwebslinger.network.ui.screens.ai.AiAssistantViewModel
import com.enterwebslinger.network.ui.screens.dashboard.DashboardScreen
import com.enterwebslinger.network.ui.screens.dashboard.DashboardViewModel
import com.enterwebslinger.network.ui.screens.datacenter.DataCenterHubScreen
import com.enterwebslinger.network.ui.screens.datacenter.DataCenterViewModel
import com.enterwebslinger.network.ui.screens.diagnostics.DiagnosticsScreen
import com.enterwebslinger.network.ui.screens.diagnostics.DiagnosticsViewModel
import com.enterwebslinger.network.ui.screens.engineering.EngineeringUtilitiesScreen
import com.enterwebslinger.network.ui.screens.engineering.EngineeringViewModel
import com.enterwebslinger.network.ui.screens.enterprise.EnterpriseHubScreen
import com.enterwebslinger.network.ui.screens.enterprise.EnterpriseViewModel
import com.enterwebslinger.network.ui.screens.firewall.FirewallHubScreen
import com.enterwebslinger.network.ui.screens.firewall.FirewallViewModel
import com.enterwebslinger.network.ui.screens.iot.IotApiWorkbenchScreen
import com.enterwebslinger.network.ui.screens.iot.IotApiWorkbenchViewModel
import com.enterwebslinger.network.ui.screens.lanscanner.LanScannerScreen
import com.enterwebslinger.network.ui.screens.lanscanner.LanScannerViewModel
import com.enterwebslinger.network.ui.screens.protoolkit.ProToolkitScreen
import com.enterwebslinger.network.ui.screens.protoolkit.ProToolkitViewModel
import com.enterwebslinger.network.ui.screens.rffield.RfFieldScreen
import com.enterwebslinger.network.ui.screens.rffield.RfFieldViewModel
import com.enterwebslinger.network.ui.screens.security.SecurityScreen
import com.enterwebslinger.network.ui.screens.security.SecurityViewModel
import com.enterwebslinger.network.ui.screens.vpn.VpnViewModel
import com.enterwebslinger.network.ui.screens.vpn.VpnWorkbenchScreen
import com.enterwebslinger.network.ui.screens.wifi.WifiSpeedScreen
import com.enterwebslinger.network.ui.screens.wifi.WifiSpeedViewModel

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val testTag: String
) {
    data object Dashboard : Screen(
        route = "dashboard",
        title = "Dashboard",
        icon = Icons.Default.Dashboard,
        testTag = "nav_dashboard"
    )

    data object LanScanner : Screen(
        route = "lan_scanner",
        title = "LAN Scan",
        icon = Icons.Default.Devices,
        testTag = "nav_lan_scanner"
    )

    data object Diagnostics : Screen(
        route = "diagnostics",
        title = "Diagnostics",
        icon = Icons.Default.Timeline,
        testTag = "nav_diagnostics"
    )

    data object Security : Screen(
        route = "security_ports",
        title = "Security",
        icon = Icons.Default.Security,
        testTag = "nav_security"
    )

    data object WifiSpeed : Screen(
        route = "wifi_speed",
        title = "Speed/Wi-Fi",
        icon = Icons.Default.Speed,
        testTag = "nav_wifi_speed"
    )

    data object EnterpriseNoc : Screen(
        route = "enterprise_noc",
        title = "NOC Suite",
        icon = Icons.Default.Hub,
        testTag = "nav_enterprise_noc"
    )

    data object DataCenter : Screen(
        route = "data_center",
        title = "Data Center",
        icon = Icons.Default.Dns,
        testTag = "nav_data_center"
    )

    data object VpnWorkbench : Screen(
        route = "vpn_workbench",
        title = "VPN Tunnel",
        icon = Icons.Default.VpnKey,
        testTag = "nav_vpn"
    )

    data object FirewallHub : Screen(
        route = "firewall_hub",
        title = "Firewalls",
        icon = Icons.Default.Security,
        testTag = "nav_firewall_hub"
    )

    data object AaaIdentity : Screen(
        route = "aaa_identity",
        title = "AAA / AD",
        icon = Icons.Default.VerifiedUser,
        testTag = "nav_aaa_identity"
    )

    data object IotApiWorkbench : Screen(
        route = "iot_api_workbench",
        title = "IoT & APIs",
        icon = Icons.Default.Sensors,
        testTag = "nav_iot_api"
    )

    data object EngineeringUtils : Screen(
        route = "engineering_utils",
        title = "Eng Tools",
        icon = Icons.Default.Build,
        testTag = "nav_engineering"
    )

    data object RfField : Screen(
        route = "rf_field",
        title = "RF & Audit",
        icon = Icons.Default.WifiFind,
        testTag = "nav_rf_field"
    )

    data object ProToolkit : Screen(
        route = "pro_toolkit",
        title = "Pro Tools",
        icon = Icons.Default.Engineering,
        testTag = "nav_pro_toolkit"
    )

    data object AiAssistant : Screen(
        route = "ai_assistant",
        title = "AI Engineer",
        icon = Icons.Default.AutoAwesome,
        testTag = "nav_ai_assistant"
    )
}

val BottomNavDestinations = listOf(
    Screen.Dashboard,
    Screen.LanScanner,
    Screen.Diagnostics,
    Screen.Security,
    Screen.WifiSpeed,
    Screen.RfField,
    Screen.ProToolkit,
    Screen.EnterpriseNoc,
    Screen.DataCenter,
    Screen.VpnWorkbench,
    Screen.FirewallHub,
    Screen.AaaIdentity,
    Screen.IotApiWorkbench,
    Screen.EngineeringUtils
)

val AllNavDestinations = BottomNavDestinations + Screen.AiAssistant

fun NavHostController.navigateSingleTopTo(route: String) {
    navigate(route) {
        popUpTo(Screen.Dashboard.route) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    dashboardViewModel: DashboardViewModel,
    lanScannerViewModel: LanScannerViewModel,
    diagnosticsViewModel: DiagnosticsViewModel,
    securityViewModel: SecurityViewModel,
    wifiSpeedViewModel: WifiSpeedViewModel,
    aiAssistantViewModel: AiAssistantViewModel,
    enterpriseViewModel: EnterpriseViewModel,
    dataCenterViewModel: DataCenterViewModel,
    vpnViewModel: VpnViewModel,
    firewallViewModel: FirewallViewModel,
    aaaIdentityViewModel: AaaIdentityViewModel,
    iotApiViewModel: IotApiWorkbenchViewModel,
    engineeringViewModel: EngineeringViewModel,
    rfFieldViewModel: RfFieldViewModel,
    proToolkitViewModel: ProToolkitViewModel,
    onOpenAiChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navigateBackToDashboard: () -> Unit = {
        if (!navController.popBackStack()) {
            navController.navigateSingleTopTo(Screen.Dashboard.route)
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                viewModel = dashboardViewModel,
                aiViewModel = aiAssistantViewModel,
                onOpenAiChat = onOpenAiChat
            )
        }

        composable(Screen.LanScanner.route) {
            LanScannerScreen(
                viewModel = lanScannerViewModel,
                aiViewModel = aiAssistantViewModel,
                onNavigateToPortScan = { targetIp ->
                    securityViewModel.setPortTarget(targetIp)
                    securityViewModel.setSelectedTab(0)
                    navController.navigateSingleTopTo(Screen.Security.route)
                }
            )
        }

        composable(Screen.Diagnostics.route) {
            DiagnosticsScreen(
                viewModel = diagnosticsViewModel,
                aiViewModel = aiAssistantViewModel
            )
        }

        composable(Screen.Security.route) {
            SecurityScreen(viewModel = securityViewModel)
        }

        composable(Screen.WifiSpeed.route) {
            WifiSpeedScreen(
                viewModel = wifiSpeedViewModel,
                aiViewModel = aiAssistantViewModel
            )
        }

        composable(Screen.EnterpriseNoc.route) {
            EnterpriseHubScreen(
                viewModel = enterpriseViewModel,
                onBack = navigateBackToDashboard
            )
        }

        composable(Screen.DataCenter.route) {
            DataCenterHubScreen(
                viewModel = dataCenterViewModel,
                onBack = navigateBackToDashboard
            )
        }

        composable(Screen.VpnWorkbench.route) {
            VpnWorkbenchScreen(
                viewModel = vpnViewModel,
                aiViewModel = aiAssistantViewModel,
                onBack = navigateBackToDashboard
            )
        }

        composable(Screen.FirewallHub.route) {
            FirewallHubScreen(
                viewModel = firewallViewModel,
                aiViewModel = aiAssistantViewModel,
                onBack = navigateBackToDashboard
            )
        }

        composable(Screen.AaaIdentity.route) {
            AaaIdentityScreen(
                viewModel = aaaIdentityViewModel,
                aiViewModel = aiAssistantViewModel,
                onBack = navigateBackToDashboard
            )
        }

        composable(Screen.IotApiWorkbench.route) {
            IotApiWorkbenchScreen(
                viewModel = iotApiViewModel,
                onBack = navigateBackToDashboard
            )
        }

        composable(Screen.EngineeringUtils.route) {
            EngineeringUtilitiesScreen(
                viewModel = engineeringViewModel,
                onBack = navigateBackToDashboard
            )
        }

        composable(Screen.RfField.route) {
            RfFieldScreen(
                viewModel = rfFieldViewModel,
                onBack = navigateBackToDashboard
            )
        }

        composable(Screen.ProToolkit.route) {
            ProToolkitScreen(
                viewModel = proToolkitViewModel,
                onBack = navigateBackToDashboard
            )
        }

        composable(Screen.AiAssistant.route) {
            AiAssistantScreen(
                viewModel = aiAssistantViewModel,
                onBack = navigateBackToDashboard
            )
        }
    }
}
