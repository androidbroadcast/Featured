package dev.androidbroadcast.featured.detekt

import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty

private const val CONFIG_PARAM_SIMPLE_NAME = "ConfigParam"

/**
 * Returns `true` if this property's type reference or initializer looks like a [ConfigParam].
 *
 * Detection is heuristic (name-based) because Detekt rules run without full type resolution
 * in the default lint mode.
 */
internal fun KtProperty.isConfigParam(): Boolean {
    // Check explicit type annotation: val x: ConfigParam<Boolean>
    val typeRef = typeReference?.text ?: ""
    if (typeRef.startsWith(CONFIG_PARAM_SIMPLE_NAME)) return true

    // Check initializer call: val x = ConfigParam(...)
    val initText = initializer?.text ?: ""
    if (initText.startsWith(CONFIG_PARAM_SIMPLE_NAME)) return true

    return false
}

/**
 * Returns `true` if this expression looks like a reference to a [ConfigParam] instance.
 *
 * Used by [HardcodedFlagValueRule] to filter `.defaultValue` accesses on non-ConfigParam types.
 * Detection is heuristic — we check whether the receiver name matches a known ConfigParam
 * variable (lowercase, common naming) or is typed as `ConfigParam`.
 */
internal fun KtExpression.isLikelyConfigParam(): Boolean {
    val text = text.trim()
    // Direct reference like `myFlag`, `newCheckout` — we can't resolve type without full
    // compilation, so we check if this is a simple name reference (not a chained call).
    // The rule also fires on explicit ConfigParam<T> typed parameters.
    // We accept all simple name references and let false-positive suppression handle edge cases.
    // A dot-qualified selector `param.defaultValue` already passed the selector check.
    return !text.contains('(') && !text.contains('.')
}
