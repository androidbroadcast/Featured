package dev.androidbroadcast.featured.registry

import dev.androidbroadcast.featured.ConfigParam
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlagRegistryTest {
    @BeforeTest
    fun setUp() {
        FlagRegistry.reset()
    }

    @Test
    fun registeredParamAppearsInAll() {
        val param = ConfigParam(key = "flag_a", defaultValue = true)
        FlagRegistry.register(param)
        assertTrue(FlagRegistry.all().contains(param))
    }

    @Test
    fun allReturnsAllRegisteredParams() {
        val p1 = ConfigParam(key = "flag_b", defaultValue = false)
        val p2 = ConfigParam(key = "flag_c", defaultValue = 42)
        FlagRegistry.register(p1)
        FlagRegistry.register(p2)
        val all = FlagRegistry.all()
        assertEquals(2, all.size)
        assertTrue(all.contains(p1))
        assertTrue(all.contains(p2))
    }

    @Test
    fun registeringDuplicateKeyDoesNotDuplicateEntry() {
        val param = ConfigParam(key = "flag_d", defaultValue = "hello")
        FlagRegistry.register(param)
        FlagRegistry.register(param)
        assertEquals(1, FlagRegistry.all().size)
    }

    @Test
    fun allReturnsEmptyWhenNothingRegistered() {
        assertTrue(FlagRegistry.all().isEmpty())
    }

    @Test
    fun allReturnsImmutableSnapshot() {
        val param = ConfigParam(key = "flag_e", defaultValue = 1)
        FlagRegistry.register(param)
        val snapshot = FlagRegistry.all()
        FlagRegistry.register(ConfigParam(key = "flag_f", defaultValue = 2))
        assertEquals(1, snapshot.size)
    }
}
