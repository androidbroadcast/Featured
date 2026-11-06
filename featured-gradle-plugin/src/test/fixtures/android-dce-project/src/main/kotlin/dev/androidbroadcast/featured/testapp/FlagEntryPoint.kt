package dev.androidbroadcast.featured.testapp

import android.app.Activity
import android.os.Bundle
import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.generated.GeneratedLocalFlagsRoot
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

/**
 * Reached only through [ConfigValues.getValueCached], not the generated accessor.
 * The stub's boolean is opaque, so this class must survive. If it disappears, R8 folded
 * the stub and `DeadFeature`'s absence does not prove the plugin rule.
 */
class UnruledFeature {
    fun label(): String {
        hits++
        return "unruled"
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
            (
                if (values.isDarkModeEnabled()) {
                    DeadFeature().label()
                } else {
                    LiveFeature().label()
                }
            ) +
            (
                if (values.readUnruledFlag()) {
                    UnruledFeature().label()
                } else {
                    "plain"
                }
            )
    }
}

/**
 * Same call shape as the generated accessor, but no `-assumevalues` rule names this method.
 * With an opaque stub this stays unknown. With a constant stub R8 folds it and deletes
 * [UnruledFeature].
 */
private fun ConfigValues.readUnruledFlag(): Boolean = getValueCached(GeneratedLocalFlagsRoot.darkMode).value
