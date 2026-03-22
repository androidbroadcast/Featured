package dev.androidbroadcast.featured.platform

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import dev.androidbroadcast.featured.InMemoryConfigValueProvider
import dev.androidbroadcast.featured.LocalConfigValueProvider
import dev.androidbroadcast.featured.datastore.DataStoreConfigValueProvider

private val Context.featuredDataStore by preferencesDataStore(name = "featured_flags")

/**
 * Returns an [InMemoryConfigValueProvider] on Android (no-arg fallback).
 *
 * For persistent storage backed by DataStore, use [defaultLocalProvider] with a [Context]:
 * ```kotlin
 * val provider = defaultLocalProvider(context)
 * ```
 */
public actual fun defaultLocalProvider(): LocalConfigValueProvider = InMemoryConfigValueProvider()

/**
 * Returns a [DataStoreConfigValueProvider] backed by Jetpack DataStore (Preferences).
 *
 * Values are stored in a DataStore file named `featured_flags` within the app's data directory
 * and persist across process restarts.
 *
 * **Preferred Android overload** — use this instead of the no-arg variant when you have a
 * [Context] available (e.g. from `Activity`, `Application`, or a DI graph):
 * ```kotlin
 * val configValues = ConfigValues(
 *     localProvider = defaultLocalProvider(context),
 * )
 * ```
 *
 * @param context An Android [Context] used to locate the DataStore file. A `applicationContext`
 *   is used internally, so any [Context] subtype is safe to pass.
 */
public fun defaultLocalProvider(context: Context): LocalConfigValueProvider =
    DataStoreConfigValueProvider(context.applicationContext.featuredDataStore)
