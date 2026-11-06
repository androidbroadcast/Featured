package dev.androidbroadcast.featured

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * JVM reflection check for the pre-scope constructor. commonTest cannot see Java constructors,
 * and already compiled callers link against this signature rather than the source overload.
 */
class ConfigParamConstructorSignatureTest {
    @Test
    fun oldConstructorParameterListDoesNotIncludeFlagScope() {
        val withoutScope =
            ConfigParam::class.java.declaredConstructors.filter { constructor ->
                constructor.parameterTypes.none { parameter -> parameter.name.contains("FlagScope") }
            }

        assertTrue(
            withoutScope.isNotEmpty(),
            "Expected a constructor whose parameter types do not include FlagScope",
        )
        assertTrue(
            withoutScope.any { constructor -> constructor.parameterCount == 7 },
            "Expected the 7-parameter constructor: key, default, valueType, description, category, since, enumConstants",
        )
    }
}
