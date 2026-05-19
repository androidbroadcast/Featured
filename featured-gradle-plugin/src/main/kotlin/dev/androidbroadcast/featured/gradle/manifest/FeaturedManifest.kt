package dev.androidbroadcast.featured.gradle.manifest

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Root manifest published by each module that applies the Featured Gradle plugin.
 *
 * **Public contract is the JSON wire format documented below.** These Kotlin types are an
 * internal producer-side helper. The consumer (PR B aggregator) may implement its own
 * deserialization model independently — renaming internal Kotlin fields does NOT break
 * the contract; changing the JSON wire format (field names, field semantics, enum variant
 * names) DOES break it and requires a [SCHEMA_VERSION] bump.
 *
 * ---
 *
 * ## Example JSON
 *
 * ```json
 * {
 *   "schemaVersion": 1,
 *   "modulePath": ":feature:checkout",
 *   "flags": [
 *     {
 *       "key": "dark_mode",
 *       "propertyName": "darkMode",
 *       "kind": "LOCAL",
 *       "valueType": "BOOLEAN",
 *       "defaultValue": "false"
 *     },
 *     {
 *       "key": "promo_banner",
 *       "propertyName": "promoBanner",
 *       "kind": "REMOTE",
 *       "valueType": "STRING",
 *       "defaultValue": "hello world",
 *       "description": "Show promo banner"
 *     },
 *     {
 *       "key": "checkout_variant",
 *       "propertyName": "checkoutVariant",
 *       "kind": "LOCAL",
 *       "valueType": "ENUM",
 *       "defaultValue": "LEGACY",
 *       "enumTypeFqn": "com.example.CheckoutVariant"
 *     }
 *   ]
 * }
 * ```
 *
 * ---
 *
 * ## Field semantics
 *
 * - **`modulePath`** — Gradle `Project.path` in the `:foo:bar` format. Root project is `":"`.
 * - **`propertyName`** — camelCase Kotlin property name for the aggregator to generate.
 *   Derived from `key` via `toCamelCase()` in the producer.
 * - **`kind`** — `LOCAL` for flags declared in `localFlags { }`, `REMOTE` for `remoteFlags { }`.
 * - **`defaultValue`** — raw default value as a string. For `STRING` valueType, the enclosing
 *   quotes (added by `FlagContainer.string()`) are removed by the producer; the stored value
 *   is the bare string (e.g. `hello world`, not `"hello world"`). For `ENUM` valueType, only
 *   the constant name is stored (e.g. `LEGACY`, not `EnumClass.LEGACY`) so that the aggregator
 *   can pass it directly to `enumValueOf<T>(defaultValue)`.
 * - **`enumTypeFqn`** — fully-qualified name of the enum class when `valueType` is `ENUM`;
 *   `null` for all other types.
 * - **`description`**, **`category`**, **`expiresAt`** — optional metadata passed through from
 *   the DSL. Absent from JSON when null (`explicitNulls = false`).
 *
 * ---
 *
 * ## Evolvability policy
 *
 * | Change                                      | Action                                       |
 * |---------------------------------------------|----------------------------------------------|
 * | Add optional field with a default           | Additive — no schema bump                    |
 * | Remove or rename existing field             | Breaking — bump [SCHEMA_VERSION] + `schema-major` attribute |
 * | Add new enum variant in [FlagKind]/[ValueType] | Breaking — bump major                     |
 * | Change semantics of existing field          | Breaking — bump major                        |
 *
 * ---
 *
 * ## ABI status
 *
 * The `featured-manifest` Usage attribute and the `schema-major` Gradle attribute are stable
 * consumer-facing contracts. See [FeaturedManifestContract] for the attribute constants.
 */
@Serializable
internal data class FeaturedManifest(
    val schemaVersion: Int,
    val modulePath: String,
    // No default value — guarantees that an empty list is serialized as "flags":[]
    // rather than being omitted when encodeDefaults = false.
    val flags: List<FlagDescriptor>,
)

/**
 * Describes a single feature flag declared via the `featured { }` DSL.
 *
 * Null optional fields are omitted from the JSON output (`explicitNulls = false` in
 * [FeaturedManifestJson]).
 */
@Serializable
internal data class FlagDescriptor(
    val key: String,
    val propertyName: String,
    val kind: FlagKind,
    val valueType: ValueType,
    val defaultValue: String,
    val enumTypeFqn: String? = null,
    val description: String? = null,
    val category: String? = null,
    val expiresAt: String? = null,
)

/** Whether the flag is declared in `localFlags { }` or `remoteFlags { }`. */
@Serializable
internal enum class FlagKind { LOCAL, REMOTE }

/** The Kotlin type of the flag's value. */
@Serializable
internal enum class ValueType { BOOLEAN, INT, LONG, FLOAT, DOUBLE, STRING, ENUM }

/** Wire-format schema version. Bump this (and the `schema-major` Gradle attribute) on breaking changes. */
internal const val SCHEMA_VERSION = 1

/**
 * Pre-configured [Json] instance used for both encoding and decoding [FeaturedManifest].
 *
 * - `prettyPrint = true` — human-readable output for easier debugging and diff review.
 * - `explicitNulls = false` — null optional fields are omitted from the JSON, keeping
 *   the output compact and forward-compatible.
 * - `encodeDefaults = false` — Kotlin default values are not written if they match the
 *   declared default. Note: [FeaturedManifest.flags] intentionally has **no** default so
 *   it is always serialized, even when empty.
 * - `ignoreUnknownKeys = true` — forward-compatible decoding: a consumer built against
 *   schema v1 can safely read a manifest produced by a future schema version that added
 *   optional fields.
 */
internal val FeaturedManifestJson =
    Json {
        prettyPrint = true
        explicitNulls = false
        encodeDefaults = false
        ignoreUnknownKeys = true
    }
