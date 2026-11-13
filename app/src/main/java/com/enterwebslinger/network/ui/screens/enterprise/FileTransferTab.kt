// === FILE: app/src/main/java/com/enterwebslinger/network/ui/screens/enterprise/FileTransferTab.kt ===
package com.enterwebslinger.network.ui.screens.enterprise

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enterwebslinger.network.data.protocol.TftpClientEngine
import com.enterwebslinger.network.domain.model.FileTransferSession
import com.enterwebslinger.network.domain.model.LocalFileItem
import com.enterwebslinger.network.domain.model.RemoteFileItem
import com.enterwebslinger.network.domain.model.TransferDirection
import com.enterwebslinger.network.domain.model.TransferJob
import com.enterwebslinger.network.domain.model.TransferProtocol
import com.enterwebslinger.network.domain.model.TransferStatus
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
fun FileTransferTab(viewModel: EnterpriseViewModel) {
    val session by viewModel.fileSession.collectAsState()
    val remoteFiles by viewModel.remoteFiles.collectAsState()
    val localFiles by viewModel.localFiles.collectAsState()
    val transferQueue by viewModel.transferQueue.collectAsState()
    val isLoading by viewModel.transferLoading.collectAsState()
    val statusMessage by viewModel.transferStatus.collectAsState()
    val previewText by viewModel.filePreviewText.collectAsState()
    val tftpResult by viewModel.tftpResult.collectAsState()
    val isTftpLoading by viewModel.isTftpLoading.collectAsState()

    var activePane by remember { mutableIntStateOf(0) }
    var showConnectDialog by remember { mutableStateOf(false) }
    var showChmodDialog by remember { mutableStateOf<RemoteFileItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(12.dp)
            .testTag("file_transfer_tab")
    ) {
        // Quick Connection Header
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (session.isConnected) "Connected: ${session.host}:${session.port}" else "Disconnected",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (session.isConnected) StatusConnected else TextSecondary
                            )
                        )
                        if (session.isConnected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "(${session.protocol.displayName})",
                                fontSize = 11.sp,
                                color = CyberCyan
                            )
                        }
                    }
                    if (statusMessage != null) {
                        Text(
                            text = statusMessage ?: "",
                            fontSize = 11.sp,
                            color = TextMuted,
                            maxLines = 1
                        )
                    }
                }

                Row {
                    if (session.isConnected) {
                        OutlinedButton(
                            onClick = { viewModel.disconnectFileTransfer() },
                            modifier = Modifier.height(48.dp).testTag("file_transfer_disconnect_button")
                        ) {
                            Text("Disconnect", fontSize = 12.sp, color = StatusError)
                        }
                    } else {
                        Button(
                            onClick = { showConnectDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                            modifier = Modifier.height(48.dp).testTag("file_transfer_connect_button")
                        ) {
                            Text("Connect", fontSize = 12.sp, color = DarkBackground, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        TabRow(
            selectedTabIndex = activePane,
            containerColor = CardBackground,
            contentColor = CyberCyan,
            modifier = Modifier.fillMaxWidth().testTag("file_transfer_tabs")
        ) {
            Tab(
                selected = activePane == 0,
                onClick = { activePane = 0 },
                text = { Text("Remote (${remoteFiles.size})", fontSize = 11.sp) },
                icon = { Icon(Icons.Default.CloudDownload, contentDescription = "Remote", modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("tab_remote_files")
            )
            Tab(
                selected = activePane == 1,
                onClick = { activePane = 1 },
                text = { Text("Local (${localFiles.size})", fontSize = 11.sp) },
                icon = { Icon(Icons.Default.Folder, contentDescription = "Local", modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("tab_local_files")
            )
            Tab(
                selected = activePane == 2,
                onClick = { activePane = 2 },
                text = { Text("Queue (${transferQueue.count { it.status == TransferStatus.IN_PROGRESS || it.status == TransferStatus.QUEUED }})", fontSize = 11.sp) },
                icon = { Icon(Icons.Default.Sync, contentDescription = "Queue", modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("tab_transfer_queue")
            )
            Tab(
                selected = activePane == 3,
                onClick = { activePane = 3 },
                text = { Text("TFTP Tool", fontSize = 11.sp) },
                icon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "TFTP", modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("tab_tftp_tool")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            when (activePane) {
                0 -> RemoteFilesPane(
                    session = session,
                    remoteFiles = remoteFiles,
                    isLoading = isLoading,
                    onNavigateUp = { viewModel.navigateRemoteUp() },
                    onRefresh = { viewModel.refreshRemoteFiles() },
                    onDirClick = { viewModel.refreshRemoteFiles(it.path) },
                    onDownloadClick = { viewModel.queueDownload(it) },
                    onChmodClick = { showChmodDialog = it },
                    onPreviewClick = { viewModel.previewRemoteFile(it.path) },
                    onOpenConnect = { showConnectDialog = true }
                )
                1 -> LocalFilesPane(
                    session = session,
                    localFiles = localFiles,
                    onNavigateUp = { viewModel.navigateLocalUp() },
                    onRefresh = { viewModel.refreshLocalFiles() },
                    onDirClick = { viewModel.refreshLocalFiles(it.path) },
                    onUploadClick = { viewModel.queueUpload(it) },
                    onUploadSafUri = { viewModel.queueUploadUri(it) },
                    onExportLocalFile = { localPath, destUri -> viewModel.exportLocalFileToUri(localPath, destUri) }
                )
                2 -> TransferQueuePane(
                    queue = transferQueue,
                    onClearFinished = { viewModel.clearFinishedTransfers() }
                )
                3 -> TftpClientPane(
                    viewModel = viewModel,
                    tftpResult = tftpResult,
                    isLoading = isTftpLoading
                )
            }
        }
    }

    if (showConnectDialog) {
        FileTransferConnectDialog(
            currentSession = session,
            onDismiss = { showConnectDialog = false },
            onConnect = { host, port, protocol, user, pass, isPassive, isBinary ->
                viewModel.connectFileTransfer(host, port, protocol, user, pass, isPassive, isBinary)
                showConnectDialog = false
            }
        )
    }

    showChmodDialog?.let { file ->
        ChmodDialog(
            file = file,
            onDismiss = { showChmodDialog = null },
            onConfirm = { octal ->
                viewModel.chmodRemote(file.path, octal)
                showChmodDialog = null
            }
        )
    }

    previewText?.let { text ->
        AlertDialog(
            onDismissRequest = { viewModel.clearFilePreview() },
            title = { Text("Remote File Preview", color = CyberCyan, fontWeight = FontWeight.Bold) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                        .background(DarkBackground, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TextPrimary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearFilePreview() },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                ) {
                    Text("Close", color = DarkBackground)
                }
            },
            containerColor = CardBackground
        )
    }
}

@Composable
fun RemoteFilesPane(
    session: FileTransferSession,
    remoteFiles: List<RemoteFileItem>,
    isLoading: Boolean,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
    onDirClick: (RemoteFileItem) -> Unit,
    onDownloadClick: (RemoteFileItem) -> Unit,
    onChmodClick: (RemoteFileItem) -> Unit,
    onPreviewClick: (RemoteFileItem) -> Unit,
    onOpenConnect: () -> Unit
) {
    if (!session.isConnected) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Not Connected to Remote Server", color = TextSecondary, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onOpenConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier.height(48.dp).testTag("remote_pane_connect_button")
                ) {
                    Text("Connect via FTP / SFTP", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateUp, modifier = Modifier.size(48.dp).testTag("remote_nav_up_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Parent Directory", tint = CyberCyan, modifier = Modifier.size(18.dp))
            }
            Text(
                text = session.currentRemotePath,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
            )
            IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp).testTag("remote_refresh_button")) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = CyberCyan, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CyberCyan)
            }
        } else if (remoteFiles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Empty directory", color = TextMuted)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().testTag("remote_file_list")) {
                items(remoteFiles) { file ->
                    RemoteFileRow(
                        file = file,
                        onDirClick = { onDirClick(file) },
                        onDownloadClick = { onDownloadClick(file) },
                        onChmodClick = { onChmodClick(file) },
                        onPreviewClick = { onPreviewClick(file) }
                    )
                }
            }
        }
    }
}

@Composable
fun RemoteFileRow(
    file: RemoteFileItem,
    onDirClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onChmodClick: () -> Unit,
    onPreviewClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable { if (file.isDirectory) onDirClick() }
            .testTag("file_item_${file.name}")
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (file.isDirectory) Icons.Default.FolderOpen else Icons.Default.Description,
                contentDescription = null,
                tint = if (file.isDirectory) CyberCyan else TextMuted,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = TextPrimary,
                    fontWeight = if (file.isDirectory) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp,
                    maxLines = 1
                )
                Row {
                    Text(text = file.permissions, fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = file.formattedSize, fontSize = 10.sp, color = CyberCyan)
                }
            }

            if (!file.isDirectory) {
                IconButton(onClick = onPreviewClick, modifier = Modifier.size(48.dp).testTag("preview_${file.name}")) {
                    Icon(Icons.Default.Visibility, contentDescription = "Preview", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDownloadClick, modifier = Modifier.size(48.dp).testTag("download_${file.name}")) {
                    Icon(Icons.Default.CloudDownload, contentDescription = "Download", tint = StatusConnected, modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = onChmodClick, modifier = Modifier.size(48.dp).testTag("chmod_${file.name}")) {
                Icon(Icons.Default.Edit, contentDescription = "Chmod", tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun LocalFilesPane(
    session: FileTransferSession,
    localFiles: List<LocalFileItem>,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
    onDirClick: (LocalFileItem) -> Unit,
    onUploadClick: (LocalFileItem) -> Unit,
    onUploadSafUri: (android.net.Uri) -> Unit,
    onExportLocalFile: (String, android.net.Uri) -> Unit
) {
    var pendingExportPath by remember { mutableStateOf<String?>(null) }

    val safUploadPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onUploadSafUri(uri)
        }
    }

    val safExportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri ->
        val src = pendingExportPath
        pendingExportPath = null
        if (destUri != null && src != null) {
            onExportLocalFile(src, destUri)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateUp, modifier = Modifier.size(48.dp).testTag("local_nav_up_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Parent Directory", tint = CyberCyan, modifier = Modifier.size(18.dp))
            }
            Text(
                text = session.currentLocalPath,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
            )
            IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp).testTag("local_refresh_button")) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = CyberCyan, modifier = Modifier.size(18.dp))
            }
        }

        if (session.isConnected) {
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = { safUploadPicker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("saf_upload_button")
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Upload File from Device Storage (SAF)", color = CyberCyan, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (localFiles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No files in local directory", color = TextMuted)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().testTag("local_file_list")) {
                items(localFiles) { file ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { if (file.isDirectory) onDirClick(file) }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (file.isDirectory) Icons.Default.FolderOpen else Icons.Default.Description,
                                contentDescription = null,
                                tint = if (file.isDirectory) CyberCyan else TextMuted,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    color = TextPrimary,
                                    fontWeight = if (file.isDirectory) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                                Text(text = file.formattedSize, fontSize = 10.sp, color = CyberCyan)
                            }

                            if (!file.isDirectory) {
                                IconButton(
                                    onClick = {
                                        pendingExportPath = file.path
                                        safExportPicker.launch(file.name)
                                    },
                                    modifier = Modifier.size(48.dp).testTag("export_${file.name}")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Save to Device", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                }
                                if (session.isConnected) {
                                    IconButton(onClick = { onUploadClick(file) }, modifier = Modifier.size(48.dp).testTag("upload_${file.name}")) {
                                        Icon(Icons.Default.CloudUpload, contentDescription = "Upload", tint = CyberCyan, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransferQueuePane(
    queue: List<TransferJob>,
    onClearFinished: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Transfer Queue (${queue.size} Jobs)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            TextButton(onClick = onClearFinished, modifier = Modifier.height(48.dp).testTag("clear_queue_button")) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear Completed", color = TextMuted, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (queue.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No active or queued transfers", color = TextMuted)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().testTag("transfer_queue_list")) {
                items(queue) { job ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (job.direction == TransferDirection.DOWNLOAD) Icons.Default.CloudDownload else Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        tint = if (job.direction == TransferDirection.DOWNLOAD) StatusConnected else CyberCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = job.filename, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                val statusColor = when (job.status) {
                                    TransferStatus.COMPLETED -> StatusConnected
                                    TransferStatus.IN_PROGRESS -> CyberCyan
                                    TransferStatus.FAILED -> StatusError
                                    TransferStatus.QUEUED -> StatusWarning
                                    TransferStatus.PAUSED -> TextMuted
                                }
                                Text(
                                    text = job.status.name,
                                    color = statusColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { job.progressPercent },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = CyberCyan,
                                trackColor = DarkBackground
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${job.bytesTransferred} / ${job.totalBytes} Bytes",
                                    fontSize = 10.sp,
                                    color = TextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (job.status == TransferStatus.IN_PROGRESS) {
                                    Text(
                                        text = job.formattedSpeed,
                                        fontSize = 10.sp,
                                        color = CyberCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (job.errorMessage != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Error: ${job.errorMessage}",
                                    color = StatusError,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TftpClientPane(
    viewModel: EnterpriseViewModel,
    tftpResult: TftpClientEngine.TftpTransferResult?,
    isLoading: Boolean
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("69") }
    var filename by remember { mutableStateOf("") }
    var blksize by remember { mutableStateOf("1428") }
    var uploadContent by remember { mutableStateOf("") }
    var isUploadMode by remember { mutableStateOf(false) }

    val tftpSafPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && host.isNotBlank()) {
            val p = port.toIntOrNull() ?: 69
            val bs = blksize.toIntOrNull() ?: 1428
            viewModel.executeTftpUriUpload(host, p, bs, uri, filename)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("tftp_client_pane")
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Pure-Kotlin TFTP Client (RFC 1350 & RFC 2348)",
                    color = CyberCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = "Download/Upload configuration files or firmware via UDP 69 with blksize negotiation.",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("TFTP Server IP") },
                        placeholder = { Text("e.g. 192.0.2.1") },
                        modifier = Modifier.weight(2f).testTag("tftp_host_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = DividerColor)
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f).testTag("tftp_port_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = DividerColor)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = filename,
                        onValueChange = { filename = it },
                        label = { Text("Remote Filename") },
                        placeholder = { Text("e.g. startup-config.cfg") },
                        modifier = Modifier.weight(2f).testTag("tftp_filename_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = DividerColor)
                    )
                    OutlinedTextField(
                        value = blksize,
                        onValueChange = { blksize = it },
                        label = { Text("Block Size") },
                        modifier = Modifier.weight(1f).testTag("tftp_blksize_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = DividerColor)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { isUploadMode = !isUploadMode },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text(if (isUploadMode) "Switch to Download" else "Switch to Upload", fontSize = 11.sp, color = CyberCyan)
                    }
                }

                if (isUploadMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { tftpSafPicker.launch(arrayOf("*/*")) },
                        enabled = !isLoading && host.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("tftp_saf_upload_button")
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload Local File / Firmware (SAF)", fontSize = 11.sp, color = CyberCyan)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uploadContent,
                        onValueChange = { uploadContent = it },
                        label = { Text("Upload Content (Config / Text)") },
                        placeholder = { Text("Paste configuration or payload to upload via TFTP WRQ...") },
                        modifier = Modifier.fillMaxWidth().height(120.dp).testTag("tftp_upload_content"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan, unfocusedBorderColor = DividerColor),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val p = port.toIntOrNull() ?: 69
                        val bs = blksize.toIntOrNull() ?: 1428
                        if (isUploadMode) {
                            viewModel.executeTftpUpload(host, p, filename, uploadContent, bs)
                        } else {
                            viewModel.executeTftpDownload(host, p, filename, bs)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("tftp_execute_button"),
                    enabled = !isLoading && host.isNotBlank() && filename.isNotBlank() && (!isUploadMode || uploadContent.isNotEmpty())
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = DarkBackground)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isUploadMode) "Upload to TFTP (WRQ)" else "Download from TFTP (RRQ)",
                        color = DarkBackground,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        tftpResult?.let { res ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("tftp_result_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (res.isSuccess) "TFTP Transfer Succeeded" else "TFTP Transfer Failed",
                            color = if (res.isSuccess) StatusConnected else StatusError,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "Bytes: ${res.bytesTransferred} (${res.totalBlocks} blocks, blksize ${res.negotiatedBlockSize})", fontSize = 11.sp, color = TextPrimary)
                    Text(text = "Duration: ${res.durationMs} ms", fontSize = 11.sp, color = TextMuted)

                    if (res.errorMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = res.errorMessage, color = StatusError, fontSize = 12.sp)
                    }

                    if (res.data != null && res.data.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Downloaded Content Preview:", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(DarkBackground, RoundedCornerShape(6.dp))
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = String(res.data.take(2048).toByteArray(), Charsets.UTF_8),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileTransferConnectDialog(
    currentSession: FileTransferSession,
    onDismiss: () -> Unit,
    onConnect: (host: String, port: Int, protocol: TransferProtocol, user: String, pass: String, isPassive: Boolean, isBinary: Boolean) -> Unit
) {
    var host by remember { mutableStateOf(currentSession.host) }
    var port by remember { mutableStateOf(currentSession.port.toString()) }
    var protocol by remember { mutableStateOf(currentSession.protocol) }
    var user by remember { mutableStateOf(currentSession.username) }
    var pass by remember { mutableStateOf("") }
    var isPassive by remember { mutableStateOf(currentSession.isPassiveMode) }
    var isBinary by remember { mutableStateOf(currentSession.isBinaryMode) }
    var expandedProtocol by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect to Remote Host", color = CyberCyan, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ExposedDropdownMenuBox(
                    expanded = expandedProtocol,
                    onExpandedChange = { expandedProtocol = !expandedProtocol }
                ) {
                    OutlinedTextField(
                        value = protocol.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Protocol") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProtocol) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedProtocol,
                        onDismissRequest = { expandedProtocol = false }
                    ) {
                        TransferProtocol.entries.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.displayName) },
                                onClick = {
                                    protocol = p
                                    port = p.defaultPort.toString()
                                    expandedProtocol = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Host") },
                        placeholder = { Text("e.g. files.example.local") },
                        modifier = Modifier.weight(2f).testTag("dialog_host_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f).testTag("dialog_port_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Username") },
                    placeholder = { Text("e.g. netadmin") },
                    modifier = Modifier.fillMaxWidth().testTag("dialog_user_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Password / Key Passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("dialog_pass_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(
                        checked = isPassive,
                        onCheckedChange = { isPassive = it }
                    )
                    Text("Passive Mode (PASV)", fontSize = 12.sp, color = TextPrimary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(
                        checked = isBinary,
                        onCheckedChange = { isBinary = it }
                    )
                    Text("Binary Transfer Mode", fontSize = 12.sp, color = TextPrimary)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConnect(host, port.toIntOrNull() ?: protocol.defaultPort, protocol, user, pass, isPassive, isBinary)
                },
                enabled = host.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                modifier = Modifier.height(48.dp).testTag("dialog_connect_confirm")
            ) {
                Text("Connect", color = DarkBackground, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                Text("Cancel", color = TextMuted)
            }
        },
        containerColor = CardBackground
    )
}

@Composable
fun ChmodDialog(
    file: RemoteFileItem,
    onDismiss: () -> Unit,
    onConfirm: (octal: String) -> Unit
) {
    var octal by remember { mutableStateOf("755") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Permissions (CHMOD)", color = CyberCyan, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("File: ${file.name}", color = TextPrimary, fontSize = 12.sp)
                Text("Current: ${file.permissions}", color = TextMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = octal,
                    onValueChange = { if (it.length <= 4) octal = it },
                    label = { Text("Octal Permissions (e.g. 755, 644)") },
                    modifier = Modifier.fillMaxWidth().testTag("chmod_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(octal) },
                enabled = octal.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                modifier = Modifier.height(48.dp)
            ) {
                Text("Apply CHMOD", color = DarkBackground)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) { Text("Cancel", color = TextMuted) }
        },
        containerColor = CardBackground
    )
}
