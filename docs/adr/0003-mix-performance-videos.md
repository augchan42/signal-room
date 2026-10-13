# ADR 0003: One performance video per mix and concept

- Status: accepted (the user chose 36 videos on 2026-10-13: "36, every mix × concept")
- Date: 2026-10-13
- Scope: `art/blender/render_mix_performances.py`, `app/src/main/assets/demo/visual/performance/`,
  `AssetCatalog`, `BroadcastClock`, `BroadcastVideoController`, `tools/validate_demo_assets.py`
- Related: [ADR 0001](0001-performer-motion.md) holds the motion rules this builds on.

## Problem

The app looped the four-second concept videos (`visual/loop-*.mp4`) under every mix. The hands did
not follow the music. The 20-second performance was only a review render, and its motion followed
the original cue, not the twelve app mixes, which differ in drum pattern, bass, hook and featured
member.

## Decision

- Render one 20-second video for each of the 12 mixes in each of the 3 concepts: 36 videos.
- Each video uses that mix's own motion: `render_mixes.py --events` writes the mix's timings, and
  `render_mix_performances.py` moves the scene's motion keys by the difference between the mix's
  motion and the cue's.
- The featured member keeps playing in the bar-7 breakdown while the others go still.
- The app plays the video once, started with the audio. The video position is the audio position,
  held at 20 s. If the video fails, the app shows the concept still, as before.
- 540 × 960, 24 fps, H.264 CRF 23, silent. EEVEE at 16 samples.

## Options set aside

- **12 videos in one look, or 3 (one per arrangement).** The user chose all 36.
- **64 samples.** At 540 × 960 the only visible difference was a little haze grain, at 2–4× the time.
- **Render once and grade the other concepts in the compositor.** EEVEE 5.2 gives no light-group
  passes. A fitted colour matrix had a relative error of 0.64 (monochrome) and 0.31 (warm), and did
  not change suit or screen colours. Between concepts the rim lights change about 3× while the key
  changes about 15%, so no single grade matches.
- **Rendering two at a time.** The user asked for one at a time.

## Cost

About two minutes per video on this Mac, about 70 minutes for all 36. Each video is 1.1–1.5 MB,
about 48 MB in the APK for all 36.
