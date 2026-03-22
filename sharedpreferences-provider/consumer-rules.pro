# Consumer ProGuard rules for sharedpreferences-provider
# These rules are applied to consuming apps automatically.

# Keep SharedPreferences implementations used by this library
-keepclassmembers class * implements android.content.SharedPreferences { *; }
