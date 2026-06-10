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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.androidbroadcast.featured.ConfigParam
import dev.androidbroadcast.featured.ConfigValue
import dev.androidbroadcast.featured.ConfigValues
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * A ready-to-use debug screen that lists all feature flags in the provided [registry]
 * and allows toggling boolean flags or viewing current values for other types.
 *
 * Flags are grouped by [ConfigParam.category]. Each flag shows its current value, source
 * (DEFAULT / LOCAL / REMOTE), and optional description.
 *
 * Boolean flags have a toggle switch. Scalar flags (String, Int, Long, Float, Double) have
 * an inline text input with type validation. All flags with a local override have a
 * "Reset to default" button that clears the override.
 *
 * Supports search by flag key/category and filtering to locally-overridden flags only.
 * A "Reset all overrides" action in the top bar resets every overridden flag at once and
 * offers an undo snackbar.
 *
 * Intended for debug/internal builds only.
 *
 * Pass `GeneratedFeaturedRegistry.all` (from the `dev.androidbroadcast.featured.application`
 * plugin) or build the list explicitly.
 *
 * @param configValues The [ConfigValues] instance used to read and override flag values.
 * @param registry The list of [ConfigParam] instances to display. Must be a stable
 *   reference (a top-level `val`, an `object` property, or a `remember`-ed list).
 *   The screen keys its internal `LaunchedEffect` on this list via `equals` (structural).
 *   A freshly-allocated list on every recomposition may restart the effect; prefer a
 *   stable top-level `val` or `object` property for predictable behavior.
 *   Each [ConfigParam.key] must be unique within the list; duplicates cause a
 *   runtime crash in `LazyColumn` key collision.
 * @param onError Callback invoked when a non-fatal internal error occurs — for example,
 *   a provider failure while building a flag item, a dead collector, or a failed
 *   override / reset operation. The screen recovers from all these errors without
 *   crashing; this callback is the hook to observe or log them.
 *
 *   Default behavior: prints the full stack trace to stdout via [println]. This is
 *   equivalent to the pre-#268 behavior (errors are printed to stdout; the format now
 *   includes the full stack trace).
 *
 *   To silence diagnostics entirely, pass `onError = {}`.
 *   To route through your own logger, pass e.g. `onError = { Timber.w(it) }`.
 *
 *   **The callback must not throw.** Any exception thrown from it is swallowed so that
 *   the screen continues to function. Throwing inside the callback will suppress the
 *   original error context — prefer logging over re-throwing.
 * @param modifier Optional [Modifier] for the root composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("ktlint:standard:function-naming")
