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
            url: "https://github.com/AndroidBroadcast/Featured/releases/download/v1.1.1/FeaturedCore.xcframework.zip",
            checksum: "74e7b65ddae47b14b7e669b3433ac8969971a9fb97c79dcde87bc99b45a054bf"
        ),
    ]
)
