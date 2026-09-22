# Signal Room Producer Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Use superpowers:subagent-driven-development only if the user chooses delegated execution. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver one offline, replayable Android booking in which audible producer choices, three preparation slots and explained judging create a meaningful challenge.

**Architecture:** One Kotlin/Compose app module, with a pure simulation and an immutable run snapshot behind a single state holder. Bundled authored mixes and pre-rendered visuals are validated before packaging; audio supplies the broadcast clock. Research and broadcast confirmation are persisted transactions, not UI-only flags.

**Tech Stack:** Kotlin, Jetpack Compose/Material 3, Kotlin serialization, coroutines, Media3, JUnit and Compose instrumentation tests; Python/NumPy for vectorized original synth composition, FFmpeg/ffprobe for media production, Blender for scene exports.

**Spec:** [Approved producer-demo design](../specs/2026-09-22-producer-demo-design.md). Read it before executing this plan.

**Status:** Written for user review. Revised after review; implementation remains paused. No dependency installation, music rendering or Blender changes are authorized by writing this document.

## Global constraints

- Native Android application ID `dev.digitalrain.synthwave`; one app module; simulation independent of Android.
- First booking only, 3–5 minutes; three-booking expansion and follow-ups are excluded.
- Exactly three preparation slots; unordered multiset. Research commits one slot immediately and at most once per attempt.
- Research reveals fixed audience preferences/show risks, never a plan-dependent score forecast.
- Fatigue starts at frontman 40, keyboardist 15, keytar player 25, drummer 30. Add 12 per rehearsal, subtract 25 per rest, then clamp once to 0–100 for the final featured member.
- Three arrangements × four featured members = twelve original instrumental mixes. No singing, celebrity audio, sampled covers or paid generation services.
- Audio: 120 BPM, 4/4, two-bar intro + eight-bar phrase; AAC-LC stereo/48 kHz/M4A; decoded duration 20.000 seconds ±50 ms; −14 LUFS integrated ±1 LU; true peak ≤−1 dBTP.
- Preview switching preserves musical position within 50 ms, with 50 ms fade-out/in; paused and ended states remain paused/ended.
- Three concept stills, four neutral member stills, three concept group loops. Loops: four seconds, 24 fps, 96 displayed frames, cycle closure at frame 97. Retain 12-frame beat lighting.
- First full viewing is mandatory; completed viewing persists across retries and enables skip without changing scores.
- Invitation starts at 35% coherence + 40% execution + 25% brief fit, threshold 65. Weights and balancing values belong in content, not simulation constants.
- Missing/undecodable audio blocks the booking with an asset-ID error. Missing video falls back to its matching concept still. Do not discard existing save data on media errors.
- No network permission, backend, accounts, analytics, purchases, live APIs, rhythm tapping or live cues.
- Preserve `art/blender/mvp/` and its review page. New demo exports must not overwrite the existing scene or five-second media.
- Commit each tested task. Do not push, install paid tools, or expand scope without authorization.

## Repository findings and execution order

At planning time the repository has art/scripts/docs, but no Gradle wrapper, app module or game tests. The earlier Garden scaffold document is not an implementation and must not supply product screens or identity.

Existing inputs:

- `art/blender/mvp/signal-room-mvp.blend`: corrected keyboard scene.
- `art/blender/mvp_controls.py`: `set_state(tier, camera_name, tone, special, ...)`.
- `art/blender/build_mvp.py`: 120-frame body/camera cycles and separate light/material pulse curves.
- `art/blender/verify_mvp.py`: existing scene checks to retain.
- `art/blender/mvp/control-1.png`: source control-room background.

Execute Tasks 1–4 first (buildable application, content, judging, durable state), then Tasks 5–7 (media), then Tasks 8–10 (playback, screens, device verification). This remains one integrated first-booking plan, not three separate products.

Review gates:

1. After Task 3: inspect actual scoring/balance report before UI work.
2. After Tasks 5–7: listen to the twelve mixes and inspect three concept loops before accepting media.
3. After Task 10: user plays the booking before any second-booking work.

## File map and contracts

All paths are relative to the repository. Kotlin production root is `app/src/main/java/dev/digitalrain/synthwave/`; mirror packages under `app/src/test/java/dev/digitalrain/synthwave/` and `app/src/androidTest/java/dev/digitalrain/synthwave/`.

| Path | Responsibility |
| --- | --- |
| `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`, `app/build.gradle.kts` | Reproducible build and verification gates |
| `app/src/main/AndroidManifest.xml`, `MainActivity.kt`, `SignalRoomApp.kt` | Offline launcher and application composition |
| `core/model/BookingModels.kt`, `RunModels.kt`, `MediaModels.kt` | Serializable, Android-free contracts |
| `core/data/ContentLoader.kt`, `ContentValidator.kt`, `SnapshotStore.kt`, `FileSnapshotStore.kt` | Authored content, structural checks and atomic local storage |
| `app/src/main/assets/content/late-night.json` | Member/arrangement/concept/audience/balancing data |
| `core/simulation/ScoreBooking.kt`, `AudienceResponse.kt`, `RunReducer.kt` | Deterministic rules, explanations and state transitions |
| `core/audio/PreviewController.kt`, `Media3AudioEngine.kt`, `BroadcastClock.kt` | Single audible player, switching and visual timing |
| `feature/booking/BookingViewModel.kt`, `BookingScreen.kt`, `DeskScreen.kt`, `PreparationScreen.kt` | Preparation flow |
| `feature/release/BroadcastScreen.kt`, `VerdictScreen.kt`, `RecapScreen.kt` | Playback, explained outcome and retry |
| `core/ui/SignalRoomTheme.kt`, `SceneBackdrop.kt` | Readable overlays, colours and asset rendering |
| `art/audio/theme.json`, `render_theme.py`, `finish_audio.py`, `tests/test_render_theme.py` | Original composition, deterministic synthesis and measured encoding |
| `art/blender/render_demo.py`, `verify_demo.py` | Isolated four-second scene preparation, export and checks |
| `tools/validate_demo_assets.py`, `tests/test_validate_demo_assets.py` | Actual media measurement, manifest/hash validation |
| `app/src/main/assets/demo/manifest.json`, `audio/*`, `visual/*` | Only the small validated runtime asset set |
| `docs/producer-demo-build.md`, `docs/producer-demo-playtest.md` | Reproduction commands and recorded evidence |

