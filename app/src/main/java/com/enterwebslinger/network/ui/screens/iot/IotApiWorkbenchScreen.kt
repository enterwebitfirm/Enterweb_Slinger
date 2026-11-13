// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/iot/IotApiWorkbenchScreen.kt ===
package com.enterwebslinger.network.ui.screens.iot

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.enterwebslinger.network.domain.model.CoapMethod
import com.enterwebslinger.network.domain.model.HttpMethod
import com.enterwebslinger.network.domain.model.MqttQos
import com.enterwebslinger.network.domain.model.MqttVersion
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CardBackgroundElevated
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.DarkBackground
import com.enterwebslinger.network.ui.theme.StatusConnected
import com.enterwebslinger.network.ui.theme.StatusError
import com.enterwebslinger.network.ui.theme.StatusWarning
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary
import com.enterwebslinger.network.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IotApiWorkbenchScreen(
    viewModel: IotApiWorkbenchViewModel,
    onBack: (() -> Unit)? = null
) {
    val selectedTab by viewModel.selectedTab.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = "IoT & APIs",
                        tint = CyberCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "IoT & API Protocol Workbench",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("iot_api_back_button")
                    ) {
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
            selectedTabIndex = selectedTab.coerceIn(0, 3),
            containerColor = CardBackground,
            contentColor = CyberCyan,
            edgePadding = 16.dp,
            divider = {}
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { viewModel.setSelectedTab(0) },
                text = { Text("MQTT 5.0") },
                modifier = Modifier.testTag("tab_mqtt")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { viewModel.setSelectedTab(1) },
                text = { Text("CoAP (RFC 7252)") },
                modifier = Modifier.testTag("tab_coap")
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { viewModel.setSelectedTab(2) },
                text = { Text("REST Waterfall") },
                modifier = Modifier.testTag("tab_rest")
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { viewModel.setSelectedTab(3) },
                text = { Text("SOAP & WSDL") },
                modifier = Modifier.testTag("tab_soap")
            )
        }

        when (selectedTab) {
            0 -> MqttTabContent(viewModel = viewModel)
            1 -> CoapTabContent(viewModel = viewModel)
            2 -> RestTabContent(viewModel = viewModel)
            3 -> SoapTabContent(viewModel = viewModel)
        }
    }
}

