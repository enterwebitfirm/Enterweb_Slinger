// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/rffield/RfFieldScreen.kt ===
package com.enterwebslinger.network.ui.screens.rffield

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CardBackgroundElevated
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.DarkBackground
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary
import com.enterwebslinger.network.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RfFieldScreen(
    viewModel: RfFieldViewModel,
    onBack: (() -> Unit)? = null
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WifiFind, contentDescription = "RF & Audit", tint = CyberCyan, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RF Survey, BLE, 5G & Security Audit",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("rf_field_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBackground)
        )

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = CardBackground,
            contentColor = CyberCyan,
            edgePadding = 12.dp,
            divider = {}
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { viewModel.setSelectedTab(0) },
                text = { Text("Floorplan Heatmap") },
                modifier = Modifier.testTag("tab_rf_floorplan")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { viewModel.setSelectedTab(1) },
                text = { Text("BLE Scanner") },
                modifier = Modifier.testTag("tab_rf_ble")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { viewModel.setSelectedTab(2) },
                text = { Text("5G NR / LTE RF") },
                modifier = Modifier.testTag("tab_rf_cellular")
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { viewModel.setSelectedTab(3) },
                text = { Text("ONVIF & Evil Twin") },
                modifier = Modifier.testTag("tab_rf_audit")
            )
        }

        statusBanner?.let { msg ->
            Surface(
                color = CardBackgroundElevated,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = msg, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.dismissBanner() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        when (selectedTab) {
            0 -> FloorplanHeatmapTab(viewModel)
            1 -> BleScannerTab(viewModel)
            2 -> CellularRfTelemetryTab(viewModel)
            else -> OnvifAndEvilTwinTab(viewModel)
        }
    }
}
