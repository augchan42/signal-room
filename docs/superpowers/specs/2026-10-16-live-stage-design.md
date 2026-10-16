# Live stage design

Decision taken: the broadcast stage moves from pre-rendered videos to live 3D drawn by Filament
(Vulkan backend). Revision 2: changes from an Opus 5.5 review are marked [review].

## What exists now
- 36 performance videos: 12 mixes (3 arrangements x 4 members) x 3 concepts, 540x960, 24 fps,
  20 s, about 50 MB with 3 loop videos. `BroadcastVideoController` syncs them to the audio; a
  second decoder gives the member-cam inset.
- A concept changes only: 4 suit colours, the rear-screen image and its saturation, key/fill/rim
  light colours and levels, searchlight colour. A mix changes only motion keys.
- Spike (throwaway branch `spike/realtime-3d`): one mix+concept GLB, 3.2 MB, of which 0.57 MB is
  motion (rotation/translation, no skins). Pixel 7 Pro, Vulkan, whole app: 30 fps cap 1.12
  cores, 60 cap 1.27; PSS 460-487 MB (video app 0.64 cores, 249 MB).

## Design
1. Branch `live-stage` from main. Godot dropped; spike code rewritten, not merged.
2. Assets [review]: one shared stage GLB, loaded once, plus 12 motion files (per-node transforms
   per frame, set through Filament's TransformManager), about 9 MB in all. First step: export the
   12 mixes and confirm they differ only in keyframes. If they don't, fall back to one GLB per
   mix (about 38 MB).
3. Concepts at runtime: the Blender export writes the concept table to JSON from `CONCEPTS`
   (no hand copy) [review]. The export skips `apply_concept`, so light tracks are at neutral
   levels (key 300, fill 48, rim x1) and the app multiplies them by the concept factor. Colours
   are set on every light, including the spot lights that stand in for area lights. The 3 screen
   images ship once as sRGB textures with mipmaps; the export is checked to still carry the
   screen's emissive texture [review].
4. Material tracks mapped by the property they animate (the spike set every one as emission
   strength) [review]. Light and material values interpolated between the 24 fps frames [review].
5. Costumes become Blender edits plus re-export.
6. Threading [review]: one render thread owns every Filament call. Swap chain create/destroy is
   posted to it; surface detach blocks until the swap chain is gone. Mix, concept and member
   changes reach it through a command queue. The clock is one immutable sample object, not three
   separate fields.
7. Frame cap 30 fps, using `Surface.setFrameRate(30)` as well as skipping vsyncs [review].
8. Stage state [review]: a `LiveStageController` replaces `BroadcastVideoController` with its own
   loading / ready / fallback states; the asset catalog drops performance and loop videos.
   Pause freezes the last frame instead of swapping to the still and destroying the surface.
9. Loading [review]: load the stage and draw one hidden frame during the count-in, so pipelines
   are built before the show (the 150 ms first-show hitch).
10. Memory [review]: free the engine when the broadcast screen closes and on `onTrimMemory`. Try
    MSAA off, a smaller shadow map and no bloom, and keep whatever looks the same.
11. Member cam: a second Filament View on the same scene with the member's fancam camera, drawn
    into a TextureView at inset size without shadows, bloom or MSAA [review]. Framing checked by
    eye against today's crops. Kept only if its cost is small; otherwise a crop of the main view.
12. Vulkan support [review]: Vulkan on an allowlist (Pixel and recent devices to start), OpenGL
    ES elsewhere and if Vulkan fails to start; OpenGL ES measured at 30 fps too. Concept still if
    the stage can't load.
13. Filament pinned at 1.75.1 (1.76+ needs compileSdk 37, unsupported by AGP 8.13.2); automatic
    bumps blocked [review]. arm64 filter and Godot dependency removed.
14. Remove the 36 performance videos and 3 loops from the APK; keep stills.
15. Haze and light beams missing for now.
16. Tests: unit tests for concept values, clock and track mapping. On the Pixel: CPU, memory,
    frame times vs the spike; one fixed frame per concept compared with the Blender frames;
    background/foreground, pause/resume, quick restarts, mix changes, forced Vulkan failure
    [review].

Approved by the user 2026-10-16 ("agree on the design please proceed").

## Found while planning (2026-10-16)
- All 12 mixes exported from `signal-room-performance.blend` (about 7 s each) have the same 430
  uniquely named nodes, meshes, materials and geometry bytes. They differ only in (a) 74-75
  animated channels, each node animating either translation or rotation, linear, one key per
  frame for 480 frames, and (b) the rest transform of 42 nodes, which differs by arrangement.
  So the shared stage plus per-mix motion holds; nothing falls back to 12 GLBs.
- The fancams are orthographic cameras in the same file: `CAM | Frontman fancam`,
  `CAM | Producer fancam` (keyboardist, stage left), `CAM | Keytar fancam`, `CAM | Drummer
  fancam`, each animated. The broadcast camera `CAM | Broadcast group` is orthographic, ymag 4.65.
- Material tracks: emission strength of `Electric cyan` and `Signal coral` (Principled BSDF),
  the rear screen (Emission node strength) and the haze volume density (dropped with the haze).
  The glTF exporter folds the emission colour's brightness into the strength, so the export
  writes each track already multiplied by that brightness, with the normalised colour.
- Filament 1.75.1 glTF materials take `baseColorFactor`, `emissiveFactor`, `emissiveStrength`
  and `emissiveMap`.
