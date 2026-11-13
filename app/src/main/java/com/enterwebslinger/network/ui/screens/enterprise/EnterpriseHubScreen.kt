// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/enterprise/EnterpriseHubScreen.kt ===
package com.enterwebslinger.network.ui.screens.enterprise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.DarkBackground
import com.enterwebslinger.network.ui.theme.StatusConnected
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary
import com.enterwebslinger.network.ui.theme.TextSecondary

data class EnterpriseTabItem(
    val title: String,
    val icon: ImageVector,
    val testTag: String
)

val EnterpriseTabs = listOf(
    EnterpriseTabItem("SNMP & Traps", Icons.Default.Router, "tab_snmp"),
    EnterpriseTabItem("Flow Telemetry", Icons.Default.CloudDownload, "tab_flows"),
    EnterpriseTabItem("Syslog Console", Icons.Default.Terminal, "tab_syslog"),
    EnterpriseTabItem("Deep Audit & Map", Icons.Default.Radar, "tab_deep_audit"),
    EnterpriseTabItem("LibreNMS / gNMI", Icons.Default.Dns, "tab_librenms"),
    EnterpriseTabItem("File Transfer", Icons.AutoMirrored.Filled.DriveFileMove, "tab_file_transfer"),
    EnterpriseTabItem("Terminal & OS", Icons.Default.Computer, "tab_terminal_os"),
    EnterpriseTabItem("Core Protocols", Icons.Default.Security, "tab_core_protocols")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterpriseHubScreen(
    viewModel: EnterpriseViewModel,
    onBack: (() -> Unit)? = null
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val isTrapRunning by viewModel.isTrapReceiverRunning.collectAsState()
    val flowStats by viewModel.flowStats.collectAsState()
    val syslogStats by viewModel.syslogStats.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("enterprise_hub_screen")
    ) {
        TopAppBar(
            title = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Enterprise NOC & NMS",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isTrapRunning || flowStats.isRunning || syslogStats.isRunning) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(StatusConnected, CircleShape)
                            )
                        }
                    }
                    Text(
                        text = "Pure-Kotlin Wire Protocols | Live Telemetry",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = CyberCyan,
                            fontSize = 10.sp
                        )
                    )
                }
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBackground)
        )

        ScrollableTabRow(
            selectedTabIndex = selectedTab.coerceIn(0, EnterpriseTabs.lastIndex),
            containerColor = CardBackground,
            contentColor = CyberCyan,
            edgePadding = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("enterprise_tab_row")
        ) {
            EnterpriseTabs.forEachIndexed { index, tab ->
                val selected = selectedTab == index
                Tab(
                    selected = selected,
                    onClick = { viewModel.setSelectedTab(index) },
                    text = {
                        Text(
                            text = tab.title,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) CyberCyan else TextSecondary
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = if (selected) CyberCyan else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag(tab.testTag)
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> SnmpMonitorTab(viewModel = viewModel)
                1 -> FlowTelemetryTab(viewModel = viewModel)
                2 -> SyslogConsoleTab(viewModel = viewModel)
                3 -> DeepFingerprintTab(viewModel = viewModel)
                4 -> LibreNmsGnmiTab(viewModel = viewModel)
                5 -> FileTransferTab(viewModel = viewModel)
                6 -> TerminalAndNetOsTab(viewModel = viewModel)
                7 -> CoreProtocolsTab(viewModel = viewModel)
            }
        }
    }
}
