package dev.androidbroadcast.featured.sharedpreferences

import android.content.SharedPreferences

internal interface ValueSaver<T : Any> {
    fun write(
        editor: SharedPreferences.Editor,
        key: String,
        value: T,
    )

    fun read(
        pref: SharedPreferences,
        key: String,
    ): T?
}
