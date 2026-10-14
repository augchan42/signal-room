# Producer demo build

## Toolchain

- JDK 17
- Android Gradle Plugin 8.13.2
- Gradle 8.13
- Kotlin 2.1.0 with the Compose Compiler Gradle plugin
- compile/target SDK 36; minimum SDK 28
- Python 3.9 or newer and FFmpeg/FFprobe with `loudnorm`, on `PATH` for APK builds

AGP 8.13 supports API 36.1, requires Gradle 8.13 and JDK 17. Kotlin 2.x uses the Compose Compiler Gradle plugin. See the official [AGP compatibility notes](https://developer.android.com/build/releases/agp-8-13-0-release-notes) and [Compose compatibility guidance](https://developer.android.com/jetpack/androidx/releases/compose-kotlin).

Set `ANDROID_HOME` or create an untracked `local.properties` containing the Android SDK path.

## Verification

```sh
python3 -m unittest discover -s art/audio/tests -v
python3 -m unittest discover -s tools/tests -v
python3 tools/validate_demo_assets.py --root app/src/main/assets/demo --report build/reports/demo-assets.json
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r dev.digitalrain.synthwave.test/androidx.test.runner.AndroidJUnitRunner
```

Replace `emulator-5554` with the intended emulator serial. `connectedDebugAndroidTest` selects every connected device, so do not use it when a phone is also attached. `connectedDebugAndroidTest` uninstalls the app and its save after it runs, so a tester's save does not survive a device-test run; reinstall the debug APK afterwards. The application is offline; its merged manifest contains neither `INTERNET` nor `ACCESS_NETWORK_STATE`.
`assembleDebug` and `assembleRelease` run `validateDemoAssets` before merging assets. The validator checks the exact 59-file manifest, SHA-256, full decoding, decoded audio duration/loudness/peak, WebP dimensions, silent 96-frame H.264 loops, and the 36 silent 480-frame, 540 × 960 H.264 performances (one per mix and concept). It fails if Python or FFmpeg is unavailable. JVM unit tests do not need either tool. Only when intentionally replacing media, run the validator with `--write-hashes`; it checks every file before atomically refreshing hashes, then reruns read-only validation. Ordinary builds never repair the manifest.

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

The booking shell loads `app/src/main/assets/demo/visual/control-room.webp` from the Blender demo scene. `SceneBackdrop` decodes packaged portrait assets without a network or Blender runtime. The manifest includes three concept stills, four member stills, three silent loops and 36 performances; `AssetCatalog` picks the performance for the selected mix and concept, and maps video failures to the matching concept still. The producer flow has five screens:

1. Intro: one plain brief of the player's role.
2. Choose: mix, member and stage as card rows, with a fixed bottom bar that shows the plan and the next action.
3. Prepare: three token slots, readiness lights ("Look & sound", "Playing"), a fatigue bar, and "Place N more tokens" / "Go on air".
4. Watch: the broadcast, with a "3 · 2 · 1 · ON AIR" count-in on a fresh first viewing and the time left on screen.
5. Reward: stars, a headline, one positive line, the score bar with the 65 and 75 markers, one tip, "Play again" and "Reset demo".

The score formulas are under "How the score works" on the Reward screen. Screens use the packaged assets above.

Display text uses the bundled Oxanium variable font from the official Google Fonts repository. Its SIL Open Font License is packaged at `app/src/main/assets/licenses/oxanium-ofl.txt`. Body text continues to use the platform sans-serif for readability.
