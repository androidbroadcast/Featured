# Consumer ProGuard rules for firebase-provider
# These rules are applied to consuming apps automatically.

# Keep Firebase Remote Config classes used by this library
-keepnames class com.google.firebase.remoteconfig.** { *; }

# Keep Featured Firebase provider classes
-keepnames class dev.androidbroadcast.featured.firebase.** { *; }
