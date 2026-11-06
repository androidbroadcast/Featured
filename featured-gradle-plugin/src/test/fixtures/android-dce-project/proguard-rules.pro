# Names stay stable so the test can see which classes remain in the DEX.
-dontobfuscate

# A kept field makes the increment a live side effect when the class is reached,
# and does not retain a class that the flag branch no longer reaches.
-keepclassmembers class dev.androidbroadcast.featured.testapp.DeadFeature {
    public static int hits;
}
-keepclassmembers class dev.androidbroadcast.featured.testapp.LiveFeature {
    public static int hits;
}
