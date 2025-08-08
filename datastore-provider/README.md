# DataStore Provider

KMP модуль для реализации `LocalConfigValueProvider` на основе Jetpack DataStore.

## Описание

Этот модуль предоставляет реализацию `LocalConfigValueProvider` с использованием Jetpack DataStore для хранения конфигурационных значений локально. Поддерживает все платформы KMP: Android, iOS и JVM.

## Поддерживаемые типы

- `String`
- `Int`
- `Long`
- `Float`
- `Double`
- `Boolean`

## Использование

### Android

```kotlin
// Создание провайдера с Context
val provider = createDataStoreConfigValueProvider(
    context = context,
    name = "my_config", // опционально
    providerId = "datastore" // опционально
)

// Или создание DataStore напрямую
val dataStore = createConfigDataStore(context, "my_config")
val provider = DataStoreConfigValueProvider(dataStore, "datastore")
```

### iOS и JVM

```kotlin
// Создание провайдера (использует файловую систему)
val provider = createDataStoreConfigValueProvider(
    name = "my_config", // опционально
    providerId = "datastore" // опционально
)

// Или создание DataStore напрямую
val dataStore = createConfigDataStore("my_config")
val provider = DataStoreConfigValueProvider(dataStore, "datastore")
```

### Работа с провайдером

```kotlin
// Определение параметра конфигурации
val myParam = ConfigParam<String>(
    key = "my_setting",
    defaultValue = "default_value"
)

// Установка значения
provider.set(myParam, "new_value")

// Получение значения
val configValue = provider.get(myParam)
println("Value: ${configValue?.value}, Source: ${configValue?.source}")

// Наблюдение за изменениями
provider.observe(myParam).collect { configValue ->
    println("Updated value: ${configValue.value}")
}
```

## Зависимости

Модуль использует:
- `androidx.datastore:datastore-core`
- `androidx.datastore:datastore-preferences`
- `kotlinx-coroutines-core`

## Хранение данных

- **Android**: Использует стандартный механизм DataStore с Context
- **JVM**: Файлы сохраняются в домашней директории пользователя (`~/.{name}.preferences_pb`)
- **iOS**: Файлы сохраняются в директории документов приложения
