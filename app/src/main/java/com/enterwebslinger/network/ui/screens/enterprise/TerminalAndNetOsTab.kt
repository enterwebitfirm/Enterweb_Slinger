// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/enterprise/TerminalAndNetOsTab.kt ===
package com.enterwebslinger.network.ui.screens.enterprise

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.enterwebslinger.network.domain.model.DiffLineType
import com.enterwebslinger.network.domain.model.TerminalProtocol
import com.enterwebslinger.network.domain.model.VendorProfile
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.DarkBackground
import com.enterwebslinger.network.ui.theme.DividerColor
import com.enterwebslinger.network.ui.theme.StatusConnected
import com.enterwebslinger.network.ui.theme.StatusError
import com.enterwebslinger.network.ui.theme.StatusWarning
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary
import com.enterwebslinger.network.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalAndNetOsTab(viewModel: EnterpriseViewModel) {
    var subTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(12.dp)
            .testTag("terminal_net_os_tab")
    ) {
        TabRow(
            selectedTabIndex = subTab,
            containerColor = CardBackground,
            contentColor = CyberCyan,
            modifier = Modifier.fillMaxWidth().testTag("terminal_subtabs")
        ) {
            Tab(
                selected = subTab == 0,
                onClick = { subTab = 0 },
                text = { Text("Terminal", fontSize = 11.sp) },
                icon = { Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("tab_interactive_terminal")
            )
            Tab(
                selected = subTab == 1,
                onClick = { subTab = 1 },
                text = { Text("Vendor Parser", fontSize = 11.sp) },
                icon = { Icon(Icons.Default.Router, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("tab_vendor_parser")
            )
            Tab(
                selected = subTab == 2,
                onClick = { subTab = 2 },
                text = { Text("NETCONF", fontSize = 11.sp) },
                icon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("tab_netconf_rpc")
            )
            Tab(
                selected = subTab == 3,
                onClick = { subTab = 3 },
                text = { Text("Config Diff", fontSize = 11.sp) },
                icon = { Icon(Icons.Default.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("tab_config_diff")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            when (subTab) {
                0 -> InteractiveTerminalView(viewModel)
                1 -> VendorAutomationView(viewModel)
                2 -> NetconfRpcView(viewModel)
                3 -> ConfigDiffView(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveTerminalView(viewModel: EnterpriseViewModel) {
    val isConnected by viewModel.terminalConnected.collectAsState()
    val terminalBuffer by viewModel.terminalBuffer.collectAsState()
    val statusText by viewModel.terminalStatus.collectAsState()

    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var protocol by remember { mutableStateOf(TerminalProtocol.SSH) }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var inputCommand by remember { mutableStateOf("") }
    var expandedProto by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(terminalBuffer.size) {
        if (terminalBuffer.isNotEmpty()) {
            listState.animateScrollToItem(terminalBuffer.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExposedDropdownMenuBox(
                        expanded = expandedProto,
                        onExpandedChange = { expandedProto = !expandedProto },
                        modifier = Modifier.weight(1.3f)
                    ) {
                        OutlinedTextField(
                            value = protocol.displayName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProto) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedProto,
                            onDismissRequest = { expandedProto = false }
                        ) {
                            TerminalProtocol.entries.filter { it != TerminalProtocol.NETCONF }.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.displayName) },
                                    onClick = {
                                        protocol = p
                                        port = p.defaultPort.toString()
                                        expandedProto = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Host") },
                        placeholder = { Text("e.g. 192.0.2.1") },
                        singleLine = true,
                        modifier = Modifier.weight(2f).testTag("terminal_host_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                    )

                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("terminal_port_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                    )
                }

                if (protocol == TerminalProtocol.SSH) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = user,
                            onValueChange = { user = it },
                            label = { Text("User") },
                            placeholder = { Text("e.g. netadmin") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("terminal_user_input"),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                        )
                        OutlinedTextField(
                            value = pass,
                            onValueChange = { pass = it },
                            label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("terminal_pass_input"),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusText ?: "Idle",
                        fontSize = 11.sp,
                        color = if (isConnected) StatusConnected else TextMuted,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )

                    if (isConnected) {
                        OutlinedButton(
                            onClick = { viewModel.disconnectTerminal() },
                            modifier = Modifier.height(48.dp).testTag("terminal_disconnect_button")
                        ) {
                            Text("Disconnect", color = StatusError, fontSize = 11.sp)
                        }
                    } else {
                        Button(
                            onClick = {
                                val p = port.toIntOrNull() ?: protocol.defaultPort
                                viewModel.connectTerminal(host, p, protocol, user, pass)
                            },
                            enabled = host.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                            modifier = Modifier.height(48.dp).testTag("terminal_connect_button")
                        ) {
                            Text("Connect", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0F14)),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("terminal_display_card")
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(8.dp)
            ) {
                items(terminalBuffer) { line ->
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = when {
                            line.startsWith("[ERROR") || line.contains("failed", ignoreCase = true) -> StatusError
                            line.startsWith("[WARNING") -> StatusWarning
                            line.startsWith(">>") || line.endsWith("#") || line.endsWith(">") -> CyberCyan
                            else -> TextPrimary
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            QuickKeyButton("ESC") { viewModel.sendSpecialKey("\u001B") }
            QuickKeyButton("TAB") { viewModel.sendSpecialKey("\t") }
            QuickKeyButton("CTRL-C") { viewModel.sendSpecialKey("\u0003") }
            QuickKeyButton("CTRL-Z") { viewModel.sendSpecialKey("\u001A") }
            QuickKeyButton("?") { viewModel.sendTerminalInput("?") }
            QuickKeyButton("|") { viewModel.sendTerminalInput(" | ") }
            QuickKeyButton("↑") { viewModel.sendSpecialKey("\u001B[A") }
            QuickKeyButton("↓") { viewModel.sendSpecialKey("\u001B[B") }
            QuickKeyButton("CLEAR") { viewModel.clearTerminalBuffer() }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputCommand,
                onValueChange = { inputCommand = it },
                placeholder = { Text("Enter command (e.g. show version)", fontSize = 12.sp) },
                modifier = Modifier.weight(1f).testTag("terminal_command_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = DividerColor,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = {
                    if (inputCommand.isNotBlank()) {
                        viewModel.sendTerminalInput("$inputCommand\n")
                        inputCommand = ""
                    }
                },
                enabled = isConnected && inputCommand.isNotBlank(),
                modifier = Modifier.size(48.dp).background(CyberCyan, RoundedCornerShape(8.dp)).testTag("terminal_send_button")
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = DarkBackground)
            }
        }
    }
}

@Composable
fun QuickKeyButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(CardBackground, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = CyberCyan
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorAutomationView(viewModel: EnterpriseViewModel) {
    var vendor by remember { mutableStateOf(VendorProfile.CISCO_IOS) }
    var command by remember { mutableStateOf("show ip interface brief") }
    var rawText by remember { mutableStateOf("") }
    var expandedVendor by remember { mutableStateOf(false) }

    val terminalBuffer by viewModel.terminalBuffer.collectAsState()
    val parsedVer by viewModel.parsedVersion.collectAsState()
    val parsedIntf by viewModel.parsedInterfaces.collectAsState()
    val parsedRoutes by viewModel.parsedRoutes.collectAsState()
    val parsedNeigh by viewModel.parsedNeighbors.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("vendor_automation_view")
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "Vendor OS Command Parser (Cisco & Juniper)",
                    color = CyberCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = expandedVendor,
                    onExpandedChange = { expandedVendor = !expandedVendor }
                ) {
                    OutlinedTextField(
                        value = vendor.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Vendor Profile") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedVendor) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedVendor,
                        onDismissRequest = { expandedVendor = false }
                    ) {
                        VendorProfile.entries.forEach { v ->
                            DropdownMenuItem(
                                text = { Text(v.displayName) },
                                onClick = {
                                    vendor = v
                                    expandedVendor = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Target Command Parser Type:", color = TextSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = if (vendor == VendorProfile.JUNIPER_JUNOS) {
                        listOf("show version", "show interfaces terse", "show route", "show bgp summary")
                    } else {
                        listOf("show version", "show ip interface brief", "show ip route", "show cdp neighbors")
                    }
                    presets.forEach { cmdPreset ->
                        OutlinedButton(
                            onClick = { command = cmdPreset },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(
                                text = cmdPreset,
                                fontSize = 11.sp,
                                color = if (command == cmdPreset) StatusConnected else CyberCyan
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { rawText = terminalBuffer.joinToString("\n") },
                    enabled = terminalBuffer.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(
                        text = if (terminalBuffer.isNotEmpty()) {
                            "Import Live SSH/Telnet Terminal Buffer (${terminalBuffer.size} lines)"
                        } else {
                            "Connect Terminal to Capture Live Output"
                        },
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    label = { Text("Raw CLI Output ($command)") },
                    placeholder = { Text("Paste live device CLI output or import captured terminal buffer...") },
                    modifier = Modifier.fillMaxWidth().height(140.dp).testTag("vendor_raw_input"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { viewModel.parseVendorCommand(vendor, command, rawText) },
                    enabled = rawText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("vendor_parse_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = DarkBackground)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Parse Telemetry into Structured Models", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        parsedVer?.let { ver ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("parsed_version_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Hardware & Software Inventory", color = CyberCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Vendor: ${ver.vendor}", color = TextPrimary, fontSize = 12.sp)
                    Text("OS Version: ${ver.osVersion}", color = TextPrimary, fontSize = 12.sp)
                    Text("Model: ${ver.model}", color = TextPrimary, fontSize = 12.sp)
                    Text("Serial: ${ver.serialNumber}", color = TextPrimary, fontSize = 12.sp)
                    Text("Uptime: ${ver.uptime}", color = TextPrimary, fontSize = 12.sp)
                    Text("Memory: ${ver.memory}", color = TextMuted, fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (parsedIntf.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("parsed_interfaces_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Parsed Interfaces (${parsedIntf.size})", color = CyberCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    parsedIntf.forEach { intf ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(intf.interfaceName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(intf.ipAddress, color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                            Text(
                                text = "${intf.status} / ${intf.protocol}",
                                color = if (intf.status.contains("up", ignoreCase = true)) StatusConnected else StatusError,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (parsedRoutes.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("parsed_routes_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Parsed IPv4 Routing Table (${parsedRoutes.size})", color = CyberCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    parsedRoutes.forEach { r ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("${r.protocol}  ${r.network}/${r.prefixLength}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("via ${r.nextHop} (${r.interfaceName})", color = TextMuted, fontSize = 11.sp)
                            }
                            Text(r.metric, color = CyberCyan, fontSize = 11.sp)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (parsedNeigh.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("parsed_neighbors_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("CDP / LLDP Neighbors (${parsedNeigh.size})", color = CyberCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    parsedNeigh.forEach { n ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(n.deviceId, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("${n.localInterface} -> ${n.remoteInterface}", color = TextMuted, fontSize = 11.sp)
                            }
                            Text(n.platform, color = CyberCyan, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NetconfRpcView(viewModel: EnterpriseViewModel) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("830") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var rpcXml by remember {
        mutableStateOf("<rpc message-id=\"101\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n  <get-config>\n    <source>\n      <running/>\n    </source>\n  </get-config>\n</rpc>")
    }

    val result by viewModel.netconfResult.collectAsState()
    val isLoading by viewModel.isNetconfLoading.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("netconf_rpc_view")
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("NETCONF-over-SSH Client (RFC 6241 / 6242)", color = CyberCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Execute XML RPCs over subsystem 'netconf' on port 830 with standard framing.", color = TextMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Host") },
                        placeholder = { Text("e.g. 192.0.2.10") },
                        singleLine = true,
                        modifier = Modifier.weight(2f).testTag("netconf_host_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("netconf_port_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text("User") },
                        placeholder = { Text("e.g. netconf") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("netconf_user_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                    )
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("netconf_pass_input"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            rpcXml = "<rpc message-id=\"101\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n  <get-config>\n    <source>\n      <running/>\n    </source>\n  </get-config>\n</rpc>"
                        },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("<get-config>", fontSize = 11.sp, color = CyberCyan)
                    }
                    OutlinedButton(
                        onClick = {
                            rpcXml = "<rpc message-id=\"102\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n  <get-interface-information>\n    <terse/>\n  </get-interface-information>\n</rpc>"
                        },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("<get-interface-information>", fontSize = 11.sp, color = CyberCyan)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = rpcXml,
                    onValueChange = { rpcXml = it },
                    label = { Text("XML RPC Request") },
                    modifier = Modifier.fillMaxWidth().height(140.dp).testTag("netconf_rpc_input"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        val p = port.toIntOrNull() ?: 830
                        viewModel.executeNetconfRpc(host, p, user, pass, rpcXml)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("netconf_send_button"),
                    enabled = !isLoading && host.isNotBlank() && rpcXml.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DarkBackground)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Execute NETCONF RPC", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        result?.let { res ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("netconf_result_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (res.isSuccess) "RPC Succeeded (200 OK)" else "RPC Failed",
                            color = if (res.isSuccess) StatusConnected else StatusError,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "${res.executionTimeMs} ms",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    if (res.errorMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Error: ${res.errorMessage}", color = StatusError, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("XML RPC Response:", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(DarkBackground, RoundedCornerShape(6.dp))
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = res.responseXml.ifBlank { "(Empty response)" },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigDiffView(viewModel: EnterpriseViewModel) {
    var runningConfig by remember { mutableStateOf("") }
    var startupConfig by remember { mutableStateOf("") }

    val diffResult by viewModel.configDiffResult.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("config_diff_view")
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("Running vs. Startup Configuration Diff Viewer", color = CyberCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Paste live running-config and startup-config snapshots to audit configuration drift.", color = TextMuted, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Running-Config (RAM)", color = StatusConnected, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = runningConfig,
                            onValueChange = { runningConfig = it },
                            placeholder = { Text("Paste running-config...", fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth().height(160.dp).testTag("running_config_input"),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Startup-Config (NVRAM)", color = StatusWarning, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = startupConfig,
                            onValueChange = { startupConfig = it },
                            placeholder = { Text("Paste startup-config...", fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth().height(160.dp).testTag("startup_config_input"),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { viewModel.computeConfigDiff(runningConfig, startupConfig) },
                    enabled = runningConfig.isNotBlank() || startupConfig.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("compute_diff_button")
                ) {
                    Icon(Icons.Default.CompareArrows, contentDescription = null, tint = DarkBackground)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Compute Visual Line-by-Line Diff", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        diffResult?.let { diff ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("diff_result_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Diff Summary", color = CyberCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row {
                            Text("+${diff.addedCount} lines added", color = StatusConnected, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("-${diff.removedCount} lines removed", color = StatusError, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .background(Color(0xFF0D1117), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Column {
                            diff.lines.forEach { line ->
                                val (bg, fg) = when (line.type) {
                                    DiffLineType.ADDED -> Color(0xFF1E3A2B) to StatusConnected
                                    DiffLineType.REMOVED -> Color(0xFF3B1E1E) to StatusError
                                    DiffLineType.UNCHANGED -> Color.Transparent to TextSecondary
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(bg)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = line.text,
                                        color = fg,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
