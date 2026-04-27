package com.syncrobotic.webrtc.signaling

import io.ktor.client.*
import io.ktor.client.engine.js.*

internal actual fun createPlatformHttpClient(
    block: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(Js, block)
