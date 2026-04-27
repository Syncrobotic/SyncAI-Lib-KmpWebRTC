package com.syncrobotic.webrtc.signaling

import io.ktor.client.*
import io.ktor.client.engine.darwin.*

internal actual fun createPlatformHttpClient(
    block: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(Darwin, block)
