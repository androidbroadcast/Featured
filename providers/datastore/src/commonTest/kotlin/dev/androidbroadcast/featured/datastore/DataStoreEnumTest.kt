package dev.androidbroadcast.featured.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import dev.androidbroadcast.featured.enumConverter
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

enum class CheckoutVariant { LEGACY, NEW_SINGLE_PAGE, NEW_MULTI_STEP }

class DataStoreEnumTest {
    private val testScope = TestScope()

    private fun createProvider(): DataStoreConfigValueProvider {
        val dataStore =
            PreferenceDataStoreFactory.createWithPath(
                scope = testScope,
                produceFile = { "test_${kotlin.random.Random.nextLong()}.preferences_pb".toPath() },
            )
        return DataStoreConfigValueProvider(dataStore)
    }

    @Test
    fun enumRoundTripWithRegisteredConverter() =
        testScope.runTest {
            val provider = createProvider()
            val converter = enumConverter<CheckoutVariant>()
            provider.registerConverter(converter)

            val param = ConfigParam("checkout_variant", CheckoutVariant.LEGACY)
            provider.set(param, CheckoutVariant.NEW_SINGLE_PAGE)

            val result = provider.get(param)
            assertNotNull(result)
            assertEquals(CheckoutVariant.NEW_SINGLE_PAGE, result.value)
            assertEquals(ConfigValue.Source.LOCAL, result.source)
        }

    @Test
    fun enumGetReturnsNullWhenNotSet() =
        testScope.runTest {
            val provider = createProvider()
            provider.registerConverter(enumConverter<CheckoutVariant>())

            val param = ConfigParam("checkout_variant", CheckoutVariant.LEGACY)
            val result = provider.get(param)
            assertNull(result)
        }

    @Test
    fun enumThrowsOnUnsupportedTypeWithoutConverter() =
        testScope.runTest {
            val provider = createProvider()
            val param = ConfigParam("checkout_variant", CheckoutVariant.LEGACY)
            assertFailsWith<IllegalArgumentException> {
                provider.set(param, CheckoutVariant.LEGACY)
            }
        }

    @Test
    fun clear_removesAllStoredValues_soGetReturnsNull() =
        testScope.runTest {
            val provider = createProvider()
            val stringParam = ConfigParam("string_flag", "default")
            val intParam = ConfigParam("int_flag", 0)
            provider.set(stringParam, "value")
            provider.set(intParam, 99)

            provider.clear()

            assertNull(provider.get(stringParam))
            assertNull(provider.get(intParam))
        }

    @Test
    fun clear_asLocalConfigValueProvider_removesAllValues() =
        testScope.runTest {
            val provider: dev.androidbroadcast.featured.LocalConfigValueProvider = createProvider()
            val param = ConfigParam("flag", false)
            provider.set(param, true)

            provider.clear()

            assertNull(provider.get(param))
        }
}
