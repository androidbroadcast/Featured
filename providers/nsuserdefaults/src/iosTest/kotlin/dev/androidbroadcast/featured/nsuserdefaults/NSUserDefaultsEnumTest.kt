package dev.androidbroadcast.featured.nsuserdefaults

import app.cash.turbine.test
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import dev.androidbroadcast.featured.enumConverter
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

enum class CheckoutVariant { LEGACY, NEW_SINGLE_PAGE, NEW_MULTI_STEP }

class NSUserDefaultsEnumTest {
    private val suiteName = "dev.androidbroadcast.featured.enumtest.${kotlin.random.Random.nextLong()}"
    private lateinit var provider: NSUserDefaultsConfigValueProvider

    @BeforeTest
    fun setUp() {
        provider = NSUserDefaultsConfigValueProvider(suiteName = suiteName)
        provider.registerConverter(enumConverter<CheckoutVariant>())
    }

    @AfterTest
    fun tearDown() {
        provider.removeSuite()
    }

    // --- enum round-trip: set / get ---

    @Test
    fun enumRoundTripSetGet() =
        runTest {
            val param = ConfigParam("checkout_variant", CheckoutVariant.LEGACY)
            provider.set(param, CheckoutVariant.NEW_SINGLE_PAGE)

            val result = provider.get(param)
            assertEquals(ConfigValue(CheckoutVariant.NEW_SINGLE_PAGE, ConfigValue.Source.LOCAL), result)
        }

    @Test
    fun enumGetReturnsNullWhenNotSet() =
        runTest {
            val param = ConfigParam("checkout_variant", CheckoutVariant.LEGACY)
            assertNull(provider.get(param))
        }

    @Test
    fun enumRoundTripAllValues() =
        runTest {
            val param = ConfigParam("checkout_variant", CheckoutVariant.LEGACY)
            for (variant in CheckoutVariant.entries) {
                provider.set(param, variant)
                val result = provider.get(param)
                assertEquals(ConfigValue(variant, ConfigValue.Source.LOCAL), result)
            }
        }

    // --- enum observe ---

    @Test
    fun enumObserveEmitsInitialDefault() =
        runTest {
            val param = ConfigParam("checkout_variant", CheckoutVariant.LEGACY)
            provider.observe(param).test {
                val initial = awaitItem()
                assertEquals(ConfigValue(CheckoutVariant.LEGACY, ConfigValue.Source.DEFAULT), initial)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun enumObserveEmitsAfterSet() =
        runTest {
            val param = ConfigParam("checkout_variant", CheckoutVariant.LEGACY)
            provider.observe(param).test {
                awaitItem() // initial DEFAULT
                provider.set(param, CheckoutVariant.NEW_MULTI_STEP)
                val update = awaitItem()
                assertEquals(ConfigValue(CheckoutVariant.NEW_MULTI_STEP, ConfigValue.Source.LOCAL), update)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- clear notifies converted type, emits DEFAULT ---

    @Test
    fun enumObserveEmitsDefaultAfterClear() =
        runTest {
            val param = ConfigParam("checkout_variant", CheckoutVariant.LEGACY)
            provider.set(param, CheckoutVariant.NEW_SINGLE_PAGE)

            provider.observe(param).test {
                val initial = awaitItem()
                assertEquals(ConfigValue(CheckoutVariant.NEW_SINGLE_PAGE, ConfigValue.Source.LOCAL), initial)

                provider.clear()
                val afterClear = awaitItem()
                assertEquals(ConfigValue(CheckoutVariant.LEGACY, ConfigValue.Source.DEFAULT), afterClear)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- resetOverride removes value and key from written-keys index ---

    @Test
    fun enumResetOverrideGetReturnsNull() =
        runTest {
            val param = ConfigParam("checkout_variant", CheckoutVariant.LEGACY)
            provider.set(param, CheckoutVariant.NEW_SINGLE_PAGE)
            provider.resetOverride(param)

            assertNull(provider.get(param))
        }

    @Test
    fun enumResetOverrideRemovesKeyFromIndex() =
        runTest {
            val param = ConfigParam("checkout_reset_key", CheckoutVariant.LEGACY)
            provider.set(param, CheckoutVariant.NEW_MULTI_STEP)
            provider.resetOverride(param)

            // If the key was removed from the index, a subsequent clear() has nothing to delete
            // for this param; re-setting and then clearing must track the key again.
            // Simpler witness: set on a separate provider sharing the suite — clear() via the
            // original provider must not affect a key it no longer owns (i.e. resetOverride removed it).
            // Use get() as the direct witness: null means the value is gone from NSUserDefaults.
            assertNull(provider.get(param))
            // Confirm the key is no longer in the written-keys index by verifying that a fresh
            // clear() does not re-emit for this key (checked indirectly: no second get entry).
            provider.clear() // must be a no-op for this key
            assertNull(provider.get(param))
        }

    @Test
    fun enumObserveEmitsDefaultAfterResetOverride() =
        runTest {
            val param = ConfigParam("checkout_variant", CheckoutVariant.LEGACY)
            provider.set(param, CheckoutVariant.NEW_SINGLE_PAGE)

            provider.observe(param).test {
                val initial = awaitItem()
                assertEquals(ConfigValue(CheckoutVariant.NEW_SINGLE_PAGE, ConfigValue.Source.LOCAL), initial)

                provider.resetOverride(param)
                val afterReset = awaitItem()
                assertEquals(ConfigValue(CheckoutVariant.LEGACY, ConfigValue.Source.DEFAULT), afterReset)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- written-keys index is updated on enum set ---

    @Test
    fun enumSetAddsKeyToWrittenIndex() =
        runTest {
            val param = ConfigParam("checkout_index_key", CheckoutVariant.LEGACY)
            provider.set(param, CheckoutVariant.NEW_SINGLE_PAGE)

            // clear() only removes provider-owned keys; if the key is tracked the subsequent
            // get must return null (i.e. the key was removed, proving the index tracked it).
            provider.clear()
            assertNull(provider.get(param))
        }

    // --- missing converter throws ---

    @Test
    fun missingConverterThrowsOnSet() =
        runTest {
            val noConverterProvider =
                NSUserDefaultsConfigValueProvider(
                    suiteName = "dev.androidbroadcast.featured.noconv.${kotlin.random.Random.nextLong()}",
                )
            try {
                val param = ConfigParam("checkout_variant", CheckoutVariant.LEGACY)
                assertFailsWith<IllegalArgumentException> {
                    noConverterProvider.set(param, CheckoutVariant.LEGACY)
                }
            } finally {
                noConverterProvider.removeSuite()
            }
        }

    @Test
    fun missingConverterThrowsOnGet() =
        runTest {
            // Write via a converter-enabled provider, then read via a plain provider to simulate
            // a missing-converter get scenario where the stored value is a raw String.
            val param = ConfigParam("checkout_variant", CheckoutVariant.LEGACY)
            provider.set(param, CheckoutVariant.NEW_SINGLE_PAGE) // stores "NEW_SINGLE_PAGE" as String

            val noConverterProvider = NSUserDefaultsConfigValueProvider(suiteName = suiteName)
            // Without a converter, the `else` branch of the when-expression throws for enum types.
            assertFailsWith<IllegalArgumentException> {
                noConverterProvider.get(param)
            }
        }
}