### Shared types

Define these in Task 2 and use the same names later. String IDs are validated against content; unknown IDs are errors, not defaults.

```kotlin
enum class ArrangementId { SPARSE, DRIVING, LAYERED }
enum class MemberId { FRONTMAN, KEYBOARDIST, KEYTAR, DRUMMER }
enum class ConceptId { MONOCHROME, WARM, ELECTRIC }
enum class PrepAction { REHEARSE, REFINE, REST }
data class Selection(val arrangement: ArrangementId, val member: MemberId, val concept: ConceptId)
data class Preparation(val rehearsals: Int, val refinements: Int, val rests: Int,
                       val researched: Boolean) {
    val used: Int get() = rehearsals + refinements + rests + if (researched) 1 else 0
}
data class AudienceState(val awareness: Double, val affinity: Double)
data class Contribution(val id: String, val points: Double, val explanation: String)
data class Dimension(val value: Double, val contributions: List<Contribution>)
data class BookingResult(val coherence: Dimension, val execution: Dimension,
    val briefFit: Dimension, val invitationScore: Double, val invited: Boolean,
    val audienceAfter: Map<String, AudienceState>, val fatigueAfter: Map<MemberId, Int>)
```

Content defines `MemberProfile(skill, fatigue, suitability, conceptSuitability)`, `ArrangementRules(baseExecution, skillWeight, fatigueWeight, coordinationWeight, conceptCoherence, conceptFit, traits)`, `ConceptProfile(brightness)`, `SegmentProfile(initial, preferences)`, and `BalanceRules(weights, threshold, rehearsalGains, refinementGains, fatiguePerRehearsal, recoveryPerRest, audience coefficients)`. `BookingContent` owns maps of these, `researchReport: List<String>`, `mixDescriptions: Map<String, String>` (all twelve audio IDs), and `contentVersion: Int`.

`RunSnapshot` fields: `schemaVersion`, `contentVersion`, `attemptId`, `phase`, `selection`, `preparation`, nullable `result`, nullable `previousResult`, `viewingCompleted`, `broadcastPositionMs`. `Phase` is `BRIEF`, `DESK`, `PREPARATION`, `BROADCAST`, `VERDICT`, `RECAP`.

`AssetManifest` owns `AudioAsset(id, path, arrangement, member, sha256)`, `ConceptAssets(concept, stillPath, loopPath)`, `MemberAsset(member, stillPath)` and `controlRoomPath`. Measured output is kept separately in `build/reports/demo-assets.json` so declarations cannot satisfy verification themselves.

## Task 1: Build a launchable offline Android shell

**Files:** Create the root Gradle files/wrapper, `app/build.gradle.kts`, manifest, `MainActivity.kt`, `SignalRoomApp.kt`, `core/ui/SignalRoomTheme.kt`, `app/src/main/res/values/strings.xml`, `feature/booking/BookingScreen.kt`; test `app/src/androidTest/java/dev/digitalrain/synthwave/LaunchTest.kt`. Modify `.gitignore`, `README.md`; create `docs/producer-demo-build.md`.

**Interface:** `@Composable fun SignalRoomApp()` initially shows the static booking title, then receives the state holder in Task 9. Use min SDK 28 as the proposed device floor; confirm the user's target emulator/phone before raising it.

- [ ] Inventory the local JDK, Android SDK, emulator and Gradle caches with read-only commands. Check the existing sibling `sixlines-android/gradle/libs.versions.toml` as a reference, not a project to copy wholesale. Verify the chosen AGP/Gradle/JDK/Kotlin/Compose/Media3 combination against official compatibility documentation; pin exact versions and wrapper checksum in this repository. Record versions and SDK paths in the build guide, never commit `local.properties`.
- [ ] Add the minimal one-module Gradle configuration, Compose test runner and launcher. Do not add networking, DI frameworks, Room, billing or navigation infrastructure that this single state-driven flow does not require.
- [ ] Write the launch test before screen content:

```kotlin
@get:Rule val compose = createAndroidComposeRule<MainActivity>()
@Test fun launchesIntoLateNightBooking() {
    compose.onNodeWithText("Late-night debut").assertIsDisplayed()
}
```

