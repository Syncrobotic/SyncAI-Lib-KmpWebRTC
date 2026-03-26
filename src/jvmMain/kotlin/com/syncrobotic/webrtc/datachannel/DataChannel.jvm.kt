package com.syncrobotic.webrtc.datachannel

import dev.onvoid.webrtc.RTCDataChannel
import dev.onvoid.webrtc.RTCDataChannelBuffer
import dev.onvoid.webrtc.RTCDataChannelObserver
import dev.onvoid.webrtc.RTCDataChannelState
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * JVM/Desktop implementation of DataChannel using webrtc-java.
 */
actual class DataChannel(
    private val nativeChannel: RTCDataChannel
) {
    private var listener: DataChannelListener? = null

    actual val label: String
        get() = nativeChannel.label

    actual val id: Int
        get() = nativeChannel.id

    actual val state: DataChannelState
        get() = when (nativeChannel.state) {
            RTCDataChannelState.CONNECTING -> DataChannelState.CONNECTING
            RTCDataChannelState.OPEN -> DataChannelState.OPEN
            RTCDataChannelState.CLOSING -> DataChannelState.CLOSING
            RTCDataChannelState.CLOSED -> DataChannelState.CLOSED
            else -> DataChannelState.CLOSED
        }

    actual val bufferedAmount: Long
        get() = nativeChannel.bufferedAmount

    init {
        nativeChannel.registerObserver(object : RTCDataChannelObserver {
            override fun onBufferedAmountChange(previousAmount: Long) {
                listener?.onBufferedAmountChange(nativeChannel.bufferedAmount)
            }

            override fun onStateChange() {
                listener?.onStateChanged(state)
            }

            override fun onMessage(buffer: RTCDataChannelBuffer?) {
                buffer?.let {
                    val data = it.data
                    val bytes = ByteArray(data.remaining())
                    data.get(bytes)
                    
                    if (it.binary) {
                        listener?.onBinaryMessage(bytes)
                    } else {
                        val message = String(bytes, StandardCharsets.UTF_8)
                        listener?.onMessage(message)
                    }
                }
            }
        })
    }

    actual fun setListener(listener: DataChannelListener?) {
        this.listener = listener
    }

    actual fun send(message: String): Boolean {
        if (state != DataChannelState.OPEN) return false
        
        val bytes = message.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.wrap(bytes)
        nativeChannel.send(RTCDataChannelBuffer(buffer, false))
        return true
    }

    actual fun sendBinary(data: ByteArray): Boolean {
        if (state != DataChannelState.OPEN) return false
        
        val buffer = ByteBuffer.wrap(data)
        nativeChannel.send(RTCDataChannelBuffer(buffer, true))
        return true
    }

    actual fun close() {
        nativeChannel.unregisterObserver()
        nativeChannel.close()
    }

    actual companion object {
        /**
         * Creates a DataChannel from native RTCDataChannel.
         * Used internally by WebRTCClient.
         */
        internal fun create(nativeChannel: RTCDataChannel): DataChannel {
            return DataChannel(nativeChannel)
        }
    }
}
