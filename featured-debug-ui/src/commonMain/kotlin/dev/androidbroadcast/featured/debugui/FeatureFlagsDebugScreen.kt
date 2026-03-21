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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValues
import dev.androidbroadcast.featured.registry.FlagRegistry
import kotlinx.coroutines.launch

/**
 * A ready-to-use debug screen that lists all feature flags registered in [FlagRegistry]
 * and allows toggling boolean flags or viewing current values for other types.
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
    var items by remember { mutableStateOf<List<DebugFlagItem<*>>>(emptyList()) }

    LaunchedEffect(configValues) {
        items = buildDebugItems(configValues, FlagRegistry.all())
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("Feature Flags") })
        },
    ) { innerPadding ->
        if (items.isEmpty()) {
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
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.key }) { item ->
                    FlagItemCard(
                        item = item,
                        onToggleBoolean = { newValue ->
                            scope.launch {
                                @Suppress("UNCHECKED_CAST")
                                configValues.override(
                                    item.param as ConfigParam<Boolean>,
                                    newValue,
                                )
                                items = buildDebugItems(configValues, FlagRegistry.all())
                            }
                        },
                    )
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
                    item.category?.let { cat ->
                        Text(
                            text = cat,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (item.defaultValue is Boolean) {
                    Switch(
                        checked = item.currentValue as Boolean,
                        onCheckedChange = { onToggleBoolean(it) },
                    )
                } else {
                    Text(
                        text = item.currentValue.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item.description?.let { desc ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.isOverridden) {
                Text(
                    text = "Default: ${item.defaultValue}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private suspend fun buildDebugItems(
    configValues: ConfigValues,
    params: List<ConfigParam<*>>,
): List<DebugFlagItem<*>> =
    params.map { param ->
        buildDebugItemForParam(configValues, param)
    }

private suspend fun <T : Any> buildDebugItemForParam(
    configValues: ConfigValues,
    param: ConfigParam<T>,
): DebugFlagItem<T> {
    val configValue = configValues.getValue(param)
    val currentValue = configValue.value
    val isOverridden = configValue.source == dev.androidbroadcast.featured.ConfigValue.Source.LOCAL
    return DebugFlagItem(
        param = param,
        currentValue = currentValue,
        overrideValue = if (isOverridden) currentValue else null,
    )
}