- [ ] Run `./gradlew :app:connectedDebugAndroidTest`; confirm the assertion fails against the initial empty shell. Implement a themed screen with the title and instrumental-demo label, then rerun.
- [ ] Run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`; inspect the merged manifest for absent `INTERNET` permission. Document local SDK setup, emulator start and these commands.
- [ ] Commit only these files: `build: add offline Android demo shell`.

## Task 2: Model and validate authored booking content

**Files:** Create the three model files and data loader/validator from the map; create `assets/content/late-night.json`; tests `core/data/ContentValidatorTest.kt`, `core/simulation/TestContent.kt`.

**Interfaces:** `loadContent(json: String): BookingContent`; `validateContent(content: BookingContent): List<String>`. `TestContent.lateNight()` loads the checked-in JSON using a Gradle-provided test-resource directory, not a second copy of balancing data.

- [ ] Write a failing test that loads real JSON, requires four unique members/three arrangements/three concepts/five segments, and rejects non-unit scoring weights:

```kotlin
@Test fun rejectsInvalidWeights() {
    val c = TestContent.lateNight()
    val invalid = c.copy(balance = c.balance.copy(weights = listOf(.35, .40, .40)))
    assertTrue(validateContent(invalid).any { it.contains("weights") })
}
```

- [ ] Run `./gradlew :app:testDebugUnitTest --tests '*ContentValidatorTest'`; see expected failure, then implement serializable contracts and validation. Require weights ≥0 and sum within 1e-9 of one; all table combinations present; skill/fatigue/traits/preferences in 0–100; preparation increments non-negative; diminishing gains correctly ordered; all IDs unique.
- [ ] Add initial content tables below. They are balancing seeds, not established fun. Revised skill values by member order FRONTMAN/KEYBOARDIST/KEYTAR/DRUMMER: `86,80,74,82`. Suitability by arrangement SPARSE/DRIVING/LAYERED: frontman `12,0,-4`; keyboardist `6,-4,10`; keytar `0,14,2`; drummer `-6,8,4`. Add conceptSuitability by MONO/WARM/ELECTRIC: frontman `12,0,-4`; keyboardist `0,8,0`; keytar `-4,0,10`; drummer `10,-4,0`. These describe visible member strengths: restrained lead presence, warm synth arrangement, electric keytar showmanship, and precise restrained percussion. Show these in member copy rather than hiding arbitrary bonuses.

| Arrangement | baseExecution | skillWeight | fatigueWeight | coordinationWeight | coherence MONO/WARM/ELECTRIC | fit MONO/WARM/ELECTRIC |
| --- | ---: | ---: | ---: | ---: | --- | --- |
| Sparse | -12 | .80 | .25 | 0 | 75/72/42 | 84/72/35 |
| Driving | 27 | .50 | .75 | 0 | 52/62/76 | 50/60/55 |
| Layered | 5 | .50 | .30 | .45 | 68/78/65 | 48/60/45 |

- [ ] Store weights `.35,.40,.25`, threshold `65`, rehearsal gains `[10,6,3]`, refinement gains `[12,7,4]` and all fatigue values from the spec in JSON. Add fixed research copy describing the five audience preferences and the show's atmosphere requirement; do not interpolate current selections.
- [ ] Add three audience traits `energy/texture/brightness`. Arrangement supplies the first two: Sparse `30/70`, Driving `85/40`, Layered `60/90`; concept supplies brightness: mono `20`, warm `50`, electric `85`. Segment preferences: performance `85/45/85`, vocal `35/25/35`, fashion `70/75/80`, story `35/45/50`, retro `45/85/50`. Fit must consume the selected concept. Label Vocal feedback as space for the instrumental melodic lead, not assessed singing. Start awareness/affinity at `10/20` for all segments. Store all audience coefficients and adoption thresholds from Task 3 in JSON.
- [ ] Add twelve explicit muted-play descriptions keyed by audio ID. Compose each from an arrangement phrase (Sparse: exposed lead over open bass/pad; Driving: active bass and electronic drums; Layered: pad and counterline around the lead) and member phrase (frontman: synth melody foregrounded, no singing; keyboardist: arpeggio/countermelody foregrounded; keytar: short melodic answers foregrounded; drummer: percussion variation foregrounded). Save the complete resulting sentences in JSON and audition them against the rendered mix; show them in desk and broadcast, muted or not. Reject missing/blank descriptions.
- [ ] Brief copy explicitly states the trade-off: restrained atmosphere helps the invitation; a richer warm arrangement can grow the retro audience; electric Driving reaches performance fans but exposes fatigue. No limited-production-budget line or budget mechanic. Sparse/monochrome is the intended safest invitation by plan success rate, not a guaranteed win; Layered/warm targets greater audience affinity, not a higher brief-fit score.
- [ ] Run validator tests including unknown enums, missing tables and invalid ranges; commit `feat: define validated late-night booking content`.

## Task 3: Deterministic judging, audience response and balance audit

**Files:** Create `ScoreBooking.kt`, `AudienceResponse.kt`; tests `ScoreBookingTest.kt`, `BalanceAuditTest.kt`. Produce `build/reports/balance/late-night.json` and record accepted findings in the build guide.

**Interfaces:** `scoreBooking(content: BookingContent, selection: Selection, prep: Preparation): BookingResult`; `effectiveFatigue(start: Int, prep: Preparation, rules: BalanceRules): Int`. Reject invalid counts or `used != 3`. Research has no arithmetic score contribution.

- [ ] Write failing fatigue, contribution and determinism tests. Explicit independent fixture:

```kotlin
@Test fun fatigueIsSummedBeforeClamping() {
    val p = Preparation(1, 0, 2, false)
    assertEquals(0, effectiveFatigue(15, p, TestContent.lateNight().balance))
}
@Test fun repeatedEvaluationIsIdentical() {
    val c = TestContent.lateNight()
    val s = Selection(ArrangementId.DRIVING, MemberId.FRONTMAN, ConceptId.WARM)
    val p = Preparation(1, 1, 1, false)
    assertEquals(scoreBooking(c, s, p), scoreBooking(c, s, p))
}
```

- [ ] Run `./gradlew :app:testDebugUnitTest --tests '*ScoreBookingTest'`; then implement the following content-driven calculations. Keep floating-point precision until display; produce contribution records from the same calculation, including clamp adjustments.

```text
fatigue = clamp(startFatigue + rehearsals*12 - rests*25, 0, 100)
coherence = clamp(conceptCoherence + suitability + memberConceptSuitability
                  + sum(first N refinementGains), 0, 100)
