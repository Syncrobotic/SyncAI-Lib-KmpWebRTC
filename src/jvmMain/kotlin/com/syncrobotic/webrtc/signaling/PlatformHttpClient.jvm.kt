package com.syncrobotic.webrtc.signaling

import io.ktor.client.*
import io.ktor.client.engine.cio.*

internal actual fun createPlatformHttpClient(
    block: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(CIO, block)
