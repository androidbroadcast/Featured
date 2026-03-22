package dev.androidbroadcast.featured

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResetOverrideTest {
    @Test
    fun resetOverride_removesLocalValue_soGetReturnsNull() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val param = ConfigParam(key = "flag", defaultValue = true)
            provider.set(param, false)

            provider.resetOverride(param)

            assertNull(provider.get(param))
        }

    @Test
    fun resetOverride_doesNotAffectOtherKeys() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val param1 = ConfigParam(key = "flag1", defaultValue = true)
            val param2 = ConfigParam(key = "flag2", defaultValue = 42)
            provider.set(param1, false)
            provider.set(param2, 99)

            provider.resetOverride(param1)

            assertNull(provider.get(param1))
            assertEquals(99, provider.get(param2)?.value)
        }

    @Test
    fun resetOverride_onConfigValues_fallsBackToDefault() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val param = ConfigParam(key = "greeting", defaultValue = "hello")
            val configValues = ConfigValues(localProvider = provider)
            configValues.override(param, "world")

            configValues.resetOverride(param)

            val result = configValues.getValue(param)
            assertEquals("hello", result.value)
            assertEquals(ConfigValue.Source.DEFAULT, result.source)
        }

    @Test
    fun resetOverride_onConfigValues_withNoLocalProvider_doesNotThrow() =
        runTest {
            // When no local provider is configured, resetOverride is a no-op
            val remote =
                object : RemoteConfigValueProvider {
                    override suspend fun fetch(activate: Boolean) = Unit

                    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? = null
                }
            val configValues = ConfigValues(remoteProvider = remote)
            val param = ConfigParam(key = "x", defaultValue = 0)

            // Should not throw
            configValues.resetOverride(param)
        }
}
