# Live stage

The broadcast screen draws the band live with Filament instead of playing a pre-rendered video.
The broadcast view and the featured member's cam come from the same scene. The motion for each of
the 12 mixes (3 arrangements × 4 members) is baked from Blender and played back against the
show's audio position.

Design: `docs/superpowers/specs/2026-10-16-live-stage-design.md`.
Plan: `docs/superpowers/plans/2026-10-16-live-stage.md`.

## Files

All in `app/src/main/assets/demo/live/` (9.8 MB), listed in `demo/manifest.json` (schema 4):

| File | What it is |
|---|---|
| `stage.glb` | The stage, band, instruments and lights (glTF binary) |
| `stage.json` | Light names, area lights, cameras for each member cam, concept looks |
| `mix/<arrangement>-<member>.motion` | Per-node motion for one mix, 24 fps (`SRM1` format, see `art/blender/live_motion.py`) |
| `mix/<arrangement>-<member>.tracks.json` | Light levels and screen strength for one mix |
| `screen-<concept>.png` | The rear screen image for each concept |

These replace the 36 performance videos and 3 loop videos (52.1 MB).

## Changing a costume, the stage or a mix

1. Edit `art/blender/demo/signal-room-performance.blend`. The `art/blender/demo/` folder is not
   in git; keep your own copy.
2. Run `art/blender/export_live_stage.sh`. It runs Blender once for the stage and once per mix,
   one at a time, and writes into `app/src/main/assets/demo/live/`. Set `BLENDER=` if Blender
   isn't in `/Applications`.
3. Run the validator to check the files and update the manifest hashes:
   `python3 tools/validate_demo_assets.py --root app/src/main/assets/demo --report /tmp/report.json --write-hashes`
   It writes hashes only after every check passes.
4. Build. `FilamentStageTest` (device test) checks that every show loads and draws, that the
   concepts differ, and that each member cam shows its member.

## Backend

Vulkan is used only on Google phones with Android 12 or later and Vulkan 1.1. Everything else
uses OpenGL ES, because weak Vulkan drivers tend to draw garbage instead of failing to start
(`core/stage/StageBackend.kt`). To force one on the next launch:

```
adb shell setprop debug.signalroom.backend opengl   # or vulkan
adb shell setprop debug.signalroom.backend ""       # back to the list
```

Only one Filament engine exists in the process at a time. Filament's Vulkan backend keeps
process-wide state, and destroying one engine while another runs crashes the other's driver
thread. That happened when an activity restart built the new screen's stage before the old one
was freed. A new engine now waits up to 5 s for the old one (`EngineTurn` in `StageScene.kt`).

If Filament throws on the render thread (loading, attaching a view, drawing), `FilamentStage`
frees the stage and the screen shows the concept's still; the next return to the app tries
again. A native crash inside Filament can't be caught this way.

## Measurements

Pixel 7 Pro, Android 16, release build signed with the debug key, Driving / Frontman / Warm, on
2026-10-16. The frame line is logged every 10 s by `FilamentStage` (tag `LiveStage`). CPU is
`top -H` summed over the app's threads, three 3-second samples during the show. PSS is
`dumpsys meminfo` TOTAL PSS.

| | Vulkan (default) | OpenGL (forced) |
|---|---|---|
| Frame interval p50 / p99 | 34.5 / 37.2 ms (about 29 fps) | 34.5 / 38.2 ms |
| Render-thread work p50 / max | 6.4 / 22 ms | 6.1 / 15 ms |
| Whole-app CPU | 1.05–1.08 cores | 1.25–1.27 cores |
| Main thread | 0.07–0.09 cores | 0.08 cores |
| PSS during the show | 471–479 MB | 513 MB |
| PSS on the end screen, 15 s later | 190–197 MB | 242 MB |
| Stage prepare (first show after launch) | 414–592 ms | 613 ms |

- On Vulkan the busiest threads are Filament's driver thread (0.27 cores; it is named
  `JNISurfaceTextu` in `top` because Android renames it when it posts to the cam's
  SurfaceTexture), the job threads (0.24) and `stage-render` (0.18).
- Against the targets: 30 fps with p99 under 40 ms is met. CPU is a little lower than the
  spike's 1.12 cores. The main thread is well under the spike's 0.19–0.22. End-screen PSS is
  below the video app's 249 MB.
- When the app is in the background, `TRIM_MEMORY_BACKGROUND` frees the stage: 305 MB → 128 MB.
  The stage loads again on return (361 ms). Before the trim arrives, the stage's threads use 0%
  CPU: the frame loop runs only while the broadcast view is attached.
- Release APK: 60.2 MB.

### Cheaper picture settings tried

Each was one rebuild, one show and a screenshot at the same moment. None was kept.

| Change | Saving | Picture | Kept |
|---|---|---|---|
| MSAA off | 11 MB PSS, render work 6.4 → 4.3 ms, no change in total CPU | Stair-stepped edges on drumsticks and keytar keys | No |
| Bloom off | 4 MB, no change in CPU or work | — | No |
| Shadow map 1024 → 512 | No change | — | No |

## Checked by hand on the phone

- Pause mid-show: the frame holds (two screenshots 3 s apart are identical). Resume continues
  from the audio position.
- Home mid-show, trim memory while away, return: the stage loads again and the show waits,
  paused, for Resume.
- Two shows back to back with different choices (Sparse / Frontman / Monochrome, then
  Driving / Frontman / Warm): the stage matched each one.
- All three concepts at 10 s against frames from the old videos: same stage, band, poses and
  screen. All four member cams frame their member.
- OpenGL forced: draws the same show (numbers above).

## Still missing

- Haze and light beams. The old videos had a volumetric haze with visible spotlight cones;
  the live stage has neither, so the Warm and Electric looks are flatter at the top of the frame.
