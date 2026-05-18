package dev.androidbroadcast.featured.gradle.manifest

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeaturedManifestSerializationTest {
    @Test
    fun `round-trip produces identical object`() {
        val manifest =
            FeaturedManifest(
                schemaVersion = SCHEMA_VERSION,
                modulePath = ":feature:checkout",
                flags =
                    listOf(
                        FlagDescriptor(
                            key = "dark_mode",
                            propertyName = "darkMode",
                            kind = FlagKind.LOCAL,
                            valueType = ValueType.BOOLEAN,
                            defaultValue = "false",
                        ),
                    ),
            )

        val json = FeaturedManifestJson.encodeToString(manifest)
        val decoded = FeaturedManifestJson.decodeFromString<FeaturedManifest>(json)

        assertEquals(manifest, decoded)
    }

    @Test
    fun `schemaVersion is present explicitly in JSON output`() {
        val manifest =
            FeaturedManifest(
                schemaVersion = 1,
                modulePath = ":",
                flags = emptyList(),
            )

        val json = FeaturedManifestJson.encodeToString(manifest)

        assertTrue(json.contains("\"schemaVersion\""), "Expected 'schemaVersion' field in JSON")
        assertTrue(json.contains("\"schemaVersion\": 1"), "Expected schemaVersion value 1 in JSON")
    }

    @Test
    fun `empty flags list serializes as empty array not omitted`() {
        val manifest =
            FeaturedManifest(
                schemaVersion = SCHEMA_VERSION,
                modulePath = ":",
                flags = emptyList(),
            )

        val json = FeaturedManifestJson.encodeToString(manifest)

        // Must appear as "flags": [] not be absent
        assertTrue(json.contains("\"flags\": []"), "Expected 'flags': [] in JSON, got:\n$json")
    }

    @Test
    fun `null optional fields are omitted from JSON`() {
        val manifest =
            FeaturedManifest(
                schemaVersion = SCHEMA_VERSION,
                modulePath = ":app",
                flags =
                    listOf(
                        FlagDescriptor(
                            key = "feature",
                            propertyName = "feature",
                            kind = FlagKind.REMOTE,
                            valueType = ValueType.BOOLEAN,
                            defaultValue = "true",
                            enumTypeFqn = null,
                            description = null,
                            category = null,
                            expiresAt = null,
                        ),
                    ),
            )

        val json = FeaturedManifestJson.encodeToString(manifest)

        assertFalse(json.contains("enumTypeFqn"), "Null enumTypeFqn must be omitted from JSON")
        assertFalse(json.contains("description"), "Null description must be omitted from JSON")
        assertFalse(json.contains("category"), "Null category must be omitted from JSON")
        assertFalse(json.contains("expiresAt"), "Null expiresAt must be omitted from JSON")
    }

    @Test
    fun `enum flag round-trip preserves enumTypeFqn`() {
        val manifest =
            FeaturedManifest(
                schemaVersion = SCHEMA_VERSION,
                modulePath = ":feature:checkout",
                flags =
                    listOf(
                        FlagDescriptor(
                            key = "checkout_variant",
                            propertyName = "checkoutVariant",
                            kind = FlagKind.LOCAL,
                            valueType = ValueType.ENUM,
                            defaultValue = "LEGACY",
                            enumTypeFqn = "com.example.CheckoutVariant",
                        ),
                    ),
            )

        val json = FeaturedManifestJson.encodeToString(manifest)
        val decoded = FeaturedManifestJson.decodeFromString<FeaturedManifest>(json)

        assertEquals("com.example.CheckoutVariant", decoded.flags.first().enumTypeFqn)
        assertEquals("LEGACY", decoded.flags.first().defaultValue)
    }

    @Test
    fun `Float and Double valueTypes round-trip correctly`() {
        val flags =
            listOf(
                FlagDescriptor(
                    key = "float_flag",
                    propertyName = "floatFlag",
                    kind = FlagKind.LOCAL,
                    valueType = ValueType.FLOAT,
                    defaultValue = "1.5",
                ),
                FlagDescriptor(
                    key = "double_flag",
                    propertyName = "doubleFlag",
                    kind = FlagKind.REMOTE,
                    valueType = ValueType.DOUBLE,
                    defaultValue = "3.14",
                ),
            )
        val manifest = FeaturedManifest(schemaVersion = SCHEMA_VERSION, modulePath = ":", flags = flags)

        val json = FeaturedManifestJson.encodeToString(manifest)
        val decoded = FeaturedManifestJson.decodeFromString<FeaturedManifest>(json)

        assertEquals(ValueType.FLOAT, decoded.flags[0].valueType)
        assertEquals(ValueType.DOUBLE, decoded.flags[1].valueType)
    }

    @Test
    fun `unknown JSON field during decode does not throw (forward-compatible)`() {
        val json =
            """
            {
              "schemaVersion": 1,
              "modulePath": ":",
              "flags": [],
              "unknownFutureField": "some value"
            }
            """.trimIndent()

        // Should not throw — ignoreUnknownKeys = true in FeaturedManifestJson
        val manifest = FeaturedManifestJson.decodeFromString<FeaturedManifest>(json)
        assertEquals(1, manifest.schemaVersion)
        assertEquals(":", manifest.modulePath)
    }

    @Test
    fun `unknown enum variant in FlagKind throws SerializationException`() {
        val json =
            """
            {
              "schemaVersion": 1,
              "modulePath": ":",
              "flags": [
                {
                  "key": "f",
                  "propertyName": "f",
                  "kind": "UNKNOWN_KIND",
                  "valueType": "BOOLEAN",
                  "defaultValue": "false"
                }
              ]
            }
            """.trimIndent()

        // Silent skip of unknown enum variants would be a silent data-loss bug.
        // SerializationException is the expected behavior — fail fast.
        assertFailsWith<SerializationException> {
            FeaturedManifestJson.decodeFromString<FeaturedManifest>(json)
        }
    }

    @Test
    fun `unknown enum variant in ValueType throws SerializationException`() {
        val json =
            """
            {
              "schemaVersion": 1,
              "modulePath": ":",
              "flags": [
                {
                  "key": "f",
                  "propertyName": "f",
                  "kind": "LOCAL",
                  "valueType": "UNKNOWN_TYPE",
                  "defaultValue": "false"
                }
              ]
            }
            """.trimIndent()

        assertFailsWith<SerializationException> {
            FeaturedManifestJson.decodeFromString<FeaturedManifest>(json)
        }
    }

    @Test
    fun `all flag fields are preserved in round-trip`() {
        val flag =
            FlagDescriptor(
                key = "my_flag",
                propertyName = "myFlag",
                kind = FlagKind.REMOTE,
                valueType = ValueType.STRING,
                defaultValue = "hello world",
                enumTypeFqn = null,
                description = "A test flag",
                category = "test",
                expiresAt = "2026-12-31",
            )
        val manifest = FeaturedManifest(schemaVersion = SCHEMA_VERSION, modulePath = ":app", flags = listOf(flag))

        val decoded =
            FeaturedManifestJson.decodeFromString<FeaturedManifest>(
                FeaturedManifestJson.encodeToString(manifest),
            )

        val decodedFlag = decoded.flags.first()
        assertEquals(flag.key, decodedFlag.key)
        assertEquals(flag.propertyName, decodedFlag.propertyName)
        assertEquals(flag.kind, decodedFlag.kind)
        assertEquals(flag.valueType, decodedFlag.valueType)
        assertEquals(flag.defaultValue, decodedFlag.defaultValue)
        assertNull(decodedFlag.enumTypeFqn)
        assertEquals("A test flag", decodedFlag.description)
        assertEquals("test", decodedFlag.category)
        assertEquals("2026-12-31", decodedFlag.expiresAt)
    }
}
