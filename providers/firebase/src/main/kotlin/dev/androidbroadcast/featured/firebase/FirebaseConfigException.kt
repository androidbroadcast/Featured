package dev.androidbroadcast.featured.firebase

/**
 * Thrown when a Firebase Remote Config operation fails.
 *
 * This exception wraps any underlying error (e.g. network failure, timeout, or
 * Firebase service error) that occurs during any [FirebaseConfigValueProvider] operation,
 * including [FirebaseConfigValueProvider.initialize] and [FirebaseConfigValueProvider.fetch].
 *
 * ## Retry recommendation
 *
 * Firebase Remote Config applies its own throttle limits; callers should not retry
 * immediately on every fetch failure. A typical strategy is:
 * - Catch [FirebaseConfigException] and log the [cause] for diagnostics.
 * - Retry only on subsequent app launches or after a significant delay (e.g. 1 hour).
 * - Use `FirebaseRemoteConfigSettings.minimumFetchIntervalInSeconds` to control
 *   how aggressively Firebase fetches from the server.
 *
 * @param message A human-readable description of the failure.
 * @param cause The underlying exception that triggered this failure.
 */
public class FirebaseConfigException(
    message: String,
    cause: Throwable,
) : Exception(message, cause)
