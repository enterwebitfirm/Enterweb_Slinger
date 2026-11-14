// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/aaa/RadiusAuthAndAcctTabs.kt ===
package com.enterwebslinger.network.ui.screens.aaa

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enterwebslinger.network.domain.model.AaaProfileKind
import com.enterwebslinger.network.domain.model.RadiusAcctStatusType
import com.enterwebslinger.network.domain.model.RadiusAccountingConfig
import com.enterwebslinger.network.domain.model.RadiusAuthProtocol
import com.enterwebslinger.network.domain.model.RadiusAuthRequestConfig
import com.enterwebslinger.network.domain.model.RadiusCoaConfig
import com.enterwebslinger.network.domain.model.RadiusServiceType
import com.enterwebslinger.network.domain.model.RadiusVsaInput
import com.enterwebslinger.network.domain.model.RadiusVsaVendor
import com.enterwebslinger.network.domain.model.RadiusWorkbenchResult
import com.enterwebslinger.network.domain.model.SavedAaaServerProfile
import com.enterwebslinger.network.ui.components.ExplainResultSection
import com.enterwebslinger.network.ui.screens.ai.AiAssistantViewModel
import com.enterwebslinger.network.ui.theme.BorderStrokeColor
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CardBackgroundElevated
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.DarkBackground
import com.enterwebslinger.network.ui.theme.StatusAmber
import com.enterwebslinger.network.ui.theme.StatusCrimson
import com.enterwebslinger.network.ui.theme.StatusEmerald
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary
import com.enterwebslinger.network.ui.theme.TextSecondary

