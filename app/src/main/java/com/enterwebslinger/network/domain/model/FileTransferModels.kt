// === FILE: app/src/main/java/com/enterwebslinger/network/domain/model/FileTransferModels.kt ===
package com.enterwebslinger.network.domain.model

enum class TransferProtocol(val displayName: String, val defaultPort: Int) {
    FTP("FTP (Plain)", 21),
    FTPS_EXPLICIT("FTPS (Explicit TLS)", 21),
    FTPS_IMPLICIT("FTPS (Implicit TLS)", 990),
    SFTP("SFTP (SSHv2)", 22),
    SCP("SCP (SSHv2)", 22)
}

enum class TransferDirection {
    UPLOAD,
    DOWNLOAD
}

enum class TransferStatus {
    QUEUED,
    IN_PROGRESS,
    PAUSED,
    COMPLETED,
    FAILED
}

data class RemoteFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val permissions: String = "rw-r--r--",
    val lastModifiedMillis: Long = System.currentTimeMillis()
) {
    val formattedSize: String
        get() {
            if (isDirectory) return "<DIR>"
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format("%.2f GB", gb)
                mb >= 1.0 -> String.format("%.2f MB", mb)
                kb >= 1.0 -> String.format("%.1f KB", kb)
                else -> "$sizeBytes B"
            }
        }
}

data class LocalFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMillis: Long
) {
    val formattedSize: String
        get() {
            if (isDirectory) return "<DIR>"
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            return when {
                mb >= 1.0 -> String.format("%.2f MB", mb)
                kb >= 1.0 -> String.format("%.1f KB", kb)
                else -> "$sizeBytes B"
            }
        }
}

data class TransferJob(
    val id: String,
    val filename: String,
    val localPath: String,
    val remotePath: String,
    val direction: TransferDirection,
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val status: TransferStatus = TransferStatus.QUEUED,
    val errorMessage: String? = null
) {
    val progressPercent: Float
        get() = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val formattedSpeed: String
        get() {
            val kb = speedBytesPerSec / 1024.0
            val mb = kb / 1024.0
            return when {
                mb >= 1.0 -> String.format("%.2f MB/s", mb)
                kb >= 1.0 -> String.format("%.1f KB/s", kb)
                else -> "$speedBytesPerSec B/s"
            }
        }
}

data class FileTransferSession(
    val host: String = "",
    val port: Int = 21,
    val protocol: TransferProtocol = TransferProtocol.FTP,
    val username: String = "",
    val isConnected: Boolean = false,
    val currentRemotePath: String = "/",
    val currentLocalPath: String = "",
    val isPassiveMode: Boolean = true,
    val isBinaryMode: Boolean = true
)
