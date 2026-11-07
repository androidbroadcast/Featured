# Consumer ProGuard rules for appmetrica-provider
# These rules are applied to consuming apps automatically.

# Keep Varioqub config classes used by this library
-keepnames class com.yandex.varioqub.config.** { *; }

# Keep Featured AppMetrica provider classes
-keepnames class dev.androidbroadcast.featured.appmetrica.** { *; }