@Composable
fun RadiusAuthWorkbenchTab(
    viewModel: AaaIdentityViewModel,
    selectedProfile: SavedAaaServerProfile?,
    selectedSecret: String,
    aiViewModel: AiAssistantViewModel?
) {
    val isRunning by viewModel.isRunning.collectAsState()
    val result by viewModel.radiusAuthResult.collectAsState()

    var serverIp by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("1812") }
    var secret by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf(RadiusAuthProtocol.PAP) }
    var nasIp by remember { mutableStateOf("127.0.0.1") }
    var nasId by remember { mutableStateOf("EnterwebSlinger") }
    var callingMac by remember { mutableStateOf("") }
    var calledSsid by remember { mutableStateOf("") }
    var vlanId by remember { mutableStateOf("") }
    var serviceType by remember { mutableStateOf(RadiusServiceType.FRAMED) }
    var includeMsgAuth by remember { mutableStateOf(true) }
    var includeVsa by remember { mutableStateOf(false) }
    var vsaVendor by remember { mutableStateOf(RadiusVsaVendor.MIKROTIK) }
    var vsaType by remember { mutableStateOf("8") }
    var vsaValue by remember { mutableStateOf("") }

    LaunchedEffect(selectedProfile?.id, selectedSecret) {
        if (selectedProfile != null && selectedProfile.kind == AaaProfileKind.RADIUS_AUTH) {
            serverIp = selectedProfile.host
            port = selectedProfile.port.toString()
            if (selectedSecret.isNotBlank()) secret = selectedSecret
            if (selectedProfile.username.isNotBlank()) username = selectedProfile.username
            nasIp = selectedProfile.nasIp
            nasId = selectedProfile.nasIdentifier
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("RFC 2865 RADIUS Authentication Tester", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RadiusAuthProtocol.entries.forEach { p ->
                            FilterChip(
                                selected = protocol == p,
                                onClick = { protocol = p },
                                label = { Text(p.displayName, fontSize = 11.sp) },
                                modifier = Modifier.testTag("rad_proto_${p.name}")
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AaaField(serverIp, { serverIp = it }, "RADIUS Server IP", Modifier.weight(2f).testTag("rad_auth_host"))
                        AaaField(port, { port = it }, "Port (1812/1645)", Modifier.weight(1f))
                    }
                    AaaField(secret, { secret = it }, "Shared Secret", Modifier.fillMaxWidth().testTag("rad_auth_secret"), isSecret = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AaaField(username, { username = it }, "User-Name", Modifier.weight(1f).testTag("rad_auth_user"))
                        AaaField(password, { password = it }, "User-Password", Modifier.weight(1f).testTag("rad_auth_pass"), isSecret = true)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AaaField(nasIp, { nasIp = it }, "NAS-IP-Address (4)", Modifier.weight(1f))
                        AaaField(nasId, { nasId = it }, "NAS-Identifier (32)", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AaaField(callingMac, { callingMac = it }, "Calling-Station-Id (MAC)", Modifier.weight(1f))
                        AaaField(calledSsid, { calledSsid = it }, "Called-Station-Id (SSID)", Modifier.weight(1f))
                    }

                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RadiusServiceType.entries.forEach { st ->
                            FilterChip(selected = serviceType == st, onClick = { serviceType = st }, label = { Text(st.label, fontSize = 10.sp) })
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Message-Authenticator (Attr 80 / Blast-RADIUS)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Switch(checked = includeMsgAuth, onCheckedChange = { includeMsgAuth = it })
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AaaField(vlanId, { vlanId = it }, "802.1X VLAN-ID (Optional)", Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Attach VSA", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(checked = includeVsa, onCheckedChange = { includeVsa = it })
                        }
                    }

                    if (includeVsa) {
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(RadiusVsaVendor.MIKROTIK, RadiusVsaVendor.CISCO, RadiusVsaVendor.SOPHOS, RadiusVsaVendor.FORTINET).forEach { v ->
                                FilterChip(selected = vsaVendor == v, onClick = { vsaVendor = v }, label = { Text(v.label, fontSize = 10.sp) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AaaField(vsaType, { vsaType = it }, "Sub-Type", Modifier.weight(0.6f))
                            AaaField(vsaValue, { vsaValue = it }, "VSA Value (e.g. 50M/100M)", Modifier.weight(1.4f))
                        }
                    }

                    Button(
                        onClick = {
                            val vsas = if (includeVsa && vsaValue.isNotBlank()) {
                                listOf(RadiusVsaInput(vsaVendor, vsaType.toIntOrNull() ?: 1, stringValue = vsaValue))
                            } else emptyList()
                            viewModel.runRadiusAuth(
                                RadiusAuthRequestConfig(
                                    serverIp = serverIp,
                                    port = port.toIntOrNull() ?: 1812,
                                    sharedSecret = secret,
                                    username = username,
                                    password = password,
                                    protocol = protocol,
                                    nasIpAddress = nasIp,
                                    serviceType = serviceType,
                                    callingStationId = callingMac,
                                    calledStationId = calledSsid,
                                    nasIdentifier = nasId,
                                    includeMessageAuthenticator = includeMsgAuth,
                                    customVlanId = vlanId,
                                    extraVsas = vsas
                                )
                            )
                        },
                        enabled = !isRunning && serverIp.isNotBlank() && secret.isNotBlank() && username.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("rad_send_auth_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send RADIUS ${protocol.name} Access-Request", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        result?.let { res ->
            item {
                RadiusResultCard(title = "Authentication Response", res = res, aiViewModel = aiViewModel, aiKey = "radius_auth_res")
            }
        }
    }
}

@Composable
fun RadiusAcctAndCoaTab(
    viewModel: AaaIdentityViewModel,
    selectedProfile: SavedAaaServerProfile?,
    selectedSecret: String,
    aiViewModel: AiAssistantViewModel?
) {
    val isRunning by viewModel.isRunning.collectAsState()
    val res by viewModel.radiusAcctCoaResult.collectAsState()
    val listenerStatus by viewModel.coaListenerStatus.collectAsState()
    val coaEvents by viewModel.coaEvents.collectAsState()

    var serverIp by remember { mutableStateOf("") }
    var acctPort by remember { mutableStateOf("1813") }
    var secret by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("netadmin") }
    var sessionId by remember { mutableStateOf("sess-0001") }
    var statusType by remember { mutableStateOf(RadiusAcctStatusType.START) }
    var sessionSec by remember { mutableStateOf("3600") }
    var inOctets by remember { mutableStateOf("1048576") }
    var outOctets by remember { mutableStateOf("2097152") }

    var coaTargetIp by remember { mutableStateOf("") }
    var coaPort by remember { mutableStateOf("3799") }
    var coaIsDisconnect by remember { mutableStateOf(true) }
    var coaRateLimit by remember { mutableStateOf("100M/100M") }

    var listenPort by remember { mutableStateOf("3799") }
    var listenSecret by remember { mutableStateOf("") }

    LaunchedEffect(selectedProfile?.id, selectedSecret) {
        if (selectedProfile != null && (selectedProfile.kind == AaaProfileKind.RADIUS_ACCT || selectedProfile.kind == AaaProfileKind.RADIUS_COA)) {
            serverIp = selectedProfile.host
            coaTargetIp = selectedProfile.host
            if (selectedProfile.kind == AaaProfileKind.RADIUS_ACCT) acctPort = selectedProfile.port.toString() else coaPort = selectedProfile.port.toString()
            if (selectedSecret.isNotBlank()) {
                secret = selectedSecret
                listenSecret = selectedSecret
            }
            if (selectedProfile.username.isNotBlank()) username = selectedProfile.username
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Accounting RFC 2866
        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("RFC 2866 RADIUS Accounting Tester", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RadiusAcctStatusType.entries.forEach { st ->
                            FilterChip(selected = statusType == st, onClick = { statusType = st }, label = { Text(st.label, fontSize = 10.sp) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AaaField(serverIp, { serverIp = it }, "Acct Server IP", Modifier.weight(2f))
                        AaaField(acctPort, { acctPort = it }, "Port (1813/1646)", Modifier.weight(1f))
                    }
                    AaaField(secret, { secret = it }, "Shared Secret", Modifier.fillMaxWidth(), isSecret = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AaaField(username, { username = it }, "User-Name", Modifier.weight(1f))
                        AaaField(sessionId, { sessionId = it }, "Acct-Session-Id", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AaaField(sessionSec, { sessionSec = it }, "Session-Time(s)", Modifier.weight(1f))
                        AaaField(inOctets, { inOctets = it }, "In-Octets", Modifier.weight(1f))
                        AaaField(outOctets, { outOctets = it }, "Out-Octets", Modifier.weight(1f))
                    }
                    Button(
                        onClick = {
                            viewModel.runRadiusAccounting(
                                RadiusAccountingConfig(
                                    serverIp = serverIp,
                                    port = acctPort.toIntOrNull() ?: 1813,
                                    sharedSecret = secret,
                                    username = username,
                                    statusType = statusType,
                                    sessionId = sessionId,
                                    sessionTimeSec = sessionSec.toLongOrNull() ?: 0L,
                                    inputOctets = inOctets.toLongOrNull() ?: 0L,
                                    outputOctets = outOctets.toLongOrNull() ?: 0L,
                                    terminateCause = if (statusType == RadiusAcctStatusType.STOP) 1 else null
                                )
                            )
                        },
                        enabled = !isRunning && serverIp.isNotBlank() && secret.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("rad_send_acct_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
                    ) { Text("Send Accounting-Request (${statusType.label})", fontWeight = FontWeight.Bold) }
                }
            }
        }

        // 2. Dynamic Authorization CoA / PoD Client (RFC 5176)
        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("RFC 5176 Dynamic Auth Client (CoA & PoD)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = coaIsDisconnect, onClick = { coaIsDisconnect = true }, label = { Text("Disconnect-Request (PoD 40)") })
                        FilterChip(selected = !coaIsDisconnect, onClick = { coaIsDisconnect = false }, label = { Text("CoA-Request (43)") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AaaField(coaTargetIp, { coaTargetIp = it }, "Target NAS IP", Modifier.weight(2f))
                        AaaField(coaPort, { coaPort = it }, "UDP Port (3799)", Modifier.weight(1f))
                    }
                    if (!coaIsDisconnect) {
                        AaaField(coaRateLimit, { coaRateLimit = it }, "MikroTik-Rate-Limit / CoA Override", Modifier.fillMaxWidth())
                    }
                    Button(
                        onClick = {
                            viewModel.runCoaOrDisconnect(
                                RadiusCoaConfig(
                                    targetIp = coaTargetIp,
                                    port = coaPort.toIntOrNull() ?: 3799,
                                    sharedSecret = secret,
                                    isDisconnect = coaIsDisconnect,
                                    username = username,
                                    sessionId = sessionId,
                                    vsaValue = if (!coaIsDisconnect) coaRateLimit else ""
                                )
                            )
                        },
                        enabled = !isRunning && coaTargetIp.isNotBlank() && secret.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("rad_send_coa_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = CardBackgroundElevated, contentColor = CyberCyan)
                    ) { Text(if (coaIsDisconnect) "Send Disconnect-Request (40)" else "Send CoA-Request (43)", fontWeight = FontWeight.Bold) }
                }
            }
        }

        res?.let { r ->
            item {
                RadiusResultCard(title = "Accounting / CoA Result", res = r, aiViewModel = aiViewModel, aiKey = "radius_acct_coa_res")
            }
        }

        // 3. Local NAS CoA Listener (Unprivileged Port 3799 Default)
        item {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Local NAS CoA Listener (UDP)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                        Text(
                            if (listenerStatus.isListening) "LISTENING :${listenerStatus.bindPort}" else "STOPPED",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (listenerStatus.isListening) StatusEmerald else TextMuted
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AaaField(listenPort, { listenPort = it }, "Listen Port (>=1024)", Modifier.weight(1f))
                        AaaField(listenSecret, { listenSecret = it }, "Secret (Optional)", Modifier.weight(1.4f), isSecret = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (listenerStatus.isListening) viewModel.stopCoaListener()
                                else viewModel.startCoaListener(listenPort.toIntOrNull() ?: 3799, listenSecret)
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("rad_coa_listener_toggle"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (listenerStatus.isListening) StatusCrimson else StatusEmerald,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(if (listenerStatus.isListening) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (listenerStatus.isListening) "Stop CoA Listener" else "Start NAS CoA Listener", fontWeight = FontWeight.Bold)
                        }
                        if (coaEvents.isNotEmpty()) {
                            OutlinedButton(onClick = { viewModel.clearCoaEvents() }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("Clear (${coaEvents.size})")
                            }
                        }
                    }
                }
            }
        }

        items(coaEvents, key = { it.id }) { ev ->
            Surface(color = CardBackgroundElevated, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${ev.requestCode.label} from ${ev.sourceIp}:${ev.sourcePort} -> Sent ${ev.replySentCode.label}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = CyberCyan
                    )
                    Text(
                        "Auth Valid: ${ev.authenticatorValid} | User: ${ev.username.ifBlank { "--" }} | Sess: ${ev.sessionId.ifBlank { "--" }}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = TextSecondary
                    )
                }
            }
        }
    }
}


