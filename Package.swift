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
            url: "https://github.com/AndroidBroadcast/Featured/releases/download/v1.0.0-Beta1/FeaturedCore.xcframework.zip",
            checksum: "cebaef358e5ec71f0ee2128ae5c91a8a4257e63da4d0b4b93c7c8a74784e373b"
        ),
    ]
)
