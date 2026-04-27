package com.syncrobotic.webrtc.signaling

import io.ktor.client.*

// wasmJs: use auto-discovery (Ktor doesn't have a dedicated wasmJs engine yet)
internal actual fun createPlatformHttpClient(
    block: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(block)
