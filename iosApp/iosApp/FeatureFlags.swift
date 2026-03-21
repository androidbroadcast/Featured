import Combine
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
    let param: CoreConfigParam<AnyObject>?
    let defaultValue: T
    let cast: (Any) -> T

    public init(param: CoreConfigParam<AnyObject>, defaultValue: T, cast: @escaping (Any) -> T) {
        self.param = param
        self.defaultValue = defaultValue
        self.cast = cast
    }

    /// Convenience initializer for testing without a live KMP runtime.
    /// The `param` is `nil`; the flag relies on the stream provided by `FeatureFlags`.
    init(key: String, defaultValue: T) where T: Any {
        self.param = nil
        self.defaultValue = defaultValue
        // Identity cast: the test stream yields values of type T directly.
        self.cast = { $0 as? T ?? defaultValue }
    }
}

/// Convenience initializers for common types, handling KMP primitive boxing.
/// Safe casts (`as?`) are used throughout; on mismatch the defaultValue is returned.
extension FeatureFlag where T == Bool {
    public init(param: CoreConfigParam<AnyObject>, defaultValue: Bool) {
        self.init(param: param, defaultValue: defaultValue) { ($0 as? NSNumber)?.boolValue ?? defaultValue }
    }
}

extension FeatureFlag where T == String {
    public init(param: CoreConfigParam<AnyObject>, defaultValue: String) {
        self.init(param: param, defaultValue: defaultValue) { ($0 as? String) ?? defaultValue }
    }
}

extension FeatureFlag where T == Int {
    public init(param: CoreConfigParam<AnyObject>, defaultValue: Int) {
        self.init(param: param, defaultValue: defaultValue) { ($0 as? NSNumber)?.intValue ?? defaultValue }
    }
}

/// Main Swift entry point for feature flags.
/// Wraps CoreConfigValues and provides type-safe, async-friendly access.
///
/// Marked `@unchecked Sendable` because `CoreConfigValues` (a Kotlin/JVM object bridged via SKIE)
/// does not carry Swift's `Sendable` annotation, but its internal state is protected by
/// Kotlin coroutine dispatch semantics. All mutable state lives on the Kotlin side.
public final class FeatureFlags: @unchecked Sendable {
    private let configValues: CoreConfigValues?

    /// A closure that produces an AsyncStream for a given flag key.
    /// Injected during testing so the class can be exercised without a live KMP runtime.
    private let streamProvider: ((String) -> AsyncStream<Any>)?

    public init(_ configValues: CoreConfigValues) {
        self.configValues = configValues
        self.streamProvider = nil
    }

    /// Testing initializer. Injects a custom stream provider instead of a live KMP runtime.
    ///
    /// - Parameter streamProvider: closure that returns an `AsyncStream<Any>` for a flag key.
    init(streamProvider: @escaping (String) -> AsyncStream<Any>) {
        self.configValues = nil
        self.streamProvider = streamProvider
    }

    /// One-shot async read. Returns the resolved value (local → remote → default).
    /// CancellationError is re-thrown; other errors fall back to defaultValue.
    @MainActor
    public func value<T>(of flag: FeatureFlag<T>) async throws -> T {
        guard let configValues, let param = flag.param else { return flag.defaultValue }
        do {
            let result = try await configValues.getValue(param: param)
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
        // Use injected test provider when available.
        if let streamProvider {
            let key = flag.param?.key ?? ""
            return AsyncStream { continuation in
                let task = Task {
                    for await rawValue in streamProvider(key) {
                        guard !Task.isCancelled else { break }
                        continuation.yield(flag.cast(rawValue))
                    }
                    continuation.finish()
                }
                continuation.onTermination = { _ in task.cancel() }
            }
        }

        guard let configValues, let param = flag.param else {
            return AsyncStream { $0.finish() }
        }

        return AsyncStream { continuation in
            let task = Task {
                do {
                    for await configValue in configValues.observe(param: param) {
                        guard !Task.isCancelled else { break }
                        continuation.yield(flag.cast(configValue.value))
                    }
                    continuation.finish()
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    /// Bridges the Kotlin Flow to a Combine `AnyPublisher`.
    ///
    /// Internally uses `Deferred` so the bridging `Task` starts only when a subscriber
    /// attaches, guaranteeing no values are missed. The task forwards values from the
    /// underlying `AsyncStream` to a `PassthroughSubject` and is cancelled as soon as
    /// the subscriber cancels its `AnyCancellable`.
    ///
    /// **Memory management:** No retain cycles occur because:
    /// - `Deferred` creates a fresh `PassthroughSubject` and `Task` per subscription,
    ///   so there are no shared mutable references across subscriptions.
    /// - The `Task` handle is stored in the `AnyCancellable` returned by `handleEvents`,
    ///   which is released (and the task cancelled) when the subscriber cancels.
    /// - `FeatureFlags` itself is not retained by the returned publisher chain.
    ///
    /// **Usage:**
    /// ```swift
    /// flags.publisher(for: darkModeFlag)
    ///     .receive(on: DispatchQueue.main)
    ///     .assign(to: \.isDarkMode, on: self)
    ///     .store(in: &cancellables)
    /// ```
    ///
    /// - Parameter flag: The `FeatureFlag` to observe.
    /// - Returns: A cold publisher that never fails and emits one value per upstream change.
    public func publisher<T>(for flag: FeatureFlag<T>) -> AnyPublisher<T, Never> {
        // `Deferred` delays stream creation until subscription time, so no values are
        // dropped between `publisher(for:)` call and the first `sink`/`assign`.
        Deferred {
            let subject = PassthroughSubject<T, Never>()
            let asyncStream = self.stream(of: flag)

            // `bridgeTask` is created and cancelled on the same Combine subscription
            // thread via `handleEvents`, so no concurrent access to the var occurs.
            var bridgeTask: Task<Void, Never>?

            return subject
                .handleEvents(
                    receiveSubscription: { _ in
                        bridgeTask = Task {
                            for await value in asyncStream {
                                guard !Task.isCancelled else { break }
                                subject.send(value)
                            }
                            subject.send(completion: .finished)
                        }
                    },
                    receiveCancel: {
                        // Cancelling the Task also triggers AsyncStream.onTermination,
                        // which in turn cancels the underlying Kotlin coroutine.
                        bridgeTask?.cancel()
                    }
                )
        }
        .eraseToAnyPublisher()
    }

    /// Persist a local override (highest priority).
    public func override<T>(_ value: T, for flag: FeatureFlag<T>) async throws {
        guard let configValues, let param = flag.param else { return }
        try await configValues.override(param: param, value: value as Any)
    }

    /// Trigger remote config fetch and activate.
    public func fetch() async throws {
        try await configValues?.fetch()
    }
}
