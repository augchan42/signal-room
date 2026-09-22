# Producer demo build

## Toolchain

- JDK 17
- Android Gradle Plugin 8.13.2
- Gradle 8.13
- Kotlin 2.1.0 with the Compose Compiler Gradle plugin
- compile/target SDK 36; minimum SDK 28

AGP 8.13 supports API 36.1, requires Gradle 8.13 and JDK 17. Kotlin 2.x uses the Compose Compiler Gradle plugin. See the official [AGP compatibility notes](https://developer.android.com/build/releases/agp-8-13-0-release-notes) and [Compose compatibility guidance](https://developer.android.com/jetpack/androidx/releases/compose-kotlin).

Set `ANDROID_HOME` or create an untracked `local.properties` containing the Android SDK path.

## Verification

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

The application is offline. Its merged manifest must not contain the `INTERNET` permission.
