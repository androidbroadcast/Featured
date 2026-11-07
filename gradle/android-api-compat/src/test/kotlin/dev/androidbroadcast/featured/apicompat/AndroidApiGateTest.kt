package dev.androidbroadcast.featured.apicompat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidApiGateTest {
    @Test
    fun `module table equals the ten spec paths and nothing else`() {
        val expected =
            listOf(
                ":core" to "core/api/android/featured-core.api",
                ":featured-compose" to "featured-compose/api/android/featured-compose.api",
                ":featured-debug-ui" to "featured-debug-ui/api/android/featured-debug-ui.api",
                ":featured-platform" to "featured-platform/api/android/featured-platform.api",
                ":featured-testing" to "featured-testing/api/android/featured-testing.api",
                ":providers:appmetrica" to
                    "providers/appmetrica/api/android/featured-appmetrica-provider.api",
                ":providers:configcat" to "providers/configcat/api/android/featured-configcat-provider.api",
                ":providers:datastore" to "providers/datastore/api/android/featured-datastore-provider.api",
                ":providers:firebase" to "providers/firebase/api/android/featured-firebase-provider.api",
                ":providers:sharedpreferences" to
                    "providers/sharedpreferences/api/android/featured-sharedpreferences-provider.api",
            )
        assertEquals(expected, AndroidApiGate.modules.map { it.projectPath to it.baselinePath })

        val joined = AndroidApiGate.modules.joinToString("\n") { "${it.projectPath} ${it.baselinePath}" }
        val outOfGate =
            listOf(
                ":providers:javaprefs",
                "javaprefs",
                ":providers:nsuserdefaults",
                "nsuserdefaults",
                ":featured-gradle-plugin",
                "featured-gradle-plugin",
                ":featured-lint-rules",
                "lint-rules",
                ":featured-detekt-rules",
                "detekt",
                ":featured-bom",
                "featured-bom",
                ":sample",
                "sample",
                ":featured-shrinker-tests",
                "shrinker-tests",
                ":featured-swift-dce-tests",
                "swift-dce-tests",
                "featured-registry",
            )
        outOfGate.forEach { forbidden ->
            assertTrue(forbidden !in joined, "gate table must not contain $forbidden")
        }
    }
}