execution = clamp(baseExecution + skillWeight*skill - fatigueWeight*fatigue
                  + coordinationWeight*(coherence-50)
                  + sum(first N rehearsalGains), 0, 100)
briefFit = conceptFit
invitationScore = weighted sum of the three bounded dimensions
invited = invitationScore >= content threshold (unrounded)
```

- [ ] Retain the original 83.01 golden calculation as a named isolated `GoldenContent.original()` fixture in `core/simulation/GoldenContent.kt` under test sources: skill 78, fatigue 40, suitability 8, concept bonus 0, coherence base 75, execution intercept 10, skill/fatigue weights .80/.25, coordination 0, fit 78, and the original preparation gains/weights. Sparse/frontman/monochrome with rehearsal/refine/rest gives fatigue 27, coherence 95, execution 75.65 and score 83.01. This protects arithmetic independently of balancing. Add a separate revised-content golden test for the same selection/plan: fatigue 27, coherence 100, execution 60.05, fit 84, score 80.02. Use 1e-6 tolerance. Other members' fatigue remains unchanged. Test low brief fit can still gain affinity. Signed execution intercepts are allowed; only final dimensions are bounded.
- [ ] Implement audience rules using JSON coefficients: fit = `100 - mean(abs(selected energy/texture/brightness - segmentPreferences))`; quality = mean(coherence, execution); requested awareness gain = `min(20, (0.10*fit + 0.08*quality)*(1 + oldAffinity/100))`; requested affinity delta = `0.20*(quality-50) + 0.18*(fit-50)`. Clamp final awareness/affinity to 0–100 and report actual after-minus-before deltas. Initial state comes from content and evaluation never mutates it. Define “{segment} adopted the group” only when actual affinity gain ≥10, final affinity ≥30 and actual awareness gain ≥10. Otherwise use literal gains/losses, not adoption language. These are prototype rules, not an audio classifier.
- [ ] Enumerate `Preparation(r,f,s,research)` with non-negative counts, research ∈ {false,true}, total three; cross with 3×4×3 selections. Write the report with success rates, strongest/weakest plans, rest-benefit examples and pairwise arrangement comparisons for matched member/concept/prep. Tests require a 35–60% total pass rate over exactly 576 legal plans (360 without research, 216 with research), report the two strata separately, a success for every arrangement, no single arrangement weakly dominating all others, and at least one rest-over-rehearsal improvement. For each arrangement×concept, optimize preparation per member and list all tied maxima; require every member to be uniquely optimal in at least one cell with an invitation-passing score (within 1e-6 tolerance). Report both invitation score and audience objective winners; do not substitute one for the other. Require Sparse/mono to have the highest success rate among the nine arrangement×concept cells, Layered/warm to beat Sparse/mono on achievable retro-affinity gain, and Driving/electric to beat both on achievable performance-awareness gain. If capped awareness ties, use performance-affinity gain as the secondary reach comparison and report the tie honestly. For quality ≥75 and segment fit ≥80 at initial audience state, require actual awareness gain 10–20; require an invitation-failing plan with actual positive niche affinity gain ≥3. Add tests for the exact adoption boundary and for concept-only changes affecting Fashion response. Mutating skill/fatigue/coherence must expose the intended arrangement sensitivities. Use isolated content fixtures to test research has no direct bonus rather than allowing an invalid two-slot plan.
- [ ] If seed content fails these gates, adjust only balancing data while keeping the isolated original golden fixture unchanged; independently recalculate revised-content golden expectations; do not weaken the gates. Review report before proceeding. Commit `feat: add explainable judging and balance audit`.

## Task 4: Durable preparation and run state

**Files:** Create `RunReducer.kt`, `SnapshotStore.kt`, `FileSnapshotStore.kt`, `BookingViewModel.kt`; tests `RunReducerTest.kt`, `SnapshotStoreTest.kt`, `BookingViewModelTest.kt`.

**Interfaces:** `reduceRun(state: RunSnapshot, action: RunAction, content: BookingContent): RunSnapshot` and `actionError(state: RunSnapshot, action: RunAction, content: BookingContent): String?`. Actions: `OpenDesk`, `Select(selection)`, `SetPreparation(preparation)`, `OpenPreparation`, `PurchaseResearch`, `ConfirmBroadcast`, `CheckpointPlayback(positionMs)`, `CompleteViewing`, `SkipViewing`, `OpenRecap`, `ConfirmRetry`. `SnapshotStore.load(): RunSnapshot?` and `save(snapshot: RunSnapshot): Unit` are suspending. ViewModel exposes `state: StateFlow<RunSnapshot>`, `message: StateFlow<String?>` and `dispatch(action: RunAction)`. Evaluate action validation before reduction; invalid actions leave state unchanged and publish the returned message. Research is available in DESK and PREPARATION only.

- [ ] Write red tests: duplicate research costs one slot; research with all three slots occupied is rejected with “Free one slot first”; editing cannot unset committed research; full-plan confirmation freezes selections/result; repeat confirmation does not recalculate/apply audience twice.

```kotlin
@Test fun cannotReclaimResearchSlot() {
    val c = TestContent.lateNight()
    val initial = newRun(c) // define newRun(content): RunSnapshot in RunReducer.kt
    val researched = reduceRun(reduceRun(initial, RunAction.OpenDesk, c), RunAction.PurchaseResearch, c)
    val edited = reduceRun(researched, RunAction.SetPreparation(Preparation(1, 1, 1, false)), c)
    assertEquals(researched, edited)
    assertTrue(edited.preparation.researched)
}
```

- [ ] Run `./gradlew :app:testDebugUnitTest --tests '*RunReducerTest'`; implement allowed-phase transitions, immutable result and researched-plan constraints. Invalid transitions return unchanged state with a separately exposed UI validation message; they never partly mutate state.
- [ ] Implement `SnapshotStore` as an interface and `FileSnapshotStore(directory: Path)` with Android-free `java.nio` APIs shared by JVM and device. Write a unique same-directory temp file, flush/force its contents, then `Files.move(temp, target, ATOMIC_MOVE, REPLACE_EXISTING)`. Verify existing-target replacement on both host and device: Java documents atomic replacement semantics as provider-specific. If atomic replacement is unsupported or fails, report save failure, retain the previous snapshot and do not publish the new state; never fall back to delete-then-rename. Clean only that operation's temp file. Serialize all dispatches using one mutex. For research and broadcast: reduce → save successfully → publish new state. On write failure keep the old state and show “Could not save; action not applied.” Do not show the report before save succeeds. Add failure-injection and restore tests against real temporary files for serialization, not just mock call counts.
- [ ] Persist schema/content versions. Unknown versions or corrupt saves show a recoverable save-error screen with explicit “Start new demo” confirmation; do not erase silently. Disable backups for this prototype save. Playback position lives in the controller, not the run reducer. Dispatch `CheckpointPlayback` only on pause/phase exit and save then; display progress through a separate playback StateFlow sampled at most every 500 ms. No per-frame mutex/persistence traffic. Force-stop may restore the last checkpoint; research and results remain committed.
- [ ] Retry increments attempt ID, clears this attempt's research/result/preparation, restores starting audience/fatigue, retains previous result and completed-viewing flag. Incomplete first viewing does not unlock skip. Test selecting a different member retargets the final plan without modifying the old member.
- [ ] Run all JVM tests and commit `feat: persist single-booking decisions and results`.

## Task 5: Compose and export twelve original musical variations

**Files:** Create `art/audio/theme.json`, `render_theme.py`, `finish_audio.py`, `tests/test_render_theme.py`, `art/audio/README.md`, `art/audio/requirements.txt`; generated masters in ignored `art/audio/masters/`, runtime mixes in `app/src/main/assets/demo/audio/`.

**Interfaces:** `frame_count(seconds: float, sample_rate: int) -> int` and `render_mix(arrangement: str, member: str, output: Path) -> None`; CLI `python3 art/audio/render_theme.py --all`; `python3 art/audio/finish_audio.py --all`. Stable IDs: `sparse-frontman`, `sparse-keyboardist`, through `layered-drummer`, with matching `.m4a` filenames.

- [ ] Write red tests for 960,000 stereo frames at 48 kHz, repeatable PCM from fixed noise seed, distinct outputs, ten bars with events bounded to the 20-second excerpt, and no non-finite samples. Test synthesis functions with short fixtures before rendering complete tracks.

```python
def test_twenty_seconds_has_exact_sample_count(self):
    self.assertEqual(frame_count(seconds=20, sample_rate=48000), 960000)
