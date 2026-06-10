package dev.androidbroadcast.featured

import platform.Foundation.NSLog

internal actual fun logProviderError(e: Throwable) {
    NSLog("Featured: Provider error — %@", e.message ?: e.toString())
}
