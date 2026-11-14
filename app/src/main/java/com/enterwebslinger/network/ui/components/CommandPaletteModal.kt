// === FILE: app/src/main/java/com/enterwebslinger/network/ui/components/CommandPaletteModal.kt ===
package com.enterwebslinger.network.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary
import com.enterwebslinger.network.ui.theme.TextSecondary

data class CommandToolItem(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val route: String,
    val tabIndex: Int? = null,
    val icon: ImageVector
)

val AllCommandTools = listOf(
    // Dashboard & Live Telemetry
    CommandToolItem("dash_telemetry", "Network Telemetry Dashboard", "Live Metrics", "Real-time ping, jitter, gateway, Wi-Fi link speed, and DNS health", "dashboard", null, Icons.Default.Dashboard),
    CommandToolItem("dash_wifi_signal", "Wi-Fi RSSI Signal Timeline", "Live Metrics", "Live RSSI dBm signal canvas graph & speedometer", "dashboard", null, Icons.Default.Speed),
    CommandToolItem("ai_assistant_screen", "AI Network Diagnostics Engineer", "AI Diagnostics", "Dedicated network diagnostic audit & live function-calling engineer", "ai_assistant", null, Icons.Default.AutoAwesome),

    // LAN Scanner & Topology
    CommandToolItem("lan_sweep", "LAN Subnet Device Sweep", "LAN & Topology", "High-speed multi-threaded ARP and ICMP subnet discovery", "lan_scanner", 0, Icons.Default.Devices),
    CommandToolItem("lan_topology", "Zenmap Visual Topology Canvas", "LAN & Topology", "Interactive force-directed node graph of all network hosts", "lan_scanner", 1, Icons.Default.Hub),

    // Diagnostics
    CommandToolItem("diag_ping", "Ping & Jitter Engine", "Diagnostics", "ICMP/TCP/HTTP ping with min/avg/max/jitter and fallback timing", "diagnostics", 0, Icons.Default.Timeline),
    CommandToolItem("diag_trace", "Multi-Hop Visual Traceroute", "Diagnostics", "Hop-by-hop IP path analysis with latency and timeouts", "diagnostics", 1, Icons.Default.Timeline),
    CommandToolItem("diag_dns", "DNS Lookup Resolver (DoH)", "Diagnostics", "Query A, AAAA, MX, NS, CNAME, TXT, SOA, and reverse PTR records", "diagnostics", 2, Icons.Default.Dns),
    CommandToolItem("diag_whois", "WHOIS Registry Client (TCP 43)", "Diagnostics", "Domain, IP, and ASN lookups chasing authoritative IANA referrals", "diagnostics", 3, Icons.Default.Public),
    CommandToolItem("diag_hostwatch", "Host Watch Heartbeat Monitor", "Security", "Continuous ICMP/TCP availability polling with uptime history", "security_ports", 2, Icons.Default.Timeline),

    // Security & Ports
    CommandToolItem("sec_ports", "TCP Port Scanner & Banner Grabber", "Security", "Top 100, Web/Admin, and custom port sweeps with service banners", "security_ports", 0, Icons.Default.Security),
    CommandToolItem("sec_wol", "Wake-on-LAN Magic Packet Sender", "Security", "102-byte UDP 7/9 broadcast with Room-persisted MAC profiles", "security_ports", 1, Icons.Default.Security),

    // Wi-Fi Speed & RF
    CommandToolItem("wifi_speedtest", "Wi-Fi Throughput Speed Test", "Wi-Fi & Speed", "Measure download and upload bandwidth in Mbps", "wifi_speed", 0, Icons.Default.Speed),
    CommandToolItem("wifi_rf_channels", "RF Channel Congestion Analyzer", "Wi-Fi & Speed", "2.4 GHz, 5 GHz, and 6 GHz spectrum channel utilization graphs", "wifi_speed", 1, Icons.Default.Speed),

    // Enterprise NOC & NMS (matches EnterpriseTabs 0..7)
    CommandToolItem("noc_snmp", "SNMP Manager, Traps & MIB Browser", "Enterprise NOC", "SNMPv1/v2c/v3 USM subtree walk, GET, MIB-II counters, UDP 1162 trap receiver", "enterprise_noc", 0, Icons.Default.Hub),
    CommandToolItem("noc_flow", "NetFlow, IPFIX & sFlow Collector", "Enterprise NOC", "Decode binary NetFlow v5/v9, IPFIX, and sFlow v5 packets on UDP 2055/4739/6343", "enterprise_noc", 1, Icons.Default.Timeline),
    CommandToolItem("noc_syslog", "Syslog Daemon (RFC 3164/5424)", "Enterprise NOC", "UDP 1514 / TCP 6514 syslog server capturing real-time facility/severity logs", "enterprise_noc", 2, Icons.Default.Terminal),
    CommandToolItem("noc_deep_audit", "Deep Audit & Visual Topology Map", "Enterprise NOC", "Service banner grabbing, OS heuristics, TLS inspection & radial topology map", "enterprise_noc", 3, Icons.Default.Hub),
    CommandToolItem("noc_librenms", "LibreNMS & gNMI Telemetry Client", "Enterprise NOC", "Connect to LibreNMS REST API or gNMI OpenConfig streaming routers", "enterprise_noc", 4, Icons.Default.Dns),
    CommandToolItem("noc_file_transfer", "Dual-Pane FTP, FTPS, SFTP & TFTP", "Enterprise NOC", "FileZilla-style local/remote dual pane file manager & RFC 1350 TFTP client", "enterprise_noc", 5, Icons.Default.Dns),
    CommandToolItem("noc_terminal", "Network OS Automation Terminal", "Enterprise NOC", "SSHv2, Telnet & NETCONF client for Cisco IOS, NX-OS & Juniper Junos", "enterprise_noc", 6, Icons.Default.Terminal),
    CommandToolItem("noc_protocols", "Core Network Protocols Inspector", "Enterprise NOC", "DHCP Rogue Scanner (UDP 67/68), ToD RFC 868, SNTP & SMTP Diagnostics", "enterprise_noc", 7, Icons.Default.Sensors),

    // Data Center & Bare-Metal
    CommandToolItem("dc_redfish", "DMTF Redfish Bare-Metal Manager", "Data Center", "Inspect iDRAC, iLO, Supermicro, XClarity BMCs and power controls", "data_center", 0, Icons.Default.Dns),
    CommandToolItem("dc_ipmi", "IPMI v2.0 / RMCP+ UDP Ping (623)", "Data Center", "ASF presence ping and channel capability probe", "data_center", 1, Icons.Default.Sensors),
    CommandToolItem("dc_vsphere", "VMware vSphere & ESXi Client", "Data Center", "Connect to vCenter REST API to list VMs, hosts, and CPU/RAM usage", "data_center", 2, Icons.Default.Dns),
    CommandToolItem("dc_hyperv", "Hyper-V & WinRM WS-Management", "Data Center", "Windows Admin Center and Hyper-V VM inspect over WS-Man", "data_center", 3, Icons.Default.Dns),
    CommandToolItem("dc_cucm", "Cisco CUCM & IP Phone Manager", "Data Center", "AXL SOAP client and IP Phone XML execution", "data_center", 4, Icons.Default.PhoneInTalk),

    // VPN Tunnel Workbench & PCAP
    CommandToolItem("vpn_wireguard", "WireGuard Tunnel Client", "VPN Workbench", "Import .conf or QR codes for high-speed userspace WireGuard TUN", "vpn_workbench", 0, Icons.Default.VpnKey),
    CommandToolItem("vpn_wg_keygen", "WireGuard Keygen, QR & AllowedIPs", "VPN Workbench", "Generate Curve25519 keys, compute CIDR AllowedIPs & import QR/PKCS#12", "vpn_workbench", 1, Icons.Default.VpnKey),
    CommandToolItem("vpn_openvpn", "OpenVPN & PKCS#12 Tunnel Client", "VPN Workbench", "Parse .ovpn w/ tls-crypt/SHA512 & unlock .p12/.pfx X.509 bundles", "vpn_workbench", 1, Icons.Default.VpnKey),
    CommandToolItem("vpn_ipsec", "Native Android IKEv2 / IPsec (PSK/EAP/RSA)", "VPN Workbench", "Hardware-accelerated OS-level IKEv2 provisioning with X.509 RSA", "vpn_workbench", 1, Icons.Default.VpnKey),
    CommandToolItem("vpn_sstp", "AnyConnect, FortiClient, SSTP & L2TP Probes", "VPN Workbench", "HTTPS <config-auth>, /remote/info, SSTP_DUPLEX_POST & L2TP portal probes", "vpn_workbench", 2, Icons.Default.VpnKey),
    CommandToolItem("vpn_ike_probe", "IKEv2 SA_INIT (UDP 500 & NAT-T 4500)", "VPN Workbench", "Live RFC 7296/3948 peer handshake & NO_PROPOSAL_CHOSEN analyzer", "vpn_workbench", 2, Icons.Default.Lock),
    CommandToolItem("vpn_mss_calc", "Tunnel Overhead & TCP MSS Calculator", "VPN Workbench", "Compute IPsec/XFRM/GRE/WireGuard/VXLAN MTU & ip tcp adjust-mss", "vpn_workbench", 2, Icons.Default.Calculate),
    CommandToolItem("vpn_leak_audit", "DNS, IPv6, Route Table & Per-App Audit", "VPN Workbench", "Inspect active routes, IPv6 leak risk & Per-App split-tunnel bypass", "vpn_workbench", 3, Icons.Default.Security),
    CommandToolItem("pcap_sniffer", "Local TUN PCAP Sniffer (Wireshark)", "VPN Workbench", "Capture L3/L4 headers into LINKTYPE_RAW .pcap and export via FileProvider", "vpn_workbench", 4, Icons.Default.Terminal),

    // Multi-Vendor Firewall Hub
    CommandToolItem("fw_hub_overview", "Multi-Vendor Firewall Hub", "Firewall Hub", "Sophos XG/XGS, FortiGate, Palo Alto PAN-OS, Cisco ASA & pfSense/OPNsense", "firewall_hub", 0, Icons.Default.Security),
    CommandToolItem("fw_ipsec_xfrm", "Firewall IPsec / XFRM & Bounce Control", "Firewall Hub", "Monitor tunnel SPIs & bounce/toggle Sophos or FortiGate Phase-2 SAs", "firewall_hub", 1, Icons.Default.VpnKey),
    CommandToolItem("fw_policy_packet", "PAN-OS Policy Match & ASA Packet-Tracer", "Firewall Hub", "Interactive security/NAT rule match, ASA packet-tracer & FortiGate zero-hit audit", "firewall_hub", 2, Icons.Default.Terminal),
    CommandToolItem("fw_drops_backup", "Sophos drppkt Logs & Config Backup", "Firewall Hub", "Live Sophos packet drop filter & FortiGate/SFOS configuration backups", "firewall_hub", 3, Icons.Default.Dns),

    // AAA, RADIUS, TACACS+ & Active Directory Workbench
    CommandToolItem("aaa_radius_auth_vsa", "RADIUS Auth Workbench (PAP/CHAP/MS-CHAPv2)", "AAA & Identity", "RFC 2865 Auth + Message-Authenticator (80), 802.1X VLAN & MikroTik/Cisco/Sophos/Forti VSAs", "aaa_identity", 0, Icons.Default.Lock),
    CommandToolItem("aaa_radius_acct_coa", "RADIUS Accounting, CoA/PoD & Local Listener", "AAA & Identity", "RFC 2866 Start/Stop/Interim, RFC 5176 CoA/Disconnect & UDP 3799 NAS CoA listener", "aaa_identity", 1, Icons.Default.Timeline),
    CommandToolItem("aaa_tacacs_rfc8907", "TACACS+ Client (RFC 8907 TCP 49)", "AAA & Identity", "MD5 obfuscated Authentication & Cisco/Juniper Command Authorization testing", "aaa_identity", 2, Icons.Default.Terminal),
    CommandToolItem("aaa_ldap_ad", "LDAP & Active Directory Tester (389 / 636)", "AAA & Identity", "RFC 4511 ASN.1 BER Simple/UPN Bind & BaseDN SearchResult memberOf inspector", "aaa_identity", 3, Icons.Default.Security),

    // IoT & API Workbench
    CommandToolItem("iot_mqtt", "MQTT 3.1.1 / 5.0 Workbench", "IoT & APIs", "Subscribe and publish to MQTT brokers with QoS levels", "iot_api_workbench", 0, Icons.Default.Sensors),
    CommandToolItem("iot_coap", "CoAP Client (RFC 7252 UDP 5683)", "IoT & APIs", "Send CoAP GET/POST/PUT/DELETE datagrams to IoT devices", "iot_api_workbench", 1, Icons.Default.Sensors),
    CommandToolItem("iot_api_tester", "Universal REST & SOAP API Tester", "IoT & APIs", "Execute HTTP REST calls, SOAP 1.1/1.2 XML envelopes, inspect headers", "iot_api_workbench", 2, Icons.Default.Public),

    // RF Survey, BLE, Cellular & Security Audit
    CommandToolItem("rf_floorplan_heatmap", "Wi-Fi Floorplan Signal Heatmap", "RF & Audit", "Interactive 2D RSSI pin mapping with step walk sensor & PNG export", "rf_field", 0, Icons.Default.WifiFind),
    CommandToolItem("ble_beacon_scanner", "BLE Bluetooth Beacon Scanner", "RF & Audit", "Scan advertising service UUIDs, TX power, RSSI & distance estimates", "rf_field", 1, Icons.Default.Sensors),
    CommandToolItem("cellular_5g_lte", "5G NR / LTE Cellular RF Telemetry", "RF & Audit", "Inspect NR/LTE NCI, PCI, TAC, ARFCN, RSRP, RSRQ, SINR & neighbors", "rf_field", 2, Icons.Default.Sensors),
    CommandToolItem("onvif_camera_discovery", "ONVIF & RTSP Camera Discovery", "RF & Audit", "WS-Discovery (239.255.255.250:3702) & TCP 554/8000/37777 probe", "rf_field", 3, Icons.Default.Security),
    CommandToolItem("evil_twin_auditor", "Evil Twin & Rogue AP Security Auditor", "RF & Audit", "Detect unknown BSSIDs, WPA3 downgrades & hidden overlapping channels", "rf_field", 3, Icons.Default.Security),

    // ISP & Protocol Pro Toolkit
    CommandToolItem("pmtud_auditor", "Path MTU Discovery (PMTUD) & MSS", "Pro Tools", "Binary search 576–1500 B DF ping & WireGuard/PPPoE/IPsec/VXLAN MSS", "pro_toolkit", 0, Icons.Default.Speed),
    CommandToolItem("dns_dnssec_benchmark", "Concurrent DNS & DNSSEC Benchmark", "Pro Tools", "Race Active DNS, 1.1.1.1, 8.8.8.8, 9.9.9.9, OpenDNS with DO/AD check", "pro_toolkit", 1, Icons.Default.Dns),
    CommandToolItem("happy_eyeballs_alpn", "Happy Eyeballs & ALPN h2/h3 Tester", "Pro Tools", "RFC 8305 IPv6 vs IPv4 connect race, TLS ALPN h2 & Alt-Svc h3 inspection", "pro_toolkit", 1, Icons.Default.Public),
    CommandToolItem("aaa_radius_tacacs", "RADIUS & TACACS+ AAA Tester", "Pro Tools", "RFC 2865 Access-Request + VSA (Type 26) & TACACS+ TCP 49 probe", "pro_toolkit", 2, Icons.Default.Lock),
    CommandToolItem("batch_ssh_traffic_stats", "Multi-Host SSH Batch & TrafficStats", "Pro Tools", "Concurrent SSHJ automation + config diff + real-time Rx/Tx byte/pkt chart", "pro_toolkit", 3, Icons.Default.Terminal),
    CommandToolItem("scheduled_audit_worker", "Background Network Audit Worker", "Pro Tools", "WorkManager 15m–6h monitor alerting on rogue MACs & offline watched hosts", "pro_toolkit", 4, Icons.Default.Timeline),

    // Engineering Utilities Hub
    CommandToolItem("eng_mac_arp", "IEEE OUI MAC & ARP Cache Inspector", "Engineering Utilities", "Lookup MAC vendor, detect randomized MAC bit-1, read /proc/net/arp", "engineering_utils", 0, Icons.Default.Devices),
    CommandToolItem("eng_subnet", "IPv4 Subnet & CIDR Calculator", "Engineering Utilities", "Calculate network, broadcast, wildcard mask, usable IP range", "engineering_utils", 1, Icons.Default.Calculate),
    CommandToolItem("eng_vlsm", "VLSM Subnet Allocation Planner", "Engineering Utilities", "Auto-divide CIDR block into optimal variable-length subnets", "engineering_utils", 1, Icons.Default.Calculate),
    CommandToolItem("eng_ipv6", "IPv6 RFC 5952 & Scope Inspector", "Engineering Utilities", "Expand/compress IPv6, detect Link-Local, ULA, IPv4-mapped", "engineering_utils", 1, Icons.Default.Calculate),
    CommandToolItem("eng_tls_cert", "Live SSL/TLS Handshake Inspector", "Engineering Utilities", "Inspect X.509 cert chain, SANs, serial, expiry, cipher suite", "engineering_utils", 2, Icons.Default.Lock),
    CommandToolItem("eng_whois", "Raw TCP Whois & BGP ASN (Port 43)", "Engineering Utilities", "Query port 43 with IANA referral chasing and RIPE ASN lookup", "engineering_utils", 3, Icons.Default.Public),
    CommandToolItem("eng_iperf3", "iPerf3 LAN Speed Engine (Port 5201)", "Engineering Utilities", "Run 5-second TCP throughput benchmark against iPerf3 server", "engineering_utils", 4, Icons.Default.Speed),
    CommandToolItem("eng_wifi_roam", "802.11 BSSID Transition Monitor", "Engineering Utilities", "Log AP handoff events, transition latency ms, signal drop", "engineering_utils", 5, Icons.Default.WifiFind),
    CommandToolItem("eng_sip_ping", "SIP OPTIONS VoIP Trunk Ping (5060)", "Engineering Utilities", "RFC 3261 OPTIONS ping to SIP PBX/SBC over UDP/TCP", "engineering_utils", 6, Icons.Default.PhoneInTalk)
)

@Composable
fun CommandPaletteModal(
    onDismiss: () -> Unit,
    onSelectTool: (CommandToolItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredTools = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            AllCommandTools
        } else {
            val q = searchQuery.trim().lowercase()
            AllCommandTools.filter {
                it.name.lowercase().contains(q) ||
                it.category.lowercase().contains(q) ||
                it.description.lowercase().contains(q)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF070B16),
            tonalElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Command Palette",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan
                            )
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("btn_close_palette")) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search 40+ networking tools, protocols, RFCs...", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_palette_search"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = Color(0xFF1E293B),
                        focusedContainerColor = CardBackground,
                        unfocusedContainerColor = CardBackground
                    ),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = CyberCyan)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "${filteredTools.size} Tools Available",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredTools) { tool ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectTool(tool)
                                    onDismiss()
                                }
                                .testTag("palette_item_${tool.id}"),
                            shape = RoundedCornerShape(10.dp),
                            color = CardBackground
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Color(0xFF0F172A), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = tool.icon,
                                        contentDescription = null,
                                        tint = CyberCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = tool.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                        )
                                        Text(
                                            text = tool.category,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = CyberCyan
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = tool.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 1
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
