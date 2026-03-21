import Combine
import SwiftUI

// MARK: - Sample: Combine publisher usage with FeatureFlags
//
// This file demonstrates how to observe a feature flag using the Combine
// publisher bridge. It is intentionally kept separate from production code
// so it can be removed or adapted without touching core logic.
//
// Key points:
//  - `publisher(for:)` is a cold publisher: the bridge Task starts only when
//    a subscriber attaches and stops as soon as the AnyCancellable is cancelled.
//  - Store the AnyCancellable in a Set<AnyCancellable> property on your object
//    to keep the subscription alive for its lifetime.
//  - Use `.receive(on: DispatchQueue.main)` before any UI update to ensure
//    state mutations happen on the main thread.

// MARK: - ObservableObject ViewModel (UIKit / SwiftUI)

/// Example ViewModel that exposes a feature-flag value as a @Published property.
///
/// Retain-cycle analysis:
///  - `flags.publisher(for:)` uses `Deferred`, so it captures `flags` (not `self`).
///  - The `assign(to:on:)` overload that accepts an `AnyCancellable` out-parameter
///    does **not** retain `self` strongly — it stores a weak reference internally.
///  - Dropping `cancellables` cancels the subscription and the bridging Task.
final class DarkModeViewModel: ObservableObject {
    @Published private(set) var isDarkMode: Bool = false

    private var cancellables: Set<AnyCancellable> = []

    /// Begins observing the dark-mode flag via Combine.
    ///
    /// Call this once after initialisation (e.g. in `viewDidLoad` or `.onAppear`).
    func observe(flags: FeatureFlags, flag: FeatureFlag<Bool>) {
        flags.publisher(for: flag)
            .receive(on: DispatchQueue.main)
            .assign(to: \.isDarkMode, on: self)
            .store(in: &cancellables)
    }

    /// Cancels the Combine subscription and the underlying Kotlin coroutine.
    func stopObserving() {
        cancellables.removeAll()
    }
}

// MARK: - SwiftUI View consuming the ViewModel

struct DarkModeExampleView: View {
    @StateObject private var viewModel = DarkModeViewModel()

    // In a real app these would be injected via the environment or initializer.
    let flags: FeatureFlags
    let darkModeFlag: FeatureFlag<Bool>

    var body: some View {
        Text(viewModel.isDarkMode ? "Dark mode ON" : "Dark mode OFF")
            .onAppear {
                viewModel.observe(flags: flags, flag: darkModeFlag)
            }
    }
}
