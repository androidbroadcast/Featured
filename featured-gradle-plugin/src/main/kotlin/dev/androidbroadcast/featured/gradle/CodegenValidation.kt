package dev.androidbroadcast.featured.gradle

internal val PACKAGE_NAME_REGEX = Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*")
internal val CLASS_NAME_REGEX = Regex("[A-Za-z_][A-Za-z0-9_]*")

/**
 * Resolves the effective package name for one section: section override → top-level
 * `featured { generation { } }` default → [ConfigParamGenerator.DEFAULT_PACKAGE].
 *
 * Validates the value at its source so the error message names the exact DSL property
 * the user has to fix.
 */
internal fun resolvePackageName(
    section: GenerationSettings,
    defaults: GenerationSettings,
    sectionName: String,
): String {
    section.packageName?.let { name ->
        require(PACKAGE_NAME_REGEX.matches(name)) {
            "featured.$sectionName.generation.packageName '$name' is not a valid Kotlin package name."
        }
        return name
    }
    defaults.packageName?.let { name ->
        require(PACKAGE_NAME_REGEX.matches(name)) {
            "featured.generation.packageName '$name' is not a valid Kotlin package name."
        }
        return name
    }
    return ConfigParamGenerator.DEFAULT_PACKAGE
}

/**
 * Resolves the custom class name for one section, or `null` when the default
 * `Generated<Kind>Flags<ModuleSuffix>` name applies.
 *
 * A top-level `featured { generation { className = … } }` is rejected: there are two
 * generated objects, so a single shared name would always collide.
 */
internal fun resolveClassName(
    section: GenerationSettings,
    defaults: GenerationSettings,
    sectionName: String,
): String? {
    require(defaults.className == null) {
        "featured.generation.className is not supported — set className inside " +
            "localFlags { generation { } } or remoteFlags { generation { } }."
    }
    val name = section.className ?: return null
    require(CLASS_NAME_REGEX.matches(name)) {
        "featured.$sectionName.generation.className '$name' is not a valid Kotlin class name."
    }
    return name
}

/** Resolves the effective visibility: section override → top-level default → INTERNAL. */
internal fun resolveVisibility(
    section: GenerationSettings,
    defaults: GenerationSettings,
): FeaturedVisibility = section.visibility ?: defaults.visibility ?: FeaturedVisibility.INTERNAL

/**
 * Rejects local/remote settings that resolve to the same fully-qualified object name —
 * the two generated files would declare duplicate classes. The same simple name is fine
 * as long as the packages differ.
 */
internal fun validateDistinctObjectFqns(
    modulePath: String,
    local: ObjectCodegen,
    remote: ObjectCodegen,
) {
    val localFqn = "${local.packageName}.${ConfigParamGenerator.localObjectName(modulePath, local.objectName)}"
    val remoteFqn = "${remote.packageName}.${ConfigParamGenerator.remoteObjectName(modulePath, remote.objectName)}"
    require(localFqn != remoteFqn) {
        "featured: localFlags and remoteFlags generation settings both resolve to '$localFqn'. " +
            "Set distinct className or packageName values."
    }
}
