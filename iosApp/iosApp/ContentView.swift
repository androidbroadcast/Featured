import UIKit
import SwiftUI
import FeaturedSampleApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ZStack(alignment: .bottom) {
            ComposeView()
                .ignoresSafeArea()

            // MARK: - #if entry point pattern demo
            //
            // DISABLE_NEW_CHECKOUT is defined in FeatureFlags.generated.xcconfig when
            // the @LocalFlag new_checkout has defaultValue = false in the shared module.
            // The Swift compiler eliminates this block from the Release binary entirely.
            // See FeatureFlags.swift and docs/ios-integration.md for setup instructions.
            #if !DISABLE_NEW_CHECKOUT
            NewCheckoutBanner()
            #endif
        }
    }
}

// MARK: - Sample entry point guarded by the new_checkout flag

/// Overlaid at the bottom of the screen only when DISABLE_NEW_CHECKOUT is not set.
///
/// In a Debug build the condition is absent (xcconfig not included), so the view
/// always appears. In a Release build where @LocalFlag new_checkout has
/// defaultValue = false, the Swift compiler removes this view and all references
/// to it via the DISABLE_NEW_CHECKOUT compilation condition — zero runtime overhead.
private struct NewCheckoutBanner: View {
    var body: some View {
        Text("New checkout is enabled")
            .font(.caption)
            .foregroundStyle(.secondary)
            .padding(.bottom, 8)
    }
}
