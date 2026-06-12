package dev.androidbroadcast.featured.gradle

/**
 * Generates the `GeneratedLocalFlagExtensions<Suffix>.kt` and
 * `GeneratedRemoteFlagExtensions<Suffix>.kt` source files — extension functions on
 * `ConfigValues` for each declared flag. Local and remote extensions live in separate files
 * because each section can configure its own package and visibility via `generation { }`.
 *
 * **Local Boolean flags** get an `is…Enabled()` extension returning the raw `Boolean`:
 * ```kotlin
 * internal fun ConfigValues.isDarkModeEnabled(): Boolean = getValueCached(GeneratedLocalFlags.darkMode).value
 * ```
 *
 * **Local non-Boolean flags** get a `get…()` extension returning the raw value type:
 * ```kotlin
 * internal fun ConfigValues.getMaxRetries(): Int = getValueCached(GeneratedLocalFlags.maxRetries).value
 * ```
 *
 * **Remote flags** get a `get…()` extension returning `ConfigValue<T>` so callers can
 * inspect the value source (DEFAULT / REMOTE / etc.):
 * ```kotlin
 * internal fun ConfigValues.getPromoBannerEnabled(): ConfigValue<Boolean> =
 *     getValueCached(GeneratedRemoteFlags.promoBannerEnabled)
 * ```
 *
 * Extensions are `internal` by default because no external production consumer depends on
 * them — modules that need `ConfigParam` values directly use `observe(GeneratedLocalFlags.x)`
 * against the generated objects within the same Gradle module. The visibility follows the
 * section's effective `generation { visibility }` setting.
 *
 * **JVM class-name uniqueness:** `@file:JvmName` is intentionally absent — it is not
 * supported on Kotlin/Native targets. Instead, the emitted files are named
 * `GeneratedLocalFlagExtensions<Suffix>.kt` / `GeneratedRemoteFlagExtensions<Suffix>.kt`
 * where `<Suffix>` is derived from the Gradle module path (e.g. `SampleFeatureCheckout`).
 * The Kotlin compiler derives the JVM class name from the file name, so
 * `GeneratedLocalFlagExtensionsSampleFeatureCheckoutKt` is unique per module without any
 * JVM-specific annotation. Unlike the flag objects, these file names are NOT affected by a
 * custom `className` — only the package is configurable for the extensions files, which
 * keeps the ProGuard `-assumevalues` class name (see [ProguardRulesGenerator]) derivable
 * from the package alone.
 */
public object ExtensionFunctionGenerator {
    private const val CONFIG_VALUES_IMPORT = "dev.androidbroadcast.featured.ConfigValues"
    private const val CONFIG_VALUE_IMPORT = "dev.androidbroadcast.featured.ConfigValue"

    /**
     * Returns the emitted `.kt` file name for the local-flags extensions of the given module.
     *
     * The file-name suffix is derived by splitting the module path on all non-alphanumeric
     * characters and PascalCasing each segment, ensuring a valid Kotlin identifier without
     * any JVM-specific annotation. The Kotlin compiler derives the JVM class name from this
     * file name (e.g. `GeneratedLocalFlagExtensionsSampleFeatureCheckoutKt`).
     *
     * Examples:
     * - `":app"` → `"GeneratedLocalFlagExtensionsApp.kt"`
     * - `":sample:feature-checkout"` → `"GeneratedLocalFlagExtensionsSampleFeatureCheckout.kt"`
     */
    public fun localFileName(modulePath: String): String = "GeneratedLocalFlagExtensions${modulePath.modulePathToFileSuffix()}.kt"

    /**
     * Returns the emitted `.kt` file name for the remote-flags extensions of the given module.
     *
     * Examples:
     * - `":app"` → `"GeneratedRemoteFlagExtensionsApp.kt"`
     * - `":sample:feature-checkout"` → `"GeneratedRemoteFlagExtensionsSampleFeatureCheckout.kt"`
     */
    public fun remoteFileName(modulePath: String): String = "GeneratedRemoteFlagExtensions${modulePath.modulePathToFileSuffix()}.kt"

    /**
     * Generates the source text for the module-specific local and remote extensions files
     * as a pair, applying the effective per-section [local] / [remote] settings.
     *
     * Returns a pair of `(localSource, remoteSource)`. Either may be an empty string
     * if there are no flags of that type.
     */
    public fun generate(
        entries: List<LocalFlagEntry>,
        modulePath: String,
        local: ObjectCodegen = ObjectCodegen(),
        remote: ObjectCodegen = ObjectCodegen(),
    ): Pair<String, String> {
        val (localEntries, remoteEntries) = entries.partition { it.isLocal }
        val localSource =
            generateFile(
                entries = localEntries,
                objectName = ConfigParamGenerator.localObjectName(modulePath, local.objectName),
                codegen = local,
                needsConfigValue = false,
            )
        val remoteSource =
            generateFile(
                entries = remoteEntries,
                objectName = ConfigParamGenerator.remoteObjectName(modulePath, remote.objectName),
                codegen = remote,
                needsConfigValue = true,
            )
        return localSource to remoteSource
    }

    private fun generateFile(
        entries: List<LocalFlagEntry>,
        objectName: String,
        codegen: ObjectCodegen,
        needsConfigValue: Boolean,
    ): String {
        if (entries.isEmpty()) return ""
        return buildString {
            appendLine("// Auto-generated by Featured Gradle Plugin — do not edit manually.")
            appendLine()
            appendLine("package ${codegen.packageName}")
            appendLine()
            appendLine("import $CONFIG_VALUES_IMPORT")
            if (needsConfigValue) appendLine("import $CONFIG_VALUE_IMPORT")
            appendLine("import ${codegen.packageName}.$objectName")
            appendLine()
            entries.forEach { entry ->
                appendLine(entry.toExtensionFunction(objectName, codegen.visibility.keyword))
            }
        }.trimEnd() + "\n"
    }

    private fun LocalFlagEntry.toExtensionFunction(
        objectName: String,
        visibilityKeyword: String,
    ): String =
        if (isLocal) {
            val funcName = extensionFunctionName()
            "$visibilityKeyword fun ConfigValues.$funcName(): $type = getValueCached($objectName.$propertyName).value\n"
        } else {
            // Remote flags always use get… regardless of type — the return is ConfigValue<T>,
            // so callers can inspect the value source.
            val funcName = "get${propertyName.capitalized()}"
            "$visibilityKeyword fun ConfigValues.$funcName(): ConfigValue<$type> = getValueCached($objectName.$propertyName)\n"
        }
}
