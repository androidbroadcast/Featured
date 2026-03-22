package dev.androidbroadcast.featured.platform

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import dev.androidbroadcast.featured.InMemoryConfigValueProvider
import dev.androidbroadcast.featured.LocalConfigValueProvider
import dev.androidbroadcast.featured.datastore.DataStoreConfigValueProvider

private val Context.featuredDataStore by preferencesDataStore(name = "featured_flags")

/**
 * Returns an [InMemoryConfigValueProvider] on Android.
 *
 * **Warning:** This overload does **not** persist values across process restarts on Android.
 * For persistent storage backed by DataStore, use [defaultLocalProvider] with a [Context]:
 * ```kotlin
 * val provider = defaultLocalProvider(context) // returns DataStoreConfigValueProvider
 * ```
 *
 * This no-arg overload exists only to satisfy the `expect`/`actual` contract so that
 * common code compiles on all platforms. Prefer the [Context] overload whenever a
 * [Context] is available.
 */
@Deprecated(
    message = "On Android, pass a Context to get a persistent DataStore-backed provider: defaultLocalProvider(context).",
    replaceWith = ReplaceWith("defaultLocalProvider(context)"),
    level = DeprecationLevel.WARNING,
)
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
 * **Note:** The underlying DataStore instance is tied to [Context.getApplicationContext].
 * Create only one [DataStoreConfigValueProvider] per DataStore file per process — typically
 * at `Application` scope or via a DI singleton. Creating multiple instances pointing at the
 * same file (e.g. once per `Activity` without caching) will cause a `DataStore` conflict
 * crash at runtime.
 *
 * @param context An Android [Context] used to locate the DataStore file.
 *   [Context.getApplicationContext] is used internally, so any [Context] subtype is safe to pass.
 */
public fun defaultLocalProvider(context: Context): LocalConfigValueProvider =
    DataStoreConfigValueProvider(context.applicationContext.featuredDataStore)
