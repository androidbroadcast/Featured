package dev.androidbroadcast.featured

import android.util.Log

private const val TAG = "Featured"

internal actual fun logProviderError(e: Throwable) {
    Log.w(TAG, "Provider error", e)
}
