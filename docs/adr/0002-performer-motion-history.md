# ADR 0002: How the performer motion was reached, pass by pass

- Status: accepted (the user approved pass 4 on 2026-10-13: "yes this looks good!!!")
- Date: 2026-10-13
- Scope: the 20-second band performance (`art/blender/performance_motion.py`,
  `art/blender/prepare_performance.py`, `art/blender/tests/verify_performance_scene.py`)
- Related: [ADR 0001](0001-performer-motion.md) holds the rules as they stand now. This record holds
  the order of the work, the user's requests, the options that were set aside and why, and the
  measurements behind each change.

## Starting point

The bright cue (`art/audio/probe/final/bright-cue-candidate.mp3`, 120 BPM) was finished and merged in
PR #2. The performance scene had four performers (singer, BORG keyboard, keytar, drums) seen from the
broadcast camera: (0, −11, 7), about 28° down, 42 mm lens, 540×960 portrait preview.

## Pass 1: notes drive the joints

- `bright_cue.py --events` writes the cue's note and drum times to `performance-events.json`
  (289 events). `performance_motion.py` turns them into joint angles per frame.
- Every move eased in and out with smoothstep, so hits had no impact and presses had no attack.

## Research and ADR 0001

The user asked: "do a websearch for best practices on band movement in games and the like", then
"yes create/update an ADR with the above and proceed with the work". The search results and their
limits are in ADR 0001. The ones that changed the code:

- Hi-Fi Rush put key poses on the beat. We put each contact on the frame at or before its event.
- Anticipation and follow-through: accelerate into a hit, rebound out of it.
- Drum stick height follows loudness; ghost notes are mostly wrist.
- Piano animation moves the wrist on chords.

## Pass 2: easing and wrists

- `sample()` takes an easing per key: `"smooth"`, `"in"` (x²) or `"out"`.
- `contact()` floors event times to frames. All 289 event times were already on frame boundaries, so
  flooring moved nothing in this cue.
- `strokes()` accelerates into each hit and rebounds with `"out"`. `presses()` uses `"in"` on the way
  down.
- Wrist pivots on both drum sticks and the keyboard chord hand (`_arm(..., wrist=True)`).
- Drummer: ghost notes are mostly wrist, the snare shoulder and torso react to accents, crash strokes
  have gain 1.4.
- `LOUD = 1.33` on rock and nod amounts.
- **Set aside: kick and hi-hat pedal feet.** The drummer's legs are behind the riser and the singer
  from the broadcast camera, so the work would not show.

## GPT-6.1 review

The user asked: "call into gpt-6.1 to review performer motion adr". It ran through Codex
(`gpt-6.1-sol`, read-only). The review is at the session scratchpad, not in the repo. Its points and
what we did:

| Point | Action |
| --- | --- |
| Joint-angle peaks are not proof of contact; no 3D check | Recorded under "Not done" in ADR 0001. Pass 4 added a 3D check for the keytar hands only. |
| "Never after the audio" is a project choice, not an ITU result; 45/125 ms are detection thresholds, 90/185 ms acceptability | ADR 0001 now says this and states the sign convention. |
| The audio adds timing jitter to hats and ghosts after the events are written | Measured in `bright_cue.py` `drum_parts`: σ 3–3.5 ms. Recorded as far below what anyone can see. |
| The keyboard's half-bar chord presses are not in the events file | Recorded as invented playing gestures. |
| Perception studies were overstated; `LOUD = 1.33` has no research basis | Claims narrowed; `LOUD` labelled artistic tuning. |
| `LOUD` applies outside the loud sections | Recorded. |
| Test gaps (other impacts, rebound timing, final-hold constancy) | Motion tests grew from 11 to 15. |
| The singer and keyboard moved in unison | Pass 3 gives each performer a different head delay. |

## Pass 3: movement on the rest of the band

