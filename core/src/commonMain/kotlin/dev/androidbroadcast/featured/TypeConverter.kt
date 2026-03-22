package dev.androidbroadcast.featured

/**
 * Converts between a typed value [T] and its [String] representation.
 *
 * Implement this interface to add support for custom types in providers that serialize
 * values as strings (SharedPreferences, DataStore, Firebase Remote Config).
 *
 * A built-in implementation for enum classes is available via [enumConverter]:
 * ```kotlin
 * val converter = enumConverter<MyEnum>()
 * val serialized: String = converter.toString(MyEnum.VALUE)  // "VALUE"
 * val deserialized: MyEnum = converter.fromString("VALUE")   // MyEnum.VALUE
 * ```
 *
 * @param T The non-null type this converter handles.
 */
public interface TypeConverter<T : Any> {
    /**
     * Converts [value] to its [String] representation.
     *
     * @param value The typed value to serialize.
     * @return The string representation of [value].
     */
    public fun toString(value: T): String

    /**
     * Converts a [String] back to a typed [T].
     *
     * @param value The string to deserialize.
     * @return The deserialized value.
     * @throws IllegalArgumentException if [value] cannot be converted to [T].
     */
    public fun fromString(value: String): T
}

/**
 * Creates a [TypeConverter] for the enum class [T] that serializes by enum constant name.
 *
 * Serialization uses [Enum.name]; deserialization uses [enumValueOf].
 * An [IllegalArgumentException] is thrown when an unknown name is encountered — the error
 * is never swallowed silently.
 *
 * ```kotlin
 * enum class Theme { LIGHT, DARK }
 *
 * val converter = enumConverter<Theme>()
 * converter.toString(Theme.DARK)    // "DARK"
 * converter.fromString("LIGHT")     // Theme.LIGHT
 * converter.fromString("UNKNOWN")   // throws IllegalArgumentException
 * ```
 *
 * @param T The enum class to convert.
 * @return A [TypeConverter] that round-trips [T] by enum constant name.
 */
public inline fun <reified T : Enum<T>> enumConverter(): TypeConverter<T> =
    object : TypeConverter<T> {
        override fun toString(value: T): String = value.name

        override fun fromString(value: String): T =
            requireNotNull(enumValues<T>().firstOrNull { it.name == value }) {
                "Unknown enum constant '$value' for ${T::class.simpleName}. " +
                    "Valid values: ${enumValues<T>().map { it.name }}"
            }
    }
