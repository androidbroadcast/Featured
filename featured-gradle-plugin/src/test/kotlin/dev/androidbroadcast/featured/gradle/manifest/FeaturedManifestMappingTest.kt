package dev.androidbroadcast.featured.gradle.manifest

import dev.androidbroadcast.featured.gradle.LocalFlagEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeaturedManifestMappingTest {
    // ── ValueType mapping ──────────────────────────────────────────────────────

    @Test
    fun `Boolean type maps to BOOLEAN ValueType`() {
        val entry = localEntry(key = "flag", type = "Boolean", defaultValue = "false")
        val descriptor = entry.toFlagDescriptor()
        assertEquals(ValueType.BOOLEAN, descriptor.valueType)
    }

    @Test
    fun `Int type maps to INT ValueType`() {
        val entry = localEntry(key = "flag", type = "Int", defaultValue = "0")
        val descriptor = entry.toFlagDescriptor()
        assertEquals(ValueType.INT, descriptor.valueType)
    }

    @Test
    fun `Long type maps to LONG ValueType`() {
        val entry = localEntry(key = "flag", type = "Long", defaultValue = "0")
        val descriptor = entry.toFlagDescriptor()
        assertEquals(ValueType.LONG, descriptor.valueType)
    }

    @Test
    fun `Float type maps to FLOAT ValueType`() {
        val entry = localEntry(key = "flag", type = "Float", defaultValue = "1.5")
        val descriptor = entry.toFlagDescriptor()
        assertEquals(ValueType.FLOAT, descriptor.valueType)
    }

    @Test
    fun `Double type maps to DOUBLE ValueType`() {
        val entry = localEntry(key = "flag", type = "Double", defaultValue = "3.14")
        val descriptor = entry.toFlagDescriptor()
        assertEquals(ValueType.DOUBLE, descriptor.valueType)
    }

    @Test
    fun `String type maps to STRING ValueType`() {
        val entry = localEntry(key = "flag", type = "String", defaultValue = "\"hello\"")
        val descriptor = entry.toFlagDescriptor()
        assertEquals(ValueType.STRING, descriptor.valueType)
    }

    // ── FlagKind mapping ───────────────────────────────────────────────────────

    @Test
    fun `local flagType maps to LOCAL FlagKind`() {
        val entry = localEntry(key = "flag", type = "Boolean", defaultValue = "false", flagType = LocalFlagEntry.FLAG_TYPE_LOCAL)
        assertEquals(FlagKind.LOCAL, entry.toFlagDescriptor().kind)
    }

    @Test
    fun `remote flagType maps to REMOTE FlagKind`() {
        val entry = localEntry(key = "flag", type = "Boolean", defaultValue = "false", flagType = LocalFlagEntry.FLAG_TYPE_REMOTE)
        assertEquals(FlagKind.REMOTE, entry.toFlagDescriptor().kind)
    }

    // ── String default value unwrapping ────────────────────────────────────────

    @Test
    fun `String defaultValue with surrounding quotes is unwrapped`() {
        val entry = localEntry(key = "greeting", type = "String", defaultValue = "\"hello\"")
        val descriptor = entry.toFlagDescriptor()
        assertEquals("hello", descriptor.defaultValue)
    }

    @Test
    fun `String defaultValue without surrounding quotes is kept as-is`() {
        // ScanResultParser stores the raw value; this tests what happens with bare strings.
        val entry = localEntry(key = "greeting", type = "String", defaultValue = "hello")
        val descriptor = entry.toFlagDescriptor()
        // removeSurrounding("\"") does nothing when the value does not start and end with "
        assertEquals("hello", descriptor.defaultValue)
    }

    // ── Enum mapping ──────────────────────────────────────────────────────────

    @Test
    fun `enum entry maps to ENUM ValueType with enumTypeFqn and stripped constant name`() {
        val entry =
            LocalFlagEntry(
                key = "checkout_variant",
                defaultValue = "com.example.CheckoutVariant.FAST",
                type = "com.example.CheckoutVariant",
                moduleName = ":app",
                propertyName = "checkoutVariant",
                flagType = LocalFlagEntry.FLAG_TYPE_LOCAL,
            )
        assertTrue(entry.isEnum, "Expected isEnum = true for FQN type")
        val descriptor = entry.toFlagDescriptor()
        assertEquals(ValueType.ENUM, descriptor.valueType)
        assertEquals("com.example.CheckoutVariant", descriptor.enumTypeFqn)
        // Only the constant name — not the FQN — is stored in defaultValue.
        assertEquals("FAST", descriptor.defaultValue)
    }

    @Test
    fun `enum entry does not strip enumTypeFqn when isEnum is false`() {
        // isEnum is computed as '.' in type — a type without dots is not an enum.
        val entry = localEntry(key = "flag", type = "Boolean", defaultValue = "false")
        assertNull(entry.toFlagDescriptor().enumTypeFqn)
    }

    // ── Unknown type error ─────────────────────────────────────────────────────

    @Test
    fun `unknown type throws IllegalStateException containing type and key`() {
        val entry = localEntry(key = "my_date_flag", type = "Date", defaultValue = "2026-01-01")
        val ex = assertFailsWith<IllegalStateException> { entry.toFlagDescriptor() }
        assertTrue(ex.message?.contains("Date") == true, "Error message must contain the type 'Date', got: ${ex.message}")
        assertTrue(ex.message?.contains("my_date_flag") == true, "Error message must contain the key 'my_date_flag', got: ${ex.message}")
    }

    // ── Optional metadata fields ───────────────────────────────────────────────

    @Test
    fun `null optional fields are passed through as null`() {
        val entry =
            LocalFlagEntry(
                key = "flag",
                defaultValue = "false",
                type = "Boolean",
                moduleName = ":app",
                propertyName = "flag",
                description = null,
                category = null,
                expiresAt = null,
            )
        val descriptor = entry.toFlagDescriptor()
        assertNull(descriptor.description)
        assertNull(descriptor.category)
        assertNull(descriptor.expiresAt)
    }

    @Test
    fun `non-null optional fields are preserved in FlagDescriptor`() {
        val entry =
            LocalFlagEntry(
                key = "flag",
                defaultValue = "false",
                type = "Boolean",
                moduleName = ":app",
                propertyName = "flag",
                description = "A useful flag",
                category = "UI",
                expiresAt = "2027-01-01",
            )
        val descriptor = entry.toFlagDescriptor()
        assertEquals("A useful flag", descriptor.description)
        assertEquals("UI", descriptor.category)
        assertEquals("2027-01-01", descriptor.expiresAt)
    }

    // ── Non-ASCII key ──────────────────────────────────────────────────────────

    @Test
    fun `non-ASCII key is passed through to FlagDescriptor unchanged`() {
        // toCamelCase() splits on '_' and uppercases each word's first char.
        // For "тёмная_тема": ["тёмная", "тема"] → "тёмная" + "Тема" = "тёмнаяТема"
        val entry =
            LocalFlagEntry(
                key = "тёмная_тема",
                defaultValue = "false",
                type = "Boolean",
                moduleName = ":app",
                propertyName =
                    "тёмная_тема"
                        .split("_")
                        .mapIndexed { i, w ->
                            if (i == 0) w.lowercase() else w.replaceFirstChar { it.uppercase() }
                        }.joinToString(""),
            )
        val descriptor = entry.toFlagDescriptor()
        assertEquals("тёмная_тема", descriptor.key)
        // propertyName is passed through as-is from the entry.
        assertEquals("тёмнаяТема", descriptor.propertyName)
    }

    // ── Pipe separator in String default value ─────────────────────────────────

    @Test
    fun `pipe character in String default value is a known parser limitation`() {
        // NOTE: ScanResultParser splits lines by '|' — strings whose value contains '|' break
        // the pipe-delimited format and inflate the field count past the supported sizes
        // (4 / 6 / 7 / 9). Lines that do not match a known field count are silently dropped
        // (parseLine returns null).
        //
        // FlagContainer.string() wraps the default in escaped quotes when serialising, so the
        // raw line for `string("my_flag", default = "a|b")` looks like:
        //   my_flag|"a|b"|String|:app|myFlag|local|||
        // which splits into 10 parts instead of the expected 9 — the parser silently drops it.
        //
        // This test documents the limitation; a future minor PR may add `require('|' !in default)`
        // to FlagContainer.string() to fail fast at configuration time instead of silently.
        val rawLine = "my_flag|\"a|b\"|String|:app|myFlag|local|||"
        val parts = rawLine.split("|")
        assertEquals(
            10,
            parts.size,
            "A '|' inside defaultValue inflates the field count past 9; parser will return null and silently drop the entry",
        )
    }

    // ── Same key in local and remote ───────────────────────────────────────────

    @Test
    fun `same key for local and remote entries produces distinct FlagDescriptors with different kinds`() {
        // Conflict detection (which entry wins, deduplication) is handled in PR B.
        // The mapper itself produces two FlagDescriptors and does not deduplicate.
        val local = localEntry(key = "promo", type = "Boolean", defaultValue = "false", flagType = LocalFlagEntry.FLAG_TYPE_LOCAL)
        val remote = localEntry(key = "promo", type = "Boolean", defaultValue = "false", flagType = LocalFlagEntry.FLAG_TYPE_REMOTE)

        val localDescriptor = local.toFlagDescriptor()
        val remoteDescriptor = remote.toFlagDescriptor()

        assertEquals("promo", localDescriptor.key)
        assertEquals("promo", remoteDescriptor.key)
        assertEquals(FlagKind.LOCAL, localDescriptor.kind)
        assertEquals(FlagKind.REMOTE, remoteDescriptor.kind)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun localEntry(
        key: String,
        type: String,
        defaultValue: String,
        flagType: String = LocalFlagEntry.FLAG_TYPE_LOCAL,
        propertyName: String = key,
    ): LocalFlagEntry =
        LocalFlagEntry(
            key = key,
            defaultValue = defaultValue,
            type = type,
            moduleName = ":app",
            propertyName = propertyName,
            flagType = flagType,
        )
}
