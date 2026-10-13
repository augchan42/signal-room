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

## Codex continuation: performer contacts and playtest (2026-10-13)

Main remains `d984f1d`. Draft PR #6 contains retry/reset timing diagnostics and two real-storage
device tests. The user's report is a few seconds of delay on both buttons, with eventual recovery.
The awake phone tests did not reproduce the delay; it is not marked fixed. A separate host Blender
startup crash does not affect the app's bundled media. Four gameplay-understanding answers remain
pending; see `docs/producer-demo-playtest.md`.

Contact source is on `codex/performer-contacts`, draft PR #7, based on PR #6's branch. Commits:
`a183e9d` drum fitting, `f9dea3d` playing fingers/pedals, `5e0afc6` held-key and hi-hat timing fixes.
PR #6 was merged into this working branch as `6830300`, not into main. Merge #6 first when approved,
then retarget #7 to main. No main merge is authorized by this continuation's draft state.

The final source checks cover all twelve mix motions:

- 1,504 assigned drum strikes. Target-fit error is below 1 mm, with a deliberate 1 mm pad-face
  clearance (physical surface clearance can therefore reach 2 mm).
- 1,541 key onsets and 12,866 held-key frames on the actual mesh faces, with gaps below 1 mm.
- 5,760 keytar neck-grip frames and the raised BORG gesture in every mix.
- 17,280 heel/sole frames and both open/closed hi-hat event states.
- 37 ordinary Blender tests, 20 audio tests and 12 validator tests pass.

The source `.blend` and approved audio are unchanged. The exporter adds corrections, articulated
playing fingers, and heel-down kick/hi-hat/sustain pedals after applying each mix's original motion.
The keytar is moved 12 cm toward its keys hand to make the full range reachable. Contiguous notes
release visually for the last two frames before the next onset; audio tails are not shortened.
See `docs/adr/0004-performer-contacts.md` for the mapping and verification commands.

Replacement media export is in progress, strictly one Blender child at a time. Each MP4 is staged,
checked for its video format/duration/frame count, fully decoded, then atomically promoted to the
existing app asset path. Do not build/install or refresh manifest hashes until all 36 replacements
are complete and full validation passes. The installed phone app still has the prior media.

The new combined review clip is intentionally untracked:
`/Users/auchan/projects/signal-room/art/blender/demo/performer-contact-review.mp4`.
The local project map is updated but the published Claude artifact remains v29.
