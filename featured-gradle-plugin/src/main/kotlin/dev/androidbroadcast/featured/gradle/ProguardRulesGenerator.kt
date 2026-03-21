package dev.androidbroadcast.featured.gradle

/**
 * Generates ProGuard/R8 `-assumevalues` rules for `@LocalFlag`-annotated
 * `ConfigParam` properties whose `defaultValue` is `false`.
 *
 * Rules are emitted only for Boolean flags frozen to `false` at release build
 * time, enabling R8 dead-branch elimination for code guarded by those flags.
 * Flags with `defaultValue = true`, non-boolean flags, and `@RemoteFlag`
 * declarations are intentionally excluded.
 */
public object ProguardRulesGenerator {
    private const val CONFIG_VALUES_CLASS = "dev.androidbroadcast.featured.ConfigValues"

    /**
     * Generates a ProGuard rules string from [entries].
     *
     * Returns a blank string when no qualifying entries exist (Boolean type,
     * `defaultValue == "false"`). Otherwise returns a valid ProGuard block
     * containing one `-assumevalues` rule per qualifying flag.
     */
    public fun generate(entries: List<LocalFlagEntry>): String {
        val disabled = entries.filter { it.type == "Boolean" && it.defaultValue == "false" }
        if (disabled.isEmpty()) return ""

        return buildString {
            appendLine("-assumevalues class $CONFIG_VALUES_CLASS {")
            appendLine("    # Flags frozen at release build time — default value is false")
            disabled.forEach { entry ->
                appendLine("    # key: ${entry.key} (module: ${entry.moduleName})")
                appendLine("    boolean isEnabled(java.lang.String) return false;")
            }
            append("}")
        }
    }
}
