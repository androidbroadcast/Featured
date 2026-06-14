import dev.androidbroadcast.featured.generated.GeneratedFeaturedRegistry

// References the auto-wired aggregator output so this fixture only compiles if the application
// plugin wired build/generated/featured/commonMain into the AGP `main` Kotlin source set AND ran
// generateFeaturedRegistry first. The compile succeeding IS the wiring assertion (stronger than a
// probe). `.all` also exercises the inlined enum reference (com.example.CheckoutVariant) from the
// aggregated feature-checkout manifest.
internal val registeredFlagCount: Int = GeneratedFeaturedRegistry.all.size
