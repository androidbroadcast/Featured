package dev.androidbroadcast.featured.platform

import dev.androidbroadcast.featured.LocalConfigValueProvider
import kotlin.test.Test
import kotlin.test.assertNotNull

class DefaultLocalProviderTest {
    @Test
    fun defaultLocalProvider_returnsNonNullProvider() {
        val provider: LocalConfigValueProvider = defaultLocalProvider()
        assertNotNull(provider)
    }
}
