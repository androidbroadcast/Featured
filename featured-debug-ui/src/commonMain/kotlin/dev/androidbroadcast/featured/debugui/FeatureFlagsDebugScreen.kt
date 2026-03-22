package dev.androidbroadcast.featured.debugui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.registry.FlagRegistry
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * A ready-to-use debug screen that lists all feature flags registered in [FlagRegistry]
 * and allows toggling boolean flags or viewing current values for other types.
 *
 * Flags are grouped by [ConfigParam.category]. Each flag shows its current value, source
 * (DEFAULT / LOCAL / REMOTE), and optional description.
 *
 * Boolean flags have a toggle switch. Scalar flags (String, Int, Long, Float, Double) have
 * an inline text input with type validation. All flags with a local override have a
 * "Reset to default" button that clears the override.
 *
 * Intended for debug/internal builds only.
 *
 * @param configValues The [ConfigValues] instance used to read and override flag values.
 * @param modifier Optional [Modifier] for the root composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("ktlint:standard:function-naming")
public fun FeatureFlagsDebugScreen(
    configValues: ConfigValues,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var groupedItems by remember {
        mutableStateOf<Map<String?, List<DebugFlagItem<*>>>>(emptyMap())
    }

    LaunchedEffect(configValues) {
        val params = FlagRegistry.all()
        groupedItems = groupFlagsByCategory(buildDebugItems(configValues, params))

        // Reactive: observe all params and refresh on any change.
        // On each emission all params are re-read — acceptable for a debug-only screen.
        val flows = params.map { param -> configValues.observe(param) }
        if (flows.isNotEmpty()) {
            flows.merge().collect {
                groupedItems = groupFlagsByCategory(buildDebugItems(configValues, params))
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("Feature Flags") })
        },
    ) { innerPadding ->
        if (groupedItems.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "No feature flags registered.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            val hasCategories = groupedItems.keys.any { it != null }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                groupedItems.forEach { (category, flagsInCategory) ->
                    val headerText =
                        when {
                            category != null -> category
                            hasCategories -> "Other"
                            else -> null
                        }
                    if (headerText != null) {
                        item(key = "header_$headerText") {
                            Text(
                                text = headerText,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                    items(flagsInCategory, key = { it.key }) { item ->
                        FlagItemCard(
                            item = item,
                            onToggleBoolean = { newValue ->
                                scope.launch {
                                    @Suppress("UNCHECKED_CAST")
                                    configValues.override(
                                        item.param as ConfigParam<Boolean>,
                                        newValue,
                                    )
                                }
                            },
                            onScalarInput = { newValue ->
                                scope.launch {
                                    @Suppress("UNCHECKED_CAST")
                                    configValues.override(
                                        item.param as ConfigParam<Any>,
                                        newValue,
                                    )
                                }
                            },
                            onResetToDefault = {
                                scope.launch {
                                    configValues.resetOverride(item.param)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun FlagItemCard(
    item: DebugFlagItem<*>,
    onToggleBoolean: (Boolean) -> Unit,
    onScalarInput: (Any) -> Unit,
    onResetToDefault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.key,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    item.description?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SourceBadge(source = item.source)
                    if (item.defaultValue is Boolean) {
                        Switch(
                            checked = item.currentValue as Boolean,
                            onCheckedChange = { onToggleBoolean(it) },
                        )
                    }
                }
            }

            if (isScalarParam(item.param) && item.defaultValue !is Boolean) {
                ScalarInputField(
                    item = item,
                    onScalarInput = onScalarInput,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                )
            }

            if (item.isOverridden) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Default: ${item.defaultValue}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    TextButton(onClick = onResetToDefault) {
                        Text(
                            text = "Reset",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun ScalarInputField(
    item: DebugFlagItem<*>,
    onScalarInput: (Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(item.key, item.currentValue) {
        mutableStateOf(item.currentValue.toString())
    }
    var isError by remember(item.key, item.currentValue) { mutableStateOf(false) }

    val keyboardType =
        when (item.defaultValue) {
            is Int, is Long -> KeyboardType.Number
            is Float, is Double -> KeyboardType.Decimal
            else -> KeyboardType.Text
        }

    fun commit() {
        val parsed = parseInput(item.param, text)
        if (parsed != null) {
            isError = false
            onScalarInput(parsed)
        } else {
            isError = true
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            isError = parseInput(item.param, newText) == null && newText.isNotEmpty()
        },
        modifier = modifier,
        isError = isError,
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Done,
            ),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        supportingText =
            if (isError) {
                { Text("Invalid ${item.defaultValue::class.simpleName} value") }
            } else {
                null
            },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("ktlint:standard:function-naming")
private fun SourceBadge(source: ConfigValue.Source) {
    data class BadgeStyle(
        val label: String,
        val containerColor: androidx.compose.ui.graphics.Color,
        val contentColor: androidx.compose.ui.graphics.Color,
    )

    val style =
        when (source) {
            ConfigValue.Source.LOCAL -> {
                BadgeStyle("LOCAL", MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
            }

            ConfigValue.Source.REMOTE,
            ConfigValue.Source.REMOTE_DEFAULT,
            -> {
                BadgeStyle(
                    "REMOTE",
                    MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.onSecondary,
                )
            }

            else -> {
                BadgeStyle(source.name, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    Badge(
        containerColor = style.containerColor,
        contentColor = style.contentColor,
    ) {
        Text(text = style.label, style = MaterialTheme.typography.labelSmall)
    }
}
