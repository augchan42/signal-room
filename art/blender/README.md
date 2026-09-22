# Signal Room MVP assets

Open `mvp/signal-room-mvp.blend` for the editable scene. The source collection is `SIGNAL ROOM - concept 01`; the original startup scene is preserved and hidden. Rebuilding replaces only the generated collection.

## Review

From this directory, run `python3 -m http.server 8769 --bind 127.0.0.1`, then open `http://127.0.0.1:8769/mvp/review.html`. A local HTTP server is needed for the screen-projection JSON. The review page includes a phone control-room mock-up, tier comparisons, the silent fancam loop, special stage, nominee/first-win graphics, and a functioning screen compositor.

All stills and the loop are 1080 × 1920. The clip is H.264, 24 fps, five seconds, and intentionally silent. The movement language is restrained: held pose, breathing/weight shift, head turns, slow lateral camera drift and beat-driven light. This is a stylized MVP cast, not final character art or full choreography.

## Scene controls

Run the embedded `Signal Room controls.py` text in Blender, or execute `mvp_controls.py`. Then call:

```python
set_state(tier=3, camera_name='group', tone='cool', pattern='waveform')
set_state(tier=1, camera_name='group', tone='neutral', special=True)
set_state(tier=3, camera_name='control')
set_state(tier=3, camera_name='group', encore=True)
set_audience_mix([.2, .15, .2, .15, .3])
set_segment_readouts([.4,.5,.7,.6,.9], [.3,.4,.6,.5,.9])
```

Camera names: `group`, `frontman`, `producer`, `keytar`, `drummer`, `control`. Tone presets: `cool`, `warm`, `neutral`; these change material colours on the same garments. Screen feeds: `signal-bars`, `test-pattern`, `waveform`, `blank`. Special stage substitutes performer-derived silhouette echoes. Audience inputs are weights, normalised by the controller; all-zero or negative input is rejected. Crowd geometry uses linked meshes and object-linked colour materials. Tier 1 has no audience, tier 2 has 9 light sticks, tier 3 has 48.

The render controller, not a property watcher, applies scene state. Editing a custom property alone does not rebuild the scene. `set_state()` is the authoritative application API. The three hook names are provisional caption metadata, not audio tracks. The HTML page is an asset review prototype, not a Compose implementation.

## Screen compositing contract

Nine `plate-{tier}-{camera}.png` files cover three tiers × group/frontman/producer. A clean plate has a blank rear screen. Three independently exported 1024 × 512 patterns replace that screen in-app.

`screen-mask-{camera}.png` is a grayscale coverage mask: white screen, black occluders. `screen-projections.json` contains normalized screen-corner coordinates in top-left image space, ordered bottom-left, bottom-right, top-right, top-left. The current cameras are orthographic, so an affine transform maps pattern pixels into the screen plane. Moving/perspective cameras need the per-frame projections and a homography, respectively. Apply the mask to the transformed feed, composite it over the plate, then add CRT treatment and captions. Masks must match the camera, dimensions, frame and visible geometry.

The local review demonstrates this contract on stills. The beauty loop includes a waveform feed. A matching lossless H.264 mask clip (`screen-mask-fancam-5s.mp4`) and `loop-projections.json` are included, with 120 projected quads. `render_mvp.py -- loop-masks` regenerates the mask frames; the finishing script encodes them. Native Android playback/compositing is separate integration work. Three transparent `lower-third-*.png` overlays provide the provisional hook titles.

## Rebuild and render

In the live Blender MCP connection, execute `build_mvp.py`. It builds geometry, rigs, patterns and animation, embeds the controller, and saves `mvp/signal-room-mvp.blend`. It uses no downloaded assets, paid services or real-time Android 3D.

```sh
/Applications/Blender.app/Contents/MacOS/Blender --background mvp/signal-room-mvp.blend --python-exit-code 1 --python render_mvp.py -- stills
/Applications/Blender.app/Contents/MacOS/Blender --background mvp/signal-room-mvp.blend --python-exit-code 1 --python render_mvp.py -- plates
/Applications/Blender.app/Contents/MacOS/Blender --background mvp/signal-room-mvp.blend --python-exit-code 1 --python render_mvp.py -- masks
/Applications/Blender.app/Contents/MacOS/Blender --background mvp/signal-room-mvp.blend --python-exit-code 1 --python render_mvp.py -- loop
python3 finish_broadcast.py
/Applications/Blender.app/Contents/MacOS/Blender --background mvp/signal-room-mvp.blend --python-exit-code 1 --python verify_mvp.py
```

The scene uses Blender 5.2 Eevee. The source paths currently target this checkout; change `ROOT` when relocating. `finish_broadcast.py` uses ffmpeg and macOS DIN/Courier fonts. Frame intermediates, drafts, and Blender backup files are ignored by Git. Milestone scenes, scripts, masks, patterns, renders and final MP4 are versioned.

## Deliberate remaining product work

Native Compose screens, scoring, audio/music, rival selection, chart logic and runtime video compositing are not implemented by these art assets. No copyrighted music or choreography is included. World-tour tier 4, detailed facial performance, cloth simulation and full choreography remain deferred. See `docs/signal-room-art-direction.md` for the agreed direction and the cover-stage follow-up mechanic.
