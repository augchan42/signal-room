# Real-time 3D spike (throwaway)

Question: can the phone draw the band live in 3D instead of playing pre-rendered videos?

1. `export_gltf.py` applies one mix's motion and one concept's look in Blender (as
   `art/blender/render_mix_performances.py` does) and exports the stage to glTF, plus a JSON file
   of animated light and material values that glTF does not carry here.
2. `godot/` plays it in Godot 4.7 with the mobile renderer: area lights rebuilt as soft spot
   lights, the volume haze hidden, the clip driven by seeking from a clock, frame times logged.

Run: export with Blender, copy `export/*` to `godot/export/`, then
`Godot --path godot --always-on-top --write-movie out/f.png --fixed-fps 24 --quit-after 480`
for a desktop render (without `--always-on-top` macOS stops redrawing the hidden window), or
`Godot --headless --path godot --export-debug Android build/spike3d.apk` for the phone.

Results on a Pixel 7 Pro (2026-10-15), warm / driving-frontman:
- glTF of the whole stage and one 20 s performance: 3.4 MB (one video: about 2.3 MB).
- APK 31 MB, of which about 25 MB is the engine.
- 69 fps average, p50 14.3 ms, p99 19 ms over 132 s; skin 37.0 -> 39.5 C; about 0.75 CPU core;
  676 MB resident memory.
- Motion matches the Blender render frame for frame. Look is close; missing: volume haze and
  light beams. The camera fit crops the left edge on the phone's taller screen.

## Inside the Signal Room app (option 2)

The app (this branch only) takes the engine from Maven (`org.godotengine:godot:4.7.2.stable`)
and the exported project files from `build/godot-assets/`, which is the `assets/` folder of
`build/spike3d.apk` without `_cl_` (it asks for fullscreen) and `dexopt/` (clashes with the app's
baseline profile). `MainActivity` starts a `GodotFragment` once, with no container; the broadcast
screen attaches its render view where the video was (`app/.../spike/GodotStage.kt`). The script
reads the audio position each frame from a plugin, `SignalRoomClock.positionMs()`, carried
forward from the app's 50 ms audio samples. With no view attached Godot draws nothing (0% CPU).

Pixel 7 Pro, release build, Driving / Frontman / Warm, same show on both builds:

| | video (main, fc8269b) | Godot, no cap (~80 fps) | Godot, 60 fps cap | Godot, 30 fps cap |
|---|---|---|---|---|
| frame time | n/a | p50 12.5 ms, p99 14-18 ms | 16.7 ms every frame | 33.3 ms every frame |
| whole app CPU (cores) | 0.64 | 1.22-1.25 | 1.18-1.27 | 1.07-1.12 |
| Godot render thread | - | 0.58 | 0.48-0.54 | 0.33 |
| memory (PSS) | 249 MB | 741-764 MB | | |
| APK | 84 MB | 160 MB (engine stored uncompressed: 71 MB) | | |
| cold launch | 256 ms | 353-472 ms | | |

- One 138-150 ms hitch each show, in the first second, when the render view is attached.
  No other hitches (two mid-show ones in one run came from `dumpsys meminfo` stalling the app).
- The Godot build still loads the full performance video into the main player (no view), so its
  CPU includes about 0.12 core that a real Godot version would not spend.
- Memory is the big cost: about +500 MB, mostly Vulkan allocations (graphics 433-480 MB vs 168 MB).
  Not tried: lower MSAA, smaller shadow atlas, no glow.
- Compose overlays (timer, member cam) draw over the Godot view without problems.
- Cap at runtime: `adb shell setprop debug.signalroom.maxfps 30`, then relaunch the app.
