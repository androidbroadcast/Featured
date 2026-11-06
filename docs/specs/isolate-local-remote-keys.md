# Spec: Isolate local and remote flag keys

Date: 2026-11-07
Status: draft
Issue: https://github.com/androidbroadcast/Featured/issues/294

This slice isolates a `localFlags` flag and a `remoteFlags` flag that share a name. It does not reject duplicate names. It is not the AND-gating task. AND of one flag stays on `ValueResolutionStrategy`.

## Promised behavior

`localFlags` and `remoteFlags` look isolated today. They are not. `ConfigValues` keys its snapshot by the raw `ConfigParam.key`, and every read walks local, then remote, then default. Two flags named `FEATURE_X` share one slot and one provider chain. A local flag can pick up a remote value. A remote flag can pick up a local override.

After this slice, one `ConfigValues` can hold a local `FEATURE_X` and a remote `FEATURE_X` that do not overwrite each other.

`ConfigParam` gains `FlagScope`: `LOCAL`, `REMOTE`, or `ANY`.

- `LOCAL` reads only the local provider, then its own default. The remote provider is not called. The remote value is not a candidate.
- `REMOTE` reads only the remote provider, then its own default. The local provider is not called during resolution. A local flag of the same name is not mixed in.
- `ANY` keeps `local ?: remote ?: default`.

`ValueResolutionStrategy` is not rewritten. It still chooses among the candidates it is given.

- For `LOCAL`, `remoteValue` is null.
- For `REMOTE`, `localValue` is null.
- For `ANY`, both candidates are supplied as they are today: a missing, unconfigured, or failed provider is null; a present value is passed through.

That null is the strategy input, including the call made from `override` and `resetOverride`. A custom strategy cannot see a skipped side, and it does not gain a new parameter.

`override` and `resetOverride` of a remote flag touch only that remote flag's local slot. They do not write or clear the local flag's slot, and they do not write the bare key. Because resolution of a `REMOTE` param passes `localValue = null`, that stored slot is not a resolution candidate. Under `ValueResolutionStrategy.Default`, `getValue` of a remote flag stays `remote ?: default` after `override` of that same remote flag. The write exists so the call cannot leak into another flag. This slice does not make `override` of a remote flag change its resolved value.

`override` of a local flag writes only that local flag's slot. Resolution of that flag passes `remoteValue = null`, so a remote value cannot become the local flag's result.

Two flags of the same scope and the same key remain last-write-wins. Isolation is only across containers. Duplicate names are not rejected in the DSL, the generator, or at runtime.

The public `key` does not change. Remote providers, including Firebase and ConfigCat, still look up `"FEATURE_X"`. The remote provider never sees the composite key.

## Public contract

`FlagScope` is a public enum in `dev.androidbroadcast.featured` with exactly `LOCAL`, `REMOTE`, and `ANY`.

`ConfigParam` exposes `scope: FlagScope`. The existing constructor and the existing factory that omit `scope` stay, and both mean `ANY`. A hand-written `ConfigParam` without `scope` does not change behavior. Generated code uses a new overload and passes the scope explicitly.

```kotlin
public enum class FlagScope { LOCAL, REMOTE, ANY }

// Existing factory. Signature stays. Result scope is ANY.
public inline fun <reified T : Any> ConfigParam(
    key: String,
    defaultValue: T,
    description: String? = null,
    category: String? = null,
    since: String? = null,
    enumConstants: List<T>? = null,
): ConfigParam<T>

// New overload used by generated code.
public inline fun <reified T : Any> ConfigParam(
    key: String,
    defaultValue: T,
    scope: FlagScope,
    description: String? = null,
    category: String? = null,
    since: String? = null,
    enumConstants: List<T>? = null,
): ConfigParam<T>
```

The existing constructor parameter list stays callable and assigns `ANY`. A new constructor overload accepts `scope`. Adding `scope` onto the old constructor signature is not allowed.

