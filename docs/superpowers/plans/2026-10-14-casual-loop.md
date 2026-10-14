# Casual Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the producer demo into a short casual loop — Intro → Choose → Prepare (three tokens, readiness lights) → Watch (count-in, time left) → Reward (stars, one positive line, one tip) — with the old formulas behind "How the score works".

**Architecture:** The scoring model stays. New pure Kotlin functions in `core/simulation/RewardTip.kt` compute stars, readiness, token notes, the best one-token swap and the highlight; `feature/booking/RewardCopy.kt` turns them into text. The Compose UI is rebuilt screen by screen in new files under `feature/booking/`, sharing drawn graphics from `ScoreGraphics.kt`. The reducer gains only "retry from VERDICT"; saves are unchanged.

**Tech Stack:** Kotlin, Jetpack Compose Material 3 (BOM 2024.12.01), Media3, kotlinx.serialization, JUnit 4, Compose UI tests on the Pixel 7 Pro.

**Spec:** `docs/superpowers/specs/2026-10-14-casual-loop-design.md`

## Global Constraints

- The scoring model (`scoreBooking`, `audienceResponse`) and the balance numbers do not change. The invitation target stays 65.
- `RUN_SCHEMA_VERSION` stays 1 and `contentVersion` stays as it is; old saves must load.
- `Phase` keeps all six values. A save in RECAP opens on the Reward screen.
- Stars: 1 star below 65, 2 stars from 65, 3 stars from 75. Headlines: "Showstopper! Invited back.", "Invited back.", "Good set — not invited this time."
- Readiness: Low below 60, Good from 60, Max at 100.
- Late-night fit dots from `conceptFit`: below 50 one dot, 50–69 two, 70 and up three.
- Tip: only if the best one-token swap gains at least 1 point; otherwise "Your preparation was the best for this show — try another mix, member or stage."
- Research has no button. A save with `researched = true` shows a locked "Research" token and scores as before.
- Count-in "3 · 2 · 1" only on the first viewing of a fresh broadcast (not when restored from a save).
- Colours: existing theme (cyan primary `0xFF54DCEC`, coral secondary `0xFFFF7868`, navy background). Oxanium display font. No new libraries; graphics drawn with Compose Canvas.
- Buttons and labels at least 16 sp, medium weight. Unselected cards keep full text contrast.
- Render or run phone tests one at a time; check CPU load before heavy work (the user's standing rule).
- Commits end with:
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_014xNuuEG2xKMRUnj3hF4dKa
  ```

## Review Focus

1. A legacy save with `researched = true` restored on Prepare: the locked token fills one slot, cannot be removed, and the button says "Place 2 more tokens" — pinned in Task 6.
2. A legacy save restored in RECAP: it must open the Reward screen with "Play again" working — pinned in Task 3 (reducer) and Task 8 (phone).
3. The best-swap tip for a researched plan must never move the Research slot — pinned in Task 2.
4. Returning from Prepare to Choose and picking another member: lights, fatigue and notes must use the new member — pinned in Task 2 (pure) via `tokenNote` with a changed selection.
5. A broadcast restored from a save at 0 ms (process death during the count-in) must not count in again and must stay paused until Resume — pinned in Task 7.

## File Structure

- Modify `app/src/main/java/dev/digitalrain/synthwave/core/simulation/ScoreBooking.kt` — split `scoreBooking` into a checked wrapper and `projectBooking` (any plan with at most three slots); add `projectScore`.
- Create `app/src/main/java/dev/digitalrain/synthwave/core/simulation/RewardTip.kt` — stars, readiness, fit dots, token notes, best swap, highlight, `canBeInvited`, `allPlans`.
- Modify `app/src/main/java/dev/digitalrain/synthwave/core/simulation/RunReducer.kt` — retry from VERDICT; make the preparation adjuster shared.
- Modify `app/src/main/java/dev/digitalrain/synthwave/core/model/BookingModels.kt`, `core/data/ContentValidator.kt`, `assets/content/late-night.json` — concept descriptions.
- Create `app/src/main/java/dev/digitalrain/synthwave/feature/booking/RewardCopy.kt` — all new player-facing text.
- Modify `feature/booking/ScoreDisplay.kt` — `timeLeft`.
- Create `feature/booking/ScoreGraphics.kt` — `ScoreBar`, `StarRow`, `ReadinessLight`, `LevelBar`, `FitDots`.
- Create `feature/booking/IntroScreen.kt`, `ChooseScreen.kt`, `PrepareScreen.kt`, `RewardScreen.kt`.
- Modify `feature/booking/BookingScreen.kt` — phase switch, fixed bottom bar slot, broadcast screen with count-in and time left; remove the old Brief/Desk/Preparation/Verdict/Recap composables.
- Modify `app/src/main/java/dev/digitalrain/synthwave/SignalRoomApp.kt` — count-in state.
- Tests: `app/src/test/.../core/simulation/RewardTipTest.kt` (new), `ScoreBookingTest.kt`, `RunReducerTest.kt`, `app/src/test/.../core/data/ContentValidatorTest.kt`, `app/src/test/.../feature/booking/RewardCopyTest.kt` (new), `ScoreDisplayTest.kt`; phone tests `BookingFlowTest`, `ResearchFlowTest`, `RetryFlowTest`, `RetryResetDeviceTest`, `VerdictExplanationTest`.

Commands used throughout:

- JVM tests: `./gradlew -q testDebugUnitTest --tests '<pattern>'`
- Build: `./gradlew -q assembleDebug`
- One phone test class: `./gradlew -q connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.digitalrain.synthwave.feature.booking.<Class>`

---

### Task 1: Projected scoring and stars

**Files:**
- Modify: `app/src/main/java/dev/digitalrain/synthwave/core/simulation/ScoreBooking.kt`
- Create: `app/src/main/java/dev/digitalrain/synthwave/core/simulation/RewardTip.kt`
- Test: `app/src/test/java/dev/digitalrain/synthwave/core/simulation/RewardTipTest.kt`

**Interfaces:**
- Produces: `fun projectBooking(content: BookingContent, selection: Selection, prep: Preparation): BookingResult`, `fun projectScore(content: BookingContent, selection: Selection, prep: Preparation): Double`, `fun starsFor(score: Double): Int`, `enum class Readiness { LOW, GOOD, MAX }`, `fun readiness(value: Double): Readiness`, `fun fitDots(content: BookingContent, selection: Selection): Int`, `fun allPlans(): List<Preparation>`, `fun canBeInvited(content: BookingContent, selection: Selection): Boolean`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package dev.digitalrain.synthwave.core.simulation

import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import dev.digitalrain.synthwave.core.model.Preparation
import dev.digitalrain.synthwave.core.model.Selection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RewardTipTest {
    private val content = TestContent.lateNight()
    private val sfm = Selection(ArrangementId.SPARSE, MemberId.FRONTMAN, ConceptId.MONOCHROME)
    private val dke = Selection(ArrangementId.DRIVING, MemberId.KEYTAR, ConceptId.ELECTRIC)

    private fun selections() = ArrangementId.entries.flatMap { a ->
        MemberId.entries.flatMap { m -> ConceptId.entries.map { c -> Selection(a, m, c) } }
    }

    @Test fun projectionEqualsScoreForFullPlans() {
        for (selection in selections()) for (plan in allPlans()) {
            assertEquals(scoreBooking(content, selection, plan), projectBooking(content, selection, plan))
        }
    }

    @Test fun projectionAcceptsPartialPlansAndRejectsOverfullOnes() {
        assertEquals(74.37, projectScore(content, sfm, Preparation(0, 0, 0, false)), 1e-2)
        assertThrows(IllegalArgumentException::class.java) {
            projectBooking(content, sfm, Preparation(2, 2, 0, false))
        }
    }

    @Test fun extraRehearsalsCanLowerTheScore() {
        val one = projectScore(content, dke, Preparation(1, 0, 0, false))
        val two = projectScore(content, dke, Preparation(2, 0, 0, false))
        assertEquals(67.25, one, 1e-2)
        assertEquals(66.05, two, 1e-2)
    }

    @Test fun starBoundaries() {
        assertEquals(1, starsFor(64.99))
        assertEquals(2, starsFor(65.0))
        assertEquals(2, starsFor(74.99))
        assertEquals(3, starsFor(75.0))
    }

    @Test fun starSplitOverAllPlans() {
        val counts = selections().flatMap { s -> allPlans().map { starsFor(scoreBooking(content, s, it).invitationScore) } }
            .groupingBy { it }.eachCount()
        assertEquals(mapOf(1 to 148, 2 to 177, 3 to 35), counts)
    }

    @Test fun sevenSelectionsCannotBeInvited() {
        assertEquals(7, selections().count { !canBeInvited(content, it) })
        assertEquals(10, allPlans().size)
    }

    @Test fun readinessLevels() {
        assertEquals(Readiness.LOW, readiness(59.99))
        assertEquals(Readiness.GOOD, readiness(60.0))
        assertEquals(Readiness.GOOD, readiness(99.99))
        assertEquals(Readiness.MAX, readiness(100.0))
    }

    @Test fun fitDotsFollowConceptFit() {
        assertEquals(3, fitDots(content, sfm)) // 84
        assertEquals(2, fitDots(content, dke)) // 55
        assertEquals(1, fitDots(content, Selection(ArrangementId.SPARSE, MemberId.FRONTMAN, ConceptId.ELECTRIC))) // 35
        assertEquals(1, fitDots(content, Selection(ArrangementId.LAYERED, MemberId.FRONTMAN, ConceptId.MONOCHROME))) // 48
        assertEquals(2, fitDots(content, Selection(ArrangementId.DRIVING, MemberId.FRONTMAN, ConceptId.MONOCHROME))) // 50
    }
}
```

- [ ] **Step 2: Run to see it fail**

Run: `./gradlew -q testDebugUnitTest --tests 'dev.digitalrain.synthwave.core.simulation.RewardTipTest'`
Expected: compilation failure — `projectBooking`, `starsFor`, `allPlans` etc. are unresolved.

- [ ] **Step 3: Split `scoreBooking`**

In `ScoreBooking.kt`, replace the head of `scoreBooking` (the two `require` calls through the line before `val member = ...`) so the file reads:

```kotlin
fun scoreBooking(
    content: BookingContent,
    selection: Selection,
    prep: Preparation,
): BookingResult {
    require(prep.rehearsals >= 0 && prep.refinements >= 0 && prep.rests >= 0) {
        "Preparation counts must be non-negative"
    }
    require(prep.used == 3) { "Preparation must use exactly three slots" }
    return projectBooking(content, selection, prep)
}

/** Scores a plan with up to three slots; unplaced slots add nothing. */
fun projectBooking(
    content: BookingContent,
    selection: Selection,
    prep: Preparation,
): BookingResult {
    require(prep.rehearsals >= 0 && prep.refinements >= 0 && prep.rests >= 0) {
        "Preparation counts must be non-negative"
    }
    require(prep.used <= 3) { "Preparation can use at most three slots" }

    val member = requireNotNull(content.members[selection.member])
    // ... the rest of the former scoreBooking body, unchanged, ending in `return BookingResult(...)`
}

fun projectScore(content: BookingContent, selection: Selection, prep: Preparation): Double =
    projectBooking(content, selection, prep).invitationScore
```

(The comment line stands for the existing body from `val member = ...` to the closing `)` of `BookingResult(...)`; move it, do not change it.)

- [ ] **Step 4: Create `RewardTip.kt`**

```kotlin
package dev.digitalrain.synthwave.core.simulation

import dev.digitalrain.synthwave.core.model.BookingContent
import dev.digitalrain.synthwave.core.model.Preparation
import dev.digitalrain.synthwave.core.model.Selection

const val THREE_STAR_SCORE = 75.0

fun starsFor(score: Double): Int = when {
    score >= THREE_STAR_SCORE -> 3
    score >= 65.0 -> 2
    else -> 1
}

enum class Readiness { LOW, GOOD, MAX }

fun readiness(value: Double): Readiness = when {
    value >= 100.0 -> Readiness.MAX
    value >= 60.0 -> Readiness.GOOD
    else -> Readiness.LOW
}

fun fitDots(content: BookingContent, selection: Selection): Int {
    val fit = requireNotNull(content.arrangements[selection.arrangement]?.conceptFit?.get(selection.concept))
    return when {
        fit >= 70.0 -> 3
        fit >= 50.0 -> 2
        else -> 1
    }
}

/** The ten ways to spend three slots without Research. */
fun allPlans(): List<Preparation> = (0..3).flatMap { rehearsals ->
    (0..3 - rehearsals).map { refinements ->
        Preparation(rehearsals, refinements, 3 - rehearsals - refinements, false)
    }
}

fun canBeInvited(content: BookingContent, selection: Selection): Boolean =
    allPlans().any { scoreBooking(content, selection, it).invited }
```

Note: `starsFor` uses 65.0 literally because the spec fixes the 2-star line at the invitation target; if `invitationThreshold` ever changes, change both.

- [ ] **Step 5: Run the tests**

Run: `./gradlew -q testDebugUnitTest --tests 'dev.digitalrain.synthwave.core.simulation.*'`
Expected: all pass, including the existing `ScoreBookingTest`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dev/digitalrain/synthwave/core/simulation/ScoreBooking.kt app/src/main/java/dev/digitalrain/synthwave/core/simulation/RewardTip.kt app/src/test/java/dev/digitalrain/synthwave/core/simulation/RewardTipTest.kt
git commit -m "feat: projected scores, stars, readiness and fit dots"
```

---

### Task 2: Token notes, best swap and highlight

**Files:**
- Modify: `app/src/main/java/dev/digitalrain/synthwave/core/simulation/RunReducer.kt` (make the adjuster shared)
- Modify: `app/src/main/java/dev/digitalrain/synthwave/core/simulation/RewardTip.kt`
- Test: `app/src/test/java/dev/digitalrain/synthwave/core/simulation/RewardTipTest.kt`

**Interfaces:**
- Consumes: `projectBooking`, `projectScore`, `scoreBooking` (Task 1).
- Produces: `fun Preparation.adjusted(kind: PrepAction, delta: Int): Preparation`, `enum class TokenNote { AT_MAX, FULLY_RESTED, TOO_TIRED, LOWERS_SHOW, HARDLY_HELPS }`, `fun tokenNote(content: BookingContent, selection: Selection, prep: Preparation, kind: PrepAction): TokenNote?`, `data class Swap(val from: PrepAction, val to: PrepAction, val gain: Double)`, `fun bestSwap(content: BookingContent, selection: Selection, prep: Preparation): Swap?`, `enum class Highlight { LOOK_AND_SOUND, PLAYING, SLOT_FIT }`, `fun highlight(content: BookingContent, result: BookingResult): Highlight`.

- [ ] **Step 1: Add failing tests to `RewardTipTest`**

```kotlin
    private val lkw = Selection(ArrangementId.LAYERED, MemberId.KEYBOARDIST, ConceptId.WARM)

    @Test fun refineNoteWhenLookAndSoundIsAlreadyAtMax() {
        assertEquals(TokenNote.AT_MAX, tokenNote(content, sfm, Preparation(0, 1, 0, false), PrepAction.REFINE))
    }

    @Test fun rehearseNoteWhenTirednessOutweighsTheGain() {
        assertEquals(TokenNote.TOO_TIRED, tokenNote(content, dke, Preparation(1, 0, 0, false), PrepAction.REHEARSE))
        assertEquals(null, tokenNote(content, dke, Preparation(0, 0, 0, false), PrepAction.REHEARSE))
    }

    @Test fun restNoteWhenAlreadyRested() {
        assertEquals(TokenNote.FULLY_RESTED, tokenNote(content, lkw, Preparation(0, 0, 1, false), PrepAction.REST))
    }

    @Test fun noNoteOnceAllSlotsAreUsed() {
        assertEquals(null, tokenNote(content, sfm, Preparation(1, 1, 1, false), PrepAction.REFINE))
    }

    @Test fun notesFollowTheCurrentMember() {
        val prep = Preparation(0, 0, 1, false)
        assertEquals(TokenNote.FULLY_RESTED, tokenNote(content, lkw, prep, PrepAction.REST))
        assertEquals(null, tokenNote(content, lkw.copy(member = MemberId.FRONTMAN), prep, PrepAction.REST))
    }

    @Test fun bestSwapNamesTheLargestGain() {
        val swap = requireNotNull(bestSwap(content, sfm, Preparation(1, 1, 1, false)))
        assertEquals(PrepAction.REFINE, swap.from)
        assertEquals(PrepAction.REST, swap.to)
        assertEquals(82.17 - 80.02, swap.gain, 1e-2)
    }

    @Test fun noSwapForTheBestPlan() {
        assertEquals(null, bestSwap(content, sfm, Preparation(1, 0, 2, false)))
    }

    @Test fun swapNeverMovesResearch() {
        val prep = Preparation(1, 0, 1, true)
        val swap = bestSwap(content, sfm, prep)
        if (swap != null) {
            val moved = prep.adjusted(swap.from, -1).adjusted(swap.to, 1)
            assertEquals(true, moved.researched)
            assertEquals(3, moved.used)
        }
    }

    @Test fun highlightIsTheLargestWeightedPart() {
        val result = scoreBooking(content, sfm, Preparation(1, 1, 1, false))
        // .35 * 100 = 35 > .40 * 60.05 = 24.02 > .25 * 84 = 21
        assertEquals(Highlight.LOOK_AND_SOUND, highlight(content, result))
    }
```

Add `import dev.digitalrain.synthwave.core.model.PrepAction` to the imports.

- [ ] **Step 2: Run to see it fail**

Run: `./gradlew -q testDebugUnitTest --tests 'dev.digitalrain.synthwave.core.simulation.RewardTipTest'`
Expected: compilation failure — `tokenNote`, `TokenNote`, `bestSwap`, `adjusted`, `highlight` unresolved.

- [ ] **Step 3: Share the adjuster**

In `RunReducer.kt`, replace

```kotlin
private fun adjustedPreparation(prep: Preparation, kind: PrepAction, delta: Int): Preparation = when (kind) {
    PrepAction.REHEARSE -> prep.copy(rehearsals = prep.rehearsals + delta)
    PrepAction.REFINE -> prep.copy(refinements = prep.refinements + delta)
    PrepAction.REST -> prep.copy(rests = prep.rests + delta)
}
```

with

```kotlin
fun Preparation.adjusted(kind: PrepAction, delta: Int): Preparation = when (kind) {
    PrepAction.REHEARSE -> copy(rehearsals = rehearsals + delta)
    PrepAction.REFINE -> copy(refinements = refinements + delta)
    PrepAction.REST -> copy(rests = rests + delta)
}

fun Preparation.count(kind: PrepAction): Int = when (kind) {
    PrepAction.REHEARSE -> rehearsals
    PrepAction.REFINE -> refinements
    PrepAction.REST -> rests
}
```

and replace the three calls `adjustedPreparation(state.preparation, action.kind, action.delta)` with `state.preparation.adjusted(action.kind, action.delta)`.

- [ ] **Step 4: Add to `RewardTip.kt`**

```kotlin
// add imports: BookingResult, PrepAction

enum class TokenNote { AT_MAX, FULLY_RESTED, TOO_TIRED, LOWERS_SHOW, HARDLY_HELPS }

/** Why adding one token of [kind] now would add little or nothing; null when it helps. */
fun tokenNote(content: BookingContent, selection: Selection, prep: Preparation, kind: PrepAction): TokenNote? {
    if (prep.used >= 3) return null
    val now = projectBooking(content, selection, prep)
    if (kind == PrepAction.REFINE && now.coherence.value >= 100.0) return TokenNote.AT_MAX
    if (kind == PrepAction.REST && now.fatigueAfter.getValue(selection.member) == 0) return TokenNote.FULLY_RESTED
    val gain = projectScore(content, selection, prep.adjusted(kind, 1)) - now.invitationScore
    return when {
        gain < 0.0 && kind == PrepAction.REHEARSE -> TokenNote.TOO_TIRED
        gain < 0.0 -> TokenNote.LOWERS_SHOW
        gain < 0.5 -> TokenNote.HARDLY_HELPS
        else -> null
    }
}

data class Swap(val from: PrepAction, val to: PrepAction, val gain: Double)

/** The single token move that most improves a full plan, if it gains at least one point. */
fun bestSwap(content: BookingContent, selection: Selection, prep: Preparation): Swap? {
    val base = scoreBooking(content, selection, prep).invitationScore
    return PrepAction.entries.filter { prep.count(it) > 0 }.flatMap { from ->
        PrepAction.entries.filter { it != from }.map { to ->
            Swap(from, to, projectScore(content, selection, prep.adjusted(from, -1).adjusted(to, 1)) - base)
        }
    }.maxByOrNull { it.gain }?.takeIf { it.gain >= 1.0 }
}

enum class Highlight { LOOK_AND_SOUND, PLAYING, SLOT_FIT }

fun highlight(content: BookingContent, result: BookingResult): Highlight {
    val weights = content.balance.weights
    return listOf(
        Highlight.LOOK_AND_SOUND to weights[0] * result.coherence.value,
        Highlight.PLAYING to weights[1] * result.execution.value,
        Highlight.SLOT_FIT to weights[2] * result.briefFit.value,
    ).maxBy { it.second }.first
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew -q testDebugUnitTest --tests 'dev.digitalrain.synthwave.core.simulation.*'`
Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dev/digitalrain/synthwave/core/simulation/ app/src/test/java/dev/digitalrain/synthwave/core/simulation/RewardTipTest.kt
git commit -m "feat: token notes, best one-token swap and show highlight"
```

---

### Task 3: Retry from the reward screen

**Files:**
- Modify: `app/src/main/java/dev/digitalrain/synthwave/core/simulation/RunReducer.kt`
- Test: `app/src/test/java/dev/digitalrain/synthwave/core/simulation/RunReducerTest.kt`

**Interfaces:**
- Produces: `RunAction.ConfirmRetry` valid in `Phase.VERDICT` and `Phase.RECAP`.

- [ ] **Step 1: Add the failing test to `RunReducerTest`** (use the file's existing helpers for building a verdict state; the existing test at line ~98 builds `verdict` — copy its setup)

```kotlin
    @Test fun retryWorksFromVerdictAndKeepsPreviousResult() {
        val content = TestContent.lateNight()
        var state = newRun(content)
        for (action in listOf(RunAction.OpenDesk, RunAction.OpenPreparation,
            RunAction.SetPreparation(Preparation(1, 1, 1, false)), RunAction.ConfirmBroadcast,
            RunAction.CheckpointPlayback(20_000), RunAction.CompleteViewing)) {
            state = reduceRun(state, action, content)
        }
        assertEquals(Phase.VERDICT, state.phase)
        assertEquals(null, actionError(state, RunAction.ConfirmRetry, content))
        val retried = reduceRun(state, RunAction.ConfirmRetry, content)
        assertEquals(Phase.BRIEF, retried.phase)
        assertEquals(2, retried.attemptId)
        assertEquals(state.result, retried.previousResult)
        assertEquals(true, retried.viewingCompleted)
    }
```

(Add imports for `Phase` and `Preparation` if the file lacks them.)

- [ ] **Step 2: Run to see it fail**

Run: `./gradlew -q testDebugUnitTest --tests 'dev.digitalrain.synthwave.core.simulation.RunReducerTest'`
Expected: FAIL — `actionError` returns "Open the recap before retrying".

- [ ] **Step 3: Allow it**

In `RunReducer.kt` `actionError`, replace

```kotlin
        RunAction.ConfirmRetry -> if (state.phase == Phase.RECAP) null else "Open the recap before retrying"
```

with

```kotlin
        RunAction.ConfirmRetry ->
            if (state.phase == Phase.VERDICT || state.phase == Phase.RECAP) null else "Finish the show before retrying"
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew -q testDebugUnitTest --tests 'dev.digitalrain.synthwave.*'`
Expected: all pass. If an existing test asserted the old message, update it to "Finish the show before retrying".

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dev/digitalrain/synthwave/core/simulation/RunReducer.kt app/src/test/java/dev/digitalrain/synthwave/core/simulation/RunReducerTest.kt
git commit -m "feat: allow retry straight from the reward screen"
```

---

### Task 4: Stage descriptions and player-facing copy

**Files:**
- Modify: `app/src/main/java/dev/digitalrain/synthwave/core/model/BookingModels.kt`
- Modify: `app/src/main/java/dev/digitalrain/synthwave/core/data/ContentValidator.kt`
- Modify: `app/src/main/assets/content/late-night.json`
- Modify: `app/src/main/java/dev/digitalrain/synthwave/feature/booking/ScoreDisplay.kt`
- Create: `app/src/main/java/dev/digitalrain/synthwave/feature/booking/RewardCopy.kt`
- Test: `app/src/test/java/dev/digitalrain/synthwave/core/data/ContentValidatorTest.kt`, `app/src/test/java/dev/digitalrain/synthwave/feature/booking/RewardCopyTest.kt`, `ScoreDisplayTest.kt`

**Interfaces:**
- Consumes: `TokenNote`, `Swap`, `Highlight`, `Readiness` (Tasks 1–2).
- Produces: `ConceptProfile.description: String`; `fun timeLeft(positionMs: Long): String`; in `RewardCopy.kt`: `fun headline(stars: Int): String`, `fun positiveLine(highlight: Highlight): String`, `fun tipText(swap: Swap?): String`, `fun actionLabel(kind: PrepAction): String`, `fun noteText(note: TokenNote): String`, `fun readinessText(level: Readiness): String`, `fun goOnAirLabel(used: Int): String`, `const val HARD_SELL: String`, `const val INTRO_LINE: String`.

- [ ] **Step 1: Failing tests**

`app/src/test/java/dev/digitalrain/synthwave/feature/booking/RewardCopyTest.kt`:

```kotlin
package dev.digitalrain.synthwave.feature.booking

import dev.digitalrain.synthwave.core.model.PrepAction
import dev.digitalrain.synthwave.core.simulation.Highlight
import dev.digitalrain.synthwave.core.simulation.Swap
import dev.digitalrain.synthwave.core.simulation.TokenNote
import org.junit.Assert.assertEquals
import org.junit.Test

class RewardCopyTest {
    @Test fun headlines() {
        assertEquals("Good set — not invited this time.", headline(1))
        assertEquals("Invited back.", headline(2))
        assertEquals("Showstopper! Invited back.", headline(3))
    }

    @Test fun tipNamesTheSwapAndTruncatesTheGain() {
        assertEquals("Swap a Refine for a Rest next time: +2.15",
            tipText(Swap(PrepAction.REFINE, PrepAction.REST, 2.1549)))
        assertEquals("Your preparation was the best for this show — try another mix, member or stage.",
            tipText(null))
    }

    @Test fun goOnAirLabel() {
        assertEquals("Place 3 more tokens", goOnAirLabel(0))
        assertEquals("Place 1 more token", goOnAirLabel(2))
        assertEquals("Go on air", goOnAirLabel(3))
    }

    @Test fun notesAndHighlights() {
        assertEquals("Look & sound is already at max", noteText(TokenNote.AT_MAX))
        assertEquals("Too tired — another Rehearse hurts", noteText(TokenNote.TOO_TIRED))
        assertEquals("Your look and sound carried the show", positiveLine(Highlight.LOOK_AND_SOUND))
    }
}
```

In `ScoreDisplayTest.kt` add:

```kotlin
    @Test fun timeLeftRoundsUpToWholeSeconds() {
        assertEquals("0:20", timeLeft(0))
        assertEquals("0:01", timeLeft(19_000))
        assertEquals("0:01", timeLeft(19_001))
        assertEquals("0:00", timeLeft(20_000))
        assertEquals("0:20", timeLeft(-5))
    }
```

In `ContentValidatorTest.kt` add (follow the file's existing pattern for loading content and calling the validator; the function name used there applies):

```kotlin
    @Test fun conceptsNeedDescriptions() {
        val content = TestContent.lateNight()
        val blank = content.copy(concepts = content.concepts.mapValues { it.value.copy(description = " ") })
        assertTrue(validateContent(blank).any { "description" in it })
        assertTrue(validateContent(content).isEmpty())
    }
```

(If the validator function in `ContentValidator.kt` has a different name or return type, use it as the existing tests in this file do; import `TestContent` from `dev.digitalrain.synthwave.core.simulation`.)

- [ ] **Step 2: Run to see it fail**

Run: `./gradlew -q testDebugUnitTest --tests 'dev.digitalrain.synthwave.feature.booking.*' --tests 'dev.digitalrain.synthwave.core.data.ContentValidatorTest'`
Expected: compilation failures for the new names.

- [ ] **Step 3: Implement**

`BookingModels.kt`:

```kotlin
@Serializable
data class ConceptProfile(
    val displayName: String,
    val brightness: Double,
    val description: String = "",
)
```

`late-night.json` concepts:

```json
    "MONOCHROME": { "displayName": "Monochrome signal", "brightness": 20, "description": "Grey suits and signal bars; cool, restrained and late-night." },
    "WARM": { "displayName": "Warm transmission", "brightness": 50, "description": "Red and tan suits under amber light; a classic studio glow." },
    "ELECTRIC": { "displayName": "Electric broadcast", "brightness": 85, "description": "Blue rims and a test pattern; bright, loud and showy." }
```

`ContentValidator.kt`, next to the `researchReport` check:

```kotlin
    content.concepts.forEach { (id, concept) ->
        if (concept.description.isBlank()) add("$id concept description must not be blank")
    }
```

`ScoreDisplay.kt`, append:

```kotlin
/** Seconds left in the 20-second show, rounded up, as "0:14". */
fun timeLeft(positionMs: Long): String {
    val left = (20_000L - positionMs.coerceIn(0L, 20_000L) + 999L) / 1_000L
    return "0:%02d".format(left)
}
```

`RewardCopy.kt`:

```kotlin
package dev.digitalrain.synthwave.feature.booking

import dev.digitalrain.synthwave.core.model.PrepAction
import dev.digitalrain.synthwave.core.simulation.Highlight
import dev.digitalrain.synthwave.core.simulation.Readiness
import dev.digitalrain.synthwave.core.simulation.Swap
import dev.digitalrain.synthwave.core.simulation.TokenNote

const val INTRO_LINE =
    "You're the producer. Pick a performance, spend three preparation tokens, then go on air to earn a return booking."
const val HARD_SELL = "This mix and stage are a hard sell for the late-night slot."

fun headline(stars: Int): String = when (stars) {
    3 -> "Showstopper! Invited back."
    2 -> "Invited back."
    else -> "Good set — not invited this time."
}

fun positiveLine(highlight: Highlight): String = when (highlight) {
    Highlight.LOOK_AND_SOUND -> "Your look and sound carried the show"
    Highlight.PLAYING -> "The band played tight"
    Highlight.SLOT_FIT -> "A perfect fit for the late-night slot"
}

fun actionLabel(kind: PrepAction): String = when (kind) {
    PrepAction.REHEARSE -> "Rehearse"
    PrepAction.REFINE -> "Refine"
    PrepAction.REST -> "Rest"
}

fun actionEffect(kind: PrepAction): String = when (kind) {
    PrepAction.REHEARSE -> "Tighter playing, more tired"
    PrepAction.REFINE -> "The look and sound fit better"
    PrepAction.REST -> "Less tired"
}

fun tipText(swap: Swap?): String = if (swap == null) {
    "Your preparation was the best for this show — try another mix, member or stage."
} else {
    "Swap a ${actionLabel(swap.from)} for a ${actionLabel(swap.to)} next time: +${displayScore(swap.gain)}"
}

fun noteText(note: TokenNote): String = when (note) {
    TokenNote.AT_MAX -> "Look & sound is already at max"
    TokenNote.FULLY_RESTED -> "Already fully rested"
    TokenNote.TOO_TIRED -> "Too tired — another Rehearse hurts"
    TokenNote.LOWERS_SHOW -> "This would lower the show"
    TokenNote.HARDLY_HELPS -> "Hardly helps now"
}

fun readinessText(level: Readiness): String = when (level) {
    Readiness.LOW -> "Low"
    Readiness.GOOD -> "Good"
    Readiness.MAX -> "Max"
}

fun goOnAirLabel(used: Int): String {
    val left = 3 - used
    return if (left <= 0) "Go on air" else "Place $left more token${if (left == 1) "" else "s"}"
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew -q testDebugUnitTest`
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dev/digitalrain/synthwave/core/model/BookingModels.kt app/src/main/java/dev/digitalrain/synthwave/core/data/ContentValidator.kt app/src/main/assets/content/late-night.json app/src/main/java/dev/digitalrain/synthwave/feature/booking/ScoreDisplay.kt app/src/main/java/dev/digitalrain/synthwave/feature/booking/RewardCopy.kt app/src/test/
git commit -m "feat: stage descriptions and the casual loop's text"
```

---

### Task 5: Drawn score graphics, Intro and Choose screens

**Files:**
- Create: `app/src/main/java/dev/digitalrain/synthwave/feature/booking/ScoreGraphics.kt`
- Create: `app/src/main/java/dev/digitalrain/synthwave/feature/booking/IntroScreen.kt`
- Create: `app/src/main/java/dev/digitalrain/synthwave/feature/booking/ChooseScreen.kt`
- Modify: `app/src/main/java/dev/digitalrain/synthwave/feature/booking/BookingScreen.kt`
- Test: `app/src/androidTest/java/dev/digitalrain/synthwave/feature/booking/BookingFlowTest.kt`

**Interfaces:**
- Consumes: `fitDots`, `Readiness`, `readinessText`, `INTRO_LINE`.
- Produces (used by Tasks 6 and 8): in `ScoreGraphics.kt` — `@Composable fun ScoreBar(score: Double, previous: Double?, modifier: Modifier = Modifier)`, `@Composable fun StarRow(stars: Int, modifier: Modifier = Modifier)`, `@Composable fun ReadinessLight(label: String, level: Readiness)`, `@Composable fun LevelBar(label: String, value: Int)`, `@Composable fun FitDots(dots: Int)`, `internal val Panel: Color`, `@Composable fun PanelCard(content: @Composable ColumnScope.() -> Unit)`. In `BookingScreen.kt` — a fixed bottom bar slot used by Choose and Prepare.

- [ ] **Step 1: Update the phone test first** — replace the body of `choosesPlanAndConfirmsBroadcast` in `BookingFlowTest`:

```kotlin
    @Test fun choosesPlanAndConfirmsBroadcast() {
        compose.setContent { SignalRoomApp(MemoryStore()) }
        awaitText(INTRO_LINE)
        compose.onNodeWithTag("open-desk").performScrollTo().performClick()
        awaitTag("arrangement-driving")
        compose.onNodeWithTag("arrangement-driving").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("arrangement-driving").assertIsSelected() }.isSuccess
        }
        compose.onNodeWithTag("member-frontman").performScrollTo().performClick()
        compose.onNodeWithTag("concept-warm").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithTag("concept-warm").assertIsSelected() }.isSuccess
        }
        awaitText("Driving · Frontman · Warm transmission")
        compose.onNodeWithTag("open-preparation").performClick()
        awaitTag("prep-rehearse")
        compose.onNodeWithTag("prep-rehearse").performScrollTo().performClick()
        compose.onNodeWithTag("prep-refine").performScrollTo().performClick()
        awaitText("Place 1 more token")
        compose.onNodeWithTag("confirm-broadcast").assertIsNotEnabled()
        compose.onNodeWithTag("prep-rest").performScrollTo().performClick()
        awaitText("Go on air")
        compose.onNodeWithTag("confirm-broadcast").performClick()
        awaitTag("broadcast-stage")
        compose.onNodeWithText("Driving / Frontman / Warm transmission").assertIsDisplayed()
    }
```

Add imports `androidx.compose.ui.test.assertIsSelected`, `androidx.compose.ui.test.assertIsNotEnabled`. The Prepare-screen assertions pass after Task 6; this test is run at the end of Task 6.

- [ ] **Step 2: Create `ScoreGraphics.kt`**

```kotlin
package dev.digitalrain.synthwave.feature.booking

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.digitalrain.synthwave.core.simulation.Readiness
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal val Panel = Color(0xF5081422)

@Composable
fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** 0–100 bar with markers at the return booking (65) and three stars (75). */
@Composable
fun ScoreBar(score: Double, previous: Double?, modifier: Modifier = Modifier) {
    val fill = remember { Animatable(0f) }
    LaunchedEffect(score) { fill.animateTo((score / 100.0).toFloat().coerceIn(0f, 1f), tween(900)) }
    val track = MaterialTheme.colorScheme.onBackground.copy(alpha = .15f)
    val bar = MaterialTheme.colorScheme.primary
    val target = MaterialTheme.colorScheme.secondary
    val ghost = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f)
    Canvas(modifier.fillMaxWidth().height(28.dp).testTag("score-bar")
        .semantics { contentDescription = "Score ${displayScore(score)} of 100" }) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        drawRoundRect(bar, size = Size(size.width * fill.value, size.height), cornerRadius = r)
        fun mark(at: Double, color: Color, width: Float) {
            val x = size.width * (at / 100.0).toFloat()
            drawLine(color, Offset(x, -4f), Offset(x, size.height + 4f), strokeWidth = width)
        }
        if (previous != null) mark(previous, ghost, 3f)
        mark(65.0, target, 5f)
        mark(75.0, bar, 5f)
    }
}

private fun starPath(cx: Float, cy: Float, outer: Float): Path = Path().apply {
    val inner = outer * .45f
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) outer else inner
        val angle = -PI / 2 + i * PI / 5
        val x = cx + radius * cos(angle).toFloat()
        val y = cy + radius * sin(angle).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Three stars; the earned ones fill one by one, 0.4 s apart. */
@Composable
fun StarRow(stars: Int, modifier: Modifier = Modifier) {
    val fills = remember { List(3) { Animatable(0f) } }
    LaunchedEffect(stars) {
        fills.forEachIndexed { index, fill ->
            if (index < stars) fill.animateTo(1f, tween(400)) else fill.snapTo(0f)
        }
    }
    val lit = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onBackground.copy(alpha = .25f)
    Row(modifier.testTag("stars-$stars").semantics { contentDescription = "$stars of 3 stars" },
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        fills.forEach { fill ->
            Canvas(Modifier.size(64.dp)) {
                val path = starPath(size.width / 2, size.height / 2, size.minDimension / 2)
                drawPath(path, dim, style = Stroke(width = 4f))
                drawPath(path, lit.copy(alpha = fill.value))
            }
        }
    }
}

@Composable
fun ReadinessLight(label: String, level: Readiness) {
    val color = when (level) {
        Readiness.LOW -> MaterialTheme.colorScheme.secondary
        Readiness.GOOD -> MaterialTheme.colorScheme.primary
        Readiness.MAX -> Color(0xFFFFE08A)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.semantics { contentDescription = "$label ${readinessText(level)}" }) {
        Canvas(Modifier.size(18.dp)) { drawCircle(color) }
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(readinessText(level), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

/** A labelled 0–100 bar, used for fatigue. */
@Composable
fun LevelBar(label: String, value: Int) {
    val track = MaterialTheme.colorScheme.onBackground.copy(alpha = .15f)
    val fill = MaterialTheme.colorScheme.secondary
    Column(verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.semantics { contentDescription = "$label $value of 100" }) {
        Text("$label $value", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = r)
            drawRoundRect(fill, size = Size(size.width * value.coerceIn(0, 100) / 100f, size.height), cornerRadius = r)
        }
    }
}

@Composable
fun FitDots(dots: Int) {
    val lit = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.onBackground.copy(alpha = .25f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics { contentDescription = "Late-night fit $dots of 3" }) {
        Text("Late-night fit", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        repeat(3) { index -> Canvas(Modifier.size(12.dp)) { drawCircle(if (index < dots) lit else dim) } }
    }
}
```

Then delete `private val Panel` and `private fun PanelCard` from `BookingScreen.kt` (now shared).

- [ ] **Step 3: Create `IntroScreen.kt`**

```kotlin
package dev.digitalrain.synthwave.feature.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.digitalrain.synthwave.core.model.BookingContent
import dev.digitalrain.synthwave.core.simulation.RunAction

@Composable
internal fun IntroScreen(content: BookingContent, onAction: (RunAction) -> Unit) {
    Text(content.title, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold)
    Text("23:40 / late-night cable slot", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
    PanelCard {
        Text(INTRO_LINE, style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("1 Choose", "2 Prepare", "3 Watch", "4 Reward").forEach {
                Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
    Button(onClick = { onAction(RunAction.OpenDesk) }, modifier = Modifier.fillMaxWidth().testTag("open-desk")) {
        Text("Start", fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}
```

- [ ] **Step 4: Create `ChooseScreen.kt`**

```kotlin
package dev.digitalrain.synthwave.feature.booking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.digitalrain.synthwave.core.audio.PlaybackState
import dev.digitalrain.synthwave.core.audio.PlaybackStatus
import dev.digitalrain.synthwave.core.data.AssetCatalog
import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.BookingContent
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import dev.digitalrain.synthwave.core.model.RunSnapshot
import dev.digitalrain.synthwave.core.simulation.RunAction
import dev.digitalrain.synthwave.core.simulation.fitDots
import dev.digitalrain.synthwave.core.ui.SceneBackdrop

@Composable
internal fun ChooseScreen(snapshot: RunSnapshot, content: BookingContent, catalog: AssetCatalog,
                          onAction: (RunAction) -> Unit) {
    val selection = snapshot.selection
    Text("Choose your show", style = MaterialTheme.typography.headlineLarge)
    CardRow("MIX") {
        ArrangementId.entries.forEach { id ->
            val rules = content.arrangements.getValue(id)
            ChoiceCard(rules.displayName, rules.riskDescription, selection.arrangement == id,
                "arrangement-${id.name.lowercase()}", { onAction(RunAction.ChooseArrangement(id)) })
        }
    }
    CardRow("FEATURED MEMBER") {
        MemberId.entries.forEach { id ->
            val member = content.members.getValue(id)
            ChoiceCard(member.displayName, member.strength, selection.member == id,
                "member-${id.name.lowercase()}", { onAction(RunAction.ChooseMember(id)) },
                picture = { SceneBackdrop("demo/${catalog.member(id).stillPath}", "${member.displayName} still",
                    Modifier.fillMaxWidth().height(120.dp)) },
                footer = { LevelBar("Fatigue", member.fatigue) })
        }
    }
    CardRow("STAGE") {
        ConceptId.entries.forEach { id ->
            val concept = content.concepts.getValue(id)
            ChoiceCard(concept.displayName, concept.description, selection.concept == id,
                "concept-${id.name.lowercase()}", { onAction(RunAction.ChooseConcept(id)) },
                picture = { SceneBackdrop("demo/${catalog.concept(id).stillPath}", "${concept.displayName} preview",
                    Modifier.fillMaxWidth().height(120.dp)) })
        }
    }
    val key = "${selection.arrangement.name.lowercase()}-${selection.member.name.lowercase()}"
    PanelCard {
        Text("NOW PLAYING", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
        Text(content.mixDescriptions.getValue(key), fontSize = 16.sp)
    }
}

/** Fixed bar under the Choose screen: the pick, play/pause and Next. */
@Composable
internal fun ChooseBar(snapshot: RunSnapshot, content: BookingContent, playback: PlaybackState,
                       onAction: (RunAction) -> Unit, onPlayPause: () -> Unit) {
    val s = snapshot.selection
    Surface(color = Panel, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${content.arrangements.getValue(s.arrangement).displayName} · " +
                "${content.members.getValue(s.member).displayName} · ${content.concepts.getValue(s.concept).displayName}",
                fontSize = 16.sp, fontWeight = FontWeight.Medium)
            FitDots(fitDots(content, s))
            if (playback.status == PlaybackStatus.ERROR) Text("Could not play ${playback.errorAssetId}.",
                color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onPlayPause, modifier = Modifier.testTag("preview-play-pause")) {
                    Text(if (playback.status == PlaybackStatus.PLAYING) "Pause mix" else "Play mix", fontSize = 16.sp)
                }
                Button(onClick = { onAction(RunAction.OpenPreparation) },
                    modifier = Modifier.weight(1f).testTag("open-preparation")) { Text("Next", fontSize = 16.sp) }
            }
        }
    }
}

@Composable
private fun CardRow(label: String, cards: @Composable () -> Unit) {
    Text(label, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) { cards() }
}

@Composable
private fun ChoiceCard(title: String, line: String, selected: Boolean, tag: String, onClick: () -> Unit,
                       picture: (@Composable () -> Unit)? = null, footer: (@Composable () -> Unit)? = null) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp),
        color = if (selected) Color(0xFF12324A) else Panel,
        border = if (selected) BorderStroke(3.dp, primary) else BorderStroke(1.dp, Color(0x40E8E1D2)),
        modifier = Modifier.width(170.dp).testTag(tag).semantics { this.selected = selected }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            picture?.invoke()
            Text((if (selected) "✓ " else "") + title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                color = if (selected) primary else MaterialTheme.colorScheme.onBackground)
            Text(line, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
            footer?.invoke()
        }
    }
}
```

Cards are 170 dp wide, so on a 412 dp-wide phone the third card of each row shows partly, which signals the row scrolls.

- [ ] **Step 5: Restructure `BookingScreen.kt`'s layout** — replace the scrolling `Column(...)` block inside `BookingScreen` with a column whose top part scrolls and whose bottom bar stays fixed:

```kotlin
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("SIGNAL ROOM / BOOKING 01 / ATTEMPT ${snapshot.attemptId}",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (recoveryRequired) {
                    PanelCard {
                        Text(message ?: "Save could not be read", color = MaterialTheme.colorScheme.error)
                        Button(onClick = onReset, modifier = Modifier.testTag("reset-demo")) { Text("Start new demo") }
                    }
                } else {
                    when (snapshot.phase) {
                        Phase.BRIEF -> IntroScreen(content, onAction)
                        Phase.DESK -> ChooseScreen(snapshot, content, catalog, onAction)
                        Phase.PREPARATION -> PreparationScreen(snapshot, content, onAction)
                        Phase.BROADCAST -> BroadcastScreen(snapshot, content, catalog, playback, visual,
                            videoPlayer, onAction, onReset, onPlayPause, onMute)
                        Phase.VERDICT -> Verdict(snapshot, content, onAction)
                        Phase.RECAP -> Recap(snapshot, content, onAction, onReset)
                    }
                    if (message != null) PanelCard { Text(message, color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.height(24.dp))
            }
            if (!recoveryRequired && snapshot.phase == Phase.DESK) {
                ChooseBar(snapshot, content, playback, onAction, onPlayPause)
            }
        }
```

Delete the old `Brief` and `Desk` composables and the old `Choice` helper. Keep `PreparationScreen`, `Verdict`, `Recap` for now (replaced in Tasks 6 and 8).

- [ ] **Step 6: Build**

Run: `./gradlew -q assembleDebug testDebugUnitTest`
Expected: builds; JVM tests pass.

- [ ] **Step 7: Check on the phone** — install and screenshot the Intro and Choose screens:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.digitalrain.synthwave/.MainActivity
```

Open the Choose screen, take a screenshot (`adb exec-out screencap -p > /tmp/choose.png` into the scratchpad) and check: selected card has the outline and check mark, third card is partly visible, the bottom bar stays put while scrolling.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/dev/digitalrain/synthwave/feature/booking/ app/src/androidTest/java/dev/digitalrain/synthwave/feature/booking/BookingFlowTest.kt
git commit -m "feat: intro and card-based choose screen with a fixed bottom bar"
```

---

### Task 6: Prepare screen (the ritual)

**Files:**
- Create: `app/src/main/java/dev/digitalrain/synthwave/feature/booking/PrepareScreen.kt`
- Modify: `app/src/main/java/dev/digitalrain/synthwave/feature/booking/BookingScreen.kt`
- Modify: `app/src/androidTest/java/dev/digitalrain/synthwave/feature/booking/ResearchFlowTest.kt` (becomes the legacy-save test)

**Interfaces:**
- Consumes: `projectBooking`, `readiness`, `tokenNote`, `count`, `actionLabel`, `actionEffect`, `noteText`, `goOnAirLabel`, `ReadinessLight`, `LevelBar`, `PanelCard`, `Panel`.
- Produces: `PrepareScreen(snapshot, content, onAction)` and `PrepareBar(snapshot, onAction)`; tags `prep-rehearse`, `prep-refine`, `prep-rest`, `slot-0`..`slot-2`, `confirm-broadcast`, `return-desk`.

- [ ] **Step 1: Rewrite `ResearchFlowTest` as a legacy-save test**

```kotlin
package dev.digitalrain.synthwave.feature.booking

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.digitalrain.synthwave.SignalRoomApp
import dev.digitalrain.synthwave.core.data.loadContent
import dev.digitalrain.synthwave.core.model.Phase
import dev.digitalrain.synthwave.core.model.Preparation
import dev.digitalrain.synthwave.core.simulation.newRun
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResearchFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun legacyResearchSaveShowsALockedToken() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val content = loadContent(context.assets.open("content/late-night.json").bufferedReader().use { it.readText() })
        val store = MemoryStore()
        runBlocking {
            store.save(newRun(content).copy(phase = Phase.PREPARATION, preparation = Preparation(0, 0, 0, true)))
        }
        compose.setContent { SignalRoomApp(store) }
        waitFor("Research (locked)")
        waitFor("Place 2 more tokens")
        compose.onNodeWithTag("confirm-broadcast").assertIsNotEnabled()
        compose.onNodeWithTag("slot-0").performScrollTo().performClick()
        waitFor("Research (locked)")
        assertEquals(true, store.current?.preparation?.researched)
        compose.onNodeWithTag("prep-rest").performScrollTo().performClick()
        compose.onNodeWithTag("prep-rest").performScrollTo().performClick()
        waitFor("Go on air")
    }

    private fun waitFor(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
```

- [ ] **Step 2: Create `PrepareScreen.kt`**

```kotlin
package dev.digitalrain.synthwave.feature.booking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.digitalrain.synthwave.core.model.BookingContent
import dev.digitalrain.synthwave.core.model.PrepAction
import dev.digitalrain.synthwave.core.model.RunSnapshot
import dev.digitalrain.synthwave.core.simulation.RunAction
import dev.digitalrain.synthwave.core.simulation.count
import dev.digitalrain.synthwave.core.simulation.projectBooking
import dev.digitalrain.synthwave.core.simulation.readiness
import dev.digitalrain.synthwave.core.simulation.tokenNote

/** Slot contents in display order: a locked Research token first, then Rehearse, Refine, Rest. */
internal fun slotTokens(prep: dev.digitalrain.synthwave.core.model.Preparation): List<PrepAction?> =
    (if (prep.researched) listOf<PrepAction?>(null) else emptyList()) +
        PrepAction.entries.flatMap { kind -> List(prep.count(kind)) { kind } }

@Composable
internal fun PrepareScreen(snapshot: RunSnapshot, content: BookingContent, onAction: (RunAction) -> Unit) {
    val prep = snapshot.preparation
    val projected = projectBooking(content, snapshot.selection, prep)
    val member = content.members.getValue(snapshot.selection.member)
    Text("Get the band ready", style = MaterialTheme.typography.headlineLarge)
    OutlinedButton(onClick = { onAction(RunAction.ReturnToDesk) }, modifier = Modifier.testTag("return-desk")) {
        Text("Back", fontSize = 16.sp)
    }
    val tokens = slotTokens(prep)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) { index ->
            val token = tokens.getOrNull(index)
            val filled = index < tokens.size
            val label = when {
                !filled -> "Empty"
                token == null -> "Research (locked)"
                else -> actionLabel(token)
            }
            Surface(
                onClick = { if (token != null) onAction(RunAction.AdjustPreparation(token, -1)) },
                enabled = filled && token != null,
                shape = RoundedCornerShape(12.dp),
                color = if (filled) Color(0xFF12324A) else Panel,
                border = BorderStroke(if (filled) 2.dp else 1.dp,
                    if (filled) MaterialTheme.colorScheme.primary else Color(0x40E8E1D2)),
                modifier = Modifier.weight(1f).height(72.dp).testTag("slot-$index"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
    PanelCard {
        ReadinessLight("Look & sound", readiness(projected.coherence.value))
        ReadinessLight("Playing", readiness(projected.execution.value))
        LevelBar("${member.displayName} fatigue", projected.fatigueAfter.getValue(snapshot.selection.member))
    }
    PrepAction.entries.forEach { kind ->
        PanelCard {
            Text(actionLabel(kind), style = MaterialTheme.typography.titleLarge)
            Text(actionEffect(kind), fontSize = 16.sp)
            tokenNote(content, snapshot.selection, prep, kind)?.let {
                Text(noteText(it), fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary)
            }
            Button(onClick = { onAction(RunAction.AdjustPreparation(kind, 1)) }, enabled = prep.used < 3,
                modifier = Modifier.testTag("prep-${kind.name.lowercase()}")) {
                Text("Add ${actionLabel(kind)}", fontSize = 16.sp)
            }
        }
    }
    Text("Tap a placed token to take it back.", fontSize = 16.sp)
}

@Composable
internal fun PrepareBar(snapshot: RunSnapshot, onAction: (RunAction) -> Unit) {
    Surface(color = Panel, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Button(onClick = { onAction(RunAction.ConfirmBroadcast) }, enabled = snapshot.preparation.used == 3,
                modifier = Modifier.fillMaxWidth().testTag("confirm-broadcast")) {
                Text(goOnAirLabel(snapshot.preparation.used), fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
```

- [ ] **Step 3: Wire it in `BookingScreen.kt`** — in the phase switch replace `Phase.PREPARATION -> PreparationScreen(snapshot, content, onAction)` with `Phase.PREPARATION -> PrepareScreen(snapshot, content, onAction)`; after the `ChooseBar` line add

```kotlin
            if (!recoveryRequired && snapshot.phase == Phase.PREPARATION) PrepareBar(snapshot, onAction)
```

and delete the old `PreparationScreen` and `PrepControl` composables.

- [ ] **Step 4: Build and run the phone tests for Tasks 5–6** (one class at a time; check `uptime` first)

```bash
./gradlew -q assembleDebug testDebugUnitTest
./gradlew -q connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.digitalrain.synthwave.feature.booking.BookingFlowTest
./gradlew -q connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.digitalrain.synthwave.feature.booking.ResearchFlowTest
```

Expected: both pass. If `performScrollTo()` fails on a node outside a scroll container, drop it for that node (bar buttons are never scrolled).

- [ ] **Step 5: Screenshot** the Prepare screen with one, two and three tokens on the phone and check the lights, the fatigue bar, a note (choose Sparse / Frontman / Monochrome and add one Refine to see "Look & sound is already at max") and the button label.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dev/digitalrain/synthwave/feature/booking/ app/src/androidTest/java/dev/digitalrain/synthwave/feature/booking/ResearchFlowTest.kt
git commit -m "feat: token ritual with readiness lights, fatigue and notes"
```

---

### Task 7: Watch: count-in and time left

**Files:**
- Modify: `app/src/main/java/dev/digitalrain/synthwave/SignalRoomApp.kt`
- Modify: `app/src/main/java/dev/digitalrain/synthwave/feature/booking/BookingScreen.kt`
- Modify: `app/src/androidTest/java/dev/digitalrain/synthwave/feature/booking/RetryResetDeviceTest.kt`

**Interfaces:**
- Consumes: `timeLeft` (Task 4).
- Produces: `BookingScreen(..., countIn: Int, ...)`; overlay tag `count-in`; time text "0:14 left • playing".

- [ ] **Step 1: Update `RetryResetDeviceTest`** — replace the wait for `"19s / 20s • paused"` with `"0:01 left • paused"`. This test restores a broadcast from a save, so it also pins Review Focus 5 (no count-in on restore). Add after that wait:

```kotlin
        compose.onNodeWithTag("count-in").assertDoesNotExist()
```

- [ ] **Step 2: Add count-in state in `SignalRoomApp.kt`**

Add `import androidx.compose.runtime.setValue` and next to `broadcastLoadedAttempt`:

```kotlin
    var countIn by remember { mutableIntStateOf(0) }
```

In the `LaunchedEffect(snapshot.phase, snapshot.attemptId)` block that starts the broadcast, insert at the top of the `if (snapshot.phase == Phase.BROADCAST) {` branch:

```kotlin
            val fresh = !restoredFromStore && !snapshot.viewingCompleted && snapshot.broadcastPositionMs == 0L
            if (fresh) {
                for (n in 3 downTo 1) { countIn = n; delay(1_000) }
                countIn = 0
            }
```

Pass `countIn = countIn` to `BookingScreen(...)`.

- [ ] **Step 3: Show it in `BookingScreen.kt`** — add a parameter `countIn: Int = 0` after `recoveryRequired`, pass it to `BroadcastScreen`, add the parameter there, and inside the stage `Box` (after the fancam inset) add:

```kotlin
        if (countIn > 0) {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).testTag("count-in"),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$countIn", fontSize = 96.sp, fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary)
                    Text("ON AIR", fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
```

Replace the status line `Text("${playback.positionMs / 1_000}s / 20s • ${playback.status.name.lowercase()}")` in the broadcast panel with

```kotlin
        Text("${timeLeft(playback.positionMs)} left • ${playback.status.name.lowercase()}",
            fontSize = 18.sp, fontWeight = FontWeight.Medium)
```

and add the same text as a corner label on the stage: inside the stage `Box`,

```kotlin
        Text(timeLeft(playback.positionMs), Modifier.align(Alignment.TopEnd).background(Panel).padding(6.dp),
            fontSize = 18.sp, fontWeight = FontWeight.Medium)
```

- [ ] **Step 4: Build and run**

```bash
./gradlew -q assembleDebug testDebugUnitTest
./gradlew -q connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.digitalrain.synthwave.feature.booking.RetryResetDeviceTest
./gradlew -q connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.digitalrain.synthwave.feature.booking.BroadcastLifecycleTest
```

Expected: pass. (`RetryResetDeviceTest` still taps `open-recap`; it is updated again in Task 8. If it fails only on `open-recap`, continue to Task 8.)

- [ ] **Step 5: On the phone**, start a fresh broadcast and check that 3, 2, 1 shows over the still, then music and video start together, and the corner shows the time left.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dev/digitalrain/synthwave/SignalRoomApp.kt app/src/main/java/dev/digitalrain/synthwave/feature/booking/BookingScreen.kt app/src/androidTest/java/dev/digitalrain/synthwave/feature/booking/RetryResetDeviceTest.kt
git commit -m "feat: count-in before the first show and time left on screen"
```

---

### Task 8: Reward screen and "How the score works"

**Files:**
- Create: `app/src/main/java/dev/digitalrain/synthwave/feature/booking/RewardScreen.kt`
- Modify: `app/src/main/java/dev/digitalrain/synthwave/feature/booking/BookingScreen.kt`
- Modify: `app/src/androidTest/java/dev/digitalrain/synthwave/feature/booking/RetryFlowTest.kt`, `RetryResetDeviceTest.kt`, `VerdictExplanationTest.kt`

**Interfaces:**
- Consumes: `starsFor`, `highlight`, `bestSwap`, `canBeInvited`, `headline`, `positiveLine`, `tipText`, `HARD_SELL`, `ScoreBar`, `StarRow`, `PanelCard`, `displayScore`.
- Produces: `RewardScreen(snapshot, content, onAction, onReset)`; tags `reward`, `confirm-retry`, `score-details`, `reset-demo`, `stars-N`.

- [ ] **Step 1: Update the phone tests**

In `RetryFlowTest` and `RetryResetDeviceTest`, every wait for tag `"open-recap"` becomes a wait for tag `"reward"`, and every `compose.onNodeWithTag("open-recap").performScrollTo().performClick()` line is deleted (the reward screen has the retry and reset buttons directly).

Replace `VerdictExplanationTest`'s body after `compose.setContent { SignalRoomApp(store) }` with:

```kotlin
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("reward").fetchSemanticsNodes().isNotEmpty() }
        val stars = starsFor(result.invitationScore)
        compose.onNodeWithTag("stars-$stars").assertExists()
        compose.onNodeWithText(headline(stars)).assertExists()
        compose.onAllNodesWithText("WHY IT LANDED THIS WAY").assertCountEquals(0)
        compose.onNodeWithTag("score-details").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("WHY IT LANDED THIS WAY").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Preparation refinement: +23.00").assertExists()
        compose.onAllNodesWithText("Bounded to the 0–100 scale", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Coherence reached the 100-point cap", substring = true)
            .assertCountEquals(1)
```

Add imports `onAllNodesWithTag`, `onNodeWithTag`, `performClick`, `performScrollTo`, `dev.digitalrain.synthwave.core.simulation.starsFor`.

Add a second test to `VerdictExplanationTest` pinning Review Focus 2 (a save in RECAP opens the reward):

```kotlin
    @Test fun recapSaveOpensTheRewardAndRetries() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val content = loadContent(context.assets.open("content/late-night.json")
            .bufferedReader().use { it.readText() })
        val selection = Selection(ArrangementId.DRIVING, MemberId.FRONTMAN, ConceptId.WARM)
        val preparation = Preparation(1, 1, 1, false)
        val store = MemoryStore()
        kotlinx.coroutines.runBlocking {
            store.save(newRun(content).copy(phase = Phase.RECAP, selection = selection, preparation = preparation,
                result = scoreBooking(content, selection, preparation), viewingCompleted = true,
                broadcastPositionMs = 20_000))
        }
        compose.setContent { SignalRoomApp(store) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("reward").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("stars-1").assertExists() // 64.80
        compose.onNodeWithTag("confirm-retry").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("open-desk").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(store.current?.attemptId == 2)
    }
```

- [ ] **Step 2: Create `RewardScreen.kt`**

```kotlin
package dev.digitalrain.synthwave.feature.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.digitalrain.synthwave.core.model.BookingContent
import dev.digitalrain.synthwave.core.model.BookingResult
import dev.digitalrain.synthwave.core.model.Dimension
import dev.digitalrain.synthwave.core.model.RunSnapshot
import dev.digitalrain.synthwave.core.simulation.RunAction
import dev.digitalrain.synthwave.core.simulation.bestSwap
import dev.digitalrain.synthwave.core.simulation.canBeInvited
import dev.digitalrain.synthwave.core.simulation.highlight
import dev.digitalrain.synthwave.core.simulation.starsFor

@Composable
internal fun RewardScreen(snapshot: RunSnapshot, content: BookingContent,
                          onAction: (RunAction) -> Unit, onReset: () -> Unit) {
    val result = requireNotNull(snapshot.result)
    val stars = starsFor(result.invitationScore)
    var details by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag("reward"), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StarRow(stars)
        Text(headline(stars), style = MaterialTheme.typography.headlineLarge,
            color = if (result.invited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
        Text(positiveLine(highlight(content, result)), fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
    PanelCard {
        val previous = snapshot.previousResult?.invitationScore
        Text("${displayScore(result.invitationScore)} / 100" +
            (previous?.let { "   (${signed(result.invitationScore - it)} on last time)" } ?: ""),
            fontSize = 18.sp, fontWeight = FontWeight.Medium)
        ScoreBar(result.invitationScore, previous)
        Text("Return booking at 65 · three stars at 75", fontSize = 14.sp)
    }
    PanelCard {
        Text("NEXT TIME", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
        Text(tipText(bestSwap(content, snapshot.selection, snapshot.preparation)), fontSize = 16.sp)
        if (!canBeInvited(content, snapshot.selection)) Text(HARD_SELL, fontSize = 16.sp)
    }
    Button(onClick = { onAction(RunAction.ConfirmRetry) }, modifier = Modifier.fillMaxWidth().testTag("confirm-retry")) {
        Text("Play again", fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
    TextButton(onClick = { details = !details }, modifier = Modifier.testTag("score-details")) {
        Text(if (details) "Hide how the score works" else "How the score works", fontSize = 16.sp)
    }
    if (details) ScoreDetails(result, content)
    OutlinedButton(onClick = onReset, modifier = Modifier.testTag("reset-demo")) { Text("Reset demo", fontSize = 16.sp) }
}

/** The former verdict and recap explanation, unchanged in substance. */
@Composable
private fun ScoreDetails(result: BookingResult, content: BookingContent) {
    PanelCard {
        Text("THE SCORE", color = MaterialTheme.colorScheme.primary)
        DimensionRow("Coherence (look & sound)", result.coherence, content.balance.weights[0])
        DimensionRow("Execution (playing)", result.execution, content.balance.weights[1])
        DimensionRow("Brief fit (late-night slot)", result.briefFit, content.balance.weights[2])
    }
    PanelCard {
        Text("WHY IT LANDED THIS WAY", color = MaterialTheme.colorScheme.primary)
        result.coherence.contributions.filter { it.id != "concept-base" && it.id != "clamp" }
            .sortedByDescending { kotlin.math.abs(it.points) }
            .take(2).forEach { Text("${it.explanation}: ${signed(it.points)}") }
        result.execution.contributions.filter { it.id != "arrangement-base" && it.id != "clamp" }
            .sortedByDescending { kotlin.math.abs(it.points) }
            .take(2).forEach { Text("${it.explanation}: ${signed(it.points)}") }
        listOf("Coherence" to result.coherence, "Execution" to result.execution).forEach { (label, dimension) ->
            val overflow = dimension.contributions.firstOrNull { it.id == "clamp" }?.points ?: 0.0
            if (overflow < 0) Text("$label reached the 100-point cap; " +
                "${displayScore(-overflow)} extra points did not count.")
        }
    }
    PanelCard {
        Text("FIVE AUDIENCES", color = MaterialTheme.colorScheme.primary)
        Text("Awareness: how many of them know the band. Affinity: how much they like it.")
        content.segments.forEach { (id, segment) ->
            val after = result.audienceAfter.getValue(id)
            Text("${segment.displayName}: awareness ${signed(after.awareness - segment.initial.awareness)}, " +
                "affinity ${signed(after.affinity - segment.initial.affinity)}")
        }
        Text("Audience gains are separate from the invitation score.")
    }
    PanelCard {
        Text("THE BRIEF AND THE AUDIENCE REPORT", color = MaterialTheme.colorScheme.primary)
        content.brief.forEach { Text(it) }
        content.researchReport.forEach { Text(it) }
    }
}

@Composable
private fun DimensionRow(label: String, dimension: Dimension, weight: Double) {
    Text("$label ${displayScore(dimension.value)} × ${displayScore(weight)} = " +
        displayScore(dimension.value * weight))
}

internal fun signed(value: Double): String = (if (value >= 0) "+" else "−") + displayScore(kotlin.math.abs(value))
```

- [ ] **Step 3: Wire it in `BookingScreen.kt`** — replace the two switch lines

```kotlin
                        Phase.VERDICT -> Verdict(snapshot, content, onAction)
                        Phase.RECAP -> Recap(snapshot, content, onAction, onReset)
```

with

```kotlin
                        Phase.VERDICT, Phase.RECAP -> RewardScreen(snapshot, content, onAction, onReset)
```

and delete the old `Verdict`, `DimensionRows`, `Recap` composables and the old private `signed` (now in `RewardScreen.kt`). Remove imports that become unused.

- [ ] **Step 4: Build and run all JVM tests**

Run: `./gradlew -q assembleDebug testDebugUnitTest`
Expected: pass.

- [ ] **Step 5: Run the phone tests touched here, one class at a time**

```bash
for c in VerdictExplanationTest RetryResetDeviceTest RetryFlowTest; do
  ./gradlew -q connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.digitalrain.synthwave.feature.booking.$c || echo "FAILED $c"
done
```

Expected: no FAILED lines.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dev/digitalrain/synthwave/feature/booking/ app/src/androidTest/java/dev/digitalrain/synthwave/feature/booking/
git commit -m "feat: reward screen with stars, one tip and the score details behind a link"
```

---

### Task 9: Full verification, screenshots and docs

**Files:**
- Modify: `docs/producer-demo-build.md`, `docs/producer-demo-playtest.md`, `docs/handoff/2026-10-13-mix-performances.md`

- [ ] **Step 1: Full JVM and build**

Run: `./gradlew -q assembleDebug testDebugUnitTest` and count results:

```bash
python3 - <<'EOF'
import glob,re
t=f=0
for p in glob.glob("app/build/test-results/testDebugUnitTest/*.xml"):
    s=open(p).read(); m=re.search(r'tests="(\d+)".*?failures="(\d+)" errors="(\d+)"',s); t+=int(m[1]); f+=int(m[2])+int(m[3])
print("unit", t, "failed", f)
EOF
```

Expected: `failed 0`.

- [ ] **Step 2: All phone tests, one class at a time** (check `uptime` first; the user asked for one at a time)

```bash
for c in core.audio.MediaPlaybackTest core.data.FileSnapshotStoreDeviceTest feature.booking.BookingFlowTest feature.booking.BroadcastLifecycleTest feature.booking.BroadcastVisualTest feature.booking.OfflineDemoTest feature.booking.PersistenceRecoveryTest feature.booking.ResearchFlowTest feature.booking.RetryFlowTest feature.booking.RetryResetDeviceTest feature.booking.VerdictExplanationTest; do
  ./gradlew -q connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.digitalrain.synthwave.$c || echo "FAILED $c"
done
```

Expected: no FAILED lines. `RetryResetDeviceTest` leaves the user's real save untouched (it uses a temporary directory).

- [ ] **Step 3: Screenshots on the Pixel 7 Pro** — install the debug APK, play one loop (Sparse / Frontman / Monochrome, Rehearse + Rest + Rest) and save screenshots of Intro, Choose, Prepare (3 tokens), Watch (during count-in and during play) and Reward (3 stars) into the scratchpad. Send them to the user with SendUserFile.

- [ ] **Step 4: Docs**
  - `docs/producer-demo-build.md`: replace the description of the producer flow with the five screens (Intro, Choose, Prepare, Watch, Reward) and note that formulas are under "How the score works".
  - `docs/producer-demo-playtest.md`: under the 2026-10-14 playtest record, add "Casual loop implemented on branch `casual-loop`; awaiting the user's second playtest."
  - Handoff doc: append a short section on the casual loop and the open second playtest.

- [ ] **Step 5: Commit, push, PR**

```bash
git add docs/
git commit -m "docs: casual loop build and test notes"
git push -u origin casual-loop
gh pr create --base main --head casual-loop --title "Casual loop: choose, prepare, watch, reward" --body-file <body>
```

The PR body lists the five screens, what moved behind "How the score works", the test counts from Steps 1–2, and ends with:

```
🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_014xNuuEG2xKMRUnj3hF4dKa
```

Do not merge until the user says "merge"; then `gh pr merge N --merge` (never squash).
