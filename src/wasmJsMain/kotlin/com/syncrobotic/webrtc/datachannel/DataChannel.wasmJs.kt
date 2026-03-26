package com.syncrobotic.webrtc.datachannel

// External interface for RTCDataChannel in WasmJS
external interface JsRTCDataChannel : JsAny {
    val label: JsString
    val id: JsNumber?
    val readyState: JsString
    val bufferedAmount: JsNumber
    var binaryType: JsString
    var onopen: (() -> Unit)?
    var onclose: (() -> Unit)?
    var onerror: ((JsAny) -> Unit)?
    var onmessage: ((JsAny) -> Unit)?
    var onbufferedamountlow: (() -> Unit)?
    fun send(data: JsString)
    fun send(data: JsAny)  // For ArrayBuffer
    fun close()
}

// External interface for ArrayBuffer
external interface JsArrayBuffer : JsAny {
    val byteLength: JsNumber
}

// JS interop functions
private fun getMessageData(event: JsAny): JsString? =
    js("event.data !== undefined && typeof event.data === 'string' ? event.data : null")

private fun getMessageBinaryData(event: JsAny): JsArrayBuffer? =
    js("event.data !== undefined && event.data instanceof ArrayBuffer ? event.data : null")

private fun createUint8Array(size: Int): JsAny =
    js("new Uint8Array(size)")

private fun createUint8ArrayFromBuffer(buffer: JsArrayBuffer): JsAny =
    js("new Uint8Array(buffer)")

private fun uint8ArrayGet(arr: JsAny, index: Int): Int =
    js("arr[index]")

private fun uint8ArraySet(arr: JsAny, index: Int, value: Int): Unit =
    js("arr[index] = value")

private fun uint8ArrayBuffer(arr: JsAny): JsAny =
    js("arr.buffer")

/**
 * WasmJS implementation of DataChannel using native WebRTC API.
 */
actual class DataChannel(
    private val nativeChannel: JsRTCDataChannel
) {
    private var listener: DataChannelListener? = null

    actual val label: String
        get() = nativeChannel.label.toString()

    actual val id: Int
        get() = nativeChannel.id?.toInt() ?: -1

    actual val state: DataChannelState
        get() = when (nativeChannel.readyState.toString()) {
            "connecting" -> DataChannelState.CONNECTING
            "open" -> DataChannelState.OPEN
            "closing" -> DataChannelState.CLOSING
            "closed" -> DataChannelState.CLOSED
            else -> DataChannelState.CLOSED
        }

    actual val bufferedAmount: Long
        get() = nativeChannel.bufferedAmount.toInt().toLong()

    init {
        // Set binary type to arraybuffer for binary data
        nativeChannel.binaryType = "arraybuffer".toJsString()
        
        nativeChannel.onopen = {
            listener?.onStateChanged(DataChannelState.OPEN)
        }
        
        nativeChannel.onclose = {
            listener?.onStateChanged(DataChannelState.CLOSED)
        }
        
        nativeChannel.onerror = { _ ->
            listener?.onError(Exception("Data channel error"))
        }
        
        nativeChannel.onmessage = { event ->
            val textData = getMessageData(event)
            val binaryData = getMessageBinaryData(event)
            
            when {
                textData != null -> listener?.onMessage(textData.toString())
                binaryData != null -> {
                    val bytes = arrayBufferToByteArray(binaryData)
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
            nativeChannel.send(message.toJsString())
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
        internal fun create(nativeChannel: JsRTCDataChannel): DataChannel {
            return DataChannel(nativeChannel)
        }
    }
}

/**
 * Convert ArrayBuffer to ByteArray.
 */
private fun arrayBufferToByteArray(buffer: JsArrayBuffer): ByteArray {
    val uint8Array = createUint8ArrayFromBuffer(buffer)
    val size = buffer.byteLength.toInt()
    return ByteArray(size) { index ->
        uint8ArrayGet(uint8Array, index).toByte()
    }
}

/**
 * Convert ByteArray to ArrayBuffer.
 */
private fun byteArrayToArrayBuffer(bytes: ByteArray): JsAny {
    val uint8Array = createUint8Array(bytes.size)
    for (i in bytes.indices) {
        uint8ArraySet(uint8Array, i, bytes[i].toInt() and 0xFF)
    }
    return uint8ArrayBuffer(uint8Array)
}