public fun FeatureFlagsDebugScreen(
    configValues: ConfigValues,
    registry: List<ConfigParam<*>>,
    onError: (Throwable) -> Unit = { e -> println("Featured debug-ui: ${e.stackTraceToString()}") },
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Per-item state map: each param key → its current DebugFlagItem.
    // Built once at startup (parallel) then updated individually on each param change.
    var itemsById by remember {
        mutableStateOf<Map<String, DebugFlagItem<*>>>(emptyMap())
    }

    // True until the parallel initial build completes and assigns itemsById for the first time.
    // Prevents the «No feature flags to display» empty state from flashing before data arrives.
    var isInitializing by remember { mutableStateOf(true) }

    // Search / filter state — survives configuration changes.
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var overriddenOnly by rememberSaveable { mutableStateOf(false) }

    // Whether the "Reset all" confirmation dialog is open.
    var showResetAllDialog by remember { mutableStateOf(false) }

    // Auto-focus on first expansion only — not on state restoration after a config change.
    // rememberSaveable ensures hasFocusedOnce survives activity recreation alongside
    // isSearchActive (also rememberSaveable). Without this, rotation would reset
    // hasFocusedOnce to false while isSearchActive stayed true, causing the keyboard to
    // re-raise on every configuration change.
    var hasFocusedOnce by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive && !hasFocusedOnce) {
            hasFocusedOnce = true
            focusRequester.requestFocus()
        }
    }

    // Per-item subscription: each param has its own coroutine that updates only its slot.
    // Initial build is parallel — O(latency), not O(N×latency).
    LaunchedEffect(configValues, registry) {
        isInitializing = true
        try {
            // Parallel initial read of all params. Per-child runCatching isolates failures:
            // one bad provider cannot cancel siblings or leave isInitializing true forever.
            val initial: Map<String, DebugFlagItem<*>> =
                coroutineScope {
                    registry
                        .map { param ->
                            async {
                                runCatching { buildDebugItemForParam(configValues, param) }
                                    .onFailure { e ->
                                        if (e is CancellationException) throw e
                                        reportError(
                                            onError,
                                            debugUiError("Failed to build item for '${param.key}'", e),
                                        )
                                    }.getOrNull()
                            }
                        }.mapNotNull { deferred -> deferred.await() }
                        .associateBy { it.key }
                }
            itemsById = initial
        } finally {
            // Guarantee the spinner is cleared even if coroutineScope throws.
            isInitializing = false
        }

        // Per-item reactive subscriptions: each param update only its own map slot.
        // Single-threaded UI dispatcher: collectors are main-confined and the update has no
        // suspension between read and write, so the copy-on-write map update is race-free.
        // Do not move collection off the main dispatcher.
        registry.forEach { param ->
            launch {
                try {
                    configValues.observe(param).collect { _ ->
                        val updated = buildDebugItemForParam(configValues, param)
                        itemsById = itemsById + (updated.key to updated)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Slot stays stale; screen remains functional for other params.
                    reportError(onError, debugUiError("Collector died for '${param.key}'", e))
                }
            }
        }
    }

    // Collapse search on back press — Android only; no-op on iOS and JVM desktop.
    PlatformBackHandler(enabled = isSearchActive) {
        isSearchActive = false
        searchQuery = ""
    }

    // Derive visible items: filter then group. Applied before grouping so empty categories
    // are dropped automatically.
    val allItems = itemsById.values.toList()
    // Guard: pass an empty query when search is closed so a stale searchQuery can never
    // silently filter the list after the user dismisses the search bar.
    val filteredItems = filterFlags(allItems, query = if (isSearchActive) searchQuery else "", overriddenOnly = overriddenOnly)
    val groupedItems = groupFlagsByCategory(filteredItems)

    // True when the registry is non-empty but active filters produce no matches.
    val isEmptyDueToFilter = allItems.isNotEmpty() && filteredItems.isEmpty()

    // Compute the reset plan from the full (unfiltered) item list so the "Reset all" count
    // and enabled state always reflect the actual number of overridden flags, regardless of
    // which filter is active.
    val resetPlan = buildResetPlan(allItems)
    val hasOverrides = resetPlan.isNotEmpty()

    // "Reset all" confirmation dialog — shown before committing any changes.
    if (showResetAllDialog) {
        AlertDialog(
            onDismissRequest = { showResetAllDialog = false },
            title = { Text("Reset ${overrideLabel(resetPlan.size)}?") },
            text = { Text("Values return to remote or default. You can undo from the snackbar.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetAllDialog = false
                        // Snapshot then reset: capture the plan NOW (before any async work
                        // starts) so undo has consistent values even if subscriptions fire
                        // between resets.
                        val plan = resetPlan
                        // Navigation away cancels this job; completed resets stay applied
                        // and undo is not offered after disposal — acceptable for a debug surface.
                        scope.launch {
                            // Parallel reset — isolate per-item failures so one bad provider
                            // cannot prevent other params from being reset.
                            // Single-threaded UI dispatcher: failedKeys is written only from
                            // this coroutine's continuation; plain mutable list is race-free.
                            val failedKeys = mutableListOf<String>()
                            coroutineScope {
                                plan
                                    .map { entry ->
                                        async {
                                            runCatching {
                                                configValues.resetOverride(entry.param)
                                            }.onFailure { e ->
                                                if (e is CancellationException) throw e
                                                failedKeys += entry.param.key
                                                reportError(
                                                    onError,
                                                    debugUiError("Failed to reset '${entry.param.key}'", e),
                                                )
                                            }
                                        }
                                    }.forEach { it.await() }
                            }

                            val resetCount = plan.size - failedKeys.size
                            val resetMessage =
                                if (failedKeys.isEmpty()) {
                                    "Reset ${overrideLabel(plan.size)}"
                                } else {
                                    "Reset $resetCount of ${plan.size} overrides"
                                }
                            val result =
                                snackbarHostState.showSnackbar(
                                    message = resetMessage,
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Long,
                                )
                            if (result == SnackbarResult.ActionPerformed) {
                                // Undo: restore all snapshotted values in parallel.
                                var undoFailCount = 0
                                coroutineScope {
                                    plan
                                        .map { entry ->
                                            async {
                                                runCatching {
                                                    @Suppress("UNCHECKED_CAST")
                                                    configValues.override(
                                                        entry.param as ConfigParam<Any>,
                                                        entry.previousValue,
                                                    )
                                                }.onFailure { e ->
                                                    if (e is CancellationException) throw e
                                                    undoFailCount++
                                                    reportError(
                                                        onError,
                                                        debugUiError("Failed to restore '${entry.param.key}'", e),
                                                    )
                                                }
                                            }
                                        }.forEach { it.await() }
                                }
                                if (undoFailCount > 0) {
                                    val restoredCount = plan.size - undoFailCount
                                    snackbarHostState.showSnackbar(
                                        message = "Restored $restoredCount of ${plan.size} overrides",
                                        duration = SnackbarDuration.Short,
                                    )
                                }
                            }
                        }
                    },
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (isSearchActive) {
                SearchTopAppBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = {
                        isSearchActive = false
                        searchQuery = ""
                    },
                    focusRequester = focusRequester,
                )
            } else {
                TopAppBar(
                    title = { Text("Feature Flags") },
                    actions = {
                        TextButton(
                            onClick = { showResetAllDialog = true },
                            enabled = hasOverrides,
                            modifier =
                                Modifier.semantics {
                                    contentDescription = "Reset all overrides"
                                },
                        ) {
                            Text("Reset all")
                        }
                        TextButton(
                            onClick = {
                                isSearchActive = true
                                hasFocusedOnce = false
                            },
                            modifier =
                                Modifier.semantics {
                                    contentDescription = "Search flags"
                                },
                        ) {
                            Text("Search")
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            // «Overridden only» filter chip row, always visible.
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = overriddenOnly,
                    onClick = { overriddenOnly = !overriddenOnly },
                    label = { Text("Overridden only") },
                    modifier =
                        Modifier.semantics {
                            contentDescription = "Overridden only"
                            role = Role.Checkbox
                            stateDescription = if (overriddenOnly) "On" else "Off"
                        },
                )
            }

            when {
                isInitializing -> {
                    // Initial parallel build in progress — show a spinner to avoid the
                    // «No feature flags» empty state flashing before data arrives.
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                allItems.isEmpty() -> {
                    // Registry is empty — no flags registered at all.
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "No feature flags to display.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                isEmptyDueToFilter -> {
                    // Registry has flags but none pass the active filters.
                    // The snackbar must be visible above this state — SnackbarHost is in
                    // the Scaffold so it is always rendered in the correct z-layer above
                    // the content regardless of which branch is shown here.
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val filterDescription =
                            buildString {
                                if (searchQuery.isNotBlank()) {
                                    append("\"${searchQuery.trim()}\"")
                                    if (overriddenOnly) append(" + overridden only")
                                } else {
                                    append("overridden only")
                                }
                            }
                        Text(
                            text = "No flags match $filterDescription",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(
                            onClick = {
                                searchQuery = ""
                                overriddenOnly = false
                            },
                        ) {
                            Text("Clear filters")
                        }
                    }
                }

                else -> {
                    val hasCategories = groupedItems.keys.any { it != null }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
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
                                        launchCatching(scope, onError, "Failed to override '${item.key}' (boolean)") {
                                            @Suppress("UNCHECKED_CAST")
                                            configValues.override(item.param as ConfigParam<Boolean>, newValue)
                                        }
                                    },
                                    onScalarInput = { newValue ->
                                        launchCatching(scope, onError, "Failed to override '${item.key}' (scalar)") {
                                            @Suppress("UNCHECKED_CAST")
                                            configValues.override(item.param as ConfigParam<Any>, newValue)
                                        }
                                    },
                                    onEnumSelect = { newValue ->
                                        launchCatching(scope, onError, "Failed to override '${item.key}' (enum)") {
                                            @Suppress("UNCHECKED_CAST")
                                            configValues.override(item.param as ConfigParam<Any>, newValue)
                                        }
                                    },
                                    onResetToDefault = {
                                        launchCatching(scope, onError, "Failed to reset '${item.key}'") {
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
    }
}

/** Returns "1 override" or "N overrides" for use in dialog titles and snackbar messages. */
private fun overrideLabel(count: Int): String = if (count == 1) "1 override" else "$count overrides"

/**
 * Launches a coroutine on [scope], runs [block], and forwards any non-cancellation exception
 * to [onError] via [reportError]. Cancellation exceptions are re-thrown to preserve structured
 * concurrency.
 */
private fun launchCatching(
    scope: CoroutineScope,
    onError: (Throwable) -> Unit,
    errorMessage: String,
    block: suspend () -> Unit,
) {
    scope.launch {
        runCatching { block() }.onFailure { e ->
            if (e is CancellationException) throw e
            reportError(onError, debugUiError(errorMessage, e))
        }
    }
}

/**
 * Wraps [cause] in an [IllegalStateException] with a human-readable [message] that
 * describes which flag key was involved. The resulting throwable is passed to the
 * caller-supplied [FeatureFlagsDebugScreen] `onError` callback.
 */
internal fun debugUiError(
    message: String,
    cause: Throwable,
): Throwable = IllegalStateException(message, cause)

/**
 * Invokes [onError] with the given [throwable] and swallows any exception thrown by the
 * callback so that the debug screen remains functional. The callback must not throw;
 * any exception from it is silently discarded here.
 */
internal fun reportError(
    onError: (Throwable) -> Unit,
    throwable: Throwable,
) {
    try {
        onError(throwable)
    } catch (_: Throwable) {
        // Swallowed: a throwing onError must not break the screen.
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("ktlint:standard:function-naming")
private fun SearchTopAppBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        navigationIcon = {
            TextButton(
                onClick = onClose,
                modifier =
                    Modifier
                        .minimumInteractiveComponentSize()
                        .semantics { contentDescription = "Close search" },
            ) {
                Text("✕")
            }
        },
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                placeholder = { Text("Search flags…") },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        imeAction = ImeAction.Search,
                    ),
                keyboardActions =
                    KeyboardActions(
                        // Filtering is reactive; no explicit submit action needed.
                        onSearch = {},
                    ),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        TextButton(
                            onClick = { onQueryChange("") },
                            modifier =
                                Modifier
                                    .minimumInteractiveComponentSize()
                                    .semantics { contentDescription = "Clear search query" },
                        ) {
                            Text("✕", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
            )
        },
    )
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun FlagItemCard(
    item: DebugFlagItem<*>,
    onToggleBoolean: (Boolean) -> Unit,
    onScalarInput: (Any) -> Unit,
    onEnumSelect: (Any) -> Unit,
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
                            modifier =
                                Modifier.semantics {
                                    contentDescription = "${item.key} toggle"
                                },
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

            @Suppress("UNCHECKED_CAST")
            val enumConstants = item.param.enumConstants as List<Enum<*>>?
            if (enumConstants != null) {
                EnumDropdown(
                    label = item.key,
                    currentValue = item.currentValue as Enum<*>,
                    options = enumConstants,
                    onSelect = onEnumSelect,
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
        modifier =
            Modifier.semantics {
                contentDescription = "Source: ${style.label}"
            },
    ) {
        Text(text = style.label, style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("ktlint:standard:function-naming")
private fun EnumDropdown(
    label: String,
    currentValue: Enum<*>,
    options: List<Enum<*>>,
    onSelect: (Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = currentValue.name,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier =
                Modifier
                    .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
