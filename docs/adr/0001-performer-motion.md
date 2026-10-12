# ADR 0001: Performer motion follows the cue's notes, with contact on the beat

- Status: accepted
- Date: 2026-10-13 (pass 2), revised 2026-10-13 (pass 3, GPT-6.1 review, pass 4)
- Scope: the 20-second band performance (`art/blender/performance_motion.py`, `art/blender/prepare_performance.py`)
- History: [ADR 0002](0002-performer-motion-history.md) records the passes, the user's choices and the options set aside.

## Context

The performance video shows four band members (singer, keyboard, keytar, drums) playing the bright
cue from a high front camera (7 m up, about 28° down) in a 540×960 portrait frame. The first pass
drove each joint from the cue's note and drum timings (`performance-events.json`), but every move
eased in and out with smoothstep. Before revising it we searched for published practice on animating
band members and musicians in games.

Published material is thin. The findings that apply, with their limits:

1. **Key poses land on the beat.** Hi-Fi Rush was animated at 120 BPM and 60 fps with key poses on
   the beat, then sped up or slowed down to match the music's tempo (Tango Gameworks, CEDEC 2023, via Game Anim). Guitar
   Hero III's note trackers triggered animation commands (developer diary), but no source describes
   how Rock Band or Guitar Hero shaped the motion itself.
2. **Audio–video sync tolerance (ITU-R BT.1359).** Sign convention: positive means the sound comes
   before the picture.
   - Average threshold at which viewers *detect* a sync error: +45 ms to −125 ms.
   - Threshold at which a sync error becomes *unacceptable*: +90 ms to −185 ms.
   - These were measured on TV material, not musical impacts.
   - One frame at 24 fps is 41.7 ms.
3. **Accelerate into an impact, then rebound.** This is the standard principle of anticipation and
   follow-through.
4. **Stick height follows loudness.** Drum teaching gives about 6–9 in. for a full snare stroke and
   about 1–3 in. or less for ghost notes; sources differ on exact heights, so treat them as examples.
   Ghost notes use the fingers and a small wrist motion; accents use the wrist with more rebound.
5. **The wrist moves on chords.** Automated piano animation (UC Davis, 2012) translates and rotates
   the wrist on chords, by an amount that depends on loudness.
6. **Visuals affect how performances are judged.** All three studies are about classical or solo
   players, not pop bands:
   - Tsay (2013): viewers picked classical-competition winners from silent 6-second clips above
     chance.
   - Platz and Kopiez (2012): visual information in general, including appearance, has d = 0.51.
   - Moura et al. (2024): with saxophonists, more motion helped the upbeat excerpt.

   None of these sets an amount of movement.
7. **MIDI-driven animation is feasible.** DRUMS (2025) animates a whole drummer from MIDI. It uses
   learned motion and IK, so it supports the approach, not these particular curves.

## Decision

Rules from the research:

- Motion stays grounded and musician-like, driven by `performance-events.json`. There is no
  choreographed dance.
- **Project choice, not a research result:** every contact (stick on pad, key press) is keyed on the
  frame at or before its event time (`contact()`), never after. This is conservative. Flooring to 24
  fps can put the picture up to 41.7 ms ahead of the sound, which is inside the detection threshold.
  The audio adds about 3 ms of random timing (σ) to hi-hats and ghost notes after the event times are
  written, which is far below what anyone can see.
- Strokes and key presses accelerate into contact (`"in"` easing). Strokes rebound straight off the
  pad (`"out"`). Key presses hold for the note, then the hand rises between notes. Smoothstep is kept
  for moves with no impact (sway, glides between hand positions, gesture holds).
- Lift scales with loudness: crashes and snare accents lift highest, ghost notes barely lift and are
  mostly wrist.
- Wrist pivots on both drum sticks and on the keyboard's chord hand. The chord-hand wrist dips after
  each chord.

Pass 3: the band besides the drummer barely showed. Measured during the drop:
- drummer's stick: 46 px
- keyboard hands: about 6 px
- keytar hands: 3–6 px
- head nods: about 2 px vertically

Most of that motion pointed toward the camera.

- **The upper BORG tier and the backing-vocal mic are left out of the performance.** From the high
  front camera the upper tier hid the main keys and both hands (the user chose this over keeping it or
  moving it behind the player). The backing mic stood in front of the keytar neck, and the keytar
  player does not sing.
- **Keyboard:**
  - The hook hand slides across the keyboard with pitch (0.72 rad of shoulder swing, up from 0.22).
  - It waits above the keys in bar 2, comes down on the first hook note, and lifts off after the final
    stab.
  - The free hand goes up at the re-entry (bar 8) only (call P). Its chords are dropped for that bar.
