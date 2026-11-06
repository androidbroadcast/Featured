package dev.androidbroadcast.featured.testapp

import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.generated.isDarkModeEnabled

/** Reachable only from the disabled branch of the local flag. Must not survive release R8. */
class DeadFeature {
    fun run() = Unit
}

/** Reachable from the enabled-by-default-false else branch. Must survive release R8. */
class LiveFeature {
    fun run() = Unit
}

object FlagEntryPoint {
    fun select(values: ConfigValues) {
        if (values.isDarkModeEnabled()) {
            DeadFeature().run()
        } else {
            LiveFeature().run()
        }
    }
}
