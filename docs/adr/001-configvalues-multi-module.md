# ADR 001: ConfigValues in Multi-Module Projects

**Status:** Accepted

## Context

In multi-module Android/KMP projects, each feature module may declare its own `ConfigParam` flags. The question is how to compose these into a single `ConfigValues` instance that is shared across the app.

## Decision

Each module declares its flags as top-level `ConfigParam` constants. A single `ConfigValues` instance is created at the app level (e.g., in the DI graph) by passing all params through the same providers.

The Gradle plugin generates a `FlagRegistrar` per module. The app module aggregates them via the generated `GeneratedFlagRegistry`, which collects every module's registrar at compile time.

Modules never create their own `ConfigValues`; they only declare params and read from the shared instance injected from the app layer.

## Consequences

- Flag namespacing is flat — param names must be unique across all modules.
- There is one set of local/remote providers for the whole app; per-module providers are not supported.
- The Gradle plugin enforces uniqueness and generates the aggregation boilerplate automatically.
