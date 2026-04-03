package dev.androidbroadcast.featured.datastore

import platform.Foundation.NSTemporaryDirectory

internal actual fun tempDir(): String = NSTemporaryDirectory()
