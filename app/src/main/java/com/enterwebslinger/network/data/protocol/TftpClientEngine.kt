// === FILE: app/src/main/java/com/enterwebslinger/network/data/protocol/TftpClientEngine.kt ===
package com.enterwebslinger.network.data.protocol

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer

class TftpClientEngine(
    private val socketTimeoutMs: Int = 3000,
    private val maxRetries: Int = 3
) {
    companion object {
        const val OP_RRQ: Short = 1
        const val OP_WRQ: Short = 2
        const val OP_DATA: Short = 3
        const val OP_ACK: Short = 4
        const val OP_ERROR: Short = 5
        const val OP_OACK: Short = 6

        const val DEFAULT_BLKSIZE = 512
        const val DEFAULT_PORT = 69
    }

    data class TftpTransferResult(
        val isSuccess: Boolean,
        val bytesTransferred: Long,
        val totalBlocks: Int,
        val negotiatedBlockSize: Int,
        val durationMs: Long,
        val errorMessage: String? = null,
        val data: ByteArray? = null
    )

    suspend fun downloadFile(
        host: String,
        port: Int = DEFAULT_PORT,
        remoteFilename: String,
        requestedBlksize: Int = 1428,
        onProgress: (bytes: Long, blocks: Int) -> Unit = { _, _ -> }
    ): TftpTransferResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var currentBlksize = DEFAULT_BLKSIZE
        var bytesReceived = 0L
        var blockCount = 0
        val outputStream = ByteArrayOutputStream()

        DatagramSocket().use { socket ->
            socket.soTimeout = socketTimeoutMs
            try {
                val targetAddress = InetAddress.getByName(host)
                var serverPort = port

                val safeBlksize = requestedBlksize.coerceIn(8, 65464)
                val rrqBuffer = ByteBuffer.allocate(512)
                rrqBuffer.putShort(OP_RRQ)
                rrqBuffer.put(remoteFilename.toByteArray(Charsets.US_ASCII))
                rrqBuffer.put(0.toByte())
                rrqBuffer.put("octet".toByteArray(Charsets.US_ASCII))
                rrqBuffer.put(0.toByte())
                if (safeBlksize != DEFAULT_BLKSIZE) {
                    rrqBuffer.put("blksize".toByteArray(Charsets.US_ASCII))
                    rrqBuffer.put(0.toByte())
                    rrqBuffer.put(safeBlksize.toString().toByteArray(Charsets.US_ASCII))
                    rrqBuffer.put(0.toByte())
                }
                rrqBuffer.flip()

                val rrqBytes = ByteArray(rrqBuffer.remaining())
                rrqBuffer.get(rrqBytes)

                socket.send(DatagramPacket(rrqBytes, rrqBytes.size, targetAddress, port))

                var expectedBlock = 1
                var finished = false
                var retryCount = 0

                val receiveBuffer = ByteArray(65536)
                val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

                while (!finished) {
                    try {
                        socket.receive(receivePacket)
                        serverPort = receivePacket.port

                        val inBuffer = ByteBuffer.wrap(receivePacket.data, 0, receivePacket.length)
                        val opcode = inBuffer.short

                        when (opcode) {
                            OP_OACK -> {
                                val optionsMap = parseOptions(receivePacket.data, 2, receivePacket.length)
                                val negotiated = optionsMap["blksize"]?.toIntOrNull()
                                if (negotiated != null && negotiated in 8..65464) {
                                    currentBlksize = negotiated
                                }
                                sendAck(socket, targetAddress, serverPort, 0)
                                retryCount = 0
                            }
                            OP_DATA -> {
                                val blockNum = inBuffer.short.toInt() and 0xFFFF
                                val dataLen = inBuffer.remaining()

                                if (blockNum == (expectedBlock and 0xFFFF)) {
                                    val blockData = ByteArray(dataLen)
                                    inBuffer.get(blockData)
                                    outputStream.write(blockData)
                                    bytesReceived += dataLen
                                    blockCount++

                                    sendAck(socket, targetAddress, serverPort, blockNum)
                                    onProgress(bytesReceived, blockCount)

                                    expectedBlock++
                                    retryCount = 0

                                    if (dataLen < currentBlksize) {
                                        finished = true
                                    }
                                } else if (blockNum == ((expectedBlock - 1) and 0xFFFF)) {
                                    sendAck(socket, targetAddress, serverPort, blockNum)
                                }
                            }
                            OP_ERROR -> {
                                val errorCode = inBuffer.short.toInt()
                                val errorBytes = ByteArray(inBuffer.remaining())
                                inBuffer.get(errorBytes)
                                val errorMsg = String(errorBytes, Charsets.US_ASCII).trimEnd(0.toChar())
                                return@withContext TftpTransferResult(
                                    isSuccess = false,
                                    bytesTransferred = bytesReceived,
                                    totalBlocks = blockCount,
                                    negotiatedBlockSize = currentBlksize,
                                    durationMs = System.currentTimeMillis() - startTime,
                                    errorMessage = "TFTP Server Error ($errorCode): $errorMsg"
                                )
                            }
                        }
                    } catch (_: SocketTimeoutException) {
                        retryCount++
                        if (retryCount >= maxRetries) {
                            return@withContext TftpTransferResult(
                                isSuccess = false,
                                bytesTransferred = bytesReceived,
                                totalBlocks = blockCount,
                                negotiatedBlockSize = currentBlksize,
                                durationMs = System.currentTimeMillis() - startTime,
                                errorMessage = "TFTP Timeout: Exceeded $maxRetries retransmission retries"
                            )
                        }
                        val lastAck = (expectedBlock - 1).coerceAtLeast(0)
                        sendAck(socket, targetAddress, serverPort, lastAck)
                    }
                }

                TftpTransferResult(
                    isSuccess = true,
                    bytesTransferred = bytesReceived,
                    totalBlocks = blockCount,
                    negotiatedBlockSize = currentBlksize,
                    durationMs = System.currentTimeMillis() - startTime,
                    data = outputStream.toByteArray()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TftpTransferResult(
                    isSuccess = false,
                    bytesTransferred = bytesReceived,
                    totalBlocks = blockCount,
                    negotiatedBlockSize = currentBlksize,
                    durationMs = System.currentTimeMillis() - startTime,
                    errorMessage = e.message ?: "TFTP Transfer failed"
                )
            }
        }
    }

    suspend fun uploadFile(
        host: String,
        port: Int = DEFAULT_PORT,
        remoteFilename: String,
        data: ByteArray,
        requestedBlksize: Int = 1428,
        onProgress: (bytes: Long, blocks: Int) -> Unit = { _, _ -> }
    ): TftpTransferResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var currentBlksize = DEFAULT_BLKSIZE
        var bytesSent = 0L
        var blockCount = 0

        DatagramSocket().use { socket ->
            socket.soTimeout = socketTimeoutMs
            try {
                val targetAddress = InetAddress.getByName(host)
                var serverPort = port

                val safeBlksize = requestedBlksize.coerceIn(8, 65464)
                val wrqBuffer = ByteBuffer.allocate(512)
                wrqBuffer.putShort(OP_WRQ)
                wrqBuffer.put(remoteFilename.toByteArray(Charsets.US_ASCII))
                wrqBuffer.put(0.toByte())
                wrqBuffer.put("octet".toByteArray(Charsets.US_ASCII))
                wrqBuffer.put(0.toByte())
                if (safeBlksize != DEFAULT_BLKSIZE) {
                    wrqBuffer.put("blksize".toByteArray(Charsets.US_ASCII))
                    wrqBuffer.put(0.toByte())
                    wrqBuffer.put(safeBlksize.toString().toByteArray(Charsets.US_ASCII))
                    wrqBuffer.put(0.toByte())
                }
                wrqBuffer.flip()

                val wrqBytes = ByteArray(wrqBuffer.remaining())
                wrqBuffer.get(wrqBytes)

                val wrqPacket = DatagramPacket(wrqBytes, wrqBytes.size, targetAddress, port)
                socket.send(wrqPacket)

                val receiveBuffer = ByteArray(2048)
                val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

                var ackedBlock0 = false
                var retry = 0
                while (!ackedBlock0 && retry < maxRetries) {
                    try {
                        socket.receive(receivePacket)
                        serverPort = receivePacket.port
                        val inBuffer = ByteBuffer.wrap(receivePacket.data, 0, receivePacket.length)
                        val opcode = inBuffer.short
                        if (opcode == OP_OACK) {
                            val optionsMap = parseOptions(receivePacket.data, 2, receivePacket.length)
                            val negotiated = optionsMap["blksize"]?.toIntOrNull()
                            if (negotiated != null && negotiated in 8..65464) {
                                currentBlksize = negotiated
                            }
                            ackedBlock0 = true
                        } else if (opcode == OP_ACK) {
                            val block = inBuffer.short.toInt() and 0xFFFF
                            if (block == 0) ackedBlock0 = true
                        } else if (opcode == OP_ERROR) {
                            val code = inBuffer.short.toInt()
                            val msgBytes = ByteArray(inBuffer.remaining())
                            inBuffer.get(msgBytes)
                            return@withContext TftpTransferResult(
                                isSuccess = false,
                                bytesTransferred = 0,
                                totalBlocks = 0,
                                negotiatedBlockSize = currentBlksize,
                                durationMs = System.currentTimeMillis() - startTime,
                                errorMessage = "TFTP WRQ Error ($code): ${String(msgBytes, Charsets.US_ASCII)}"
                            )
                        }
                    } catch (_: SocketTimeoutException) {
                        retry++
                        socket.send(wrqPacket)
                    }
                }

                if (!ackedBlock0) {
                    return@withContext TftpTransferResult(
                        isSuccess = false,
                        bytesTransferred = 0,
                        totalBlocks = 0,
                        negotiatedBlockSize = currentBlksize,
                        durationMs = System.currentTimeMillis() - startTime,
                        errorMessage = "TFTP Error: Server did not acknowledge WRQ"
                    )
                }

                var offset = 0
                var currentBlock = 1
                var finished = false

                while (!finished) {
                    val chunkSize = (data.size - offset).coerceAtMost(currentBlksize)
                    val blockBuffer = ByteBuffer.allocate(4 + chunkSize)
                    blockBuffer.putShort(OP_DATA)
                    blockBuffer.putShort(currentBlock.toShort())
                    blockBuffer.put(data, offset, chunkSize)
                    blockBuffer.flip()

                    val dataPacketBytes = ByteArray(blockBuffer.remaining())
                    blockBuffer.get(dataPacketBytes)
                    val dataPacket = DatagramPacket(dataPacketBytes, dataPacketBytes.size, targetAddress, serverPort)

                    var blockAcked = false
                    var blockRetries = 0

                    while (!blockAcked && blockRetries < maxRetries) {
                        socket.send(dataPacket)
                        try {
                            socket.receive(receivePacket)
                            val inBuffer = ByteBuffer.wrap(receivePacket.data, 0, receivePacket.length)
                            val opcode = inBuffer.short
                            if (opcode == OP_ACK) {
                                val ackedBlock = inBuffer.short.toInt() and 0xFFFF
                                if (ackedBlock == (currentBlock and 0xFFFF)) {
                                    blockAcked = true
                                    offset += chunkSize
                                    bytesSent += chunkSize
                                    blockCount++
                                    onProgress(bytesSent, blockCount)
                                    if (chunkSize < currentBlksize) {
                                        finished = true
                                    }
                                    currentBlock++
                                }
                            } else if (opcode == OP_ERROR) {
                                val errCode = inBuffer.short.toInt()
                                return@withContext TftpTransferResult(
                                    isSuccess = false,
                                    bytesTransferred = bytesSent,
                                    totalBlocks = blockCount,
                                    negotiatedBlockSize = currentBlksize,
                                    durationMs = System.currentTimeMillis() - startTime,
                                    errorMessage = "TFTP Error during block $currentBlock: Code $errCode"
                                )
                            }
                        } catch (_: SocketTimeoutException) {
                            blockRetries++
                        }
                    }

                    if (!blockAcked) {
                        return@withContext TftpTransferResult(
                            isSuccess = false,
                            bytesTransferred = bytesSent,
                            totalBlocks = blockCount,
                            negotiatedBlockSize = currentBlksize,
                            durationMs = System.currentTimeMillis() - startTime,
                            errorMessage = "TFTP Timeout: Failed to get ACK for block $currentBlock"
                        )
                    }
                }

                TftpTransferResult(
                    isSuccess = true,
                    bytesTransferred = bytesSent,
                    totalBlocks = blockCount,
                    negotiatedBlockSize = currentBlksize,
                    durationMs = System.currentTimeMillis() - startTime
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TftpTransferResult(
                    isSuccess = false,
                    bytesTransferred = bytesSent,
                    totalBlocks = blockCount,
                    negotiatedBlockSize = currentBlksize,
                    durationMs = System.currentTimeMillis() - startTime,
                    errorMessage = e.message ?: "TFTP upload failed"
                )
            }
        }
    }

    private fun sendAck(socket: DatagramSocket, address: InetAddress, port: Int, blockNumber: Int) {
        val ackBuffer = ByteBuffer.allocate(4)
        ackBuffer.putShort(OP_ACK)
        ackBuffer.putShort(blockNumber.toShort())
        val ackBytes = ackBuffer.array()
        socket.send(DatagramPacket(ackBytes, ackBytes.size, address, port))
    }

    private fun parseOptions(data: ByteArray, offset: Int, length: Int): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var i = offset
        while (i < length) {
            val nameStart = i
            while (i < length && data[i] != 0.toByte()) i++
            if (i >= length) break
            val name = String(data, nameStart, i - nameStart, Charsets.US_ASCII).lowercase()
            i++

            val valStart = i
            while (i < length && data[i] != 0.toByte()) i++
            if (i >= length) break
            val value = String(data, valStart, i - valStart, Charsets.US_ASCII)
            i++

            result[name] = value
        }
        return result
    }
}