```

- [ ] Author an original A-minor theme without reference audio. Store MIDI note events/tick durations in JSON: two-bar instrumental introduction, then eight-bar phrase with harmony Am/F/C/G/Am/F/E/Am; resolve and end envelopes by 20 seconds. Use a motif authored for this demo, not transcribed from Visage or MOVE. Define note scheduling from sample positions, not wall-clock sleeps.
- [ ] Pin NumPy in `art/audio/requirements.txt` and use a project-local virtual environment, not global Python changes. Implement vectorized/block-based offline synth voices: band-limited or filtered synth bass/pad/lead, enveloped electronic kick/snare/hat with seeded noise. Sparse leaves exposed lead/bass space; Driving adds active bass/drums; Layered adds countermelody/pad with intentional room for the featured part. Frontman highlights lead melody, keyboardist arpeggio/countermelody, keytar short melodic answers, drummer percussion variation. Use arrangement-specific event patterns, not twelve gain-only duplicates. Keep MIDI-event JSON authoritative so a later synth renderer can replace this one without changing app IDs. Benchmark a 20-second mix before rendering all twelve and record machine/time; do not promise seconds without measuring.
- [ ] Run `python3 -m unittest discover -s art/audio/tests -v`; render masters; use two-pass FFmpeg `loudnorm` measured parameters to encode AAC-LC/M4A. Re-measure decoded outputs; do not trust target encoder settings as measured results.
- [ ] Listen to matched bars across all twelve files, particularly Sparse's exposed lead and Layered's refinement rationale. Record authoring provenance and audition notes. Do not approve inaudible variants just because hashes differ. Commit sources and final runtime mixes, not intermediate WAVs: `feat: add original producer-demo arrangements`.

## Task 6: Export concept-matched four-second visuals

**Files:** Create `art/blender/render_demo.py`, `verify_demo.py`; outputs `art/blender/demo/signal-room-demo.blend` and runtime `assets/demo/visual/`; modify `.gitignore` for demo frames only. Existing MVP source scene and review exports remain unchanged.

**Interfaces:** `render_demo.py -- prepare`, `-- stills`, `-- loops`; `verify_demo.py` reads the prepared demo scene. Concept IDs map to monochrome/warm/electric; member IDs map to existing camera names frontman/producer/keytar/drummer.

- [ ] First write a Blender regression check expecting frame-end 96 and equal body/camera transforms at frames 1 and 97. Run against the original scene and confirm failure (its cycle is 120 frames). Require beat-1 light peak at frame 1 (presentation time zero), equal peaks at frames 1 and 13, and a lower trough between. Verify the encoded first displayed frame retains that peak.
- [ ] Prepare a separate scene from the corrected MVP. Map body/camera keyframe time with `newFrame = 1 + (oldFrame-1)*96/120`, including handles; rebuild light/material pulses at original 12/24-frame periods through closure. Do not globally scale every F-curve. Keep unused special-stage echo objects hidden for booking 1.
- [ ] Add explicit concept application after `set_state(tier=1, camera_name='group')`: monochrome uses dark neutral clothing, restrained rims and warm face key; warm uses warm clothing and warmer fill; electric uses cyan/coral rims and brighter fill. Keep the same tier-1 kit with no audience for all three. Do not use flagship geometry to imply the electric concept grants free reach. Inspect existing light types and preserve readable skin tones.
- [ ] Render three 1080×1920 concept stills and three 96-frame loops. Encode H.264/yuv420p MP4 with no audio. Export four neutral member stills and a control-room background. Name paths `visual/concept-{id}.webp`, `visual/member-{id}.webp`, `visual/loop-{id}.mp4`, `visual/control-room.webp`.
- [ ] Run commands with the actual installed Blender binary, for example:

```sh
'/Applications/Blender.app/Contents/MacOS/Blender' --background art/blender/mvp/signal-room-mvp.blend --python-exit-code 1 --python art/blender/render_demo.py -- prepare
'/Applications/Blender.app/Contents/MacOS/Blender' --background art/blender/demo/signal-room-demo.blend --python-exit-code 1 --python art/blender/verify_demo.py
```

- [ ] Inspect frames 96→1 for popping, each member's instrument clearance and all three concepts at phone size. Retain keyboard orientation/stack tests from `verify_mvp.py`; compare frame 1 with closure frame 97 rather than incorrectly demanding frame 96 equal frame 1. Commit `feat: export first-booking concept media`.

## Task 7: Make the asset manifest a packaging gate

**Files:** Create `assets/demo/manifest.json`, `tools/validate_demo_assets.py`, `tools/tests/test_validate_demo_assets.py`; modify `app/build.gradle.kts`; create `core/data/AssetCatalog.kt` and `AssetCatalogTest.kt`.

**Interfaces:** `validate_file(path: Path) -> list[str]` performs existence/decoding checks; CLI `python3 tools/validate_demo_assets.py --root app/src/main/assets/demo --report build/reports/demo-assets.json` adds the full manifest/tolerance checks and exits nonzero on any defect. Add explicit `--write-hashes` authoring mode: validate IDs, paths and all media first, then atomically update hashes only if those checks pass, and rerun normal read-only validation. Normal validation never repairs or rewrites the manifest. `AssetCatalog.audio(selection: Selection): AudioAsset`, `concept(id: ConceptId): ConceptAssets`, `member(id: MemberId): MemberAsset`.

- [ ] Create fixture tests with genuinely missing/corrupt media, wrong channel count, excessive peak, short audio and 95-frame video. Use FFmpeg-generated short fixtures for actual decode failures; keep numeric boundary tests separate from expensive integration tests.

```python
def test_missing_file_blocks_validation(self):
    failures = validate_file(self.temp_root / 'missing.m4a')
    self.assertTrue(any('missing' in failure for failure in failures))