`key` remains the public bare name. Construction does not rewrite the caller's key. `equals` compares constructor properties, so it includes `scope`. `hashCode` stays derived from `key` only, which is the contract already documented on `ConfigParam`. The snapshot does not use `ConfigParam` as its map key.

Generated local flags are `LOCAL`. Generated remote flags are `REMOTE`. That includes both generated objects and `GeneratedFeaturedRegistry.all`. The registry already receives `kind` on the manifest. It uses that kind as the scope. It does not drop one flag because the public key matches the other. The manifest schema does not change, and `SCHEMA_VERSION` does not bump.

Generated accessor JVM signatures do not change. A local Boolean accessor still returns `Boolean`. Other local accessors still return the raw type. Remote accessors still return `ConfigValue<T>`. R8 `-assumevalues` rules, `-checkdiscard`, and iOS `DISABLE_<FLAG>` conditions stay on the same flags they use today.

`ValueResolutionStrategy` and `ValueResolutionStrategy.Default` keep their current signatures and selection code. `Default` remains `localValue ?: remoteValue ?: defaultValue` on the candidates it receives.

### Storage identity

Snapshot and local storage are keyed by scope plus key, not the bare key. The slot names are the scope name, a colon, and the public key:

- `LOCAL:FEATURE_X`
- `REMOTE:FEATURE_X`
- `ANY:FEATURE_X`

These are three slots in the snapshot, the warm-set, DataStore, and SharedPreferences. The same pattern is the local slot in every local provider: DataStore, SharedPreferences, NSUserDefaults, Java Preferences, and `InMemoryConfigValueProvider`. The composite string is only a storage identity. It is not parsed back into a scope and a key. A public key that contains a colon stays one slot because the prefix is fixed and the remainder is the whole public key.

The remote provider is called with the public key. A local read or write uses the composite slot and does not change `ConfigParam.key` on the caller's instance.

`getValue`, `getValueCached`, `observe`, `warmUp`, `fetch`, and `initialize` use that scoped slot. `getValueCached` for one scope does not return the other scope's snapshot entry. A cold read returns that param's own default. The existing snapshot rules stay, and they apply per scoped slot: a `DEFAULT` result is not written, and the LOCAL-protection rule does not let a `LOCAL` slot block a `REMOTE` slot.

`clearOverrides` still clears the local provider as a whole. It is not a new per-scope operation. The snapshot is still not cleared by that call. A later `getValue` or `resetOverride` re-resolves one param on its own scope rules.

`observe` keeps the trigger model. A local emission is a signal for that param's storage key, then a full resolve under that param's scope. A write to the other scope's slot is not that signal.

## Compatibility

This is not a major-version API break. The old public constructor and the old factory still compile already-shipped callers. No public symbol is removed or renamed. There is no deprecation window.

Already compiled call sites hit the old constructor, so their params are `ANY` and keep the chain `local ?: remote ?: default`. Isolation starts when generated code is regenerated with the new overload. Hand-written params that omit `scope` stay `ANY`.

The storage identity change is the isolation decision. This slice does not add a migration. A value previously stored under the bare key is not read as `LOCAL:FEATURE_X`, `REMOTE:FEATURE_X`, or `ANY:FEATURE_X`. The changelog is the user-facing notice.

iOS and JVM remain preview. A new public property and enum are additive on Android. The old constructor signature remains, so an already compiled Android caller still links.

### AGENTS.md contracts this slice changes

These sentences in `AGENTS.md` are no longer true as written. The implementation updates them. It does not leave the contradiction in the repository contract.

