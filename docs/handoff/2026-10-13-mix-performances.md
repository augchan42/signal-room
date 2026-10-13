# Handoff: after the mix performance videos (2026-10-13)

For the next Claude session working in `/Users/auchan/projects/signal-room`.

## Where things stand

- `main` has the twelve app mixes (PR #4) and the 36 performance videos (PR #5), both merged with
  merge commits.
- The app plays one 20-second video per mix and concept,
  `app/src/main/assets/demo/visual/performance/<concept>-<arrangement>-<member>.mp4`, once,
  alongside the mix audio. It falls back to the concept's 4-second loop, then the concept still.
- The user checked `review-warm-sparse-keyboardist.mp4` (video with mix audio) and approved it:
  "these look good". Earlier they approved the mixes by ear.
- Tests at merge: the validator passes all 59 assets and its 12 tests pass. 54 JVM unit tests pass.
  On the phone, `MediaPlaybackTest` and `OfflineDemoTest` pass (4 tests). 31 Blender motion tests
  pass.

## How the pieces fit

- Audio: `art/audio/render_mixes.py --all`, then `finish_audio.py --all` (see `art/audio/README.md`).
- Mix timings: `render_mixes.py --all --events art/blender/demo/mix-events` writes 12 JSON files.
- Videos: `art/blender/render_mix_performances.py -- <arrangement>-<member> <concept>`, one Blender
  process per video, about 2 minutes each (see `art/blender/demo/README.md`).
- Validation: `python3 tools/validate_demo_assets.py --root app/src/main/assets/demo --report
  app/build/reports/demo-assets.json --write-hashes` after replacing media.
- Build: `./gradlew -q assembleDebug testDebugUnitTest`.
- Decisions: `docs/adr/0001` (motion rules), `0002` (motion history), `0003` (why 36 videos, why
  each concept is rendered rather than graded, why 16 samples).

## Next steps

1. Uncoached playtest (`docs/producer-demo-playtest.md`).
2. Performer work not done: a per-contact geometry check (hand on key or drum at each hit), pedal
   feet, and fingers.

## Rules the user has set

- Render one video at a time, never several in parallel. Check CPU load before heavy work.
- Merges to `main`: always a PR and a merge commit (`gh pr merge N --merge`), never squash.
- List the full absolute path of every delivered file. The user may be on another device, so also
  send files with SendUserFile.
- Plain, literal wording; no mannered prose.
- Project map: `.project-map/` (untracked, never committed), published to the private artifact
  https://claude.ai/artifact/77NdtGywNMjis7Nk6RYVDD. Update it with the project-map agent after
  each milestone and republish to the same link. Answer "where are we?" from it. Check its facts.

## Environment

- Python: `art/audio/.venv/bin/python`. Blender 5.2: `/Applications/Blender.app/Contents/MacOS/Blender`.
- A Blender process open for days and a Gradle daemon may be the user's own; leave them alone.
- Untracked and not committed on purpose: review clips in `art/blender/demo/` and probe MP3s in
  `art/audio/probe/`. Render frames in `art/blender/demo/frames/` are ignored.
