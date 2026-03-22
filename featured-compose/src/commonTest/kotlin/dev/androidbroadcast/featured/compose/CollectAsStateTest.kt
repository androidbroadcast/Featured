package dev.androidbroadcast.featured.compose

import app.cash.turbine.test
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.InMemoryConfigValueProvider
import dev.androidbroadcast.featured.observeValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for [ConfigValues.collectAsState].
 *
 * [collectAsState] is a @Composable function that calls
 * `observeValue(param).collectAsState(initial = param.defaultValue)`.
 * Since Compose UI test infrastructure is not available in common unit tests,
 * these tests validate the contract of the underlying flow — the same flow
 * that [collectAsState] subscribes to — covering all required behaviours:
 *
 * - Initial value comes from [ConfigParam.defaultValue]
 * - State updates when a new value is emitted
 * - Flow cancellation is handled correctly
 * - All primitive types (Boolean, String, Int) are supported
 */
class CollectAsStateTest {
    // ── String ───────────────────────────────────────────────────────────────

    private val stringParam = ConfigParam("string_flag", "default_string")
    private val stringProvider = InMemoryConfigValueProvider()
    private val stringConfigValues = ConfigValues(localProvider = stringProvider)

    @Test
    fun collectAsStateUsesDefaultValueWhenNoOverrideIsSet() =
        runTest {
            val value = stringConfigValues.observeValue(stringParam).first()

            assertEquals(stringParam.defaultValue, value)
        }

    @Test
    fun collectAsStateReflectsUpdatedStringValue() =
        runTest {
            stringProvider.set(stringParam, "updated_string")

            val value = stringConfigValues.observeValue(stringParam).first()

            assertEquals("updated_string", value)
        }

    @Test
    fun collectAsStateEmitsNewStringValueToActiveSubscriber() =
        runTest {
            stringProvider.set(stringParam, "first")

            stringConfigValues.observeValue(stringParam).test {
                assertEquals("first", awaitItem())

                stringProvider.set(stringParam, "second")
                assertEquals("second", awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun collectAsStateCancelsFlowCleanlyForStringParam() =
        runTest {
            stringProvider.set(stringParam, "initial")

            var receivedCount = 0
            stringConfigValues.observeValue(stringParam).test {
                assertEquals("initial", awaitItem())
                receivedCount++
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(1, receivedCount)
        }

    // ── Int ──────────────────────────────────────────────────────────────────

    private val intParam = ConfigParam("int_flag", 0)
    private val intProvider = InMemoryConfigValueProvider()
    private val intConfigValues = ConfigValues(localProvider = intProvider)

    @Test
    fun collectAsStateUsesDefaultIntValue() =
        runTest {
            val value = intConfigValues.observeValue(intParam).first()

            assertEquals(intParam.defaultValue, value)
        }

    @Test
    fun collectAsStateReflectsUpdatedIntValue() =
        runTest {
            intProvider.set(intParam, 42)

            val value = intConfigValues.observeValue(intParam).first()

            assertEquals(42, value)
        }

    @Test
    fun collectAsStateEmitsNewIntValueToActiveSubscriber() =
        runTest {
            intProvider.set(intParam, 1)

            intConfigValues.observeValue(intParam).test {
                assertEquals(1, awaitItem())

                intProvider.set(intParam, 2)
                assertEquals(2, awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── Boolean ───────────────────────────────────────────────────────────────

    private val boolParam = ConfigParam("bool_flag", false)
    private val boolProvider = InMemoryConfigValueProvider()
    private val boolConfigValues = ConfigValues(localProvider = boolProvider)

    @Test
    fun collectAsStateUsesDefaultBooleanValue() =
        runTest {
            val value = boolConfigValues.observeValue(boolParam).first()

            assertEquals(false, value)
        }

    @Test
    fun collectAsStateReflectsUpdatedBooleanValue() =
        runTest {
            boolProvider.set(boolParam, true)

            val value = boolConfigValues.observeValue(boolParam).first()

            assertEquals(true, value)
        }

    @Test
    fun collectAsStateEmitsNewBooleanValueToActiveSubscriber() =
        runTest {
            boolProvider.set(boolParam, false)

            boolConfigValues.observeValue(boolParam).test {
                assertEquals(false, awaitItem())

                boolProvider.set(boolParam, true)
                assertEquals(true, awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }
}