// =============================================================================
// TAB 1: MQTT 3.1.1 & 5.0 Content
// =============================================================================
@Composable
private fun MqttTabContent(viewModel: IotApiWorkbenchViewModel) {
    val isConnected by viewModel.isMqttConnected.collectAsState()
    val status by viewModel.mqttStatus.collectAsState()
    val messages by viewModel.mqttMessages.collectAsState()
    val subscriptions by viewModel.mqttSubscribedTopics.collectAsState()

    val host by viewModel.mqttHost.collectAsState()
    val port by viewModel.mqttPort.collectAsState()
    val clientId by viewModel.mqttClientId.collectAsState()
    val username by viewModel.mqttUsername.collectAsState()
    val password by viewModel.mqttPassword.collectAsState()
    val useTls by viewModel.mqttUseTls.collectAsState()
    val version by viewModel.mqttVersion.collectAsState()

    val pubTopic by viewModel.mqttPublishTopic.collectAsState()
    val pubPayload by viewModel.mqttPublishPayload.collectAsState()
    val pubQos by viewModel.mqttPublishQos.collectAsState()
    val pubRetain by viewModel.mqttPublishRetain.collectAsState()
    val newSubTopic by viewModel.mqttNewSubTopic.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Connection Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mqtt_connection_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Broker Connection",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(if (isConnected) StatusConnected else StatusError, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val isErr = status.contains("error", ignoreCase = true) || status.contains("rejected", ignoreCase = true)
                            Text(
                                text = status,
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    isConnected -> StatusConnected
                                    isErr -> StatusError
                                    else -> TextMuted
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { viewModel.mqttHost.value = it },
                            label = { Text("Broker Host") },
                            placeholder = { Text("e.g. mqtt.example.local") },
                            modifier = Modifier
                                .weight(2f)
                                .testTag("mqtt_host_input"),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { viewModel.mqttPort.value = it },
                            label = { Text("Port") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mqtt_port_input"),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = clientId,
                        onValueChange = { viewModel.mqttClientId.value = it },
                        label = { Text("Client Identifier (Optional)") },
                        placeholder = { Text("Auto-generated if blank") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mqtt_client_id_input"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { viewModel.mqttUsername.value = it },
                            label = { Text("Username (Opt)") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mqtt_username_input"),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { viewModel.mqttPassword.value = it },
                            label = { Text("Password (Opt)") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mqtt_password_input"),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Use TLS (MQTTS / Port 8883)", fontSize = 12.sp, color = TextSecondary)
                        Switch(
                            checked = useTls,
                            onCheckedChange = { enabled ->
                                viewModel.mqttUseTls.value = enabled
                                if (enabled && viewModel.mqttPort.value == "1883") {
                                    viewModel.mqttPort.value = "8883"
                                } else if (!enabled && viewModel.mqttPort.value == "8883") {
                                    viewModel.mqttPort.value = "1883"
                                }
                            },
                            modifier = Modifier.testTag("mqtt_tls_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.mqttVersion.value = if (version == MqttVersion.MQTT_5_0) MqttVersion.MQTT_3_1_1 else MqttVersion.MQTT_5_0
                                },
                                modifier = Modifier.heightIn(min = 48.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (version == MqttVersion.MQTT_5_0) "Protocol: MQTT 5.0" else "Protocol: MQTT 3.1.1", fontSize = 11.sp)
                            }
                        }

                        Button(
                            onClick = {
                                if (isConnected) viewModel.disconnectMqtt() else viewModel.connectMqtt()
                            },
                            enabled = isConnected || host.isNotBlank(),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("mqtt_connect_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isConnected) StatusError else CyberCyan,
                                contentColor = if (isConnected) Color.White else Color(0xFF00363D)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (isConnected) "Disconnect" else "Connect", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Publish Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mqtt_publish_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Publish Telemetry",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = pubTopic,
                        onValueChange = { viewModel.mqttPublishTopic.value = it },
                        label = { Text("Topic") },
                        placeholder = { Text("e.g. sensors/rack-01/temp") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mqtt_pub_topic_input"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = pubPayload,
                        onValueChange = { viewModel.mqttPublishPayload.value = it },
                        label = { Text("Payload (JSON / Bytes)") },
                        placeholder = { Text("e.g. {\"temp\": 22.5}") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .testTag("mqtt_pub_payload_input"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.mqttPublishQos.value = when (pubQos) {
                                        MqttQos.AT_MOST_ONCE -> MqttQos.AT_LEAST_ONCE
                                        MqttQos.AT_LEAST_ONCE -> MqttQos.EXACTLY_ONCE
                                        MqttQos.EXACTLY_ONCE -> MqttQos.AT_MOST_ONCE
                                    }
                                },
                                modifier = Modifier.heightIn(min = 48.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("QoS ${pubQos.value}", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = { viewModel.mqttPublishRetain.value = !pubRetain },
                                modifier = Modifier.heightIn(min = 48.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (pubRetain) "Retain: ON" else "Retain: OFF", fontSize = 11.sp)
                            }
                        }

                        Button(
                            onClick = { viewModel.publishMqtt() },
                            enabled = isConnected && pubTopic.isNotBlank(),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("mqtt_publish_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Publish", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Subscriptions & Live Feed Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mqtt_subscriptions_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Subscribe to Topics",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newSubTopic,
                            onValueChange = { viewModel.mqttNewSubTopic.value = it },
                            label = { Text("Topic Filter") },
                            placeholder = { Text("e.g. sensors/#") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mqtt_sub_input"),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Button(
                            onClick = { viewModel.subscribeMqtt() },
                            enabled = isConnected && newSubTopic.isNotBlank(),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("mqtt_subscribe_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Subscribe")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        subscriptions.forEach { sub ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = CardBackgroundElevated
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = sub, style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Unsubscribe $sub",
                                        tint = TextMuted,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { viewModel.unsubscribeMqtt(sub) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Messages Feed
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Messages (${messages.size})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                TextButton(onClick = { viewModel.clearMqttMessages() }) {
                    Text("Clear Feed", fontSize = 11.sp, color = TextSecondary)
                }
            }
        }

        items(messages) { msg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = msg.topic,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = CyberCyan
                        )
                        Text(
                            text = "QoS ${msg.qos.value}${if (msg.isRetained) " (Retain)" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        color = DarkBackground
                    ) {
                        Text(
                            text = msg.payloadString,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = TextPrimary,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// TAB 2: CoAP RFC 7252 Content
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoapTabContent(viewModel: IotApiWorkbenchViewModel) {
    val host by viewModel.coapHost.collectAsState()
    val port by viewModel.coapPort.collectAsState()
    val method by viewModel.coapMethod.collectAsState()
    val uriPath by viewModel.coapUriPath.collectAsState()
    val payload by viewModel.coapPayload.collectAsState()
    val isObserving by viewModel.isCoapObserving.collectAsState()
    val status by viewModel.coapStatus.collectAsState()
    val resources by viewModel.coapDiscoveredResources.collectAsState()
    val lastResponse by viewModel.lastCoapResponse.collectAsState()

    var methodExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("coap_request_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CoAP Request Builder",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { viewModel.coapHost.value = it },
                            label = { Text("CoAP Server") },
                            placeholder = { Text("e.g. coap.example.local") },
                            modifier = Modifier
                                .weight(2f)
                                .testTag("coap_host_input"),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { viewModel.coapPort.value = it },
                            label = { Text("Port") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("coap_port_input"),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = methodExpanded,
                            onExpandedChange = { methodExpanded = !methodExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = method.name,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Method") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = methodExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .testTag("coap_method_dropdown"),
                                shape = RoundedCornerShape(8.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = methodExpanded,
                                onDismissRequest = { methodExpanded = false }
                            ) {
                                CoapMethod.values().forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m.name) },
                                        onClick = {
                                            viewModel.coapMethod.value = m
                                            methodExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = uriPath,
                            onValueChange = { viewModel.coapUriPath.value = it },
                            label = { Text("Uri-Path") },
                            placeholder = { Text("e.g. sensors/temp") },
                            modifier = Modifier
                                .weight(2f)
                                .testTag("coap_uri_path_input"),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = payload,
                        onValueChange = { viewModel.coapPayload.value = it },
                        label = { Text("Payload (Optional for POST/PUT)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(70.dp)
                            .testTag("coap_payload_input"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = isObserving,
                                onCheckedChange = { viewModel.isCoapObserving.value = it }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Observe (RFC 7641)", fontSize = 12.sp, color = TextSecondary)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.discoverCoapResources() },
                                enabled = host.isNotBlank(),
                                modifier = Modifier.heightIn(min = 48.dp).testTag("coap_discover_button"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Discover", fontSize = 11.sp)
                            }

                            Button(
                                onClick = { viewModel.executeCoapRequest() },
                                enabled = host.isNotBlank(),
                                modifier = Modifier.heightIn(min = 48.dp).testTag("coap_send_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Send", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        status?.let { st ->
            item {
                Surface(
                    color = CardBackgroundElevated,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = st,
                        style = MaterialTheme.typography.bodySmall,
                        color = CyberCyan,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Response Card
        lastResponse?.let { resp ->
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("coap_response_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CoAP Response",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )

                            val badgeColor = if (resp.isSuccess) StatusConnected else StatusError
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = badgeColor.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "${resp.responseCode} (${resp.roundTripTimeMs} ms)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = badgeColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Msg ID: 0x${resp.messageId.toString(16).uppercase()} | Type: ${resp.type} | Format: ${resp.contentType}${resp.observeSequence?.let { " | Obs #$it" } ?: ""}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = DarkBackground
                        ) {
                            Text(
                                text = resp.payload.ifBlank { "[Empty Response Body]" },
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = TextPrimary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        if (resources.isNotEmpty()) {
            item {
                Text(
                    text = "Discovered Resources (/.well-known/core)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
            }

            items(resources) { res ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.coapUriPath.value = res.path.removePrefix("/") },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = res.path, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = CyberCyan)
                            if (res.title.isNotBlank()) {
                                Text(text = res.title, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                        }
                        if (res.isObservable) {
                            Surface(shape = RoundedCornerShape(4.dp), color = CardBackgroundElevated) {
                                Text("Observable", fontSize = 10.sp, color = StatusConnected, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// TAB 3: REST & Timing Waterfall Content
// =============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestTabContent(viewModel: IotApiWorkbenchViewModel) {
    val url by viewModel.restUrl.collectAsState()
    val method by viewModel.restMethod.collectAsState()
    val body by viewModel.restRequestBody.collectAsState()
    val ignoreSsl by viewModel.restIgnoreSslErrors.collectAsState()
    val isLoading by viewModel.isRestLoading.collectAsState()
    val response by viewModel.restResponse.collectAsState()

    var methodExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rest_request_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "REST Client & Timing Inspector",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = methodExpanded,
                            onExpandedChange = { methodExpanded = !methodExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = method.name,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = methodExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .testTag("rest_method_dropdown"),
                                shape = RoundedCornerShape(8.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = methodExpanded,
                                onDismissRequest = { methodExpanded = false }
                            ) {
                                HttpMethod.values().forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m.name) },
                                        onClick = {
                                            viewModel.restMethod.value = m
                                            methodExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = url,
                            onValueChange = { viewModel.restUrl.value = it },
                            label = { Text("Endpoint URL") },
                            placeholder = { Text("e.g. https://api.example.com/health") },
                            modifier = Modifier
                                .weight(2.5f)
                                .testTag("rest_url_input"),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    if (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = body,
                            onValueChange = { viewModel.restRequestBody.value = it },
                            label = { Text("Request Body (JSON)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .testTag("rest_body_input"),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = ignoreSsl,
                                onCheckedChange = { viewModel.restIgnoreSslErrors.value = it },
                                modifier = Modifier.testTag("rest_ignore_ssl_switch")
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Allow Self-Signed TLS", fontSize = 11.sp, color = TextSecondary)
                        }

                        Button(
                            onClick = { viewModel.executeRestRequest() },
                            enabled = !isLoading && url.isNotBlank(),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("rest_send_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF00363D))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Executing...")
                            } else {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Send Request", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Response & Timing Waterfall
        response?.let { resp ->
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rest_response_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Response Metrics",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (resp.statusCode in 200..299) StatusConnected.copy(alpha = 0.2f) else StatusError.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "${resp.statusCode} ${resp.statusMessage} (${resp.timing.totalDurationMs} ms)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (resp.statusCode in 200..299) StatusConnected else StatusError,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Timing Waterfall Breakdown Bars
                        val tf = resp.timing
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "TIMING WATERFALL BREAKDOWN",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        WaterfallBar(label = "DNS Resolution", ms = tf.dnsLookupMs, totalMs = tf.totalDurationMs, barColor = CyberCyan)
                        WaterfallBar(label = "TCP Connect", ms = tf.tcpHandshakeMs, totalMs = tf.totalDurationMs, barColor = StatusConnected)
                        WaterfallBar(label = "TLS Handshake", ms = tf.tlsHandshakeMs, totalMs = tf.totalDurationMs, barColor = StatusWarning)
                        WaterfallBar(label = "Time to First Byte (TTFB)", ms = tf.ttfbMs, totalMs = tf.totalDurationMs, barColor = Color(0xFF8B5CF6))
                        WaterfallBar(label = "Content Download", ms = tf.downloadDurationMs, totalMs = tf.totalDurationMs, barColor = Color(0xFFEC4899))

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "RESPONSE PAYLOAD",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = DarkBackground
                        ) {
                            Text(
                                text = resp.body.take(1500),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = TextPrimary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WaterfallBar(label: String, ms: Long, totalMs: Long, barColor: Color) {
    val fraction = if (totalMs > 0) (ms.toFloat() / totalMs.toFloat()).coerceIn(0.05f, 1f) else 0.1f

    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(text = "$ms ms", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextPrimary)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(CardBackgroundElevated)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(barColor)
            )
        }
    }
}

// =============================================================================
// TAB 4: SOAP 1.1/1.2 & WSDL Inspector Content
// =============================================================================
@Composable
private fun SoapTabContent(viewModel: IotApiWorkbenchViewModel) {
    val wsdlUrl by viewModel.wsdlUrl.collectAsState()
    val soapEndpoint by viewModel.soapEndpointUrl.collectAsState()
    val soapAction by viewModel.soapActionHeader.collectAsState()
    val soapPayload by viewModel.soapXmlPayload.collectAsState()
    val soap12 by viewModel.soapVersion12.collectAsState()
    val soapIgnoreSsl by viewModel.soapIgnoreSslErrors.collectAsState()
    val isLoading by viewModel.isSoapLoading.collectAsState()
    val services by viewModel.wsdlServices.collectAsState()
    val result by viewModel.soapResult.collectAsState()
    val xpathQuery by viewModel.xpathQuery.collectAsState()
    val xpathMatches by viewModel.xpathMatches.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // WSDL Inspector Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wsdl_fetch_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "WSDL Discovery & Inspector",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = wsdlUrl,
                            onValueChange = { viewModel.wsdlUrl.value = it },
                            label = { Text("WSDL URL") },
                            placeholder = { Text("e.g. https://service.example.com/api?WSDL") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("wsdl_url_input"),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Button(
                            onClick = { viewModel.fetchAndParseWsdl() },
                            enabled = !isLoading && wsdlUrl.isNotBlank(),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("fetch_wsdl_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Parse WSDL")
                        }
                    }

                    if (services.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Discovered SOAP Operations:",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        services.forEach { srv ->
                            srv.operations.forEach { op ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .clickable { viewModel.selectSoapOperation(op) },
                                    shape = RoundedCornerShape(6.dp),
                                    color = CardBackgroundElevated
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = op.name, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = CyberCyan)
                                        Text(text = "Action: ${op.soapAction.takeLast(20)}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // SOAP Invocation Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("soap_execute_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SOAP Envelope & Execution",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = soapEndpoint,
                        onValueChange = { viewModel.soapEndpointUrl.value = it },
                        label = { Text("Service Endpoint URL") },
                        placeholder = { Text("e.g. https://service.example.com/api") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("soap_endpoint_input"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = soapAction,
                        onValueChange = { viewModel.soapActionHeader.value = it },
                        label = { Text("SOAPAction Header (Optional)") },
                        placeholder = { Text("e.g. http://example.com/Operation") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("soap_action_input"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = soapPayload,
                        onValueChange = { viewModel.soapXmlPayload.value = it },
                        label = { Text("SOAP XML Envelope") },
                        placeholder = { Text("<soapenv:Envelope>...</soapenv:Envelope>") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .testTag("soap_xml_payload_input"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Allow Self-Signed TLS", fontSize = 11.sp, color = TextSecondary)
                        Switch(
                            checked = soapIgnoreSsl,
                            onCheckedChange = { viewModel.soapIgnoreSslErrors.value = it },
                            modifier = Modifier.testTag("soap_ignore_ssl_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.soapVersion12.value = !soap12 },
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (soap12) "SOAP 1.2" else "SOAP 1.1", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { viewModel.executeSoapRequest() },
                            enabled = !isLoading && soapEndpoint.isNotBlank() && soapPayload.isNotBlank(),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("soap_execute_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Execute SOAP", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // SOAP Response & XPath Evaluator
        result?.let { res ->
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("soap_result_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "SOAP Response",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Text(
                                text = "HTTP ${res.statusCode} (${res.executionTimeMs} ms)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (res.isSuccess) StatusConnected else StatusError
                            )
                        }

                        if (!res.errorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = StatusError.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = res.errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StatusError,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // XPath Query Tool
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = xpathQuery,
                                onValueChange = { viewModel.xpathQuery.value = it },
                                label = { Text("XPath Query") },
                                placeholder = { Text("e.g. //Result/text()") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("xpath_query_input"),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Button(
                                onClick = { viewModel.runXPathQuery() },
                                enabled = xpathQuery.isNotBlank(),
                                modifier = Modifier.heightIn(min = 48.dp).testTag("xpath_eval_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = CardBackgroundElevated, contentColor = CyberCyan),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("XPath")
                            }
                        }

                        if (xpathMatches.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "XPath Extracted Values:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            xpathMatches.forEach { match ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = DarkBackground,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                ) {
                                    Text(text = match, style = MaterialTheme.typography.bodySmall, color = StatusConnected, modifier = Modifier.padding(6.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = DarkBackground
                        ) {
                            Text(
                                text = res.responseXml.take(1500),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = TextPrimary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
