package dev.androidbroadcast.featured.gradle

/**
 * Effective code-generation settings for one generated flag object (local or remote),
 * resolved from the `featured { }` DSL by [FeaturedPlugin] — section `generation { }`
 * values win over the top-level `featured { generation { } }` defaults, which win over
 * the built-in defaults captured here.
 */
public data class ObjectCodegen(
    /** Package of the generated object and its extensions file. */
    val packageName: String = ConfigParamGenerator.DEFAULT_PACKAGE,
    /** Custom object name, or `null` for the default `Generated<Kind>Flags<ModuleSuffix>`. */
    val objectName: String? = null,
    /** Visibility of the generated object and its extension functions. */
    val visibility: FeaturedVisibility = FeaturedVisibility.INTERNAL,
)
