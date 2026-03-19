import Foundation
import FeaturedSampleApp

/// A type-safe wrapper around a KMP CoreConfigParam.
///
/// Declare flags on the Kotlin side (in your shared module), then wrap them here:
///
///     // Kotlin (shared module):
///     object AppFlags {
///         val darkMode = ConfigParam<Boolean>(key = "dark_mode", defaultValue = false)
///     }
///
///     // Swift:
///     let darkModeFlag = FeatureFlag(param: AppFlags.shared.darkMode, defaultValue: false)
public struct FeatureFlag<T> {
    let param: CoreConfigParam<AnyObject>
    let defaultValue: T
    let cast: (Any) -> T

    public init(param: CoreConfigParam<AnyObject>, defaultValue: T, cast: @escaping (Any) -> T) {
        self.param = param
        self.defaultValue = defaultValue
        self.cast = cast
    }
}

/// Convenience initializers for common types, handling KMP primitive boxing.
extension FeatureFlag where T == Bool {
    public init(param: CoreConfigParam<AnyObject>, defaultValue: Bool) {
        self.init(param: param, defaultValue: defaultValue) { ($0 as! NSNumber).boolValue }
    }
}

extension FeatureFlag where T == String {
    public init(param: CoreConfigParam<AnyObject>, defaultValue: String) {
        self.init(param: param, defaultValue: defaultValue) { $0 as! String }
    }
}

extension FeatureFlag where T == Int {
    public init(param: CoreConfigParam<AnyObject>, defaultValue: Int) {
        self.init(param: param, defaultValue: defaultValue) { ($0 as! NSNumber).intValue }
    }
}

/// Main Swift entry point for feature flags.
/// Wraps CoreConfigValues and provides type-safe, async-friendly access.
public final class FeatureFlags {
    private let configValues: CoreConfigValues

    public init(_ configValues: CoreConfigValues) {
        self.configValues = configValues
    }

    /// One-shot async read. Returns the resolved value (local → remote → default).
    /// CancellationError is re-thrown; other errors fall back to defaultValue.
    public func value<T>(of flag: FeatureFlag<T>) async throws -> T {
        do {
            let result = try await configValues.getValue(param: flag.param)
            return flag.cast(result.value)
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            return flag.defaultValue
        }
    }

    /// Reactive stream of unwrapped values backed by a Kotlin Flow.
    /// The returned AsyncStream runs until the calling Task is cancelled.
    /// In SwiftUI, use .task { for await value in flags.stream(of:) { ... } }
    /// — the .task modifier automatically cancels when the view disappears.
    public func stream<T>(of flag: FeatureFlag<T>) -> AsyncStream<T> {
        AsyncStream { continuation in
            let task = Task {
                do {
                    for await configValue in configValues.observe(param: flag.param) {
                        guard !Task.isCancelled else { break }
                        continuation.yield(flag.cast(configValue.value))
                    }
                    continuation.finish()
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    /// Persist a local override (highest priority).
    public func override<T>(_ value: T, for flag: FeatureFlag<T>) async throws {
        try await configValues.override(param: flag.param, value: value as Any)
    }

    /// Trigger remote config fetch and activate.
    public func fetch() async throws {
        try await configValues.fetch()
    }
}