```

- [ ] Implement manifest validation for the exact 12 audio / 3 concept / 4 member / 3 loop mapping plus control room, unique IDs, file existence, safe relative paths, SHA-256 and real decoding. ffprobe supplies codec/sample rate/channels/frame count; FFmpeg loudness measurement supplies decoded LUFS/true peak. Measure duration using decoded presentation samples with codec padding handled, not rounded container duration alone.
- [ ] Run fixture tests, then real validation. Require all spec tolerances and 1080×1920 source visuals; write per-file measurements and failures. Record source-scene closure evidence separately because a manifest cannot prove animation continuity.
- [ ] Register Gradle `validateDemoAssets` as an `Exec` task and attach it to the application variant asset-merge task (for example `mergeDebugAssets`/`mergeReleaseAssets` after confirming the pinned AGP graph), not `preBuild`. Keep local unit tests Android-resource-free (`includeAndroidResources=false`); read content through JVM test resources. Declare script/manifest/media inputs and report output so edits invalidate it. Fresh machines must install documented FFmpeg/Python prerequisites; absence fails clearly rather than bypassing validation. Prove with `./gradlew :app:testDebugUnitTest --dry-run` that the validator and application asset merge are absent, and with `./gradlew :app:assembleDebug --dry-run` that validation precedes asset merge. Run JVM tests with Python/FFmpeg unavailable while retaining the required JDK/SDK/Gradle environment. Corrupt a copied media fixture and verify APK assembly fails; do not infer graph isolation solely from a task name.
- [ ] Add runtime existence/open checks and decoder-error mapping to asset IDs; video errors choose only the matching still, audio errors block the booking. Test unknown IDs fail explicitly. Commit `build: validate demo media before packaging`.

## Task 8: Position-preserving audio and broadcast clock

**Files:** Create audio classes in the map; tests `core/audio/PreviewControllerTest.kt`, `BroadcastClockTest.kt`, device `core/audio/MediaPlaybackTest.kt`.

**Interfaces:** `PreviewController.select(asset: AudioAsset)`, `play()`, `pause()`, `replay()`, `setMuted(Boolean)`, `close()`; observable `PlaybackState(assetId, positionMs, status, errorAssetId)`. `Status` is `IDLE`, `LOADING`, `PLAYING`, `PAUSED`, `ENDED`, `ERROR`. `BroadcastClock.visualPosition(audioPositionMs: Long): Long` returns modulo 4000, with completion handled separately at 20000.

- [ ] Write red controller tests for same-position switching, preserving pause/end, latest rapid selection winning, background pause and decoder error. A controllable audio-engine test double supplies time and asynchronous readiness; assert controller state/selection rather than mock existence. Write device tests with the real bundled media to verify actual decoder seeking.

```kotlin
@Test fun visualClockUsesAudioPosition() {
    assertEquals(1250L, BroadcastClock.visualPosition(9250L))
}
```

- [ ] Implement one audible Media3 player. Capture position at the start of switching; fade out 50 ms, stop/replace/prepare, seek to captured position, then fade in 50 ms only if previously playing. Serialize commands and cancel stale loads with a monotonically increasing request ID. First selection starts at zero; ended selection stays ended until replay.
- [ ] On broadcast confirmation stop preview, select its final mix and begin at zero. A second video-only player may decode the selected muted loop; this is not a second audio stream. Align video to audio modulo 4000 on start/resume and correct measured drift exceeding 50 ms. Pause both on background/audio-focus loss and require explicit resume; handle headphone disconnect. Mute must not stop timeline advancement or scoring.
- [ ] Run JVM tests, then `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.digitalrain.synthwave.core.audio.MediaPlaybackTest`. Measure loaded-position errors ≤50 ms and verify no simultaneous audible streams. Add a debug-only synchronization fixture with a known sample-zero click and frame-zero flash. Capture its playback on the target device using timestamped decoded-PCM instrumentation plus an audiovisual recording to distinguish codec priming from output/display latency; report method, measured offset and uncertainty. Require first-beat/light alignment ≤50 ms after start, seek and resume, then verify the real mixes share that start. Inspect FFmpeg edit-list/skip-sample metadata and Media3's actual trimming; do not hardcode a presumed 43 ms AAC delay or treat currentPosition alone as audible proof. If alignment fails, correct encoding metadata/playback synchronization and remeasure. On media error pause and expose stable asset ID; preserve save. Commit `feat: add synchronized offline preview and broadcast playback`.

## Task 9: Connect the complete producer-decision UI

**Files:** Implement feature screens and UI helpers from the map; connect `SignalRoomApp.kt` and `BookingViewModel.kt`; tests `feature/BookingFlowTest.kt`, `ResearchFlowTest.kt`, `RetryFlowTest.kt` in androidTest.

**Interfaces:** Screens receive `RunSnapshot`, validated content/catalog and an `onAction: (RunAction) -> Unit` callback; playback screens also receive the controller. No screen computes scoring or edits persistence directly.

- [ ] Write a red Compose flow test using production state logic and testable storage: open desk, select Driving/frontman/warm, fill rehearsal/refine/rest, confirm, view, inspect verdict, open recap, confirm retry. Assert preparation count and named reasons, not pixel positions. Give controls stable semantic tags such as `arrangement-driving`, `research-open`, `confirm-broadcast`, `skip-performance`.

```kotlin
compose.onNodeWithTag("research-open").performClick()
compose.onNodeWithText("Audience report").assertIsDisplayed()
compose.onNodeWithText("2 preparation slots remaining").assertIsDisplayed()
```

- [ ] Build the brief with public target/risks and instrumental label; desk with three concept still previews, four member strengths/fatigue cards and twelve mapped audio selections. Report only public arrangement risk changes before research. Use control-room art as a backdrop with readable solid/translucent panels, not text over busy equipment.
- [ ] Build unordered preparation controls with remaining-slot count and rehearsal/refinement diminishing-return descriptions. Research explicitly commits one slot; if full, request removal of an editable action first. Confirmation summarizes selected mix, concept, member and plan. Ignore double submits through the state transaction, not merely a disabled button.
- [ ] Broadcast uses selected concept loop plus neutral labelled member inset, continuous audio and accessible pause/mute controls. First attempt has no skip; subsequent attempts do. Render fallback/error states exactly as specified. Mark full viewing through the real playback-end event, not a wall-clock delay.
- [ ] Verdict shows weighted dimension contributions, top causes, invitation result and five segment deltas separately. Recap compares the previous attempt and confirms retry/reset. No follow-up button, nonexistent chart rank, live-vote claim or second-booking promise. Display a pass/fail badge and threshold “Needed 65.00.” Display score to two decimals using downward rounding for these non-negative scores (64.999 → 64.99), while judging uses the full value; test 64.96, 64.999 and 65.00. Do not round a failure up to an apparent pass.
- [ ] Test first-view completion, replay skip, rapid taps, research visibility independent of selection, background/resume, rotation, text scaling and narrow portrait layout. Inspect real Broadcast specifically for keyboard clearance and correct concept/member mapping. Commit `feat: connect first-booking producer loop`.

## Task 10: Verify the offline APK and evaluate the actual game

**Files:** Create `docs/producer-demo-playtest.md`; update build guide and README; add device `PersistenceRecoveryTest.kt`, `OfflineDemoTest.kt`.

**Interfaces:** Deliver a locally built `app/build/outputs/apk/debug/app-debug.apk`, documented checks and an honest list of remaining defects. This is not a store-ready release.

- [ ] Add red recovery tests around real persisted snapshots: restore after research, after confirmation and midway through mandatory viewing; duplicate confirmation must retain the same result. Test incompatible/corrupt saves and explicit reset. Verify a media defect does not erase a valid snapshot.
- [ ] Run the release gate:

```sh
python3 -m unittest discover -s art/audio/tests -v
python3 -m unittest discover -s tools/tests -v
python3 tools/validate_demo_assets.py --root app/src/main/assets/demo --report build/reports/demo-assets.json
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

