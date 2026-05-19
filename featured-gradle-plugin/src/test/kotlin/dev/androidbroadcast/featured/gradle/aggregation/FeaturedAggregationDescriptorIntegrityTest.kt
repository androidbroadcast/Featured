package dev.androidbroadcast.featured.gradle.aggregation

import dev.androidbroadcast.featured.gradle.manifest.FeaturedManifest
import dev.androidbroadcast.featured.gradle.manifest.FlagDescriptor
import dev.androidbroadcast.featured.gradle.manifest.FlagKind
import dev.androidbroadcast.featured.gradle.manifest.SCHEMA_VERSION
import dev.androidbroadcast.featured.gradle.manifest.ValueType
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class FeaturedAggregationDescriptorIntegrityTest {
    private fun enumFlag(
        key: String = "checkout_variant",
        enumTypeFqn: String? = "com.example.CheckoutVariant",
        defaultValue: String = "LEGACY",
    ) = FlagDescriptor(
        key = key,
        propertyName = key,
        kind = FlagKind.LOCAL,
        valueType = ValueType.ENUM,
        defaultValue = defaultValue,
        enumTypeFqn = enumTypeFqn,
    )

    private fun primitiveFlag(
        key: String = "some_flag",
        valueType: ValueType,
        defaultValue: String,
    ) = FlagDescriptor(
        key = key,
        propertyName = key,
        kind = FlagKind.LOCAL,
        valueType = valueType,
        defaultValue = defaultValue,
        enumTypeFqn = null,
    )

    private fun singleManifest(flag: FlagDescriptor) =
        listOf(
            FeaturedManifest(
                schemaVersion = SCHEMA_VERSION,
                modulePath = ":feature-a",
                flags = listOf(flag),
            ),
        )

    @Test
    fun `valid ENUM flag with FQN and identifier constant does not throw`() {
        // Sanity: a well-formed manifest passes without exception.
        validateFlagDescriptorIntegrity(singleManifest(enumFlag()))
    }

    @Test
    fun `ENUM flag with semicolon in FQN throws IllegalArgumentException naming key and module`() {
        // Simulates a malicious FQN that would inject a Kotlin init block into the generated source.
        val maliciousFqn = "kotlin.Unit>(); init { injectCode() }; private val x: ConfigParam<kotlin.Unit"
        val ex =
            assertFailsWith<IllegalArgumentException> {
                validateFlagDescriptorIntegrity(singleManifest(enumFlag(enumTypeFqn = maliciousFqn)))
            }
        val msg = ex.message ?: ""
        assertContains(msg, "checkout_variant", message = "Message must name the flag key")
        assertContains(msg, ":feature-a", message = "Message must name the module path")
    }

    @Test
    fun `ENUM flag with angle bracket in FQN throws`() {
        assertFailsWith<IllegalArgumentException> {
            validateFlagDescriptorIntegrity(singleManifest(enumFlag(enumTypeFqn = "com.example<T>.Foo")))
        }
    }

    @Test
    fun `ENUM flag with parenthesis in FQN throws`() {
        assertFailsWith<IllegalArgumentException> {
            validateFlagDescriptorIntegrity(singleManifest(enumFlag(enumTypeFqn = "com.example().Foo")))
        }
    }

    @Test
    fun `ENUM flag with brace in FQN throws`() {
        assertFailsWith<IllegalArgumentException> {
            validateFlagDescriptorIntegrity(singleManifest(enumFlag(enumTypeFqn = "com.example{}.Foo")))
        }
    }

    @Test
    fun `ENUM flag with space in FQN throws`() {
        assertFailsWith<IllegalArgumentException> {
            validateFlagDescriptorIntegrity(singleManifest(enumFlag(enumTypeFqn = "com.example .Foo")))
        }
    }

    @Test
    fun `ENUM flag with Unicode line separator in FQN throws`() {
        // U+2028 LINE SEPARATOR — not a valid Kotlin identifier character; must be rejected.
        val fqnWithLineSeparator = "com.example Foo"
        assertFailsWith<IllegalArgumentException> {
            validateFlagDescriptorIntegrity(singleManifest(enumFlag(enumTypeFqn = fqnWithLineSeparator)))
        }
    }

    @Test
    fun `ENUM flag with injection in defaultValue throws`() {
        // Simulates a malicious constant name that would inject statements into the generated source.
        val maliciousDefault = "INSTANCE; injectCode()"
        val ex =
            assertFailsWith<IllegalArgumentException> {
                validateFlagDescriptorIntegrity(singleManifest(enumFlag(defaultValue = maliciousDefault)))
            }
        val msg = ex.message ?: ""
        assertContains(msg, "checkout_variant", message = "Message must name the flag key")
        assertContains(msg, ":feature-a", message = "Message must name the module path")
    }

    @Test
    fun `ENUM flag with null enumTypeFqn throws IllegalArgumentException naming key and module`() {
        val ex =
            assertFailsWith<IllegalArgumentException> {
                validateFlagDescriptorIntegrity(singleManifest(enumFlag(enumTypeFqn = null)))
            }
        val msg = ex.message ?: ""
        assertContains(msg, "checkout_variant", message = "Message must name the flag key")
        assertContains(msg, ":feature-a", message = "Message must name the module path")
    }

    // --- Primitive defaultValue validation tests ---

    @Test
    fun `BOOLEAN defaultValue 'true' does not throw`() {
        validateFlagDescriptorIntegrity(singleManifest(primitiveFlag(valueType = ValueType.BOOLEAN, defaultValue = "true")))
    }

    @Test
    fun `BOOLEAN defaultValue 'false' does not throw`() {
        validateFlagDescriptorIntegrity(singleManifest(primitiveFlag(valueType = ValueType.BOOLEAN, defaultValue = "false")))
    }

    @Test
    fun `BOOLEAN defaultValue with appended statement throws naming key and module`() {
        // Simulates injection of an extra statement appended to the boolean literal.
        val ex =
            assertFailsWith<IllegalArgumentException> {
                validateFlagDescriptorIntegrity(
                    singleManifest(primitiveFlag(key = "some_flag", valueType = ValueType.BOOLEAN, defaultValue = "true; init { evil() }")),
                )
            }
        val msg = ex.message ?: ""
        assertContains(msg, "some_flag", message = "Message must name the flag key")
        assertContains(msg, ":feature-a", message = "Message must name the module path")
    }

    @Test
    fun `INT defaultValue with method-call suffix throws`() {
        // The exact attack vector from the security review: 0.also { ... } is a valid Kotlin expression
        // but must not be emitted verbatim as a ConfigParam defaultValue literal.
        val ex =
            assertFailsWith<IllegalArgumentException> {
                validateFlagDescriptorIntegrity(
                    singleManifest(primitiveFlag(key = "some_flag", valueType = ValueType.INT, defaultValue = "0.also { injectCode() }")),
                )
            }
        val msg = ex.message ?: ""
        assertContains(msg, "some_flag", message = "Message must name the flag key")
        assertContains(msg, ":feature-a", message = "Message must name the module path")
    }

    @Test
    fun `INT defaultValue '-42' does not throw`() {
        // Negative integers are valid and must be allowed.
        validateFlagDescriptorIntegrity(singleManifest(primitiveFlag(valueType = ValueType.INT, defaultValue = "-42")))
    }

    @Test
    fun `LONG defaultValue max signed 64-bit value does not throw`() {
        validateFlagDescriptorIntegrity(
            singleManifest(primitiveFlag(valueType = ValueType.LONG, defaultValue = "9223372036854775807")),
        )
    }

    @Test
    fun `FLOAT defaultValue '3_14' does not throw`() {
        validateFlagDescriptorIntegrity(singleManifest(primitiveFlag(valueType = ValueType.FLOAT, defaultValue = "3.14")))
    }

    @Test
    fun `FLOAT defaultValue with non-numeric prefix throws`() {
        val ex =
            assertFailsWith<IllegalArgumentException> {
                validateFlagDescriptorIntegrity(
                    singleManifest(primitiveFlag(key = "some_flag", valueType = ValueType.FLOAT, defaultValue = "NaN; injectCode()")),
                )
            }
        val msg = ex.message ?: ""
        assertContains(msg, "some_flag", message = "Message must name the flag key")
        assertContains(msg, ":feature-a", message = "Message must name the module path")
    }

    @Test
    fun `DOUBLE defaultValue scientific notation does not throw`() {
        validateFlagDescriptorIntegrity(singleManifest(primitiveFlag(valueType = ValueType.DOUBLE, defaultValue = "1.5e10")))
    }

    @Test
    fun `DOUBLE defaultValue with brace injection throws`() {
        val ex =
            assertFailsWith<IllegalArgumentException> {
                validateFlagDescriptorIntegrity(
                    singleManifest(primitiveFlag(key = "some_flag", valueType = ValueType.DOUBLE, defaultValue = "1.5} init { evil() }")),
                )
            }
        val msg = ex.message ?: ""
        assertContains(msg, "some_flag", message = "Message must name the flag key")
        assertContains(msg, ":feature-a", message = "Message must name the module path")
    }
}
