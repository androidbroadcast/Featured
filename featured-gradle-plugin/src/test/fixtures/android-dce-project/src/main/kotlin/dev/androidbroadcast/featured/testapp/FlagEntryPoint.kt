package dev.androidbroadcast.featured.testapp

import android.app.Activity
import android.os.Bundle
import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.generated.isDarkModeEnabled

/** Reachable only from the disabled branch of the local flag. Must not survive release R8. */
class DeadFeature {
    fun label(): String {
        hits++
        return "dead"
    }

    companion object {
        @JvmField
        var hits: Int = 0
    }
}

/** Reachable from the else branch when the local default is false. Must survive release R8. */
class LiveFeature {
    fun label(): String {
        hits++
        return "live"
    }

    companion object {
        @JvmField
        var hits: Int = 0
    }
}

/** Framework entry point. Default Android keep rules retain onCreate. */
class FlagActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val values = ConfigValues()
        title =
            if (values.isDarkModeEnabled()) {
                DeadFeature().label()
            } else {
                LiveFeature().label()
            }
    }
}