- [ ] Install/run on the selected emulator with network disabled. Play a full first attempt, a failed invitation that still gains affinity, and a successful retry with skip. Rotate/background/force-stop/relaunch at each important boundary. Inspect the packaged APK contents and merged manifest: twelve mixes, required visual set, no masters/Blender files, no network permission.
- [ ] Record device/API level, toolchain versions, APK size, build/test results, asset measurement report and actual duration. Distinguish automated evidence, implementer inspection and user feedback. Do not label playtesting complete before the user plays.
- [ ] Ask the user to play without coaching: which choice was difficult, which mix change was audible, why did the verdict happen, and what would they change next time? Record the response; only tune content within this booking. New mechanics or bookings require a new approved scope.
- [ ] Commit verification/documentation checkpoint `test: verify offline producer demo end to end`; present APK and review notes. Stop before booking 2.

## Plan self-review and handoff

Coverage: research timing/commitment → Tasks 2/4/9; fatigue/order/arrangement failures → Tasks 2/3; original twelve-mix contract → Task 5; explicit concept/member matrix and four-second timing → Task 6; missing assets/measurements → Task 7; preview seek and clock → Task 8; skip/retry/save recovery → Tasks 4/9/10; deterministic explanations and separate audience → Task 3; offline native delivery → Tasks 1/10.

