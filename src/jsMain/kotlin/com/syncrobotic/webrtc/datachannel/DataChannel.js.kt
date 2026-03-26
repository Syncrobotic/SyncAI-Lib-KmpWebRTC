package com.syncrobotic.webrtc.datachannel

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get

/**
 * JavaScript/Browser implementation of DataChannel using native WebRTC API.
 */
actual class DataChannel(
    private val nativeChannel: dynamic
) {
    private var listener: DataChannelListener? = null

    actual val label: String
        get() = nativeChannel.label as String

    actual val id: Int
        get() = (nativeChannel.id as? Number)?.toInt() ?: -1

    actual val state: DataChannelState
        get() = when (nativeChannel.readyState as? String) {
            "connecting" -> DataChannelState.CONNECTING
            "open" -> DataChannelState.OPEN
            "closing" -> DataChannelState.CLOSING
            "closed" -> DataChannelState.CLOSED
            else -> DataChannelState.CLOSED
        }

    actual val bufferedAmount: Long
        get() = (nativeChannel.bufferedAmount as? Number)?.toLong() ?: 0

    init {
        // Set binary type to arraybuffer for binary data
        nativeChannel.binaryType = "arraybuffer"
        
        nativeChannel.onopen = {
            listener?.onStateChanged(DataChannelState.OPEN)
        }
        
        nativeChannel.onclose = {
            listener?.onStateChanged(DataChannelState.CLOSED)
        }
        
        nativeChannel.onerror = { event: dynamic ->
            val error = event?.error ?: Exception("Data channel error")
            listener?.onError(error as? Throwable ?: Exception(error.toString()))
        }
        
        nativeChannel.onmessage = { event: dynamic ->
            val data = event.data
            when {
                data is String -> listener?.onMessage(data)
                data is ArrayBuffer -> {
                    val bytes = arrayBufferToByteArray(data)
                    listener?.onBinaryMessage(bytes)
                }
            }
        }
        
        nativeChannel.onbufferedamountlow = {
            listener?.onBufferedAmountChange(bufferedAmount)
        }
    }

    actual fun setListener(listener: DataChannelListener?) {
        this.listener = listener
    }

    actual fun send(message: String): Boolean {
        if (state != DataChannelState.OPEN) return false
        
        try {
            nativeChannel.send(message)
            return true
        } catch (e: Throwable) {
            listener?.onError(e)
            return false
        }
    }

    actual fun sendBinary(data: ByteArray): Boolean {
        if (state != DataChannelState.OPEN) return false
        
        try {
            val arrayBuffer = byteArrayToArrayBuffer(data)
            nativeChannel.send(arrayBuffer)
            return true
        } catch (e: Throwable) {
            listener?.onError(e)
            return false
        }
    }

    actual fun close() {
        nativeChannel.onopen = null
        nativeChannel.onclose = null
        nativeChannel.onerror = null
        nativeChannel.onmessage = null
        nativeChannel.onbufferedamountlow = null
        nativeChannel.close()
    }

    actual companion object {
        /**
         * Creates a DataChannel from native RTCDataChannel.
         * Used internally by WebRTCClient.
         */
        internal fun create(nativeChannel: dynamic): DataChannel {
            return DataChannel(nativeChannel)
        }
    }
}

/**
 * Convert ArrayBuffer to ByteArray.
 */
private fun arrayBufferToByteArray(buffer: ArrayBuffer): ByteArray {
    val int8Array = Int8Array(buffer)
    return ByteArray(int8Array.length) { int8Array[it] }
}

/**
 * Convert ByteArray to ArrayBuffer.
 */
private fun byteArrayToArrayBuffer(bytes: ByteArray): ArrayBuffer {
    val int8Array = Int8Array(bytes.size)
    for (i in bytes.indices) {
        int8Array.asDynamic()[i] = bytes[i]
    }
    return int8Array.buffer
}
