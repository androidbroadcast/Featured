import dev.androidbroadcast.featured.generated.GeneratedLocalFlagsRoot

// References the auto-wired generated object so this fixture only compiles if the plugin wired
// build/generated/featured/buildType/debug into the debug variant AND ran generateConfigParamDebug
// first. The compile succeeding IS the wiring assertion (stronger than a probe).
internal val darkModeKey: String = GeneratedLocalFlagsRoot.darkMode.key
