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

            // #if entry point pattern demo: DISABLE_NEW_CHECKOUT is set in
            // FeatureFlags.generated.xcconfig when @LocalFlag new_checkout has
            // defaultValue = false. The compiler removes this block in Release.
            // See FeatureFlags.swift and docs/ios-integration.md for setup.
            #if !DISABLE_NEW_CHECKOUT
            NewCheckoutBanner()
            #endif
        }
    }
}

// MARK: - Sample entry point guarded by the new_checkout flag

/// Demo view eliminated from Release binaries by DISABLE_NEW_CHECKOUT DCE.
/// Always visible in Debug (xcconfig not included in Debug configuration).
private struct NewCheckoutBanner: View {
    var body: some View {
        Text("New checkout is enabled")
            .font(.caption)
            .foregroundStyle(.secondary)
            .padding(.bottom, 8)
    }
}
