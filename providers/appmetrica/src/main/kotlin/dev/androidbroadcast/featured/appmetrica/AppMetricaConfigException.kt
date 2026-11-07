package dev.androidbroadcast.featured.appmetrica

/**
 * Thrown when a Varioqub fetch or activation fails.
 *
 * This is the only public failure type for [AppMetricaConfigValueProvider]. [kotlinx.coroutines.CancellationException]
 * is not wrapped and is not a subtype of this exception.
 *
 * Do not retry immediately. Varioqub throttles fetches, including when the listener reports
 * `REQUEST_THROTTLED`.
 *
 * @param message A human-readable description of the failure.
 * @param cause The underlying failure. Always non-null.
 */
public class AppMetricaConfigException(
    message: String,
    cause: Throwable,
) : Exception(message, cause)
