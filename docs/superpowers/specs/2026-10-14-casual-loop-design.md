# Casual loop: choose, prepare, watch, reward

- Date: 2026-10-14
- Status: draft for the user's review
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
2. **Prepare**: place three tokens and watch a show meter rise.
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
- Member cards show fatigue as a small bar instead of "Fatigue 25/100".

### 3. Prepare (phase PREPARATION) — the ritual

- Three token slots at the top, filled in order as the player taps Rehearse, Refine or Rest
  (`prep-*`). Tapping a filled slot removes that token.
- One action card per kind, with a plain effect line: "Rehearse: tighter playing, more tired",
  "Refine: the look and sound fit better", "Rest: less tired".
- **Show meter**: a large horizontal gauge 0–100 with a marker at 65 labelled "Return booking"
  and a marker at 75 labelled "3 stars". It shows the projected score for the current tokens and
  animates when a token is placed. With fewer than three tokens, unplaced slots count as nothing.
- **Fatigue bar** for the featured member, updating as tokens are placed.
- A token that adds nothing (for example a refinement once coherence is at its cap) shows a short
  note under its card: "Already at max — try Rehearse or Rest". Its button stays enabled.
- The button reads "Place 2 more tokens" while disabled and "Go on air" (`confirm-broadcast`)
  when all three are placed.
- "Back" (`return-desk`) returns to Choose with the tokens kept.
- **Research is removed from play.** Its report moves into "How the score works". The
  `researched` field stays in the save and is always false for new runs.

### 4. Watch (phase BROADCAST)

- The stage video as now, with a countdown ring and "0:14" in the corner, and the show meter as a
  thin bar under the video that pulses on the beat (the mix's 120 BPM).
- Pause/resume, mute and skip (after the first full viewing) as now.

### 5. Reward (phase VERDICT; RECAP is folded in)

- A full-screen reveal: stars fill one by one (about 0.4 s each) with the stage colours, then the
  headline.
  - 3 stars: score ≥ 75, "Showstopper! Invited back."
  - 2 stars: score ≥ 65, "Invited back."
  - 1 star: below 65, "Good set — not invited this time."
  Over all 360 plans, about 41% get 1 star, 49% get 2 and 10% get 3.
- The final score on the show meter, with the previous attempt's score as a faint marker and the
  change ("+3.24") on a retry.
- **One tip**, chosen by fixed rules in this order:
  1. Coherence was capped: "Your look and sound were already perfect — swap a Refine for
     Rehearse or Rest."
  2. Fatigue cost more than 8 points of execution: "<Member> was tired — try a Rest."
  3. Otherwise the part with the lowest weighted share: execution → "Try a Rehearse", coherence →
     "Try a Refine", brief fit → "Try a different mix or stage for this late-night slot."
  4. Rules 1–3 are checked against `projectScore`: the suggested one-token change (one token
     moved to another kind) must gain at least 1 point, otherwise the next rule is tried. If no rule qualifies:
     "Try a different mix, member or stage to see another show."
- **Crowd row**: five audience icons (performance, melodic-lead, fashion, story, retro fans). An
  icon lights up when that audience was won over (the existing `adopted` rule) and shows a small
  heart count otherwise. Tapping an icon shows one line about what they like.
- Buttons: "Play again" (`confirm-retry`, keeps nothing but the previous score) and "Watch again"
  (replays the performance without changing the result).
- A small "How the score works" link opens a sheet with the current formula text (the three
  parts, weights, caps, the "why it landed" lines, audience awareness and affinity numbers, the
  brief and the research report). This is the existing verdict content, moved.

### Visual style

- The existing dark navy, cyan (primary) and coral (secondary) colours and the Oxanium display
  font. Stars and the meter use cyan; the 65 marker coral.
- All graphics drawn with Compose Canvas and standard animations; no new libraries.
- Buttons and labels at least 16 sp, medium weight.

## Saves

- `RUN_SCHEMA_VERSION` stays 1; no new fields are required.
- A save in RECAP opens on the Reward screen.
- A save with `researched = true` loads, still counts that slot as used, and scores as before.
- `ConfirmRetry` is allowed from VERDICT and RECAP.

## Code

- `core/simulation/ScoreBooking.kt`: add `projectScore(content, selection, prep)` that scores a
  plan with fewer than three slots (unplaced slots do nothing), and `starsFor(score)`.
- New `core/simulation/RewardTip.kt`: the tip rules above as a pure function.
- `core/simulation/RunReducer.kt`: allow `ConfirmRetry` from VERDICT; add `ReplayViewing`
  (VERDICT → BROADCAST at 0 ms, result unchanged); `PurchaseResearch` stays for old saves but has
  no button.
- `feature/booking/BookingScreen.kt` is split by screen: `IntroScreen.kt`, `ChooseScreen.kt`,
  `PrepareScreen.kt`, `WatchScreen.kt`, `RewardScreen.kt`, plus `ShowMeter.kt` and `Stars.kt`
  for the shared graphics. `BookingScreen.kt` keeps only the phase switch and backdrop.

## Testing

- JVM: `projectScore` equals `scoreBooking` for full plans and is monotonic per token; `starsFor`
  boundaries (64.99, 65, 74.99, 75); every tip rule with a crafted plan; the 1-point rule; reducer
  retry from VERDICT and replay keeps the result; old RECAP and researched saves load.
- Phone (existing device tests updated): a full loop by tags; the disabled "Go on air" label; stars
  and headline for two known plans, with the expected stars computed in the test from
  `scoreBooking`, not hard-coded; "How the score works" shows
  "Coherence reached the 100-point cap" for the capped plan; countdown text during playback.
- Screenshots of each screen on the Pixel 7 Pro for the user's review.

## Out of scope

- New music, new videos, sound effects for the reward (can follow).
- Changing the balance numbers.
- More than one booking.
