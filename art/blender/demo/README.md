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

`signal-room-performance.blend` is a separate 480-frame scene derived from the four-second demo. It has four instrument gestures, two moving searchlights, and five lighting sections aligned to the 120 BPM Driving / Frontman audio mix. The original demo scene and Android assets are unchanged.

Regenerate the scene and preview with:

```sh
'/Applications/Blender.app/Contents/MacOS/Blender' --background art/blender/demo/signal-room-demo.blend --python-exit-code 1 --python art/blender/prepare_performance.py
'/Applications/Blender.app/Contents/MacOS/Blender' --background art/blender/demo/signal-room-performance.blend --python-exit-code 1 --python art/blender/tests/verify_performance_scene.py
'/Applications/Blender.app/Contents/MacOS/Blender' --background art/blender/demo/signal-room-performance.blend --python-exit-code 1 --python art/blender/render_performance.py -- render
```

The render writes `performance-monochrome.webm` at 540 × 960, 24 fps with Opus audio. `review.html` plays it above the older four-second silent loops. This is a review render, not an Android asset or a final performance cut. Intermediate JPEG frames are ignored by Git.
