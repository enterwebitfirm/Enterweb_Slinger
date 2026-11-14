// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/aaa/TacacsAndLdapTab.kt ===
package com.enterwebslinger.network.ui.screens.aaa

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enterwebslinger.network.domain.model.AaaProfileKind
import com.enterwebslinger.network.domain.model.LdapTestConfig
import com.enterwebslinger.network.domain.model.SavedAaaServerProfile
import com.enterwebslinger.network.domain.model.TacacsAuthConfig
import com.enterwebslinger.network.domain.model.TacacsAuthenMethod
import com.enterwebslinger.network.domain.model.TacacsOperationMode
import com.enterwebslinger.network.ui.components.ExplainResultSection
import com.enterwebslinger.network.ui.screens.ai.AiAssistantViewModel
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.DarkBackground
import com.enterwebslinger.network.ui.theme.StatusCrimson
import com.enterwebslinger.network.ui.theme.StatusEmerald
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary
import com.enterwebslinger.network.ui.theme.TextSecondary

@Composable
fun TacacsWorkbenchTab(
    viewModel: AaaIdentityViewModel,
    selectedProfile: SavedAaaServerProfile?,
    selectedSecret: String,
    aiViewModel: AiAssistantViewModel?
) {
    val isRunning by viewModel.isRunning.collectAsState()
    val result by viewModel.tacacsResult.collectAsState()

    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("49") }
    var secret by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var privLevel by remember { mutableStateOf("15") }
    var mode by remember { mutableStateOf(TacacsOperationMode.AUTHENTICATION) }
    var authenMethod by remember { mutableStateOf(TacacsAuthenMethod.PAP) }
    var command by remember { mutableStateOf("show") }
    var cmdArgs by remember { mutableStateOf("running-config") }

    LaunchedEffect(selectedProfile?.id, selectedSecret) {
        if (selectedProfile != null && selectedProfile.kind == AaaProfileKind.TACACS_PLUS) {
            host = selectedProfile.host
            port = selectedProfile.port.toString()
            if (selectedSecret.isNotBlank()) secret = selectedSecret
            if (selectedProfile.username.isNotBlank()) username = selectedProfile.username
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("RFC 8907 TACACS+ Client (MD5 Obfuscated)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TacacsOperationMode.entries.forEach { m ->
                            FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label, fontSize = 11.sp) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TacacsAuthenMethod.entries.forEach { am ->
                            FilterChip(selected = authenMethod == am, onClick = { authenMethod = am }, label = { Text(am.label, fontSize = 11.sp) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AaaField(host, { host = it }, "TACACS+ Server IP", Modifier.weight(2f).testTag("tacacs_host_input"))
                        AaaField(port, { port = it }, "Port (49)", Modifier.weight(1f))
                    }
                    AaaField(secret, { secret = it }, "TACACS+ Shared Key (Enables MD5 Pad)", Modifier.fillMaxWidth(), isSecret = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AaaField(username, { username = it }, "Username", Modifier.weight(1f))
                        if (mode == TacacsOperationMode.AUTHENTICATION) {
                            AaaField(password, { password = it }, "Password", Modifier.weight(1f), isSecret = true)
                        } else {
                            AaaField(privLevel, { privLevel = it }, "Priv Lvl", Modifier.weight(0.6f))
                        }
                    }
                    if (mode == TacacsOperationMode.COMMAND_AUTHORIZATION) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AaaField(command, { command = it }, "Command (e.g. show)", Modifier.weight(1f))
                            AaaField(cmdArgs, { cmdArgs = it }, "Args (e.g. running-config)", Modifier.weight(1.4f))
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.runTacacsTest(
                                TacacsAuthConfig(
                                    serverIp = host,
                                    port = port.toIntOrNull() ?: 49,
                                    sharedSecret = secret,
                                    mode = mode,
                                    authenMethod = authenMethod,
                                    username = username,
                                    password = password,
                                    privilegeLevel = privLevel.toIntOrNull() ?: 15,
                                    commandToAuthorize = command,
                                    commandArgs = cmdArgs
                                )
                            )
                        },
                        enabled = !isRunning && host.isNotBlank() && username.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("tacacs_send_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Execute TACACS+ ${mode.label}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        result?.let { res ->
            item {
                val color = if (res.isAuthorizedOrPassed) StatusEmerald else StatusCrimson
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${res.mode.label} • ${res.statusLabel} (${res.rttMs} ms)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = color)
                        Text(
                            "Session 0x${res.sessionIdHex} | MD5 Body Pad: ${res.obfuscatedBody} | Ver=0x${res.versionByte?.toString(16) ?: "--"}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = TextSecondary
                        )
                        if (res.serverMessage.isNotBlank()) {
                            Text("Server Message: ${res.serverMessage}", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        }
                        res.returnedAvPairs.forEach { av ->
                            Surface(color = DarkBackground, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                                Text("AV-Pair: $av", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = CyberCyan, modifier = Modifier.padding(8.dp))
                            }
                        }
                        res.errorMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = StatusCrimson) }

                        val summary = "TACACS+ ${res.serverIp}:${res.port} Mode=${res.mode.name} Status=${res.statusLabel} Obfuscated=${res.obfuscatedBody} Msg=${res.serverMessage} AVs=${res.returnedAvPairs} Err=${res.errorMessage}"
                        ExplainResultSection(
                            diagnosisKey = "tacacs_res",
                            contextTitle = "TACACS+ Response",
                            rawResultText = summary,
                            aiViewModel = aiViewModel,
                            buttonTestTag = "explain_tacacs_btn"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LdapAdWorkbenchTab(
    viewModel: AaaIdentityViewModel,
    selectedProfile: SavedAaaServerProfile?,
    selectedSecret: String,
    aiViewModel: AiAssistantViewModel?
) {
    val isRunning by viewModel.isRunning.collectAsState()
    val result by viewModel.ldapResult.collectAsState()

    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("389") }
    var useLdaps by remember { mutableStateOf(false) }
    var allowSelfSigned by remember { mutableStateOf(true) }
    var bindDn by remember { mutableStateOf("") }
    var bindPassword by remember { mutableStateOf("") }
    var baseDn by remember { mutableStateOf("DC=corp,DC=local") }
    var filterText by remember { mutableStateOf("(objectClass=*)") }

    LaunchedEffect(selectedProfile?.id, selectedSecret) {
        if (selectedProfile != null && selectedProfile.kind == AaaProfileKind.LDAP_AD) {
            host = selectedProfile.host
            port = selectedProfile.port.toString()
            useLdaps = selectedProfile.useTls
            allowSelfSigned = selectedProfile.allowSelfSignedTls
            if (selectedProfile.username.isNotBlank()) bindDn = selectedProfile.username
            if (selectedProfile.baseDn.isNotBlank()) baseDn = selectedProfile.baseDn
            if (selectedSecret.isNotBlank()) bindPassword = selectedSecret
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("RFC 4511 LDAP / Active Directory Bind & Search", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AaaField(host, { host = it }, "Domain Controller IP / FQDN", Modifier.weight(2f).testTag("ldap_host_input"))
                        AaaField(port, { port = it }, "Port", Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("LDAPS Encrypted (TCP 636)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Switch(checked = useLdaps, onCheckedChange = {
                            useLdaps = it
                            port = if (it) "636" else "389"
                        })
                    }
                    if (useLdaps) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Trust Self-Signed AD Cert", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Switch(checked = allowSelfSigned, onCheckedChange = { allowSelfSigned = it })
                        }
                    }
                    AaaField(bindDn, { bindDn = it }, "Bind DN or UPN (e.g. admin@corp.local)", Modifier.fillMaxWidth().testTag("ldap_bind_dn"))
                    AaaField(bindPassword, { bindPassword = it }, "Bind Password", Modifier.fillMaxWidth().testTag("ldap_bind_pass"), isSecret = true)
                    AaaField(baseDn, { baseDn = it }, "Search Base DN (e.g. DC=corp,DC=local)", Modifier.fillMaxWidth())
                    AaaField(filterText, { filterText = it }, "Filter (e.g. (sAMAccountName=jdoe))", Modifier.fillMaxWidth())

                    Button(
                        onClick = {
                            viewModel.runLdapTest(
                                LdapTestConfig(
                                    host = host,
                                    port = port.toIntOrNull() ?: if (useLdaps) 636 else 389,
                                    useLdaps = useLdaps,
                                    allowSelfSignedTls = allowSelfSigned,
                                    bindDnOrUpn = bindDn,
                                    bindPassword = bindPassword,
                                    baseDn = baseDn,
                                    searchFilter = filterText
                                )
                            )
                        },
                        enabled = !isRunning && host.isNotBlank() && bindDn.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("ldap_test_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Run LDAP Bind & Search", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        result?.let { res ->
            item {
                val color = if (res.isBindSuccess) StatusEmerald else StatusCrimson
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Bind: ${res.bindResultLabel} • ${res.rttMs} ms", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = color)
                        Text("Transport: ${res.tlsProtocol} | Returned Objects: ${res.entries.size}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        if (res.bindDiagnosticMessage.isNotBlank()) {
                            Text(res.bindDiagnosticMessage, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        }
                        res.errorMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = StatusCrimson) }

                        val raw = "LDAP ${res.host}:${res.port} TLS=${res.tlsProtocol} Bind=${res.bindResultLabel} Diag=${res.bindDiagnosticMessage} Entries=${res.entries.size} Err=${res.errorMessage}"
                        ExplainResultSection(
                            diagnosisKey = "ldap_res",
                            contextTitle = "LDAP / AD Result",
                            rawResultText = raw,
                            aiViewModel = aiViewModel,
                            buttonTestTag = "explain_ldap_btn"
                        )
                    }
                }
            }

            items(res.entries) { entry ->
                Surface(color = CardBackground, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.dn, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = CyberCyan)
                        entry.attributes.forEach { (k, vals) ->
                            Text("$k: ${vals.joinToString(", ")}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextPrimary)
                        }
                    }
                }
            }
        }
    }
}
