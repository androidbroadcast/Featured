package dev.androidbroadcast.featured

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

enum class CheckoutVariant { LEGACY, NEW_SINGLE_PAGE, NEW_MULTI_STEP }

class EnumConfigParamTest {
    // --- TypeConverter round-trip ---

    @Test
    fun enumConverterSerializesAndDeserializesByName() {
        val converter = enumConverter<CheckoutVariant>()
        assertEquals("NEW_SINGLE_PAGE", converter.toString(CheckoutVariant.NEW_SINGLE_PAGE))
        assertEquals(CheckoutVariant.NEW_SINGLE_PAGE, converter.fromString("NEW_SINGLE_PAGE"))
    }

    @Test
    fun enumConverterThrowsOnUnknownName() {
        val converter = enumConverter<CheckoutVariant>()
        assertFailsWith<IllegalArgumentException> {
            converter.fromString("UNKNOWN_VARIANT")
        }
    }

    // --- TypeConverters registry ---

    @Test
    fun typeConvertersRegistryStoresAndRetrievesConverter() {
        val converters = TypeConverters()
        val converter = enumConverter<CheckoutVariant>()
        converters.put<CheckoutVariant>(converter)

        val retrieved = converters.get<CheckoutVariant>()
        assertNotNull(retrieved)
        assertEquals(CheckoutVariant.LEGACY, retrieved.fromString("LEGACY"))
    }

    @Test
    fun typeConvertersReturnsNullForUnregisteredType() {
        val converters = TypeConverters()
        assertNull(converters.get<CheckoutVariant>())
    }

    // --- InMemoryConfigValueProvider with enum ---

    @Test
    fun inMemoryProviderStoresAndRetrievesEnumValue() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val param =
                ConfigParam(
                    key = "checkout_variant",
                    defaultValue = CheckoutVariant.LEGACY,
                )

            provider.set(param, CheckoutVariant.NEW_SINGLE_PAGE)
            val result = provider.get(param)

            assertEquals(CheckoutVariant.NEW_SINGLE_PAGE, result?.value)
            assertEquals(ConfigValue.Source.LOCAL, result?.source)
        }

    @Test
    fun configValuesReturnsDefaultEnumWhenNoProviderSet() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val configValues = ConfigValues(localProvider = provider)
            val param =
                ConfigParam(
                    key = "checkout_variant",
                    defaultValue = CheckoutVariant.LEGACY,
                )

            val result = configValues.getValue(param)

            assertEquals(CheckoutVariant.LEGACY, result.value)
            assertEquals(ConfigValue.Source.DEFAULT, result.source)
        }

    @Test
    fun configValuesOverridesEnumAndObservesChange() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val configValues = ConfigValues(localProvider = provider)
            val param =
                ConfigParam(
                    key = "checkout_variant",
                    defaultValue = CheckoutVariant.LEGACY,
                )

            configValues.override(param, CheckoutVariant.NEW_MULTI_STEP)
            val result = configValues.getValue(param)

            assertEquals(CheckoutVariant.NEW_MULTI_STEP, result.value)
        }
}
