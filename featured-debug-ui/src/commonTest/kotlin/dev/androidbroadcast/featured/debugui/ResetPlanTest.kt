package dev.androidbroadcast.featured.debugui

import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResetPlanTest {
    private fun makeItem(
        key: String,
        defaultValue: Boolean,
        overrideValue: Boolean?,
        source: ConfigValue.Source = if (overrideValue != null) ConfigValue.Source.LOCAL else ConfigValue.Source.DEFAULT,
    ) = DebugFlagItem(
        param = ConfigParam(key = key, defaultValue = defaultValue),
        currentValue = overrideValue ?: defaultValue,
        overrideValue = overrideValue,
        source = source,
    )

    private fun makeStringItem(
        key: String,
        defaultValue: String,
        overrideValue: String?,
        source: ConfigValue.Source = if (overrideValue != null) ConfigValue.Source.LOCAL else ConfigValue.Source.DEFAULT,
    ) = DebugFlagItem(
        param = ConfigParam(key = key, defaultValue = defaultValue),
        currentValue = overrideValue ?: defaultValue,
        overrideValue = overrideValue,
        source = source,
    )

    private fun makeIntItem(
        key: String,
        defaultValue: Int,
        overrideValue: Int?,
        source: ConfigValue.Source = if (overrideValue != null) ConfigValue.Source.LOCAL else ConfigValue.Source.DEFAULT,
    ) = DebugFlagItem(
        param = ConfigParam(key = key, defaultValue = defaultValue),
        currentValue = overrideValue ?: defaultValue,
        overrideValue = overrideValue,
        source = source,
    )

    // --- plan set == overridden set ---

    @Test
    fun buildResetPlan_onlyIncludesOverriddenItems() {
        val overridden = makeItem("flag_on", defaultValue = false, overrideValue = true)
        val notOverridden = makeItem("flag_off", defaultValue = true, overrideValue = null)
        val plan = buildResetPlan(listOf(overridden, notOverridden))
        assertEquals(1, plan.size)
        assertEquals("flag_on", plan[0].param.key)
    }

    @Test
    fun buildResetPlan_includesAllOverriddenItems() {
        val items =
            listOf(
                makeItem("a", defaultValue = false, overrideValue = true),
                makeIntItem("b", defaultValue = 0, overrideValue = 42),
                makeStringItem("c", defaultValue = "x", overrideValue = null),
                makeStringItem("d", defaultValue = "y", overrideValue = "z"),
            )
        val plan = buildResetPlan(items)
        assertEquals(3, plan.size)
        val keys = plan.map { it.param.key }.toSet()
        assertEquals(setOf("a", "b", "d"), keys)
    }

    // --- values captured correctly ---

    @Test
    fun buildResetPlan_capturesCurrentOverrideValue() {
        val item = makeStringItem("flag", defaultValue = "original", overrideValue = "override")
        val plan = buildResetPlan(listOf(item))
        assertEquals(1, plan.size)
        // The previousValue must be the override value, not the default.
        assertEquals("override", plan[0].previousValue)
    }

    @Test
    fun buildResetPlan_capturesBooleanOverrideValue() {
        val item = makeItem("bool_flag", defaultValue = false, overrideValue = true)
        val plan = buildResetPlan(listOf(item))
        assertEquals(true, plan[0].previousValue)
    }

    @Test
    fun buildResetPlan_capturesIntOverrideValue() {
        val item = makeIntItem("int_flag", defaultValue = 0, overrideValue = 99)
        val plan = buildResetPlan(listOf(item))
        assertEquals(99, plan[0].previousValue)
    }

    // --- empty plan → action disabled semantics ---

    @Test
    fun buildResetPlan_emptyListProducesEmptyPlan() {
        val plan = buildResetPlan(emptyList())
        assertTrue(plan.isEmpty())
    }

    @Test
    fun buildResetPlan_noOverridesProducesEmptyPlan() {
        val items =
            listOf(
                makeItem("a", defaultValue = true, overrideValue = null),
                makeStringItem("b", defaultValue = "hello", overrideValue = null),
                makeIntItem("c", defaultValue = 0, overrideValue = null),
            )
        val plan = buildResetPlan(items)
        // Empty plan means "Reset all" should be disabled.
        assertTrue(plan.isEmpty())
    }

    @Test
    fun buildResetPlan_emptyPlanImpliesHasOverridesFalse() {
        val items = listOf(makeItem("x", defaultValue = false, overrideValue = null))
        val plan = buildResetPlan(items)
        // hasOverrides = plan.isNotEmpty() — false means button disabled.
        assertEquals(false, plan.isNotEmpty())
    }

    @Test
    fun buildResetPlan_nonEmptyPlanImpliesHasOverridesTrue() {
        val items = listOf(makeItem("x", defaultValue = false, overrideValue = true))
        val plan = buildResetPlan(items)
        assertEquals(true, plan.isNotEmpty())
    }

    // --- param reference is preserved ---

    @Test
    fun buildResetPlan_preservesParamReference() {
        val param = ConfigParam(key = "param_key", defaultValue = "default")
        val item =
            DebugFlagItem(
                param = param,
                currentValue = "overridden",
                overrideValue = "overridden",
                source = ConfigValue.Source.LOCAL,
            )
        val plan = buildResetPlan(listOf(item))
        assertEquals(1, plan.size)
        // The param in the entry must be the same instance used to call resetOverride.
        assertEquals(param.key, plan[0].param.key)
        assertEquals(param.defaultValue, plan[0].param.defaultValue)
    }
}
