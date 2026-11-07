package dev.androidbroadcast.featured.apicompat

/**
 * The only module table for the Android API gate. The plugin and the tests both read this list.
 * No other project is registered.
 */
internal data class AndroidApiModule(
    val projectPath: String,
    val baselinePath: String,
) {
    val baselineFileName: String
        get() = baselinePath.substringAfterLast('/')
}

internal object AndroidApiGate {
    val modules: List<AndroidApiModule> =
        listOf(
            AndroidApiModule(":core", "core/api/android/featured-core.api"),
            AndroidApiModule(":featured-compose", "featured-compose/api/android/featured-compose.api"),
            AndroidApiModule(":featured-debug-ui", "featured-debug-ui/api/android/featured-debug-ui.api"),
            AndroidApiModule(":featured-platform", "featured-platform/api/android/featured-platform.api"),
            AndroidApiModule(":featured-testing", "featured-testing/api/android/featured-testing.api"),
            AndroidApiModule(
                ":providers:appmetrica",
                "providers/appmetrica/api/android/featured-appmetrica-provider.api",
            ),
            AndroidApiModule(":providers:configcat", "providers/configcat/api/android/featured-configcat-provider.api"),
            AndroidApiModule(":providers:datastore", "providers/datastore/api/android/featured-datastore-provider.api"),
            AndroidApiModule(":providers:firebase", "providers/firebase/api/android/featured-firebase-provider.api"),
            AndroidApiModule(
                ":providers:sharedpreferences",
                "providers/sharedpreferences/api/android/featured-sharedpreferences-provider.api",
            ),
        )

    fun moduleFor(projectPath: String): AndroidApiModule? = modules.find { it.projectPath == projectPath }
}
