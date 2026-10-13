# First-booking visual export

The source scene is `../mvp/signal-room-mvp.blend`. `signal-room-demo.blend` is a separate 4-second, 24 fps, 1080 × 1920 version. It has 96 displayed frames and a matching closure pose at frame 97. The source scene is unchanged.

Regenerate with Blender 5.2 and FFmpeg on the path:

```sh
'/Applications/Blender.app/Contents/MacOS/Blender' --background art/blender/mvp/signal-room-mvp.blend --python-exit-code 1 --python art/blender/render_demo.py -- prepare
'/Applications/Blender.app/Contents/MacOS/Blender' --background art/blender/demo/signal-room-demo.blend --python-exit-code 1 --python art/blender/verify_demo.py
'/Applications/Blender.app/Contents/MacOS/Blender' --background art/blender/demo/signal-room-demo.blend --python-exit-code 1 --python art/blender/render_demo.py -- stills
'/Applications/Blender.app/Contents/MacOS/Blender' --background art/blender/demo/signal-room-demo.blend --python-exit-code 1 --python art/blender/render_demo.py -- loops
```

The Android output is under `app/src/main/assets/demo/visual/`: three concept stills, four isolated member stills, one control-room still, and three silent concept loops. The stills are WebP. The Android loops are H.264, limited-range yuv420p MP4. Each loop render also writes a 360 × 640 VP9/WebM preview alongside this README for `review.html`, because the in-app browser displays black frames when it plays these H.264 files. The page falls back to the Android MP4 if WebM is unsupported. Intermediate JPEG frames are ignored by Git.

Verification on October 11, 2026: `verify_demo.py` passed the closure, beat-pulse, keyboard-orientation, tier-1 and hidden-audience checks. FFprobe reported 1080 × 1920 for all eight stills and 1080 × 1920, 24 fps, 96 frames, 4.000 seconds, H.264/yuv420p, video-only for all three loops. FFmpeg decoded all three without errors. The decoded first frame was visually checked against the frame-seven trough; the first beat retained its brighter rim/floor light. Frames 96 and 1 were visually checked for a clean repeat. Member stills were inspected for performer/instrument clearance at portrait size.

Open `review.html` through a local HTTP server from the repository root to inspect the media together. The page also links to the twelve audio variants.

## 20-second performance test

`signal-room-performance.blend` is a separate 480-frame scene derived from the four-second demo. It has two moving searchlights and five lighting sections aligned to the approved 120 BPM bright cue (`art/audio/probe/final/bright-cue-candidate.mp3`). The original demo scene and Android assets are unchanged.

The performers' movement follows the cue's actual timings, which `bright_cue.py --events` writes to `performance-events.json`. `performance_motion.py` turns those timings into joint angles for each frame. The timing rules come from [ADR 0001](../../../docs/adr/0001-performer-motion.md): each contact lands on the frame at or before its audio, strokes accelerate into contact and rebound out of it, and lift follows loudness.

- **Drummer:** the left stick hits the snares and ghost notes, and the right stick plays the hi-hats. The two sticks alternate on rolls and tom fills. Both sticks hit the crashes at bars 3, 8 and 10. Ghost notes are mostly a wrist snap with little arm lift; snare accents and crashes use the whole arm. The snare shoulder and the upper body react to accents.
- **Keyboard player:** the right hand waits above the keys in bar 2, presses on each hook note, slides across the keyboard with the pitch, rises between notes, and lifts off after the final stab. The left hand holds the chords, its wrist gives on each chord, and it goes up in the air at the re-entry (bar 8). The upper BORG tier is hidden in this scene, because from the camera it covered the keys and hands.
- **Keytar player:** the keys hand plays the bass line, and the neck hand moves on chord changes. The keytar sits high on the chest with both hands playing from the front. It tilts about the keys hand so the neck goes up on the drop and the re-entry and stays up from the final stab, with the neck hand following. The backing-vocal mic is hidden.
- **Singer:** the free arm points out on the drop, opens at bar 5, goes up in a fist at the re-entry, and holds the fist from the final stab. The singer tosses their head on the drop, the re-entry and the final stab. Their new-wave hair has short sides and a top swept up and over to one side (eight tufts of three segments). It swings after each toss and with the head, with the tips lagging the roots, and settles in the breakdown.
- **Everyone:**
  - A hip pivot carries the upper body, and a neck pivot carries the head. Upper bodies rock side to side and heads tilt and nod on the beat, scaled by section. Each performer's head moves with a different delay, so the band doesn't move in unison.
  - Everyone is nearly still in the breakdown (bar 7) and holds a pose from the final stab.
  - Feet stay planted. The four-second demo's whole-body sway, which slid the feet, is removed.
  - The keytar moves with its player.

