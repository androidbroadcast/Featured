package dev.androidbroadcast.featured.appmetrica;

import com.yandex.varioqub.config.model.ConfigValue;

/**
 * Kotlin sees Varioqub's source constants as internal, so this module cannot name them from Kotlin.
 * They are public JVM fields. This bridge keeps the mapping on those named fields.
 */
final class VarioqubSources {
    static final int SOURCE_DEFAULT = ConfigValue.SOURCE_DEFAULT;
    static final int SOURCE_INAPP_DEFAULT = ConfigValue.SOURCE_INAPP_DEFAULT;
    static final int SOURCE_SERVER = ConfigValue.SOURCE_SERVER;

    private VarioqubSources() {}
}