The user said: "most of the movement is on the drummer. any ideas on seeing more movement on the
others? for the borg keyboardist, the top keyboard is obscuring the keys (maybe that's ok) and for the
keytar, i don't see much at all".

Measured on screen during the drop, before the change:

| Part | Travel |
| --- | --- |
| Drummer's stick | 46 px |
| Keyboard hands | about 6 px |
| Keytar hands | 3–6 px |
| Head nods | about 2 px (most of the nod points at the camera) |

### BORG upper tier (call Q)

Three stills were compared: keep the upper tier, tilt it toward the camera, or remove it. Tilting did
not clear the keys from this camera height. The user chose "Remove upper tier". The
`BORG upper synthesizer*` and `Synth upper tier*` objects are hidden in the performance scene only.
The backing-vocal mic was also hidden, because it stood in front of the keytar neck and the keytar
player does not sing.

### Changes

- Keyboard hook hand slides with pitch (`-0.36*(note-76)/8`), hovers in bar 2, comes down on the first
  hook note, rises between notes, and lifts off after the final stab.
- Keyboard free hand goes up at the re-entry. **Call P:** bar 8 only, chosen by default and recorded
  in the map. Raising it at the drop as well was the other option.
- Keytar gets a tilt pivot ("PERFORMANCE | Keytar tilt"): −0.4 rad on the drop and the re-entry, −0.45
  from the final stab. The neck hand follows a pose table found by searching the scene.
- Head side tilts doubled. Head delays: keyboard 2 frames, singer 3, keytar 4.
- The user then asked: "yers and maybe have some flowing hair that moves for the lead singer at
  specific parts?" The singer tosses their head at `FLIPS` (bar 3, bar 8, bar 10 beat 8), and
  `hair_swing()` runs a spring chain per lock.

Measured after:

| Part | Travel |
| --- | --- |
| Keyboard hook hand | 20 px |
| Keytar neck hand | about 20 px |
| Keytar head | 15 px |
| Singer head | 10 px |

## Pass 4: the hair and the keytar hands

The user said: "ok getting there. that hairstyle for the frontman is all off. he needs a big hair on
top that waves. and the keytar is obscuring the players hand, so maybe some fixing needs to be done in
blender".

### Hair (call R)

| Try | Result |
| --- | --- |
| Shoulder-length locks (pass 3) | Rejected: "no long hair on the sides", "that's not new wave". |
| Flat-top (boxy high top) | Offered as a fourth option; not chosen. |
| Spiky crown (first big-top try) | Set aside before showing: it read as a spiky crown, not a new-wave cut. The tufts were made wider and thicker. |
| Quiff, side-swept wing, feathered | Rendered side by side. The user chose "B side-swept wing", and the tufts were then made less spiky. |

Final style: the original cap stays as short sides. The swept fringe is hidden. On top, eight tufts of
three segments (`segments=(0.08, 0.07, 0.05)`, width 0.115, thickness 0.1, taper 0.68, curl 0.3)
sweep up and over to camera left, with one long peak at the front. The `SIGNAL_ROOM_HAIR` switch used
for the comparison was removed.

### Keytar hands

The keys hand was behind the keytar body and the neck hand rested on the body.

Problems found on the way, in order:

1. The keys arm could not reach the front of the keys from any pose. Fix: the keytar moves 12 cm up
   and 7 cm toward the player (`KEYTAR_OFFSET = (0, 0.07, 0.12)`).
2. Bass presses of 0.28 pushed the hand 4.5 cm into the keys. Fix: `presses(bass, 0.1, lift=-0.1)`,
   about 1.5 cm.
3. The slide along the keys swings the hand on a shoulder arc, which pushed it back into the keys at
   the ends. Fix: `_keys_depth_fix(d) = -0.45*d - 0.5*d*d`, slide amplitude 0.3. Residual under 2 mm.
4. The neck-hand pose table no longer matched the moved keytar. Fix: `KEYTAR_NECK` was searched again.

The Blender check now requires, on every other frame: the keys hand at least 3 cm in front of the key
face, the neck hand in front of the neck, and neck-hand slip under 3 cm in the tilt frame.

The user approved this pass: "yes this looks good!!!"

## Calls and who made them

| Call | Choice | Made by |
| --- | --- | --- |
| N | Contact on the frame at or before the event | Default, from the research |
| P | Keyboard free hand up at bar 8 only | Default, recorded in the map |
| Q | Remove the BORG upper tier | User |
| R | Side-swept wing hair | User |
| O | Commit and merge with a PR and a merge commit | Pending the user's go-ahead |

## Not done

- Distance from each stick tip to its pad and each keyboard hand to its keys at every contact. Only
  the keytar hands are checked in 3D.
- Pedal feet (hidden from this camera).
- Fingers (the figures have block hands).
- The keyboard's half-bar chord presses are not in the events file.

## Tests at approval

- `art/blender/tests/`: 24 tests pass, 15 of them in `test_performance_motion.py`. Joint jump limit is 0.7 rad per frame
  for the drummer's elbow and wrist, 0.35 otherwise.
- `art/blender/tests/verify_performance_scene.py` passes in Blender 5.2.2.
