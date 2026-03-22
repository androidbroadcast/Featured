package dev.androidbroadcast.featured

/**
 * Optional capability for a [RemoteConfigValueProvider] that supports loading
 * previously cached values into memory without performing a network fetch.
 *
 * Implement this interface alongside [RemoteConfigValueProvider] when your provider
 * has a local disk cache (e.g. Firebase Remote Config) and you want to make those
 * cached values available immediately at app start — before calling [ConfigValues.fetch].
 *
 * [ConfigValues.initialize] calls [initialize] on the remote provider when it
 * implements this interface.
 */
public interface InitializableConfigValueProvider {
    /**
     * Loads previously cached values into memory so they are immediately available
     * via [ConfigValueProvider.get] without a network round-trip.
     *
     * Must NOT perform any network fetch. Use [RemoteConfigValueProvider.fetch] for that.
     */
    public suspend fun initialize()
}