- Runtime: "Treat flag keys as shared storage/cache identities." The shared identity becomes scope plus key. Across scopes, the same public key is two slots and may have different types. Within one scope, the same key is still one slot, last-write-wins, including across aggregated modules. This slice does not add a uniqueness check.
- Runtime: "The default precedence is local override, then remote, then declared default. `ValueResolutionStrategy` can replace selection policy after both providers have been read." That precedence and that "both providers" read stay true only for `ANY`. For `LOCAL`, the remote provider is not read and `remoteValue` is null. For `REMOTE`, the local provider is not read and `localValue` is null. The strategy is still a pure selection function. It is not rewritten.
- Provider: an absent provider read is still null, and `ConfigValues` still performs fallback. A skipped side is not read and is not turned into a stored override. Read-path errors, cancellation, and strategy exceptions keep the current boundary. A provider that was not called produces no `onProviderError`.
- Aggregation: manifests stay declarative. `kind` is already `LOCAL` or `REMOTE`. No new aggregation mechanism.
- Release optimization: unchanged. Local defaults remain the release-constant path when generated R8 rules apply. Remote flags remain runtime-controlled. This slice does not change accessor signatures, rule wiring, or which container is eligible for `-assumevalues`.

`CHANGELOG.md` under `[Unreleased]` states that the same name in `localFlags` and `remoteFlags` is no longer one store. It also states that local storage and the snapshot are keyed by scope plus key, so a previously stored bare key is not that slot.

## Checks

The feature is shown when all of the following are true.

1. A `ConfigValues` test holds a local `FEATURE_X` and a remote `FEATURE_X` in one instance. Writing or resolving one does not replace the other's snapshot slot. `getValueCached` returns each param's own slot, or that param's own default when its slot is cold.
2. A remote value for `FEATURE_X` does not become the result of the local flag. The remote provider is not called for that local read. `remoteValue` passed to the strategy is null.
3. A local override of the local `FEATURE_X` does not become the result of the remote flag. During remote resolution the local provider is not called, and `localValue` passed to the strategy is null, including from `override` and `resetOverride` of the remote flag.
4. `override` of the remote flag stores only `REMOTE:FEATURE_X`. `resetOverride` of the remote flag clears only that slot. Neither call writes or clears `LOCAL:FEATURE_X` or the bare key. After `override` of the remote flag, `ValueResolutionStrategy.Default` still resolves that flag as remote, then its own default.
5. A provider test reads a real local store and shows two distinct keys, `LOCAL:FEATURE_X` and `REMOTE:FEATURE_X`, after both flags are written. DataStore or SharedPreferences is enough to show this. The same slot rule is what the other local providers use.
6. An `ANY` param with key `FEATURE_X` still resolves `local ?: remote ?: default`. Its local slot is `ANY:FEATURE_X`, not the bare key and not the `LOCAL` or `REMOTE` slot. Its remote lookup key is `FEATURE_X`.
7. A recording remote provider, used for a `REMOTE` or `ANY` read, is asked for `FEATURE_X` and is not asked for `REMOTE:FEATURE_X` or `ANY:FEATURE_X`. It is not called for a `LOCAL` read.
8. Generator tests show a local flag emitted with `FlagScope.LOCAL`, a remote flag emitted with `FlagScope.REMOTE`, and the public key string still `"FEATURE_X"`. The registry emits both when the same key is in both containers, each with its scope.
9. A call to the existing `ConfigParam` factory and constructor without `scope` still compiles, and the resulting `scope` is `ANY`.
10. `CHANGELOG.md` `[Unreleased]` contains the store-split statement from the compatibility section. `AGENTS.md` runtime bullets match the contract above.

`ValueResolutionStrategyTest` still passes without a change to `Default` beyond what a null candidate already means.

## Out of scope

- Build-type defaults.
- New remote providers.
- A new AND DSL, or changing `ValueResolutionStrategy` so that one flag ANDs local and remote.
- Rejecting duplicate names.
- A migration of previously stored bare keys.
- Debug-panel presentation. A hybrid feature is not collapsed into one debug row here. `FeatureFlagsDebugScreen` is not given a new interaction. Its existing `override` calls stay; a `REMOTE` param still does not take that override as a resolution candidate.
- Changing generated accessor signatures, R8 rules, or iOS stripping conditions.
