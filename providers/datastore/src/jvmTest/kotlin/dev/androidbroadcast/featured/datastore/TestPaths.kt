package dev.androidbroadcast.featured.datastore

internal actual fun tempDir(): String = System.getProperty("java.io.tmpdir")!!
