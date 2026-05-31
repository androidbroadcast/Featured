plugins {
    id("java-library")
    id("dev.androidbroadcast.featured")
}

// No featured { } block — the plugin is applied with zero flag declarations.
// Expected: generateFeaturedManifest produces a manifest with an empty flags array.
