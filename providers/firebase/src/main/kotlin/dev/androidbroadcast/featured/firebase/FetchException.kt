package dev.androidbroadcast.featured.firebase

/**
 * Thrown when a Firebase Remote Config fetch operation fails.
 *
 * This exception wraps any underlying error (e.g. network failure, timeout, or
 * Firebase service error) that occurs during [FirebaseConfigValueProvider.fetch].
 *
 * ## Retry recommendation
 *
 * Firebase Remote Config applies its own throttle limits; callers should not retry
 * immediately on every failure. A typical strategy is:
 * - Catch [FetchException] and log the [cause] for diagnostics.
 * - Retry only on subsequent app launches or after a significant delay (e.g. 1 hour).
 * - Use `FirebaseRemoteConfigSettings.minimumFetchIntervalInSeconds` to control
 *   how aggressively Firebase fetches from the server.
 *
 * @param message A human-readable description of the failure.
 * @param cause The underlying exception that triggered this failure.
 */
public class FetchException(
    message: String,
    cause: Throwable,
) : Exception(message, cause)