Regenerate the events with `art/audio/.venv/bin/python art/audio/bright_cue.py --events art/blender/demo/performance-events.json`. `art/blender/tests/test_performance_motion.py` tests the motion without Blender.

Regenerate the scene and preview with:

```sh
'/Applications/Blender.app/Contents/MacOS/Blender' --background art/blender/demo/signal-room-demo.blend --python-exit-code 1 --python art/blender/prepare_performance.py
'/Applications/Blender.app/Contents/MacOS/Blender' --background art/blender/demo/signal-room-performance.blend --python-exit-code 1 --python art/blender/tests/verify_performance_scene.py
'/Applications/Blender.app/Contents/MacOS/Blender' --background art/blender/demo/signal-room-performance.blend --python-exit-code 1 --python art/blender/render_performance.py -- render
```

The render writes `performance-monochrome.webm` at 540 × 960, 24 fps with Opus audio. `review.html` plays it above the older four-second silent loops. This is a review render of the cue, not an Android asset. Intermediate JPEG frames are ignored by Git.

## App performance videos

The app plays one 20-second performance per mix and concept: 36 silent MP4s in `app/src/main/assets/demo/visual/performance/<concept>-<arrangement>-<member>.mp4`. The app starts each one with the mix's audio and plays it once; it falls back to the concept's four-second loop, then to the concept still.

Each video uses the mix's own motion. `render_mixes.py --events` writes the mix's timings to `mix-events/<arrangement>-<member>.json`, and `render_mix_performances.py` moves every motion key in the performance scene by the difference between that mix's motion and the cue's. It then sets the concept's suits, screen, light colours and light levels, renders 480 frames with EEVEE at 16 samples and encodes H.264 (CRF 23, limited range, no audio). In each mix:

- The motion follows that mix's drum pattern, bass and hook (sparse, driving or layered).
- The featured member keeps playing in the bar-7 breakdown while the others go still: the keyboardist's right hand plays the arpeggio, the keytar's keys hand plays the answers and the bar-7 run, the drummer leans into the break, and the singer points and tosses their head.

Render one at a time; each takes about two minutes on this Mac:

```sh
art/audio/.venv/bin/python art/audio/render_mixes.py --all --events art/blender/demo/mix-events
'/Applications/Blender.app/Contents/MacOS/Blender' --background art/blender/demo/signal-room-performance.blend --python-exit-code 1 --python art/blender/render_mix_performances.py -- driving-keytar warm
python3 tools/validate_demo_assets.py --root app/src/main/assets/demo --report app/build/reports/demo-assets.json --write-hashes
```

Frames go to the ignored `frames/mixes/<concept>-<mix>/`.

### Performer surface contacts

After applying a mix's authored motion, the exporter fits stick tips to their assigned pads,
adds articulated playing fingers and fits the BORG/keytar fingertips to their keys. The keytar
neck hand remains on its touch strip. Heel-down kick, hi-hat and sustain pedals follow the mix
events. The source `.blend` is unchanged; these additions run at export time. See
`docs/adr/0004-performer-contacts.md` for the physical mapping and limits.

Run these geometry checks sequentially before exporting new videos:

```sh
/Applications/Blender.app/Contents/MacOS/Blender --background /Users/auchan/projects/signal-room/art/blender/demo/signal-room-performance.blend --threads 1 --python-exit-code 1 --python /Users/auchan/projects/signal-room/art/blender/tests/verify_drum_contacts.py
/Applications/Blender.app/Contents/MacOS/Blender --background /Users/auchan/projects/signal-room/art/blender/demo/signal-room-performance.blend --threads 1 --python-exit-code 1 --python /Users/auchan/projects/signal-room/art/blender/tests/verify_key_contacts.py
/Applications/Blender.app/Contents/MacOS/Blender --background /Users/auchan/projects/signal-room/art/blender/demo/signal-room-performance.blend --threads 1 --python-exit-code 1 --python /Users/auchan/projects/signal-room/art/blender/tests/verify_pedal_contacts.py
```

Do not refresh manifest hashes until all 36 replacement videos have been rendered and validated.
