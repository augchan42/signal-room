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
