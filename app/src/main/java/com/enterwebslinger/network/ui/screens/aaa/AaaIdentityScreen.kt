// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/aaa/AaaIdentityScreen.kt ===
package com.enterwebslinger.network.ui.screens.aaa

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enterwebslinger.network.domain.model.AaaProfileKind
import com.enterwebslinger.network.ui.screens.ai.AiAssistantViewModel
import com.enterwebslinger.network.ui.theme.BorderStrokeColor
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CardBackgroundElevated
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.DarkBackground
import com.enterwebslinger.network.ui.theme.StatusCrimson
import com.enterwebslinger.network.ui.theme.StatusEmerald
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary
import com.enterwebslinger.network.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AaaIdentityScreen(
    viewModel: AaaIdentityViewModel,
    aiViewModel: AiAssistantViewModel? = null,
    onBack: (() -> Unit)? = null
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val statusBanner by viewModel.statusBanner.collectAsState()
    val profiles by viewModel.savedProfiles.collectAsState()
    val selectedProfile by viewModel.selectedProfile.collectAsState()
    val selectedSecret by viewModel.selectedProfileSecret.collectAsState()

    var showAddProfileDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AAA, RADIUS, TACACS+ & AD",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("aaa_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                }
            },
            actions = {
                if (isRunning) {
                    IconButton(onClick = { viewModel.cancelActiveTask() }, modifier = Modifier.testTag("aaa_cancel_button")) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = CyberCyan)
                    }
                }
                IconButton(onClick = { showAddProfileDialog = true }, modifier = Modifier.testTag("aaa_add_profile_button")) {
                    Icon(Icons.Default.Add, contentDescription = "Save AAA Server Profile", tint = StatusEmerald)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBackground)
        )

        // Saved RADIUS/TACACS+/LDAP Vault Profile Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (profiles.isEmpty()) {
                OutlinedButton(
                    onClick = { showAddProfileDialog = true },
                    modifier = Modifier.heightIn(min = 40.dp).testTag("aaa_empty_add_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save RADIUS / TACACS+ / AD Server", fontSize = 11.sp)
                }
            } else {
                profiles.forEach { prof ->
                    val active = prof.id == selectedProfile?.id
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) CardBackgroundElevated else DarkBackground,
                        border = BorderStroke(1.dp, if (active) CyberCyan else BorderStrokeColor),
                        modifier = Modifier
                            .clickable { viewModel.selectProfile(prof) }
                            .testTag("aaa_profile_${prof.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(prof.name, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = if (active) CyberCyan else TextPrimary)
                                Text(
                                    "${prof.kind.label} • ${prof.host}:${prof.port}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                                    color = TextMuted
                                )
                            }
                            if (active) {
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { viewModel.deleteProfile(prof) },
                                    modifier = Modifier.size(26.dp).testTag("aaa_del_profile_${prof.id}")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusCrimson, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = CardBackground,
            contentColor = CyberCyan,
            edgePadding = 12.dp,
            divider = {}
        ) {
            listOf("RADIUS Auth & VSA", "Accounting & CoA", "TACACS+ (49)", "LDAP / AD").forEachIndexed { idx, label ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { viewModel.setSelectedTab(idx) },
                    text = { Text(label) },
                    modifier = Modifier.testTag("aaa_tab_$idx")
                )
            }
        }

        statusBanner?.let { banner ->
            Surface(
                color = CardBackgroundElevated,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(banner, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.dismissBanner() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        when (selectedTab) {
            0 -> RadiusAuthWorkbenchTab(
                viewModel = viewModel,
                selectedProfile = selectedProfile,
                selectedSecret = selectedSecret,
                aiViewModel = aiViewModel
            )
            1 -> RadiusAcctAndCoaTab(
                viewModel = viewModel,
                selectedProfile = selectedProfile,
                selectedSecret = selectedSecret,
                aiViewModel = aiViewModel
            )
            2 -> TacacsWorkbenchTab(
                viewModel = viewModel,
                selectedProfile = selectedProfile,
                selectedSecret = selectedSecret,
                aiViewModel = aiViewModel
            )
            3 -> LdapAdWorkbenchTab(
                viewModel = viewModel,
                selectedProfile = selectedProfile,
                selectedSecret = selectedSecret,
                aiViewModel = aiViewModel
            )
        }
    }

    if (showAddProfileDialog) {
        SaveAaaProfileDialog(
            onDismiss = { showAddProfileDialog = false },
            onSave = { name, kind, host, port, user, secret, nasIp, nasId, baseDn, useTls, selfSigned ->
                viewModel.saveProfile(name, kind, host, port, user, secret, nasIp, nasId, baseDn, useTls, selfSigned)
                showAddProfileDialog = false
            }
        )
    }
}

@Composable
private fun SaveAaaProfileDialog(
    onDismiss: () -> Unit,
    onSave: (String, AaaProfileKind, String, Int, String, String, String, String, String, Boolean, Boolean) -> Unit
) {
    var kind by remember { mutableStateOf(AaaProfileKind.RADIUS_AUTH) }
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf(kind.defaultPort.toString()) }
    var username by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var nasIp by remember { mutableStateOf("127.0.0.1") }
    var nasId by remember { mutableStateOf("EnterwebSlinger") }
    var baseDn by remember { mutableStateOf("DC=corp,DC=local") }
    var useTls by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save AAA / Identity Server", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AaaProfileKind.entries.forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = {
                                kind = k
                                port = if (k == AaaProfileKind.LDAP_AD && useTls) "636" else k.defaultPort.toString()
                            },
                            label = { Text(k.label, fontSize = 11.sp) }
                        )
                    }
                }
                AaaDialogField(name, { name = it }, "Profile Name", "Core-FreeRADIUS / AD-DC01")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AaaDialogField(host, { host = it }, "Host / IP", "10.0.0.10", Modifier.weight(2f))
                    AaaDialogField(port, { port = it }, "Port", kind.defaultPort.toString(), Modifier.weight(1f))
                }
                AaaDialogField(
                    username,
                    { username = it },
                    if (kind == AaaProfileKind.LDAP_AD) "Bind DN / UPN" else "Default Username",
                    if (kind == AaaProfileKind.LDAP_AD) "admin@corp.local" else "netadmin"
                )
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text(if (kind == AaaProfileKind.LDAP_AD) "Bind Password (Vaulted)" else "Shared Secret (Vaulted)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (kind != AaaProfileKind.LDAP_AD) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AaaDialogField(nasIp, { nasIp = it }, "NAS-IP", "127.0.0.1", Modifier.weight(1f))
                        AaaDialogField(nasId, { nasId = it }, "NAS-Identifier", "EnterwebSlinger", Modifier.weight(1f))
                    }
                } else {
                    AaaDialogField(baseDn, { baseDn = it }, "Search Base DN", "DC=corp,DC=local")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Use LDAPS (TLS 636)", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Switch(checked = useTls, onCheckedChange = {
                            useTls = it
                            port = if (it) "636" else "389"
                        })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name, kind, host, port.toIntOrNull() ?: kind.defaultPort,
                        username, secret, nasIp, nasId, baseDn, useTls, true
                    )
                },
                enabled = host.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D)),
                modifier = Modifier.testTag("aaa_save_profile_confirm_btn")
            ) { Text("Save to Vault") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        containerColor = CardBackground
    )
}

@Composable
private fun AaaDialogField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        placeholder = { Text(placeholder, color = TextMuted) },
        singleLine = true,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CyberCyan,
            unfocusedBorderColor = TextMuted,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        ),
        shape = RoundedCornerShape(8.dp)
    )
}
