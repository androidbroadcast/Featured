package dev.androidbroadcast.featured.appmetrica;

import com.yandex.varioqub.config.model.ConfigValue;

/**
 * Kotlin cannot call the Varioqub {@code ConfigValue} constructor: the bytecode constructor is public,
 * but the Kotlin metadata marks it internal. Tests still need a real instance.
 */
public final class VarioqubConfigValues {
    private VarioqubConfigValues() {}

    public static ConfigValue of(String value, int sourceType, long testId) {
        return new ConfigValue(value, sourceType, testId);
    }
}