- **Keytar:**
  - The keytar tilts about the keys hand: the neck goes up 0.4 rad on the drop and the re-entry, and
    stays up from the final stab.
  - The neck hand follows the neck using arm poses found by searching the scene. The Blender check
    holds it within 3 cm of its grip.
  - The keys hand slides further, and the player rocks more.
- **Heads:** nods were mostly toward the camera, so side tilts are larger (×2). Each performer nods
  with a different delay (2–4 frames) so the band does not move in unison.
- **Singer's hair (pass 4):** a new-wave cut, chosen by the user from three styles. The sides stay
  short (the original cap). The top is eight tufts, each a chain of three segments, swept up and over
  to camera left with one long peak at the front. The first try was shoulder-length locks, which the
  user rejected as not new wave.
  - Each segment springs toward its parent's angle and is pulled back to its rest pose, so the tips
    lag the roots.
  - The singer tosses their head on the drop, the re-entry and the final stab, alternating sides, and
    the hair gets a push at each toss.
  - In the breakdown the hair settles.
- **Keytar hands in front (pass 4):** the keys hand was behind the keytar body and the neck hand
  rested on the body.
  - The keytar sits 12 cm higher and 7 cm closer to the player, so the arms can reach the front of
    the instrument.
  - Both arms have base poses found by searching the scene. The neck-hand table was found again for
    the new keytar position.
  - Bass presses push about 1.5 cm into the front-facing keys.
  - The slide along the keys is corrected at the shoulder, so the hand's arc doesn't push it into
    the keys.
  - The Blender check holds the keys hand at least 3 cm in front of the key face, and the neck hand in
    front of the neck, on every other frame.
- `LOUD = 1.33` multiplies rock and nod amounts. It is artistic tuning, not a research figure.

## Not done

- **Kick and hi-hat pedal feet.** The drummer's legs are hidden behind the riser and the singer from
  the front camera. Revisit if a drummer fan camera is rendered.
- **Fingers.** The figures have block hands.
- **Exact keyboard contact geometry.** The Blender check covers the keytar hands and the hand
  travel, but not the distance from each hand to the keys or from each stick tip to its pad at every
  contact. The keyboard's half-bar chord presses are playing gestures, not notes in the events file.
- **`LOUD` covers every section.** It scales section levels that are already zero in the breakdown,
  so in practice it changes the intro and the loud sections.

## Consequences

- The hits look harder, and keyboard, keytar and singer moves now show from the front camera.
- Peak per-frame joint speed goes up. The unit tests bound it at 0.7 rad per frame for the drummer's
  arms and 0.35 rad for everything else.
- `sample()` takes an easing per key. Arm poses that need to reach a point are stored as constants
  found in the scene (`KEYBOARD_RAISE`, `KEYTAR_NECK`), so a change to the figures means searching for
  them again.

## Sources

- Hi-Fi Rush music-synced animation: https://www.gameanim.com/2023/09/08/hi-fi-rush-music-synced-animation/
- Guitar Hero III developer diary: https://www.gamespot.com/articles/guitar-hero-iii-developer-diary-1/1100-6177993/
- ITU-R BT.1359-1: https://www.itu.int/dms_pubrec/itu-r/rec/bt/R-REC-BT.1359-1-199811-I!!PDF-E.pdf
- ITU-R BT.1359 windows: https://www.forasoft.com/learn/audio-for-video/articles-audio/lip-sync-itu-r-bt-1359-tolerance-windows
- AV sync tolerances: https://www.tvtechnology.com/opinions/av-synchronization-how-bad-is-bad
- Anticipation and follow-through: https://www.premiumbeat.com/blog/animation-easing-anticipation-follow-through/
- Stick height and ghost notes: https://drumbeatsonline.com/blog/ghost-notes-on-drums-what-they-are-and-how-to-play-them
- Ghost-note mechanics: https://beat-note.app/blog/technique/dynamics/2024/08/27/ghost-notes-basics-grooves/
- Automated piano animation: https://www.cs.ucdavis.edu/~neff/papers/cav1477.pdf
- Tsay 2013: https://pmc.ncbi.nlm.nih.gov/articles/PMC3767512/
- Visual information in performance: https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2017.00513/pdf
- Moura et al. 2024: https://link.springer.com/article/10.1007/s00426-024-01928-x
- DRUMS, MIDI-driven drummer: https://www.theodoroskyriakou.com/projects/drums/Paper.pdf
- Review: GPT-6.1 (gpt-6.1-sol, via Codex, read-only), 2026-10-13
