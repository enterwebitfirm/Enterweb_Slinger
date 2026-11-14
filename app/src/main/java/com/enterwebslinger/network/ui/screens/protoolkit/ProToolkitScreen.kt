// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/protoolkit/ProToolkitScreen.kt ===
package com.enterwebslinger.network.ui.screens.protoolkit

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
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Info
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
fun ProToolkitScreen(
    viewModel: ProToolkitViewModel,
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
                    Icon(Icons.Default.Engineering, contentDescription = "Pro Toolkit", tint = CyberCyan, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ISP & Protocol Field Toolkit",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("pro_toolkit_back_button")) {
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
                text = { Text("PMTUD & MSS") },
                modifier = Modifier.testTag("tab_pro_pmtud")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { viewModel.setSelectedTab(1) },
                text = { Text("DNSSEC & HTTP/3") },
                modifier = Modifier.testTag("tab_pro_dns_alpn")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { viewModel.setSelectedTab(2) },
                text = { Text("RADIUS & TACACS+") },
                modifier = Modifier.testTag("tab_pro_aaa")
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { viewModel.setSelectedTab(3) },
                text = { Text("SSH Batch & Traffic") },
                modifier = Modifier.testTag("tab_pro_batch")
            )
            Tab(
                selected = selectedTab == 4,
                onClick = { viewModel.setSelectedTab(4) },
                text = { Text("Auto Audit Worker") },
                modifier = Modifier.testTag("tab_pro_worker")
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
            0 -> PmtudAuditorTab(viewModel)
            1 -> DnsAndHappyEyeballsTab(viewModel)
            2 -> AaaRadiusTacacsTab(viewModel)
            3 -> BatchSshAndTrafficTab(viewModel)
            else -> BackgroundAuditWorkerTab(viewModel)
        }
    }
}
