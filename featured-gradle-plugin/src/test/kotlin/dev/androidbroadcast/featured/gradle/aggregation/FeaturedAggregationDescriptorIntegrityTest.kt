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
}
