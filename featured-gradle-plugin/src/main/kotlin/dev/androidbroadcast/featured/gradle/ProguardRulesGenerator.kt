package dev.androidbroadcast.featured.gradle

/**
 * Generates ProGuard/R8 `-assumevalues` rules for `@LocalFlag`-annotated
 * `ConfigParam` properties whose `defaultValue` is `false`.
 *
 * Rules are emitted only for Boolean flags frozen to `false` at release build
 * time, enabling R8 dead-branch elimination for code guarded by those flags.
 * Flags with `defaultValue = true`, non-boolean flags, and `@RemoteFlag`
 * declarations are intentionally excluded.
 *
 * **Method signature note:** The generated rule targets `isEnabled(String)`
 * on `ConfigValues`. This signature will be finalised when the `isEnabled`
 * API lands; update [IS_ENABLED_RULE] if the signature changes.
 */
public object ProguardRulesGenerator {
    private const val CONFIG_VALUES_CLASS = "dev.androidbroadcast.featured.ConfigValues"

    /**
     * ProGuard member rule that tells R8 `isEnabled(String)` always returns `false`.
     * A single copy covers all disabled flags — R8 applies it per call-site key.
     *
     * Update this constant when the `isEnabled` method signature is finalised.
     */
    private const val IS_ENABLED_RULE = "boolean isEnabled(java.lang.String) return false;"

    /**
     * Generates a ProGuard rules string from [entries].
     *
     * Returns a blank string when no qualifying entries exist (Boolean type,
     * `defaultValue == "false"`). Otherwise returns a single `-assumevalues`
     * block listing the disabled flag keys as comments and one shared method rule.
     */
    public fun generate(entries: List<LocalFlagEntry>): String {
        val disabled = entries.filter { it.type == "Boolean" && it.defaultValue == "false" }
        if (disabled.isEmpty()) return ""

        return buildString {
            appendLine("-assumevalues class $CONFIG_VALUES_CLASS {")
            appendLine("    # Flags frozen at release build time — default value is false")
            disabled.forEach { entry ->
                appendLine("    # key: ${entry.key} (module: ${entry.moduleName})")
            }
            appendLine("    $IS_ENABLED_RULE")
            append("}")
        }
    }
}
