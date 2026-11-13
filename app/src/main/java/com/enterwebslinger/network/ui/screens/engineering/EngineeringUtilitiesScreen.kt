// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/engineering/EngineeringUtilitiesScreen.kt ===
package com.enterwebslinger.network.ui.screens.engineering

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.StatusAmber
import com.enterwebslinger.network.ui.theme.StatusCrimson
import com.enterwebslinger.network.ui.theme.StatusEmerald
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary
import com.enterwebslinger.network.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineeringUtilitiesScreen(
    viewModel: EngineeringViewModel,
    onBack: () -> Unit
) {
    val selectedTab by viewModel.selectedTab.collectAsState()

    val tabTitles = listOf(
        "MAC & ARP",
        "Subnet/VLSM/IPv6",
        "TLS Certificate",
        "Whois & ASN",
        "iPerf3 LAN Test",
        "Wi-Fi Roaming",
        "SIP VoIP Ping"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060913))
            .testTag("engineering_utilities_screen")
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Engineering Utilities",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan
                    )
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back_engineering")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = CyberCyan
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBackground)
        )

        ScrollableTabRow(
            selectedTabIndex = selectedTab.coerceIn(0, tabTitles.lastIndex),
            edgePadding = 8.dp,
            containerColor = CardBackground,
            contentColor = CyberCyan,
            divider = {}
        ) {
            tabTitles.forEachIndexed { index, title ->
                val selected = selectedTab == index
                Tab(
                    selected = selected,
                    onClick = { viewModel.setSelectedTab(index) },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) CyberCyan else TextSecondary
                        )
                    },
                    modifier = Modifier.testTag("eng_tab_$index")
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            when (selectedTab) {
                0 -> MacAndArpTab(viewModel)
                1 -> SubnetVlsmTab(viewModel)
                2 -> TlsCertTab(viewModel)
                3 -> WhoisAsnTab(viewModel)
                4 -> Iperf3Tab(viewModel)
                5 -> WifiRoamingTab(viewModel)
                6 -> SipVoipTab(viewModel)
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Tab 0: MAC OUI & ARP Table
// -----------------------------------------------------------------------------
@Composable
private fun MacAndArpTab(viewModel: EngineeringViewModel) {
    val macInput by viewModel.macInput.collectAsState()
    val macVendorResult by viewModel.macVendorResult.collectAsState()
    val arpNeighbors by viewModel.arpNeighbors.collectAsState()
    val isLoadingArp by viewModel.isLoadingArp.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = CardBackground
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Devices, contentDescription = null, tint = CyberCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("IEEE OUI & Private MAC Detector", fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = macInput,
                        onValueChange = { viewModel.setMacInput(it) },
                        label = { Text("MAC Address") },
                        placeholder = { Text("e.g. DC:2B:61:A0:12:34") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_mac_lookup"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color(0xFF2D3748)
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.resolveMacVendor() },
                        enabled = macInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("btn_resolve_mac")
                    ) {
                        Text("Lookup Vendor & Bit-1 Privacy Flag", fontWeight = FontWeight.Bold)
                    }

                    if (macVendorResult != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0B132B), RoundedCornerShape(8.dp))
                                .border(1.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text("Vendor Classification:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text(
                                    text = macVendorResult!!,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (macVendorResult!!.contains("Randomized") || macVendorResult!!.contains("Invalid")) StatusAmber else StatusEmerald,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active ARP / Neighbor Cache (/proc/net/arp)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                )
                IconButton(onClick = { viewModel.refreshArpTable() }, modifier = Modifier.testTag("btn_refresh_arp")) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh ARP", tint = CyberCyan)
                }
            }
        }

        if (isLoadingArp) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = CyberCyan)
            }
        } else if (arpNeighbors.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = CardBackground
                ) {
                    Text(
                        text = "No ARP table neighbors found or kernel neighbor table restricted.",
                        modifier = Modifier.padding(16.dp),
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(arpNeighbors) { entry ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = CardBackground
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(entry.ipAddress, fontWeight = FontWeight.Bold, color = CyberCyan, fontFamily = FontFamily.Monospace)
                            Text(entry.deviceInterface, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(entry.macAddress, style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontFamily = FontFamily.Monospace)
                            if (entry.isRandomizedMac) {
                                Text("Randomized MAC", style = MaterialTheme.typography.labelSmall, color = StatusAmber)
                            }
                        }
                        Text(entry.vendor, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Tab 1: Subnet / CIDR / VLSM / IPv6 Calculator
// -----------------------------------------------------------------------------
@Composable
private fun SubnetVlsmTab(viewModel: EngineeringViewModel) {
    val scrollState = rememberScrollState()
    val subnetIp by viewModel.subnetIpInput.collectAsState()
    val cidrPrefix by viewModel.cidrPrefixInput.collectAsState()
    val calcResult by viewModel.ipv4CalcResult.collectAsState()
    val subnetError by viewModel.subnetError.collectAsState()

    val vlsmParent by viewModel.vlsmParentCidr.collectAsState()
    val vlsmRequirements by viewModel.vlsmRequirements.collectAsState()
    val vlsmAllocations by viewModel.vlsmAllocations.collectAsState()
    val vlsmError by viewModel.vlsmError.collectAsState()

    var reqName by remember { mutableStateOf("") }
    var reqHosts by remember { mutableStateOf("") }

    val ipv6Input by viewModel.ipv6Input.collectAsState()
    val ipv6Result by viewModel.ipv6Result.collectAsState()
    val ipv6Error by viewModel.ipv6Error.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section A: IPv4 Subnet Calculator
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = CardBackground
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Calculate, contentDescription = null, tint = CyberCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("IPv4 Subnet & CIDR Calculator", fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = subnetIp,
                        onValueChange = { viewModel.setSubnetIpInput(it) },
                        label = { Text("IPv4 Address") },
                        placeholder = { Text("e.g. 192.0.2.100") },
                        modifier = Modifier
                            .weight(2f)
                            .testTag("input_subnet_ip"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color(0xFF2D3748)
                        )
                    )
                    OutlinedTextField(
                        value = cidrPrefix.toString(),
                        onValueChange = { viewModel.setCidrPrefixInput(it.toIntOrNull() ?: 24) },
                        label = { Text("Prefix (0-32)") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_cidr_prefix"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color(0xFF2D3748)
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.calculateSubnet() },
                    enabled = subnetIp.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_calc_subnet")
                ) {
                    Text("Calculate Subnet Masks & Range", fontWeight = FontWeight.Bold)
                }

                subnetError?.let { err ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(err, color = StatusCrimson, style = MaterialTheme.typography.bodySmall)
                }

                if (calcResult != null) {
                    val r = calcResult!!
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0B132B), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SubnetRow("Network Address:", r.networkAddress)
                        SubnetRow("Broadcast Address:", r.broadcastAddress)
                        SubnetRow("Subnet Mask:", r.netmask)
                        SubnetRow("Cisco Wildcard Mask:", r.wildcardMask)
                        SubnetRow("Usable Range:", "${r.firstUsableIp} - ${r.lastUsableIp}")
                        SubnetRow("Usable Hosts:", "${r.usableHosts} of ${r.totalHosts}")
                        SubnetRow("Classification:", "${r.ipClass} | ${if (r.isPrivateRfc1918) "Private (RFC 1918/CGNAT)" else "Public Internet"}")
                        SubnetRow("Binary Netmask:", r.binaryNetmask)
                    }
                }
            }
        }

        // Section B: VLSM Planner
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = CardBackground
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("VLSM Subnet Allocation Planner", fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = vlsmParent,
                    onValueChange = { viewModel.setVlsmParentCidr(it) },
                    label = { Text("Parent CIDR Block") },
                    placeholder = { Text("e.g. 192.0.2.0/24") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_vlsm_parent"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = Color(0xFF2D3748)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = reqName,
                        onValueChange = { reqName = it },
                        label = { Text("Subnet Name") },
                        placeholder = { Text("e.g. Core-VLAN") },
                        modifier = Modifier.weight(1.6f).testTag("input_vlsm_req_name"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color(0xFF2D3748)
                        )
                    )
                    OutlinedTextField(
                        value = reqHosts,
                        onValueChange = { reqHosts = it },
                        label = { Text("Hosts") },
                        placeholder = { Text("e.g. 50") },
                        modifier = Modifier.weight(1f).testTag("input_vlsm_req_hosts"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color(0xFF2D3748)
                        )
                    )
                    IconButton(
                        onClick = {
                            val h = reqHosts.toIntOrNull() ?: 0
                            if (reqName.isNotBlank() && h > 0) {
                                viewModel.addVlsmRequirement(reqName, h)
                                reqName = ""
                                reqHosts = ""
                            }
                        },
                        enabled = reqName.isNotBlank() && (reqHosts.toIntOrNull() ?: 0) > 0,
                        modifier = Modifier.size(48.dp).testTag("btn_add_vlsm_req")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Subnet Requirement", tint = CyberCyan)
                    }
                }

                if (vlsmRequirements.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    vlsmRequirements.forEachIndexed { index, (name, count) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .background(Color(0xFF0B132B), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("$name ($count hosts)", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                            IconButton(
                                onClick = { viewModel.removeVlsmRequirement(index) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove requirement", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.calculateVlsm() },
                    enabled = vlsmParent.isNotBlank() && vlsmRequirements.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_calc_vlsm")
                ) {
                    Text("Auto-Allocate Optimal VLSM Blocks", fontWeight = FontWeight.Bold)
                }

                vlsmError?.let { err ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(err, color = StatusCrimson, style = MaterialTheme.typography.bodySmall)
                }

                if (vlsmAllocations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    vlsmAllocations.forEach { alloc ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF0B132B)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(alloc.subnetName, fontWeight = FontWeight.Bold, color = CyberCyan)
                                    Text("/${alloc.cidrPrefix} (${alloc.allocatedHosts} usable)", color = StatusEmerald, fontWeight = FontWeight.Bold)
                                }
                                Text("Network: ${alloc.networkAddress} (Mask: ${alloc.netmask})", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                Text("Range: ${alloc.usableRange}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }

        // Section C: IPv6 Calculator
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = CardBackground
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("IPv6 Hex & Scope Inspector", fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = ipv6Input,
                    onValueChange = { viewModel.setIpv6Input(it) },
                    label = { Text("IPv6 Address") },
                    placeholder = { Text("e.g. 2001:db8:85a3::8a2e:370:7334") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_ipv6"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = Color(0xFF2D3748)
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.calculateIpv6() },
                    enabled = ipv6Input.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_calc_ipv6")
                ) {
                    Text("Expand & Inspect IPv6 Scope", fontWeight = FontWeight.Bold)
                }

                ipv6Error?.let { err ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(err, color = StatusCrimson, style = MaterialTheme.typography.bodySmall)
                }

                if (ipv6Result != null) {
                    val v6 = ipv6Result!!
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0B132B), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SubnetRow("Compressed:", v6.compressedFormat)
                        SubnetRow("Expanded (RFC 5952):", v6.expandedFormat)
                        SubnetRow("Address Scope:", v6.addressScope)
                        if (v6.ipv4MappedEquivalent != null) {
                            SubnetRow("IPv4-Mapped IPv6:", v6.ipv4MappedEquivalent)
                        }
                        SubnetRow("Address Capacity:", v6.totalAddressesEstimate)
                    }
                }
            }
        }
    }
}

@Composable
private fun SubnetRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold), color = TextPrimary)
    }
}

