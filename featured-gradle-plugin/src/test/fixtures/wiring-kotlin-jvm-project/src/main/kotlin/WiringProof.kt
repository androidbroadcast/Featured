import dev.androidbroadcast.featured.generated.GeneratedLocalFlagsRoot

// References the auto-wired generated object so this fixture only compiles if the plugin wired
// build/generated/featured/commonMain into the `main` source set AND ran generateConfigParam
// first. The compile succeeding IS the wiring assertion (stronger than reading a src-dir probe).
internal val darkModeKey: String = GeneratedLocalFlagsRoot.darkMode.key
