package dev.androidbroadcast.featured

import app.cash.turbine.test
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryConfigValueProviderTest {
    @Test
    fun testGetNonExistentParam() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val param = ConfigParam("non_existent", "default")

            val result = provider.get(param)
            assertNull(result)
        }

    @Test
    fun testSetAndGetStringParam() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val param = ConfigParam("string_key", "default")

            provider.set(param, "test_value")
            val result = provider.get(param)

            assertEquals("test_value", result?.value)
            assertEquals(ConfigValue.Source.LOCAL, result?.source)
        }

    @Test
    fun testSetAndGetIntParam() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val param = ConfigParam("int_key", 0)

            provider.set(param, 42)
            val result = provider.get(param)

            assertEquals(42, result?.value)
            assertEquals(ConfigValue.Source.LOCAL, result?.source)
        }

    @Test
    fun testSetAndGetBooleanParam() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val param = ConfigParam("bool_key", false)

            provider.set(param, true)
            val result = provider.get(param)

            assertEquals(true, result?.value)
            assertEquals(ConfigValue.Source.LOCAL, result?.source)
        }

    @Test
    fun testOverwriteExistingValue() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val param = ConfigParam("key", "initial")

            provider.set(param, "first_value")
            provider.set(param, "second_value")

            val result = provider.get(param)
            assertEquals("second_value", result?.value)
        }

    @Test
    fun testClear() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val param = ConfigParam("key", "default")

            provider.set(param, "value")
            assertEquals("value", provider.get(param)?.value)

            provider.clear()
            assertNull(provider.get(param))
        }

    @Test
    fun clear_removesAllStoredOverrides() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val param1 = ConfigParam("key1", "default1")
            val param2 = ConfigParam("key2", 99)
            provider.set(param1, "value1")
            provider.set(param2, 42)

            provider.clear()

            assertNull(provider.get(param1))
            assertNull(provider.get(param2))
        }

    @Test
    fun clear_asLocalConfigValueProvider_removesAllOverrides() =
        runTest {
            // Verify clear() is callable through the interface
            val provider: LocalConfigValueProvider = InMemoryConfigValueProvider()
            val param = ConfigParam("key", false)
            provider.set(param, true)

            provider.clear()

            assertNull(provider.get(param))
        }

    @Test
    fun testObserveInitialValue() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val param = ConfigParam("observe_key", "default")

            provider.set(param, "initial_value")

            provider.observe(param).test {
                val emission = awaitItem()
                assertEquals("initial_value", emission.value)
                assertEquals(ConfigValue.Source.LOCAL, emission.source)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun testObserveValueChanges() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val param = ConfigParam("observe_key", "default")

            provider.set(param, "initial")

            provider.observe(param).test {
                // Initial value
                assertEquals("initial", awaitItem().value)

                // Update value
                provider.set(param, "updated")
                assertEquals("updated", awaitItem().value)

                // Update again
                provider.set(param, "final")
                assertEquals("final", awaitItem().value)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun observeEmitsDefaultImmediately_whenKeyNeverWritten() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val param = ConfigParam("non_existent", "my_default")

            provider.observe(param).test {
                // Contract: always emits immediately — DEFAULT when key is absent
                val initial = awaitItem()
                assertEquals("my_default", initial.value)
                assertEquals(ConfigValue.Source.DEFAULT, initial.source)

                // Subsequent set emits LOCAL
                provider.set(param, "new_value")
                val afterSet = awaitItem()
                assertEquals("new_value", afterSet.value)
                assertEquals(ConfigValue.Source.LOCAL, afterSet.source)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun testObserveMultipleParams() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val param1 = ConfigParam("key1", "default1")
            val param2 = ConfigParam("key2", "default2")

            provider.set(param1, "value1")
            provider.set(param2, "value2")

            provider.observe(param1).test {
                assertEquals("value1", awaitItem().value)

                // Update param2 - should not affect param1 observer
                provider.set(param2, "updated2")

                // Update param1 - should affect param1 observer
                provider.set(param1, "updated1")
                assertEquals("updated1", awaitItem().value)

                cancelAndIgnoreRemainingEvents()
            }
        }

    // G2: clear() emits DEFAULT for every key that was set — contract change from prior behavior.
    @Test
    fun clearEmitsDefaultForPreviouslySetKey() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val param = ConfigParam("key", "default")
            provider.set(param, "value")

            provider.observe(param).test {
                // consume initial LOCAL emission
                awaitItem()

                provider.clear()

                val afterClear = awaitItem()
                assertEquals("default", afterClear.value)
                assertEquals(ConfigValue.Source.DEFAULT, afterClear.source)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun clearEmitsDefaultForEachSetKey() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val param1 = ConfigParam("k1", "d1")
            val param2 = ConfigParam("k2", "d2")
            provider.set(param1, "v1")
            provider.set(param2, "v2")

            provider.observe(param1).test {
                awaitItem() // initial for param1

                provider.observe(param2).test {
                    awaitItem() // initial for param2

                    provider.clear()

                    // Both observers must receive their DEFAULT frame.
                    val afterClear2 = awaitItem()
                    assertEquals("d2", afterClear2.value)
                    assertEquals(ConfigValue.Source.DEFAULT, afterClear2.source)

                    cancelAndIgnoreRemainingEvents()
                }

                // Outer (param1) observer must also have received its DEFAULT frame.
                val afterClear1 = awaitItem()
                assertEquals("d1", afterClear1.value)
                assertEquals(ConfigValue.Source.DEFAULT, afterClear1.source)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun observeAfterClearEmitsDefaultAsFirstFrame() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val param = ConfigParam("after_clear_key", "my_default")
            provider.set(param, "stored_value")

            provider.clear()

            // Subscriber attached AFTER clear() — must see DEFAULT as the first frame, not LOCAL.
            provider.observe(param).test {
                val first = awaitItem()
                assertEquals("my_default", first.value)
                assertEquals(ConfigValue.Source.DEFAULT, first.source)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun clearWithNoStoredKeysIsNoOp() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val param = ConfigParam("any_key", "default")

            provider.observe(param).test {
                awaitItem() // initial DEFAULT

                // clear() on empty storage must not emit and must not crash.
                provider.clear()
                expectNoEvents()

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun clearTwiceSecondIsNoOp() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val param = ConfigParam("double_clear_key", "default")
            provider.set(param, "value")

            provider.observe(param).test {
                awaitItem() // initial LOCAL

                provider.clear()
                val afterFirst = awaitItem()
                assertEquals(ConfigValue.Source.DEFAULT, afterFirst.source)

                // Second clear — storage is already empty, must produce no emissions.
                provider.clear()
                expectNoEvents()

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun clearDoesNotEmitForKeyNeverSet() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val setParam = ConfigParam("set_key", "default_set")
            val unsetParam = ConfigParam("unset_key", "default_unset")
            provider.set(setParam, "value")

            provider.observe(unsetParam).test {
                awaitItem() // initial DEFAULT (key never set)

                provider.clear()

                // unset_key was not in storage — clear() must not emit for it
                expectNoEvents()

                cancelAndIgnoreRemainingEvents()
            }
        }

    // Parallel resetOverride of N keys must not lose any removals.
    @Test
    fun parallelResetOverrideOfNKeysLosesNoRemovals() =
        runTest {
            val n = 50
            val provider = InMemoryConfigValueProvider()
            val params = List(n) { i -> ConfigParam("pk$i", i) }
            params.forEach { p -> provider.set(p, p.defaultValue + 1) }

            params
                .map { p ->
                    launch { provider.resetOverride(p) }
                }.forEach { it.join() }

            params.forEach { p ->
                assertNull(provider.get(p), "key ${p.key} should be absent after resetOverride")
            }
        }

    // G3: resetOverride after set → next frame is DEFAULT-sourced.
    @Test
    fun resetOverride_afterSet_emitsDefaultSourcedFrame() =
        runTest {
            val provider = InMemoryConfigValueProvider()
            val param = ConfigParam("key", "my_default")
            provider.set(param, "value")

            provider.observe(param).test {
                // Initial LOCAL frame
                val initial = awaitItem()
                assertEquals("value", initial.value)
                assertEquals(ConfigValue.Source.LOCAL, initial.source)

                // resetOverride → should emit DEFAULT-sourced frame
                provider.resetOverride(param)
                val afterReset = awaitItem()
                assertEquals("my_default", afterReset.value)
                assertEquals(ConfigValue.Source.DEFAULT, afterReset.source)

                cancelAndIgnoreRemainingEvents()
            }
        }
}