// -----------------------------------------------------------------------------
// Tab 2: TLS Certificate Inspector
// -----------------------------------------------------------------------------
@Composable
private fun TlsCertTab(viewModel: EngineeringViewModel) {
    val host by viewModel.tlsHostInput.collectAsState()
    val port by viewModel.tlsPortInput.collectAsState()
    val certInfo by viewModel.tlsCertInfo.collectAsState()
    val isLoading by viewModel.tlsLoading.collectAsState()
    val error by viewModel.tlsError.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = CardBackground
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = CyberCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SSL/TLS Handshake & Certificate Inspector", fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { viewModel.setTlsHostInput(it) },
                            label = { Text("Host or IP") },
                            placeholder = { Text("e.g. example.com") },
                            modifier = Modifier
                                .weight(3f)
                                .testTag("input_tls_host"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = Color(0xFF2D3748)
                            )
                        )
                        OutlinedTextField(
                            value = port.toString(),
                            onValueChange = { viewModel.setTlsPortInput(it.toIntOrNull() ?: 443) },
                            label = { Text("Port") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_tls_port"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = Color(0xFF2D3748)
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.inspectTlsCertificate() },
                        enabled = !isLoading && host.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("btn_inspect_tls")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF00363D), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Handshaking...")
                        } else {
                            Text("Inspect Live Certificate Chain", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (error != null) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF2B0B0B)
                ) {
                    Text(error!!, color = StatusCrimson, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (certInfo != null) {
            val c = certInfo!!
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = CardBackground
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Certificate Status", fontWeight = FontWeight.Bold, color = TextPrimary)
                            if (c.isExpired) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = StatusCrimson)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("EXPIRED", color = StatusCrimson, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = StatusEmerald)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("VALID (${c.daysUntilExpiry} days left)", color = StatusEmerald, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        SubnetRow("Negotiated TLS:", c.negotiatedProtocol)
                        SubnetRow("Cipher Suite:", c.negotiatedCipherSuite)
                        SubnetRow("Valid Until:", c.validUntil)
                        SubnetRow("Valid From:", c.validFrom)
                        SubnetRow("Serial Number:", c.serialNumberHex)
                        SubnetRow("Chain Depth:", "${c.chainLength} Certificates")

                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Subject DN:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(c.subjectDn, style = MaterialTheme.typography.bodySmall, color = CyberCyan, fontFamily = FontFamily.Monospace)

                        Text("Issuer DN:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(c.issuerDn, style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontFamily = FontFamily.Monospace)

                        if (c.subjectAlternativeNames.isNotEmpty()) {
                            Text("Subject Alternative Names (${c.subjectAlternativeNames.size}):", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(
                                c.subjectAlternativeNames.take(8).joinToString(", ") + if (c.subjectAlternativeNames.size > 8) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text("SHA-256 Fingerprint:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(c.sha256Fingerprint, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = TextPrimary, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Tab 3: Whois & BGP ASN Inspector
// -----------------------------------------------------------------------------
@Composable
private fun WhoisAsnTab(viewModel: EngineeringViewModel) {
    val queryInput by viewModel.whoisQueryInput.collectAsState()
    val whoisResult by viewModel.whoisResult.collectAsState()
    val isLoading by viewModel.whoisLoading.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = CardBackground
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Public, contentDescription = null, tint = CyberCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Raw TCP Whois & BGP ASN Inspector", fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = queryInput,
                        onValueChange = { viewModel.setWhoisQueryInput(it) },
                        label = { Text("Domain, IPv4, IPv6, or AS Number") },
                        placeholder = { Text("e.g. example.org or AS13335") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_whois_query"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color(0xFF2D3748)
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.performWhois() },
                        enabled = !isLoading && queryInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("btn_whois_query")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF00363D), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Chasing IANA Referrals...")
                        } else {
                            Text("Query Port 43 with Referral Follow", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (whoisResult != null) {
            val res = whoisResult!!
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = CardBackground
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SubnetRow("Query Target:", res.query)
                        SubnetRow("Authoritative Server:", res.serverUsed)
                        if (res.registrar.isNotEmpty()) SubnetRow("Registrar:", res.registrar)
                        if (res.creationDate.isNotEmpty()) SubnetRow("Creation Date:", res.creationDate)
                        if (res.expiryDate.isNotEmpty()) SubnetRow("Expiry Date:", res.expiryDate)
                        if (res.asnInfo.isNotEmpty()) SubnetRow("BGP ASN / Origin:", res.asnInfo)

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Raw Registry Record:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF030712), RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = res.rawText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = Color(0xFF93C5FD)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Tab 4: iPerf3 LAN Throughput Speed Test (Port 5201)
// -----------------------------------------------------------------------------
@Composable
private fun Iperf3Tab(viewModel: EngineeringViewModel) {
    val targetHost by viewModel.iperfTargetHost.collectAsState()
    val targetPort by viewModel.iperfTargetPort.collectAsState()
    val progress by viewModel.iperfProgress.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = CardBackground
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = CyberCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("iPerf3 Client LAN Speed Engine (Port 5201)", fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = targetHost,
                            onValueChange = { viewModel.setIperfTargetHost(it) },
                            label = { Text("iPerf3 Server Host/IP") },
                            placeholder = { Text("e.g. 192.0.2.50") },
                            modifier = Modifier
                                .weight(3f)
                                .testTag("input_iperf_host"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = Color(0xFF2D3748)
                            )
                        )
                        OutlinedTextField(
                            value = targetPort.toString(),
                            onValueChange = { viewModel.setIperfTargetPort(it.toIntOrNull() ?: 5201) },
                            label = { Text("Port") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_iperf_port"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = Color(0xFF2D3748)
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.startIperf3Test() },
                        enabled = !progress.isRunning && targetHost.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("btn_run_iperf3")
                    ) {
                        if (progress.isRunning) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF00363D), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Streaming TCP Data...")
                        } else {
                            Text("Start 5-Second Throughput Benchmark", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (progress.currentMbps > 0 || progress.finalAverageMbps > 0) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = CardBackground
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val displayMbps = if (progress.finalAverageMbps > 0) progress.finalAverageMbps else progress.currentMbps
                        Text(
                            text = "${"%.2f".format(displayMbps)} Mbps",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = StatusEmerald,
                                fontSize = 36.sp
                            )
                        )
                        Text(
                            text = if (progress.finalAverageMbps > 0) "Final Average LAN Speed" else "Current TCP Bitrate",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Transferred: ${(progress.totalBytesTransferred / (1024 * 1024))} MB in ${"%.1f".format(progress.elapsedSeconds)}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        if (progress.logOutput.isNotEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF030712)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("iPerf3 Protocol Log:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        progress.logOutput.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp), color = Color(0xFFA7F3D0))
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Tab 5: Wi-Fi Roaming Monitor
// -----------------------------------------------------------------------------
@Composable
private fun WifiRoamingTab(viewModel: EngineeringViewModel) {
    val roamingEvents by viewModel.roamingEvents.collectAsState(initial = emptyList())
    val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = CardBackground
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.WifiFind, contentDescription = null, tint = CyberCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("802.11k/v/r BSSID Transition Logger", fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tracks seamless AP handoffs across mesh/enterprise access points, logging transition latency in milliseconds and signal drops.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        if (roamingEvents.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = CardBackground
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No roaming transitions detected yet.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Walk between Wi-Fi access points to log live 802.11 BSSID handoff events.", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        } else {
            items(roamingEvents) { event ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = CardBackground
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(event.ssid, fontWeight = FontWeight.Bold, color = CyberCyan)
                            Text(sdf.format(Date(event.timestamp)), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${event.oldBssid}  ->  ${event.newBssid}", style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Signal: ${event.rssiDbm} dBm | ${event.frequencyMhz} MHz", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text("Handoff: ${event.transitionLatencyMs} ms", style = MaterialTheme.typography.labelSmall, color = StatusEmerald, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Tab 6: SIP OPTIONS VoIP Ping (UDP/TCP 5060)
// -----------------------------------------------------------------------------
@Composable
private fun SipVoipTab(viewModel: EngineeringViewModel) {
    val host by viewModel.sipHostInput.collectAsState()
    val port by viewModel.sipPortInput.collectAsState()
    val useTcp by viewModel.sipUseTcp.collectAsState()
    val sipResult by viewModel.sipResult.collectAsState()
    val isLoading by viewModel.sipLoading.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = CardBackground
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.PhoneInTalk, contentDescription = null, tint = CyberCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SIP OPTIONS VoIP Trunk Ping (RFC 3261)", fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { viewModel.setSipHostInput(it) },
                            label = { Text("SIP PBX / SBC Host or IP") },
                            placeholder = { Text("e.g. sip.example.org") },
                            modifier = Modifier
                                .weight(3f)
                                .testTag("input_sip_host"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = Color(0xFF2D3748)
                            )
                        )
                        OutlinedTextField(
                            value = port.toString(),
                            onValueChange = { viewModel.setSipPortInput(it.toIntOrNull() ?: 5060) },
                            label = { Text("Port") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_sip_port"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = Color(0xFF2D3748)
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Transport Protocol: ${if (useTcp) "TCP" else "UDP"}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("UDP", color = if (!useTcp) CyberCyan else TextMuted, style = MaterialTheme.typography.labelSmall)
                            Switch(
                                checked = useTcp,
                                onCheckedChange = { viewModel.setSipUseTcp(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan)
                            )
                            Text("TCP", color = if (useTcp) CyberCyan else TextMuted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.pingSipOptions() },
                        enabled = !isLoading && host.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("btn_ping_sip")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF00363D), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Transmitting OPTIONS PDU...")
                        } else {
                            Text("Send SIP OPTIONS Datagram", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (sipResult != null) {
            val res = sipResult!!
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = CardBackground
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("SIP Response Status", fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(
                                text = "${res.statusCode} ${res.statusText}",
                                color = if (res.statusCode in 200..299) StatusEmerald else if (res.statusCode in 400..499) StatusAmber else StatusCrimson,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        SubnetRow("Round-Trip Time (RTT):", if (res.rttMs >= 0) "${res.rttMs} ms" else "Timeout")
                        SubnetRow("Transport:", res.protocol)
                        if (res.serverHeader.isNotEmpty()) SubnetRow("Server User-Agent:", res.serverHeader)
                        if (res.allowMethods.isNotEmpty()) SubnetRow("Allowed Methods:", res.allowMethods)

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Raw SIP Headers:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF030712), RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = res.rawResponse,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFF93C5FD)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
