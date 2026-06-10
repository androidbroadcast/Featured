package dev.androidbroadcast.featured

internal actual fun logProviderError(e: Throwable) {
    System.err.println("Featured: Provider error — ${e.stackTraceToString()}")
}
