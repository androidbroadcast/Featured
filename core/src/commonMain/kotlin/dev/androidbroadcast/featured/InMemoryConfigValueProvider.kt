package dev.androidbroadcast.featured

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull

public class InMemoryConfigValueProvider() : LocalConfigValueProvider {

    private var storage: Map<String, Any> = emptyMap()
    private val changedKeyFlow = MutableSharedFlow<String>(extraBufferCapacity = 1000)

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> get(param: ConfigParam<T>): ConfigValue<T>? {
        return storage[param.key]?.let { value ->
            ConfigValue(
                value as T,
                source = ConfigValue.Source.LOCAL
            )
        }
    }

    public override suspend fun <T : Any> set(param: ConfigParam<T>, value: T) {
        storage += param.key to value
        changedKeyFlow.emit(param.key)
    }

    public fun clear() {
        storage = emptyMap()
    }

    override fun <T : Any> observe(param: ConfigParam<T>): Flow<ConfigValue<T>> {
        return flow {
            get(param)?.let { emit(it) }

            changedKeyFlow
                .filter { key -> key == param.key }
                .mapNotNull { get(param) }
                .let { emitAll(it) }
        }
    }
}