// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/vpn/VpnProfilesAndQrSection.kt ===
package com.enterwebslinger.network.ui.screens.vpn

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enterwebslinger.network.data.repository.ImportedPkcs12Bundle
import com.enterwebslinger.network.domain.model.AllowedIpsCalculation
import com.enterwebslinger.network.domain.model.VpnProfile
import com.enterwebslinger.network.domain.model.VpnProtocol
import com.enterwebslinger.network.domain.model.WireGuardKeyPair
import com.enterwebslinger.network.ui.theme.CardBackground
import com.enterwebslinger.network.ui.theme.CardBackgroundElevated
import com.enterwebslinger.network.ui.theme.CyberCyan
import com.enterwebslinger.network.ui.theme.DarkBackground
import com.enterwebslinger.network.ui.theme.StatusEmerald
import com.enterwebslinger.network.ui.theme.StatusError
import com.enterwebslinger.network.ui.theme.TextMuted
import com.enterwebslinger.network.ui.theme.TextPrimary
import com.enterwebslinger.network.ui.theme.TextSecondary

@Composable
fun VpnProfilesToolkitCard(
    generatedKeyPair: WireGuardKeyPair?,
    allowedIpsCalc: AllowedIpsCalculation?,
    importedPkcs12: ImportedPkcs12Bundle?,
    selectedProfile: VpnProfile?,
    onOpenImportWg: () -> Unit,
    onOpenImportOvpn: () -> Unit,
    onOpenAddIkev2: () -> Unit,
    onGenerateWgKeys: () -> Unit,
    onShowWgQr: () -> Unit,
    onImportQrBitmap: (Bitmap) -> Unit,
    onImportPkcs12Bytes: (ByteArray, String) -> Unit,
    onCalculateAllowedIps: (String, String) -> Unit,
    onCreateWgFromBuilder: (String, String, String, String, String, String, String, String, Int) -> Unit,
    onTuneKeepalive: (Int, String?) -> Unit
) {
    val context = LocalContext.current
    var showP12Dialog by remember { mutableStateOf(false) }
    var showWgBuilder by remember { mutableStateOf(false) }
    var pendingP12Bytes by remember { mutableStateOf<ByteArray?>(null) }

    val qrPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                @Suppress("DEPRECATION")
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                val softwareBmp = if (bmp.config != Bitmap.Config.ARGB_8888) {
                    bmp.copy(Bitmap.Config.ARGB_8888, false)
                } else bmp
                onImportQrBitmap(softwareBmp)
            }
        }
    }

    val qrCameraPreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) onImportQrBitmap(bmp)
    }

    val p12PickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()?.let { bytes ->
                pendingP12Bytes = bytes
                showP12Dialog = true
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Import & Provisioning Actions
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenImportWg, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("import_wg_button")) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("WG .conf", fontSize = 11.sp)
            }
            OutlinedButton(onClick = onOpenImportOvpn, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("import_ovpn_button")) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("OpenVPN", fontSize = 11.sp)
            }
            OutlinedButton(onClick = onOpenAddIkev2, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("add_ikev2_button")) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("IKEv2", fontSize = 11.sp)
            }
        }

        // QR & PKCS#12 Import Row (Zero-permission Photo Picker + Camera Preview + SAF .p12)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { qrPhotoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("wg_qr_gallery_btn")
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("QR Photo", fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = { qrCameraPreviewLauncher.launch(null) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("wg_qr_camera_btn")
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("QR Cam", fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = { p12PickerLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("import_p12_btn")
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(".p12 Cert", fontSize = 11.sp)
            }
        }

        importedPkcs12?.let { p12 ->
            Surface(color = StatusEmerald.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "PKCS#12 Ready (${p12.alias}): ${p12.subjectDn.take(80)} — auto-binds to OpenVPN / IKEv2 RSA.",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusEmerald,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        // WireGuard Curve25519 Keypair + AllowedIPs Subtraction + Keepalive Tuner
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBackground), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("WireGuard Keygen, QR & AllowedIPs", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = onGenerateWgKeys, modifier = Modifier.heightIn(min = 40.dp).testTag("wg_keygen_btn")) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Keygen", fontSize = 11.sp)
                        }
                        if (selectedProfile?.protocol == VpnProtocol.WIREGUARD) {
                            OutlinedButton(onClick = onShowWgQr, modifier = Modifier.heightIn(min = 40.dp).testTag("wg_show_qr_btn")) {
                                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("QR", fontSize = 11.sp)
                            }
                        }
                    }
                }

                generatedKeyPair?.let { kp ->
                    Surface(color = DarkBackground, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("PublicKey: ${kp.publicKeyBase64}", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = CyberCyan)
                            Text("Preshared: ${kp.presharedKeyBase64.take(24)}...", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
                            TextButton(onClick = { showWgBuilder = true }) {
                                Text("Build New WireGuard Profile with This Key ->", color = StatusEmerald, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Exact AllowedIPs CIDR Subtraction Calculator
                var includeCidrs by remember { mutableStateOf("0.0.0.0/0") }
                var excludeCidrs by remember { mutableStateOf("192.168.0.0/16, 10.0.0.0/8") }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VpnMiniField(includeCidrs, { includeCidrs = it }, "Include CIDRs", Modifier.weight(0.75f))
                    VpnMiniField(excludeCidrs, { excludeCidrs = it }, "Exclude CIDRs", Modifier.weight(1.25f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onCalculateAllowedIps(includeCidrs, excludeCidrs) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("wg_calc_allowed_ips_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = CardBackgroundElevated, contentColor = CyberCyan)
                    ) {
                        Text("Compute AllowedIPs", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    if (selectedProfile?.protocol == VpnProtocol.WIREGUARD) {
                        var keepalive by remember(selectedProfile.id) {
                            mutableStateOf((selectedProfile.wireGuardConfig?.persistentKeepalive ?: 25).toString())
                        }
                        VpnMiniField(keepalive, { keepalive = it }, "Keepalive(s)", Modifier.width(96.dp))
                        Button(
                            onClick = {
                                onTuneKeepalive(
                                    keepalive.toIntOrNull() ?: 25,
                                    allowedIpsCalc?.formattedCsv
                                )
                            },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("wg_apply_keepalive_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
                        ) {
                            Text("Apply", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                allowedIpsCalc?.let { calc ->
                    Text(
                        "AllowedIPs (${calc.resultCidrs.size}): ${calc.formattedCsv}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = StatusEmerald
                    )
                }
            }
        }
    }

    if (showP12Dialog && pendingP12Bytes != null) {
        var p12Pass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showP12Dialog = false },
            title = { Text("Unlock PKCS#12 (.p12 / .pfx)", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = p12Pass,
                    onValueChange = { p12Pass = it },
                    label = { Text("Archive Password (blank if none)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onImportPkcs12Bytes(pendingP12Bytes!!, p12Pass)
                        showP12Dialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
                ) { Text("Extract X.509 Chain") }
            },
            dismissButton = {
                TextButton(onClick = { showP12Dialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = CardBackground
        )
    }

    if (showWgBuilder && generatedKeyPair != null) {
        WgBuilderDialog(
            keyPair = generatedKeyPair,
            defaultAllowed = allowedIpsCalc?.formattedCsv ?: "0.0.0.0/0",
            onDismiss = { showWgBuilder = false },
            onCreate = { name, addr, dns, peerPub, endpoint, allowed, keepalive ->
                onCreateWgFromBuilder(name, generatedKeyPair.privateKeyBase64, addr, dns, peerPub, generatedKeyPair.presharedKeyBase64, endpoint, allowed, keepalive)
                showWgBuilder = false
            }
        )
    }
}

@Composable
fun ProfileItemCard(
    profile: VpnProfile,
    isSelected: Boolean,
    isTunnelActive: Boolean,
    onSelect: () -> Unit,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("profile_card_${profile.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CardBackgroundElevated else CardBackground
        ),
        border = if (isSelected) BorderStroke(1.dp, CyberCyan) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(4.dp), color = DarkBackground) {
                        Text(
                            text = profile.protocol.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${profile.serverAddress}:${profile.port}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextSecondary
                )
                profile.wireGuardConfig?.let { wg ->
                    Text(
                        text = "Keepalive: ${wg.persistentKeepalive}s | AllowedIPs: ${wg.peerAllowedIps.size} CIDRs",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }

            Button(
                onClick = onConnect,
                modifier = Modifier.heightIn(min = 48.dp).testTag("connect_profile_${profile.id}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTunnelActive) StatusError else CyberCyan,
                    contentColor = if (isTunnelActive) Color.White else Color(0xFF00363D)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isTunnelActive) "Active" else "Connect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun QrPreviewDialog(bitmap: Bitmap, confText: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("WireGuard .conf QR Code", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "WireGuard Configuration QR Code",
                    modifier = Modifier.size(220.dp).background(Color.White).padding(6.dp)
                )
                Text(confText.take(240), style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = TextSecondary)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = CyberCyan) }
        },
        containerColor = CardBackground
    )
}

@Composable
fun ImportTextConfigDialog(
    title: String,
    defaultName: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onImport: (name: String, content: String) -> Unit
) {
    val context = LocalContext.current
    var profileName by remember { mutableStateOf("") }
    var configText by remember { mutableStateOf("") }
    var fileLoadError by remember { mutableStateOf<String?>(null) }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst() && profileName.isBlank()) {
                        profileName = (cursor.getString(0) ?: "").substringBeforeLast(".")
                    }
                }
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("Unable to open selected file stream")
            }.fold(
                onSuccess = { loaded -> configText = loaded; fileLoadError = null },
                onFailure = { err -> fileLoadError = err.message ?: "Failed to read selected file" }
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { openDocLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("vpn_browse_conf_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Browse .conf / .ovpn File", fontSize = 12.sp)
                }
                fileLoadError?.let { err -> Text(err, style = MaterialTheme.typography.labelSmall, color = StatusError) }
                VpnMiniField(profileName, { profileName = it }, "Profile Name ($defaultName)")
                OutlinedTextField(
                    value = configText,
                    onValueChange = { configText = it },
                    label = { Text("Paste Configuration File") },
                    placeholder = { Text(placeholder, fontSize = 11.sp, color = TextMuted) },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(profileName.ifBlank { defaultName }, configText) },
                enabled = configText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
            ) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
        containerColor = CardBackground
    )
}

@Composable
fun AddIkev2ProfileDialog(
    hasPkcs12Ready: Boolean,
    onDismiss: () -> Unit,
    onCreate: (name: String, server: String, remoteId: String, psk: String, user: String, pass: String, authMode: String) -> Unit
) {
    var authMode by remember { mutableStateOf(if (hasPkcs12Ready) "RSA" else "PSK") }
    var name by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var remoteId by remember { mutableStateOf("") }
    var psk by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Provision IKEv2 / IPsec", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("PSK", "EAP", "RSA").forEach { mode ->
                        FilterChip(selected = authMode == mode, onClick = { authMode = mode }, label = { Text(mode, fontSize = 11.sp) })
                    }
                }
                VpnMiniField(name, { name = it }, "Profile Name")
                VpnMiniField(server, { server = it }, "Server Host / IP")
                VpnMiniField(remoteId, { remoteId = it }, "Remote Identity (Optional)")
                if (authMode == "PSK") {
                    OutlinedTextField(value = psk, onValueChange = { psk = it }, label = { Text("Pre-Shared Key") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                } else if (authMode == "EAP") {
                    VpnMiniField(username, { username = it }, "EAP Username")
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("EAP Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                } else {
                    Text(
                        if (hasPkcs12Ready) "Unlocked PKCS#12 X.509 Certificate & Key will be bound via setAuthDigitalSignature()."
                        else "Import a .p12/.pfx archive first using '.p12 Cert' button in Profiles tab.",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasPkcs12Ready) StatusEmerald else StatusError
                    )
                }
            }
        },
        confirmButton = {
            val valid = server.isNotBlank() && ((authMode == "PSK" && psk.isNotBlank()) || (authMode == "EAP" && username.isNotBlank()) || (authMode == "RSA" && hasPkcs12Ready))
            Button(
                onClick = { onCreate(name.ifBlank { "IKEv2-$authMode" }, server.trim(), remoteId.trim(), psk, username.trim(), password, authMode) },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
            ) { Text("Save Profile") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
        containerColor = CardBackground
    )
}

@Composable
private fun WgBuilderDialog(
    keyPair: WireGuardKeyPair,
    defaultAllowed: String,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, String, String, Int) -> Unit
) {
    var name by remember { mutableStateOf("WG-Enterprise") }
    var addr by remember { mutableStateOf("10.66.66.2/32") }
    var dns by remember { mutableStateOf("1.1.1.1") }
    var peerPub by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("vpn.company.com:51820") }
    var allowed by remember { mutableStateOf(defaultAllowed) }
    var keepalive by remember { mutableStateOf("25") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create WireGuard Profile", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Local PubKey: ${keyPair.publicKeyBase64.take(24)}...", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = CyberCyan)
                VpnMiniField(name, { name = it }, "Profile Name")
                VpnMiniField(addr, { addr = it }, "Interface Address CIDR")
                VpnMiniField(dns, { dns = it }, "DNS Servers")
                VpnMiniField(peerPub, { peerPub = it }, "Peer PublicKey (Base64)")
                VpnMiniField(endpoint, { endpoint = it }, "Peer Endpoint (host:port)")
                VpnMiniField(allowed, { allowed = it }, "AllowedIPs")
                VpnMiniField(keepalive, { keepalive = it }, "PersistentKeepalive (sec)")
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, addr, dns, peerPub, endpoint, allowed, keepalive.toIntOrNull() ?: 25) },
                enabled = endpoint.isNotBlank() && addr.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF00363D))
            ) { Text("Create Profile") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } },
        containerColor = CardBackground
    )
}

@Composable
private fun VpnMiniField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
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
