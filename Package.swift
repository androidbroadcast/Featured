// swift-tools-version: 5.9
// The swift-tools-version declares the minimum version of Swift required to build this package.
//
// This file is auto-managed: the `url` and `checksum` fields are updated automatically
// by the publish CI workflow when a new release is tagged.

import PackageDescription

let package = Package(
    name: "Featured",
    platforms: [
        .iOS(.v16),
    ],
    products: [
        .library(
            name: "FeaturedCore",
            targets: ["FeaturedCore"]
        ),
    ],
    targets: [
        .binaryTarget(
            name: "FeaturedCore",
            // Updated automatically by CI on each release.
            url: "https://github.com/AndroidBroadcast/Featured/releases/download/v1.1.0/FeaturedCore.xcframework.zip",
            checksum: "28b0d80da0bdce65eca3eafd4f836e4dcd49e10f2fcf7ea8c38eb8aa31e2be6f"
        ),
    ]
)
