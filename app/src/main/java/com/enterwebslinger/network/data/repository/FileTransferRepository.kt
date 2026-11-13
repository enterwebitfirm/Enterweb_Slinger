// === FILE: app/src/main/java/com/enterwebslinger/network/data/repository/FileTransferRepository.kt ===
package com.enterwebslinger.network.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.enterwebslinger.network.data.protocol.TftpClientEngine
import com.enterwebslinger.network.domain.model.FileTransferSession
import com.enterwebslinger.network.domain.model.LocalFileItem
import com.enterwebslinger.network.domain.model.RemoteFileItem
import com.enterwebslinger.network.domain.model.TransferDirection
import com.enterwebslinger.network.domain.model.TransferJob
import com.enterwebslinger.network.domain.model.TransferProtocol
import com.enterwebslinger.network.domain.model.TransferStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPSClient
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class FileTransferRepository(
    context: Context,
    private val scope: CoroutineScope
) {
    private val appContext: Context = context.applicationContext
    private val queueMutex = Mutex()
    @Volatile
    private var isQueueProcessing = false

    private val _session = MutableStateFlow(
        FileTransferSession(
            currentLocalPath = appContext.filesDir.absolutePath
        )
    )
    val session: StateFlow<FileTransferSession> = _session.asStateFlow()

    private val _remoteFiles = MutableStateFlow<List<RemoteFileItem>>(emptyList())
    val remoteFiles: StateFlow<List<RemoteFileItem>> = _remoteFiles.asStateFlow()

    private val _localFiles = MutableStateFlow<List<LocalFileItem>>(emptyList())
    val localFiles: StateFlow<List<LocalFileItem>> = _localFiles.asStateFlow()

    private val _transferQueue = MutableStateFlow<List<TransferJob>>(emptyList())
    val transferQueue: StateFlow<List<TransferJob>> = _transferQueue.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var ftpClient: FTPClient? = null
    private var sshClient: SSHClient? = null
    private var sftpClient: SFTPClient? = null

    val tftpEngine = TftpClientEngine()

    init {
        refreshLocalFiles()
    }

    fun setStatusMessage(msg: String?) {
        _statusMessage.value = msg
    }

    fun refreshLocalFiles(path: String = _session.value.currentLocalPath) {
        scope.launch(Dispatchers.IO) {
            try {
                val dir = File(path)
                if (dir.exists() && dir.isDirectory) {
                    val files = dir.listFiles()?.map { file ->
                        LocalFileItem(
                            name = file.name,
                            path = file.absolutePath,
                            isDirectory = file.isDirectory,
                            sizeBytes = if (file.isDirectory) 0L else file.length(),
                            lastModifiedMillis = file.lastModified()
                        )
                    }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()

                    _localFiles.value = files
                    _session.update { it.copy(currentLocalPath = dir.absolutePath) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusMessage.value = "Failed to list local directory: ${e.message}"
            }
        }
    }

    fun navigateLocalUp() {
        val current = File(_session.value.currentLocalPath)
        current.parentFile?.let { parent ->
            if (parent.canRead()) {
                refreshLocalFiles(parent.absolutePath)
            }
        }
    }

    suspend fun connect(
        host: String,
        port: Int,
        protocol: TransferProtocol,
        username: String,
        password: String,
        isPassive: Boolean = true,
        isBinary: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (host.isBlank()) return@withContext Result.failure(IllegalArgumentException("Host is required"))
        _isLoading.value = true
        disconnect()

        try {
            when (protocol) {
                TransferProtocol.FTP,
                TransferProtocol.FTPS_EXPLICIT,
                TransferProtocol.FTPS_IMPLICIT -> {
                    val client = when (protocol) {
                        TransferProtocol.FTPS_IMPLICIT -> FTPSClient(true)
                        TransferProtocol.FTPS_EXPLICIT -> FTPSClient(false)
                        else -> FTPClient()
                    }

                    client.connectTimeout = 10000
                    client.setDataTimeout(java.time.Duration.ofMillis(15000))
                    client.connect(host, port)

                    val loginSuccess = if (username.isBlank()) {
                        client.login("anonymous", "anonymous@enterwebslinger.net")
                    } else {
                        client.login(username, password)
                    }

                    if (!loginSuccess) {
                        client.disconnect()
                        return@withContext Result.failure(Exception("FTP Authentication failed for user '$username'"))
                    }

                    if (isPassive) client.enterLocalPassiveMode() else client.enterLocalActiveMode()
                    client.setFileType(if (isBinary) FTP.BINARY_FILE_TYPE else FTP.ASCII_FILE_TYPE)

                    ftpClient = client
                    _session.update {
                        it.copy(
                            host = host,
                            port = port,
                            protocol = protocol,
                            username = username,
                            isConnected = true,
                            currentRemotePath = "/",
                            isPassiveMode = isPassive,
                            isBinaryMode = isBinary
                        )
                    }
                    refreshRemoteFiles("/")
                }

                TransferProtocol.SFTP,
                TransferProtocol.SCP -> {
                    val ssh = SSHClient()
                    ssh.connectTimeout = 10000
                    ssh.timeout = 15000
                    ssh.addHostKeyVerifier(PromiscuousVerifier())
                    ssh.connect(host, port)
                    ssh.authPassword(username, password)

                    val sftp = ssh.newSFTPClient()
                    sshClient = ssh
                    sftpClient = sftp

                    _session.update {
                        it.copy(
                            host = host,
                            port = port,
                            protocol = protocol,
                            username = username,
                            isConnected = true,
                            currentRemotePath = sftp.canonicalize(".").takeIf { p -> p.isNotBlank() } ?: "/",
                            isPassiveMode = isPassive,
                            isBinaryMode = isBinary
                        )
                    }
                    refreshRemoteFiles(_session.value.currentRemotePath)
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            disconnect()
            throw e
        } catch (e: Exception) {
            disconnect()
            _statusMessage.value = "Connection failed: ${e.message}"
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    fun disconnect() {
        try {
            ftpClient?.let {
                if (it.isConnected) {
                    it.logout()
                    it.disconnect()
                }
            }
        } catch (_: Exception) {}
        ftpClient = null

        try {
            sftpClient?.close()
        } catch (_: Exception) {}
        sftpClient = null

        try {
            sshClient?.disconnect()
        } catch (_: Exception) {}
        sshClient = null

        _session.update { it.copy(isConnected = false) }
        _remoteFiles.value = emptyList()
    }

    suspend fun refreshRemoteFiles(path: String = _session.value.currentRemotePath) = withContext(Dispatchers.IO) {
        _isLoading.value = true
        try {
            val items = mutableListOf<RemoteFileItem>()
            val ftp = ftpClient
            val sftp = sftpClient

            if (ftp != null && ftp.isConnected) {
                val files = ftp.listFiles(path)
                files.forEach { file ->
                    val name = file.name
                    if (name != "." && name != "..") {
                        val fullPath = if (path.endsWith("/")) "$path$name" else "$path/$name"
                        items.add(
                            RemoteFileItem(
                                name = name,
                                path = fullPath,
                                isDirectory = file.isDirectory,
                                sizeBytes = if (file.isDirectory) 0L else file.size,
                                permissions = file.rawListing.take(10).ifBlank { if (file.isDirectory) "drwxr-xr-x" else "-rw-r--r--" },
                                lastModifiedMillis = file.timestamp?.timeInMillis ?: System.currentTimeMillis()
                            )
                        )
                    }
                }
                _session.update { it.copy(currentRemotePath = path) }
            } else if (sftp != null) {
                val resources = sftp.ls(path)
                resources.forEach { res ->
                    val name = res.name
                    if (name != "." && name != "..") {
                        val isDir = res.isDirectory
                        items.add(
                            RemoteFileItem(
                                name = name,
                                path = res.path,
                                isDirectory = isDir,
                                sizeBytes = if (isDir) 0L else res.attributes.size,
                                permissions = res.attributes.mode.toString(),
                                lastModifiedMillis = res.attributes.mtime * 1000L
                            )
                        )
                    }
                }
                _session.update { it.copy(currentRemotePath = path) }
            }

            _remoteFiles.value = items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _statusMessage.value = "Failed to list remote directory: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun changeRemoteDirectory(newPath: String) {
        refreshRemoteFiles(newPath)
    }

    suspend fun navigateRemoteUp() {
        val current = _session.value.currentRemotePath
        val parent = if (current.contains("/")) {
            val idx = current.lastIndexOf('/')
            if (idx <= 0) "/" else current.substring(0, idx)
        } else "/"
        refreshRemoteFiles(parent)
    }

    suspend fun chmodRemote(path: String, permissionsOctal: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val ftp = ftpClient
            val sftp = sftpClient
            if (ftp != null && ftp.isConnected) {
                val reply = ftp.sendSiteCommand("CHMOD $permissionsOctal $path")
                if (reply) {
                    refreshRemoteFiles()
                    return@withContext Result.success(true)
                }
            } else if (sftp != null) {
                val mode = permissionsOctal.toInt(8)
                sftp.chmod(path, mode)
                refreshRemoteFiles()
                return@withContext Result.success(true)
            }
            Result.failure(Exception("CHMOD command failed"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readRemoteTextPreview(remotePath: String, maxBytes: Int = 32768): Result<String> = withContext(Dispatchers.IO) {
        try {
            val ftp = ftpClient
            val sftp = sftpClient

            if (ftp != null && ftp.isConnected) {
                val outputStream = ByteArrayOutputStream()
                val inputStream: InputStream? = ftp.retrieveFileStream(remotePath)
                if (inputStream != null) {
                    inputStream.use { stream ->
                        val buffer = ByteArray(4096)
                        var totalRead = 0
                        while (totalRead < maxBytes) {
                            val read = stream.read(buffer, 0, (maxBytes - totalRead).coerceAtMost(buffer.size))
                            if (read <= 0) break
                            outputStream.write(buffer, 0, read)
                            totalRead += read
                        }
                    }
                    val completed = ftp.completePendingCommand()
                    if (!completed) {
                        return@withContext Result.failure(Exception("FTP server failed to complete preview: ${ftp.replyString.trim()}"))
                    }
                    return@withContext Result.success(outputStream.toString(Charsets.UTF_8.name()))
                }
            } else if (sftp != null) {
                sftp.open(remotePath).use { remoteFile ->
                    val buffer = ByteArray(maxBytes)
                    val read = remoteFile.read(0, buffer, 0, maxBytes)
                    if (read > 0) {
                        return@withContext Result.success(String(buffer, 0, read, Charsets.UTF_8))
                    }
                }
            }
            Result.failure(Exception("Could not read file preview"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun queueDownload(remoteFile: RemoteFileItem) {
        val targetLocalPath = File(_session.value.currentLocalPath, remoteFile.name).absolutePath
        val job = TransferJob(
            id = UUID.randomUUID().toString(),
            filename = remoteFile.name,
            localPath = targetLocalPath,
            remotePath = remoteFile.path,
            direction = TransferDirection.DOWNLOAD,
            totalBytes = remoteFile.sizeBytes
        )
        _transferQueue.update { it + job }
        processQueue()
    }

    fun queueUpload(localFile: LocalFileItem) {
        val remotePath = _session.value.currentRemotePath
        val targetRemotePath = if (remotePath.endsWith("/")) "$remotePath${localFile.name}" else "$remotePath/${localFile.name}"
        val job = TransferJob(
            id = UUID.randomUUID().toString(),
            filename = localFile.name,
            localPath = localFile.path,
            remotePath = targetRemotePath,
            direction = TransferDirection.UPLOAD,
            totalBytes = localFile.sizeBytes
        )
        _transferQueue.update { it + job }
        processQueue()
    }

    fun queueUploadUri(uri: Uri) {
        val (displayName, sizeBytes) = queryUriMetadata(uri)
        val remotePath = _session.value.currentRemotePath
        val targetRemotePath = if (remotePath.endsWith("/")) "$remotePath$displayName" else "$remotePath/$displayName"
        val job = TransferJob(
            id = UUID.randomUUID().toString(),
            filename = displayName,
            localPath = uri.toString(),
            remotePath = targetRemotePath,
            direction = TransferDirection.UPLOAD,
            totalBytes = sizeBytes
        )
        _transferQueue.update { it + job }
        processQueue()
    }

    private fun queryUriMetadata(uri: Uri): Pair<String, Long> {
        var name = "upload_${System.currentTimeMillis()}.bin"
        var size = 0L
        try {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { name = it }
                    }
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        size = cursor.getLong(sizeIdx).coerceAtLeast(0L)
                    }
                }
            }
        } catch (_: Exception) {}
        return name to size
    }

    suspend fun exportLocalFileToUri(localPath: String, destUri: Uri): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val src = File(localPath)
            if (!src.exists() || src.isDirectory) {
                return@withContext Result.failure(IllegalArgumentException("Source file is unavailable"))
            }
            val out = appContext.contentResolver.openOutputStream(destUri)
                ?: return@withContext Result.failure(IllegalStateException("Unable to open SAF destination stream"))
            var copied = 0L
            FileInputStream(src).use { input ->
                out.use { output ->
                    copied = input.copyTo(output)
                    output.flush()
                }
            }
            _statusMessage.value = "Exported ${src.name} (${copied} bytes) to device storage"
            Result.success(copied)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _statusMessage.value = "Export failed: ${e.message}"
            Result.failure(e)
        }
    }

    suspend fun readUriBytes(uri: Uri): Result<Pair<String, ByteArray>> = withContext(Dispatchers.IO) {
        try {
            val (name, _) = queryUriMetadata(uri)
            val input = appContext.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException("Unable to read selected document"))
            val bytes = input.use { it.readBytes() }
            Result.success(name to bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun processQueue() {
        scope.launch(Dispatchers.IO) {
            queueMutex.withLock {
                if (isQueueProcessing) return@withLock
                isQueueProcessing = true
                try {
                    while (true) {
                        val queued = _transferQueue.value.firstOrNull { it.status == TransferStatus.QUEUED } ?: break

                        _transferQueue.update { list ->
                            list.map { if (it.id == queued.id) it.copy(status = TransferStatus.IN_PROGRESS) else it }
                        }

                        try {
                            when (queued.direction) {
                                TransferDirection.DOWNLOAD -> executeDownload(queued)
                                TransferDirection.UPLOAD -> executeUpload(queued)
                            }

                            _transferQueue.update { list ->
                                list.map {
                                    if (it.id == queued.id) {
                                        it.copy(
                                            status = TransferStatus.COMPLETED,
                                            bytesTransferred = it.bytesTransferred.coerceAtLeast(queued.totalBytes)
                                        )
                                    } else it
                                }
                            }
                            refreshLocalFiles()
                            refreshRemoteFiles()
                        } catch (e: CancellationException) {
                            _transferQueue.update { list ->
                                list.map { if (it.id == queued.id) it.copy(status = TransferStatus.PAUSED) else it }
                            }
                            throw e
                        } catch (e: Exception) {
                            _transferQueue.update { list ->
                                list.map {
                                    if (it.id == queued.id) it.copy(status = TransferStatus.FAILED, errorMessage = e.message) else it
                                }
                            }
                        }
                    }
                } finally {
                    isQueueProcessing = false
                }
            }
        }
    }

    private suspend fun executeDownload(job: TransferJob) = withContext(Dispatchers.IO) {
        val ftp = ftpClient
        val sftp = sftpClient
        val destFile = File(job.localPath)
        destFile.parentFile?.mkdirs()

        var transferred = 0L
        var lastTime = System.currentTimeMillis()
        var lastBytes = 0L

        if (ftp != null && ftp.isConnected) {
            val inputStream = ftp.retrieveFileStream(job.remotePath) ?: throw Exception("Failed to open remote stream")
            inputStream.use { inStream ->
                FileOutputStream(destFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (inStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        transferred += read

                        val now = System.currentTimeMillis()
                        val deltaMs = now - lastTime
                        if (deltaMs >= 500) {
                            val speed = (transferred - lastBytes) * 1000 / deltaMs.coerceAtLeast(1)
                            lastTime = now
                            lastBytes = transferred
                            updateJobProgress(job.id, transferred, speed)
                        }
                    }
                    outputStream.flush()
                }
            }
            if (!ftp.completePendingCommand()) {
                throw Exception("FTP server failed to finalize download: ${ftp.replyString.trim()}")
            }
            updateJobProgress(job.id, transferred.coerceAtLeast(job.totalBytes), 0L)
        } else if (sftp != null) {
            sftp.get(job.remotePath, job.localPath)
            updateJobProgress(job.id, job.totalBytes, 0L)
        } else {
            throw Exception("No active FTP/SFTP session")
        }
    }

    private suspend fun executeUpload(job: TransferJob) = withContext(Dispatchers.IO) {
        val ftp = ftpClient
        val sftp = sftpClient
        val isContentUri = job.localPath.startsWith("content://")

        var tempCacheFile: File? = null
        try {
            val inputStreamProvider: () -> InputStream = if (isContentUri) {
                val uri = Uri.parse(job.localPath)
                ;{
                    appContext.contentResolver.openInputStream(uri)
                        ?: throw Exception("Unable to open SAF source URI")
                }
            } else {
                val srcFile = File(job.localPath)
                if (!srcFile.exists()) throw Exception("Local file not found: ${job.localPath}")
                ;{ FileInputStream(srcFile) }
            }

            var transferred = 0L
            var lastTime = System.currentTimeMillis()
            var lastBytes = 0L

            if (ftp != null && ftp.isConnected) {
                val outputStream = ftp.storeFileStream(job.remotePath)
                    ?: throw Exception("Failed to open remote upload stream")
                inputStreamProvider().use { inputStream ->
                    outputStream.use { outStream ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (inputStream.read(buffer).also { read = it } != -1) {
                            outStream.write(buffer, 0, read)
                            transferred += read

                            val now = System.currentTimeMillis()
                            val deltaMs = now - lastTime
                            if (deltaMs >= 500) {
                                val speed = (transferred - lastBytes) * 1000 / deltaMs.coerceAtLeast(1)
                                lastTime = now
                                lastBytes = transferred
                                updateJobProgress(job.id, transferred, speed)
                            }
                        }
                        outStream.flush()
                    }
                }
                if (!ftp.completePendingCommand()) {
                    throw Exception("FTP server failed to finalize upload: ${ftp.replyString.trim()}")
                }
                updateJobProgress(job.id, transferred.coerceAtLeast(job.totalBytes), 0L)
            } else if (sftp != null) {
                val sftpLocalPath = if (isContentUri) {
                    val temp = File.createTempFile("saf_upload_", ".tmp", appContext.cacheDir)
                    tempCacheFile = temp
                    inputStreamProvider().use { inp ->
                        FileOutputStream(temp).use { out -> inp.copyTo(out) }
                    }
                    temp.absolutePath
                } else {
                    job.localPath
                }
                sftp.put(sftpLocalPath, job.remotePath)
                updateJobProgress(job.id, job.totalBytes, 0L)
            } else {
                throw Exception("No active FTP/SFTP session")
            }
        } finally {
            tempCacheFile?.delete()
        }
    }

    private fun updateJobProgress(jobId: String, bytes: Long, speed: Long) {
        _transferQueue.update { list ->
            list.map {
                if (it.id == jobId) it.copy(bytesTransferred = bytes, speedBytesPerSec = speed) else it
            }
        }
    }

    fun clearFinishedJobs() {
        _transferQueue.update { list ->
            list.filter { it.status == TransferStatus.QUEUED || it.status == TransferStatus.IN_PROGRESS }
        }
    }

    fun createLocalFile(filename: String, content: String): File {
        val file = File(_session.value.currentLocalPath, filename)
        file.writeText(content, Charsets.UTF_8)
        refreshLocalFiles()
        return file
    }
}
