# NetworkScaleLabel

Android-приложение для работы с сетевыми весами и печати этикеток.

## Описание

Мобильное приложение для подключения к сетевым весам, получения данных о массе товара и печати этикеток через принтер.

## Технологии

- **Язык:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Архитектура:** MVVM + Clean Architecture
- **DI:** Hilt
- **Асинхронность:** Kotlin Coroutines + Flow
- **Сеть:** Retrofit + OkHttp
- **БД:** Room

## Требования

- Android Studio Ladybug или новее
- JDK 21+
- Android SDK 37
- Min SDK: 26 (Android 8.0)

## Сборка

```bash
./gradlew assembleDebug
```

APK будет located в: `app/build/outputs/apk/debug/`

## Лицензия

MIT License — см. [LICENSE](LICENSE)