The numeric content tables, min SDK 28 and vectorized synthesis method are implementation proposals within the approved design, not previously measured outcomes. Review these along with the ten tasks before execution. No claim is made that the scoring seeds pass the balance gate or that the music is enjoyable until those tests and auditions occur.

After user review, execute inline with `superpowers:executing-plans`, or use delegated task-by-task work only if the user selects it. Do not start either execution mode while the user is reviewing this plan.

## Review evidence and technical references

A read-only enumeration during plan review evaluated the original formulas over 576 legal plans: 467 passes (81.08%), including 297/360 without research and 170/216 with research. Original maxima: Sparse 88.77 (keyboardist/mono, rehearse/refine/rest), Driving 82.90 (drummer/electric, refine/rest/rest), Layered 93.46 (keyboardist/warm, rehearse/refine/rest). Only keyboardist and drummer were invitation-optimal. This is a planning diagnostic, not a completed Task 3 test.

The revised invitation seeds above give 319/576 passes (55.38%) in the same diagnostic and all four members have winning optimal cells: frontman Sparse/mono 82.17, keyboardist Layered/warm 80.76, keytar Driving/electric 78.35, drummer Driving/mono 69.35. Sparse/mono passes 62/64 plans, compared with Layered/warm 60/64 and Driving/electric 48/64. Require both passing and failing plans even within the safest cell. The revised audience and safety/reach gates still require the production Task 3 report; do not claim the entire balance gate passes from this partial diagnostic.

- [Android unit-test resource option](https://developer.android.com/reference/tools/gradle-api/8.13/com/android/build/api/dsl/UnitTestOptions): enabling Android resources can pull asset/resource merging into local tests. Inspect the chosen AGP task graph.
- [Java Files.move contract](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html): unsupported atomic moves fail, and replacement of an existing target is implementation-specific with ATOMIC_MOVE. Verify on both supported filesystems.
- [Media3 gapless metadata](https://developer.android.google.cn/reference/androidx/media3/extractor/GaplessInfoHolder): delay/padding metadata exists, but this is not evidence that a particular encode/device meets our end-to-end timing budget.
