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

## Scoring regression fixtures

The score tests preserve two documented calculations. They are formula checks, not target scores:

- Original fixture: coherence 95, execution 75.65 and brief fit 78 produce `0.35×95 + 0.40×75.65 + 0.25×78 = 83.01`.
- Current content: coherence 100, execution 60.05 and brief fit 84 produce `0.35×100 + 0.40×60.05 + 0.25×84 = 80.02`.

The invitation threshold remains 65.

## Initial balance audit

`BalanceAuditTest` enumerates all 576 legal selection and preparation plans and writes `build/reports/balance/late-night.json`.

- Overall invitation rate: 55.38%.
- Without research: 58.89%; with research: 49.54%. Research spends one of three preparation slots and has no score bonus.
- Arrangement invitation rates: Sparse 58.85%, Driving 46.88%, Layered 60.42%.
- Sparse/monochrome is the safest cell at 96.88%, while Sparse/electric has no passing plan.
- Strongest invitation plan: Sparse/frontman/monochrome with one rehearsal and two rests, score 82.17.
- Strongest total audience-affinity plan: Layered/keyboardist/warm with rehearsal, refinement and rest, total gain 62.15 across five segments.
- A Driving/frontman/monochrome rest example scores 67.10 versus 63.00 after replacing one rest with rehearsal.

These figures are prototype balance evidence, not claims about later bookings.

## Runtime visual bridge

The booking shell loads `app/src/main/assets/demo/visual/control-room.webp`, derived from the 1080 × 1920 Blender render `art/blender/mvp/control-1.png`. `SceneBackdrop` decodes packaged portrait assets without a network or Blender runtime. The later concept stills and member stills will use the same asset path contract before `AssetCatalog` selects them dynamically.

Display text uses the bundled Oxanium variable font from the official Google Fonts repository. Its SIL Open Font License is packaged at `app/src/main/assets/licenses/oxanium-ofl.txt`. Body text continues to use the platform sans-serif for readability.
