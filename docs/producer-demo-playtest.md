# Signal Room producer demo: test notes

This is a local debug APK for one offline booking. It is not a store-ready build. The 20-second music is an original instrumental placeholder; it has no vocals, live generation, account, or network service.

## Build and device

- APK: `app/build/outputs/apk/debug/app-debug.apk` (43,083,861 bytes; 41.1 MiB).
- Build host: JDK 17.0.18, Python 3.14.7, FFmpeg 9.0.1, Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.1.0.
- Test device: `Medium_Phone_API_36.1` emulator, Android API 36, portrait override 1080×1920, font scale 1.0.
- The APK contains 12 AAC mixes, three concept stills, three silent four-second loops, four member stills, a control-room still, and the asset manifest. It contains no Blender project or PCM master. Debug-only sync calibration media are also packaged in this debug build.
- The merged manifest has no `INTERNET` or `ACCESS_NETWORK_STATE` permission. Media3 contributes `WAKE_LOCK`.

## Automated evidence

- Audio tests: 10 passed. Asset-validator tests: 11 passed.
- Asset validation: 23 production assets passed hash, decode, dimensions, duration, loudness, peak, and silent-loop checks. Report: `build/reports/demo-assets.json`.
- JVM unit tests, Android lint, debug APK, and Android test APK build passed.
- Emulator instrumentation: all 19 tests passed in the latest suite. A 17-test run also passed with both Wi-Fi and mobile data disabled, followed by an offline gameplay test: Sparse/electric failed invitation but raised at least one segment's affinity; Sparse/monochrome with one rehearsal and two rests passed on retry, which skipped the already-seen performance. Run the complete suite with `adb -s emulator-5554 shell am instrument -w -r dev.digitalrain.synthwave.test/androidx.test.runner.AndroidJUnitRunner` so it cannot select another connected device.
- The 20-second first viewing is required. A retry can skip; this is covered by a real playback-end UI test.
- The outcome-path UI test took 36.8 seconds including its required 20-second first viewing. This is an automated path duration, not an uncoached player's completion time.

## Implementer inspection

- The electric/keyboardist Broadcast screen has the performer behind the BORG synth, not inside it. The warm/frontman screen has the matching stage and a labelled member inset.
- A 720×1600 layout with 1.4× font scale was checked and returned to 1080×1920 at 1.0×.
- Broadcast restoration after rotation and backgrounding is paused until an explicit resume.
- A new explicit broadcast after an earlier background event starts without a second Resume tap. When playback is paused, the selected concept still remains visible instead of a black video surface.
- Wi-Fi and mobile data were restored to their initial enabled state after the offline tests.

## Known limits and open gates

- First-beat audio/light output alignment to within 50 ms is **not verified**. Position and seek tests pass, but the headless emulator's device-audio screen recording produced irregular timestamps even for a premuxed calibration clip. An audio-enabled emulator retry caused a System UI recorder hang. Do not infer physical output alignment from `currentPosition`.
- The original 12 mixes meet technical media checks, but a human listening audition is still required to judge whether arrangement and featured-member changes are clear and fun.
- The figures have subtle body motion and beat-pulsed lighting, but not clear instrument-playing gestures. A beat-synced figure-animation design is awaiting user approval before Blender changes.
- The user's uncoached playtest is pending. Automated tests are not a substitute for deciding whether choices and verdicts are understandable.
- There is only one booking. No later-week progression, vocals, choreography, audience voting, or monetization is claimed.

## User playtest

For music review, open `art/audio/review.html` through the local repository server. Jump to the phrase at 0:04. Keep Frontman selected while switching Sparse, Driving and Layered; then keep one arrangement selected while switching the four members. The page preserves the play position across choices. Record which differences are obvious without reading the descriptions and which are too subtle.

Play one attempt without guidance, then retry once. Afterward, record answers to these questions:

1. Which producer choice was difficult?
2. Which mix change could you hear?
3. Why did the invitation pass or fail?
4. What would you change on the next attempt?

Record the user's answers here before calling the playtest complete. Tuning is limited to this booking; a new mechanic or second booking needs a separate scope.
