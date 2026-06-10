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
            checksum: "0eff77bd2c9d25a903167d30563a273e54a070d34d4c9d3ff61ea8c628da25ec"
        ),
    ]
)
