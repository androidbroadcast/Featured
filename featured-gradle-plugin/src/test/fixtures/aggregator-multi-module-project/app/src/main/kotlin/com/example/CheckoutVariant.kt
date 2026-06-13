package com.example

// The aggregated `feature-checkout` module declares an enum flag whose type is
// `com.example.CheckoutVariant`. `featuredAggregation(project(...))` pulls only the manifest
// variant, NOT the feature module's compile classpath, so the generated
// `GeneratedFeaturedRegistry` — which references `com.example.CheckoutVariant.LEGACY` inline — only
// compiles if this enum is visible on the app's own classpath. Defining it here (instead of
// `implementation(project(":feature-checkout"))`) keeps the fixture self-contained: the feature
// modules carry no Kotlin source, so they never need their own `:core` stub.
enum class CheckoutVariant {
    LEGACY,
    MODERN,
}
