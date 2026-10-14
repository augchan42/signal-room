# Signal Room producer demo: test notes

This is a local debug APK for one offline booking. The 20-second instrumental cue and twelve mixes have been approved by ear. Each mix has a matching performance video in each of the three concepts. There are no vocals, live generation, accounts, or network services.

## Build and device

- APK: `/Users/auchan/projects/signal-room/app/build/outputs/apk/debug/app-debug.apk` (92,292,728 bytes; 88.0 MiB, rebuilt 2026-10-13 from `e2e13ea` on `codex/performer-contacts`, including PR #6 diagnostics and all 36 contact-corrected videos).
- Build host: JDK 17.0.18, Python 3.14.7, FFmpeg 9.0.1, Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.1.0.
- Current test device: attached Pixel 7 Pro, Android 16 / API 36. Earlier layout and full-suite checks used the `Medium_Phone_API_36.1` emulator with a 1080×1920 portrait override and font scale 1.0.
- The production manifest lists 59 media assets: 12 AAC mixes, 36 silent 20-second performance videos (three concepts × three arrangements × four featured members), three concept stills, three silent four-second fallback loops, four member stills, and a control-room still. The APK contains no Blender project or PCM master. Debug-only sync calibration media are also packaged in this debug build.
- The merged manifest has no `INTERNET` or `ACCESS_NETWORK_STATE` permission. Media3 contributes `WAKE_LOCK`.

## Current verification (2026-10-13)

- At the start of continuation, GitHub confirmed PR #4 merged as `348eca0` and PR #5 as `d984f1d`, with no open PRs then. Main remains `d984f1d`; current diagnostics/contact work is in draft PRs #6/#7.
- Full asset validation passed for all 59 media assets without refreshing hashes. Report: `/Users/auchan/projects/signal-room/app/build/reports/demo-assets.json`.
- Asset-validator tests: 12 passed. Music-driven motion tests: 22 passed.
- Debug APK build and all 54 JVM unit tests passed (zero failures, errors, or skipped tests).
- The current debug app and test APK were installed on the Pixel 7 Pro. `MediaPlaybackTest` and `OfflineDemoTest` passed all four tests. The app was then launched and its opening “Late-night debut / Attempt 1” screen was checked.
- Two additional device tests restore the last second of a real performance, resume it, enter the recap, and exercise retry/reset with a durable test save. All eight targeted device tests pass in the contact-corrected build (53.946 seconds total), including two complete first-view/retry outcome paths. The latest durable run reached the brief in 131 ms for retry and 103 ms for reset. An earlier test timeout coincided with the phone entering sleep and is not evidence of an app stall.
- The replacement videos were exported sequentially, decoded before promotion, and all 59 assets passed validation before and after hash authoring. The APK build revalidated the bundle. All 54 JVM tests, 37 Blender tests, 20 audio tests and 12 validator tests pass. The app/test APKs were installed with replacement installs, preserving the real save; the real opening brief was checked afterward.
- This verifies packaging and program behavior. It does not complete the user's uncoached playtest or establish physical audio/video output sync.

## Earlier automated evidence

- Audio tests: 10 passed. Asset-validator tests: 11 passed.
- Asset validation: 23 production assets passed hash, decode, dimensions, duration, loudness, peak, and silent-loop checks. Report: `build/reports/demo-assets.json`.
- JVM unit tests, Android lint, debug APK, and Android test APK build passed.
- Emulator instrumentation: all 20 tests passed in the latest suite. A 17-test run also passed with both Wi-Fi and mobile data disabled, followed by an offline gameplay test: Sparse/electric failed invitation but raised at least one segment's affinity; Sparse/monochrome with one rehearsal and two rests passed on retry, which skipped the already-seen performance. Run the complete suite with `adb -s emulator-5554 shell am instrument -w -r dev.digitalrain.synthwave.test/androidx.test.runner.AndroidJUnitRunner` so it cannot select another connected device.
- The 20-second first viewing is required. A retry can skip; this is covered by a real playback-end UI test.
- The outcome-path UI test took 36.8 seconds including its required 20-second first viewing. This is an automated path duration, not an uncoached player's completion time.

## Implementer inspection

- The electric/keyboardist Broadcast screen has the performer behind the BORG synth, not inside it. The warm/frontman screen has the matching stage and a labelled member inset.
- A 720×1600 layout with 1.4× font scale was checked and returned to 1080×1920 at 1.0×.
- Broadcast restoration after rotation and backgrounding is paused until an explicit resume.
- A new explicit broadcast after an earlier background event starts without a second Resume tap. When playback is paused, the selected concept still remains visible instead of a black video surface.
- The verdict now separates score-cap bookkeeping from the ranked producer choices. A fully refined Sparse/Frontman/Monochrome result explains that 22 extra coherence points did not count, rather than ranking the −22 cap adjustment as a cause.
- Wi-Fi and mobile data were restored to their initial enabled state after the offline tests.

## Known limits and open gates

- First-beat audio/light output alignment to within 50 ms is **not verified**. Position and seek tests pass, but the headless emulator's device-audio screen recording produced irregular timestamps even for a premuxed calibration clip. An audio-enabled emulator retry caused a System UI recorder hang. Do not infer physical output alignment from `currentPosition`.
- The user approved the revised mixes by ear and the review performance clip. Whether an uncoached player understands the producer decisions and verdicts is still untested.
- The app now plays mix-specific 20-second performances with music-driven arm/wrist motion, torso and head movement, singer hair motion, and moving searchlights. Assigned drum contacts, held key/fingertip contacts, keytar grip, articulated playing fingers and heel-down pedals are now verified and included in all 36 videos (ADR 0004). This is stylized playing, not independent full chord fingering or detailed facial performance.
- The user's uncoached playtest is recorded below (2026-10-14). The game was understandable but opaque; a casual-loop redesign follows.
- There is only one booking. No later-week progression, vocals, choreography, audience voting, or monetization is claimed.

## User playtest

For music review, open `art/audio/review.html` through the local repository server. Jump to the phrase at 0:04. Keep Frontman selected while switching Sparse, Driving and Layered; then keep one arrangement selected while switching the four members. The page preserves the play position across choices. Record which differences are obvious without reading the descriptions and which are too subtle.

Play one attempt without guidance, then retry once. Afterward, record answers to these questions:

1. Which producer choice was difficult?
2. Which mix change could you hear?
3. Why did the invitation pass or fail?
4. What would you change on the next attempt?

Record the user's answers here before calling the playtest complete. Tuning is limited to this booking; a new mechanic or second booking needs a separate scope.

### Playtest record

In progress. The user reported on 2026-10-13 that both “Retry booking” and “Reset demo” waited a few seconds, then the next screen appeared without further input. No Android ANR was recorded. The delay was not reproduced in the awake device tests above and is not marked fixed. The debug app now logs action request/completion times under `SignalRoomActions` so a recurrence can be measured. The gameplay report below replaces the four questions.

**Uncoached playtest, 2026-10-14 (Pixel 7 Pro, from Reset demo).** Two attempts: 77.52 and 80.76,
both invited back. The user walked through mix, member and stage choice, playback controls,
preparation, Research, broadcast, results, editing and retry. Audio could not be heard over the
connection; playback controls were checked visually.

- Worked: arrangement and member descriptions give decision clues; stage previews; three
  preparation slots with clear tradeoffs; editing keeps preparation; results show success, the
  required score, and on the second recap the previous score and +3.24.
- Confusing: the brief introduces systems before the player's role; the desk takes several screens
  of scrolling and the selections and next action are not kept visible; selected options look like
  unselected ones and button text is thin and dim; "Confirm broadcast" is disabled with unused slots
  without saying why; results explain formulas but not what to change (both attempts wasted
  coherence above the cap, learned only afterwards); "awareness" and "affinity" are unexplained and
  the recap refers to audience changes "above" on the previous screen; the broadcast has no
  countdown.
- Suggested first changes: one plain introduction; a visible plan summary and Continue button;
  immediate preparation effects with cap warnings and reasons for disabled buttons; results that
  lead with the outcome and one adjustment, formulas under Details; an attempt comparison.
- The user's direction after the report: "this is a simple casual game ... add some ritual before
  the great music and animation with a cool reward/prize/congrats screen ... simple loop, great
  simple visuals, great music". Formulas stay available behind a link. Design:
  `docs/superpowers/specs/2026-10-14-casual-loop-design.md`.
- Casual loop implemented on branch `casual-loop`; awaiting the user's second playtest.

### Initial performer contact inspection (2026-10-13)

A separate background Blender process inspected stick tips at the hit frames for all twelve mix motions, without rendering or saving the scene. It measured the nearest surface of any drum pad, so this is a lower bound on the gap, not confirmation that a stick reaches the intended pad. Of 1,575 contacts, 1,503 were more than 5 cm from every pad; median gaps per mix ranged from 8.76 to 10.86 cm. This established the need for the corrective pass recorded below.

Local report: `/Users/auchan/projects/signal-room/app/build/reports/performer-contact-audit.json`. The initial sandboxed background process crashed at startup; rerunning outside the sandbox completed. The Android app does not use live Blender.

The corrective pass is now complete for the MVP: 1,504 assigned drum strikes (under 1 mm target
error with a deliberate 1 mm face clearance), 1,541 key onsets, 12,866 held-key frames, 5,760 neck
grip frames, raised BORG gestures, and 17,280 heel/sole frames. The source blend and approved audio
are unchanged. Code review also caught early key travel and late hi-hat closing; regression tests
now cover both. Main is still `d984f1d`; draft PR #7 is stacked on draft PR #6.
