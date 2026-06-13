package dev.androidbroadcast.featured.gradle

/**
 * Kotlin visibility modifier applied to the generated flag objects and their
 * `ConfigValues` extension functions.
 *
 * Configured via [GenerationSettings.visibility] — either as a module-wide default in
 * `featured { generation { } }` or per section in `localFlags { generation { } }` /
 * `remoteFlags { generation { } }`. Defaults to [INTERNAL]: a module's flag declarations
 * are an implementation detail that other modules must not reference directly.
 */
public enum class FeaturedVisibility(
    internal val keyword: String,
) {
    PUBLIC("public"),
    INTERNAL("internal"),
}
