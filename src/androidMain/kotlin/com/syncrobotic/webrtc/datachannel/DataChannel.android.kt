package com.syncrobotic.webrtc.datachannel

import org.webrtc.DataChannel as RtcDataChannel
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Android implementation of DataChannel using WebRTC SDK.
 */
actual class DataChannel(
    private val nativeChannel: RtcDataChannel
) {
    private var listener: DataChannelListener? = null
    private val observer: RtcDataChannel.Observer

    actual val label: String
        get() = nativeChannel.label()

    actual val id: Int
        get() = nativeChannel.id()

    actual val state: DataChannelState
        get() = when (nativeChannel.state()) {
            RtcDataChannel.State.CONNECTING -> DataChannelState.CONNECTING
            RtcDataChannel.State.OPEN -> DataChannelState.OPEN
            RtcDataChannel.State.CLOSING -> DataChannelState.CLOSING
            RtcDataChannel.State.CLOSED -> DataChannelState.CLOSED
            else -> DataChannelState.CLOSED
        }

    actual val bufferedAmount: Long
        get() = nativeChannel.bufferedAmount()

    init {
        observer = object : RtcDataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {
                listener?.onBufferedAmountChange(nativeChannel.bufferedAmount())
            }

            override fun onStateChange() {
                listener?.onStateChanged(state)
            }

            override fun onMessage(buffer: RtcDataChannel.Buffer?) {
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
        }
        nativeChannel.registerObserver(observer)
    }

    actual fun setListener(listener: DataChannelListener?) {
        this.listener = listener
    }

    actual fun send(message: String): Boolean {
        if (state != DataChannelState.OPEN) return false
        
        val bytes = message.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.wrap(bytes)
        return nativeChannel.send(RtcDataChannel.Buffer(buffer, false))
    }

    actual fun sendBinary(data: ByteArray): Boolean {
        if (state != DataChannelState.OPEN) return false
        
        val buffer = ByteBuffer.wrap(data)
        return nativeChannel.send(RtcDataChannel.Buffer(buffer, true))
    }

    actual fun close() {
        nativeChannel.unregisterObserver()
        nativeChannel.close()
        nativeChannel.dispose()
    }

    actual companion object {
        /**
         * Creates a DataChannel from native RTCDataChannel.
         * Used internally by WebRTCClient.
         */
        internal fun create(nativeChannel: RtcDataChannel): DataChannel {
            return DataChannel(nativeChannel)
        }
    }
}
