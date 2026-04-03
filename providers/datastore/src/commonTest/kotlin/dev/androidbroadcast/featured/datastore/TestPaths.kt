package dev.androidbroadcast.featured.datastore

/**
 * Returns the platform's temporary directory as an absolute path string,
 * used in tests to satisfy DataStore's OkioStorage requirement for absolute paths.
 */
internal expect fun tempDir(): String
