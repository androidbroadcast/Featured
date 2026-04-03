package dev.androidbroadcast.featured

public interface RemoteConfigValueProvider : ConfigValueProvider {
    /**
     * Fetches the latest configuration values from the remote source and apply them.
     * This method should be called to ensure that the latest values are available.
     * Recommended to do in on start of user's app session (not equals app start).
     *
     * @param activate If true, the fetched values will be activated immediately.
     *                 Some providers can't support this and will ignore this parameter.
     */
    public suspend fun fetch(activate: Boolean = true)
}
