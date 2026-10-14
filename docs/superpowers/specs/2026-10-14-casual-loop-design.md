# Casual loop: choose, prepare, watch, reward

- Date: 2026-10-14
- Status: draft for the user's review, revised after a review by GPT-6.1-sol (high)
- Branch: `casual-loop` (from `singer-sightline`, PR #8)

## Why

The user's uncoached playtest (two attempts, 77.52 and 80.76, both invited) found the core loop
understandable but the game opaque: the brief introduces several systems before saying what the
player does; the desk takes several screens of scrolling; selected options look like unselected
ones; "Confirm broadcast" is disabled without a reason; results explain formulas but not what to
change; "awareness" and "affinity" are unexplained; the broadcast has no countdown.

The user's direction: "this is a simple casual game ... add some ritual before the great music and
animation with a cool reward/prize/congrats screen ... simple loop, great simple visuals, great
music, that's it." The formulas stay available "if someone wants to click and see", hidden by
default.

## Goal

One short loop that a first-time player understands without reading rules:

1. **Choose** a mix, a featured member and a stage.
2. **Prepare**: place three tokens and watch the band get ready.
3. **Watch** the 20-second performance.
4. **Reward**: stars, a congratulation, one tip, play again.

Success: a new player can finish a loop and say why they got their stars and what to try next,
without opening the details.

## What stays

- The scoring model (`scoreBooking`, `audienceResponse`) and the content file. The invitation
  target stays 65.
- The 36 performance videos, 12 mixes, stills and the media pipeline.
- The save file. `Phase` keeps all six values so older saves load (see Saves).
- Test tags used by the phone tests where the control still exists: `open-desk`,
  `open-preparation`, `prep-rehearse`, `prep-refine`, `prep-rest`, `remove-*`, `confirm-broadcast`,
  `broadcast-stage`, `broadcast-play-pause`, `broadcast-mute`, `skip-performance`, `confirm-retry`,
  `reset-demo`, `arrangement-*`, `member-*`, `concept-*`, `preview-play-pause`, `return-desk`.

## What changes, screen by screen

### 1. Intro (phase BRIEF)

- Title, the 23:40 slot line, and one sentence: "You're the producer. Pick a performance, spend
  three preparation tokens, then go on air to earn a return booking."
- A four-step strip (Choose, Prepare, Watch, Reward) as icons.
- One button: "Start" (`open-desk`). The three brief paragraphs move into "How the score works".

### 2. Choose (phase DESK)

- Three sections, each a horizontal row of large cards: Mix (3), Member (4), Stage (3).
- Each card: a picture (member still, concept still; for a mix, a simple icon of its energy), the
  name, and the existing one-line description. The selected card has a bright 3 dp outline in the
  primary colour, a check mark and a filled background; unselected cards are dimmed. Button text
  uses a medium weight at full contrast.
- A fixed bottom bar, always visible: the current pick ("Driving · Keytar · Electric"), a
  play/pause button for the mix (`preview-play-pause`) and "Next" (`open-preparation`).
- Member cards show fatigue as a labelled bar ("Fatigue") instead of "Fatigue 25/100".
- Each card row shows a partial next card at the right edge so it is clear the row scrolls.
  Unselected cards stay at full text contrast; only the selected card gets the outline, check
  mark and filled background.
- Stage cards get a one-line description (new content text, one per concept).
- A "Late-night fit" line under the bottom-bar pick shows 1–3 dots from the mix and stage's brief
  fit (`conceptFit`: below 50 one dot, 50–69 two, 70 and up three; today only Sparse with
  Monochrome or Warm gets three), so a weak pairing is visible before preparing.

### 3. Prepare (phase PREPARATION) — the ritual

- Three token slots at the top, filled in order as the player taps Rehearse, Refine or Rest
  (`prep-*`). Tapping a filled slot removes that token.
- One action card per kind, with a plain effect line: "Rehearse: tighter playing, more tired",
  "Refine: the look and sound fit better", "Rest: less tired".
- **Two readiness lights**, not the score: "Look & sound" (coherence) and "Playing" (execution),
  each Low / Good / Max from the plan so far, and the featured member's **fatigue bar**. Lights and
  fatigue animate when a token is placed. The score itself stays hidden until the reward, so the
  reveal keeps its tension. Thresholds: Low below 60, Good from 60, Max at the 100 cap.
- A token that adds nothing or costs points shows a short note under its card, from the actual
  projected effect: "Look & sound is already at max" or "Too tired — another Rehearse hurts". Its
  button stays enabled.
- The button reads "Place 2 more tokens" while disabled and "Go on air" (`confirm-broadcast`)
  when all three are placed.
- "Back" (`return-desk`) returns to Choose with the tokens kept.
- **Research is removed from play.** Its report moves into "How the score works". The
  `researched` field stays in the save and is always false for new runs.

### 4. Watch (phase BROADCAST)

- **Go-on-air transition**: a 3-second "3 · 2 · 1 · ON AIR" count over the stage still, then the
  performance starts. Only on the first viewing.
- The stage video as now, with the time left as text ("0:14") in the corner, driven by the
  playback position.
- Pause/resume, mute and skip (after the first full viewing) as now.

### 5. Reward (phase VERDICT; RECAP is folded in)

- A full-screen reveal: stars fill one by one (about 0.4 s each) in the stage colours, then a large
  award card.
  - 3 stars: score ≥ 75, "Showstopper! Invited back."
  - 2 stars: score ≥ 65, "Invited back."
  - 1 star: below 65, "Good set — not invited this time."
  The thresholds are provisional. Over all 360 plans (each selection with each preparation,
  equally weighted, not likely player choices) the split is 148 / 177 / 35 plans (41.11 / 49.17 /
  9.72%). 9 of the 36 selections can reach 3 stars; 7 cannot be invited with any preparation.
- **One positive line** naming the strongest weighted part: "Your look and sound carried the
  show", "The band played tight", or "A perfect fit for the late-night slot".
- The score on a 0–100 bar with markers at 65 and 75, the previous attempt's score as a faint
  marker and the change ("+3.24") on a retry.
- **One tip**, from the actual best one-token swap: every plan that moves one token to another
  kind is scored with `projectScore`; if the best gains at least 1 point, the tip names it ("Swap a
  Refine for a Rest next time: +2.40"). Otherwise: "Your preparation was the best for this show —
  try another mix, member or stage." For the 7 selections that cannot be invited it adds "This
  mix and stage are a hard sell for the late-night slot."
- Buttons: "Play again" (`confirm-retry`).
- A small "How the score works" link opens a sheet with the current formula text (the three
  parts, weights, caps, the "why it landed" lines, the five audiences' awareness and affinity
  changes with a one-line definition of each, the brief and the research report). This is the
  existing verdict content, moved.

### Visual style

- The existing dark navy, cyan (primary) and coral (secondary) colours and the Oxanium display
  font. Stars and the meter use cyan; the 65 marker coral.
- All graphics drawn with Compose Canvas and standard animations; no new libraries.
- Buttons and labels at least 16 sp, medium weight.

## Saves

- `RUN_SCHEMA_VERSION` stays 1; no new fields are required.
- A save in RECAP opens on the Reward screen.
- Retry keeps what it keeps now: `attemptId` + 1, `previousResult` and `viewingCompleted`.
- Preparation is stored as counts, so restored tokens are shown in the order Rehearse, Refine,
  Rest.
- A save with `researched = true` loads, shows a locked "Research" token in one slot, and scores
  as before.
- `ConfirmRetry` is allowed from VERDICT and RECAP.

## Code

- `core/simulation/ScoreBooking.kt`: add `projectScore(content, selection, prep)`, which scores a
  plan with fewer than three slots (unplaced slots do nothing), and `starsFor(score)`.
- New `core/simulation/RewardTip.kt`: the readiness levels, the token notes, the positive line and
  the best-swap tip as pure functions.
- `core/simulation/RunReducer.kt`: allow `ConfirmRetry` from VERDICT. `PurchaseResearch` stays
  for old saves but has no button.
- New screens go in their own files under `feature/booking/` (`IntroScreen.kt`,
  `ChooseScreen.kt`, `PrepareScreen.kt`, `RewardScreen.kt`) with the shared bar and stars in
  `ScoreGraphics.kt`. The broadcast screen stays in `BookingScreen.kt`.

## Testing

- JVM: `projectScore` equals `scoreBooking` for full plans (not monotonic: extra rehearsals can
  lower the score, e.g. Driving / Keytar / Electric 67.25 → 66.05 → 63.65); `starsFor` boundaries
  (64.99, 65, 74.99, 75); readiness levels and token notes; the best-swap tip and its 1-point
  guard; the hard-sell line for the 7 selections; reducer retry from VERDICT; old RECAP and
  researched saves load.
- Phone: update `BookingFlowTest`, `ResearchFlowTest` (becomes a legacy-save test), `RetryFlowTest`,
  `RetryResetDeviceTest`, `VerdictExplanationTest` and the selected-text and countdown
  assertions; keep the lifecycle, persistence and recovery coverage. Stars and headline are
  checked for two plans with the expected stars computed from `scoreBooking`; "How the score
  works" shows "Coherence reached the 100-point cap" for a capped plan.
- Screenshots of each screen on the Pixel 7 Pro for the user's review.

## Out of scope

- New music, new videos, sound effects for the reward (can follow).
- "Watch again", a crowd-icon row on the reward screen, a beat-pulsing meter, a countdown ring.
- Changing the balance numbers.
- More than one booking.
