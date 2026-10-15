# Live Stage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 36 pre-rendered performance videos with the band drawn live by Filament (Vulkan where it is known to work), synced to the show's audio, with the member cam drawn live from the same scene.

**Architecture:** Blender exports one shared stage file (no animation), a stage description (concept looks, area lights, camera sizes), three rear-screen images and, per mix, a small binary motion file plus a JSON file of light and emission levels per frame. In the app, one render thread ("stage-render") owns every Filament call; it loads the stage once, applies a mix's motion and a concept's look, and draws the broadcast view into a SurfaceView and the member's fancam into a TextureView. `LiveStageController` replaces `BroadcastVideoController`: it loads the stage during the count-in, reports LOADING / READY / FALLBACK, and feeds the audio position to a `StageClock` that the render thread reads every frame.

**Tech Stack:** Blender 5.2.2 (Python, glTF exporter), Python 3 (`art/audio/.venv`), Kotlin 2.1, Jetpack Compose, Filament + gltfio 1.75.1 (Android), kotlinx.serialization, JUnit 4, AndroidX instrumented tests on the Pixel 7 Pro.

**Spec:** `docs/superpowers/specs/2026-10-16-live-stage-design.md`

## Global Constraints

- Branch `live-stage` from main; `spike/realtime-3d` stays throwaway and is only read for reference (`git show spike/realtime-3d:<path>`). No Godot.
- Filament and gltfio stay at exactly `1.75.1` (1.76+ needs compileSdk 37, which AGP 8.13.2 does not support). Do not bump them.
- Frame cap 30 fps.
- Every Filament call happens on the "stage-render" thread. Surface detach blocks until that thread has destroyed the swap chain.
- Vulkan only on the allowlist (Google devices, Android 12 / SDK 31+, Vulkan 1.1 hardware); OpenGL ES elsewhere and whenever the Vulkan engine fails to start.
- If the live stage can't be drawn, show the concept still, as the app does today when a video fails.
- Run one Gradle command at a time, with `--no-watch-fs`. Check `uptime` first; wait if the 1-minute load is above 30.
- Run Blender one process at a time.
- Before driving the Pixel 7 Pro (`ANDROID_SERIAL=2B011FDH300KNR`), tell the user and make sure they are not using it; say when done.
- Commit messages end with the two attribution lines given in the session (Co-Authored-By and Claude-Session).
- Plain wording in comments and docs. Match the surrounding code's comment density and naming.

## Review Focus

1. **Pause, background and resume mid-show:** the last frame stays on screen while paused; after returning from another app the stage comes back (reloading if memory was trimmed) at the audio position, not frame 0. Pinned by Task 6's `pausedBroadcastShowsTheFrozenLiveStage` and Task 7's checklist.
2. **Switching shows quickly (retry, new demo, different mix or concept):** a slow earlier load never overwrites a later one, and the stage shows the newest mix and concept. Pinned by Task 5's `laterLoadWins` and Task 4's `everyShowLoadsAndDrawsTheBand`.
3. **A phone where Vulkan misbehaves or is missing:** the OpenGL ES path draws the same show. Pinned by Task 4's `openGlDrawsTheSameShow`.
4. **A missing or damaged live file:** the show plays with the concept still and the "unavailable" label, never a crash or black box. Pinned by Task 5's `missingFileFallsBackWithoutTheEngine` and `engineFailureFallsBackToTheStill`, and Task 2's validator tests.
5. **Leaving the broadcast screen:** the engine's memory is freed (PSS back near the video app's level on the end screen). Pinned by Task 7's memory measurement.

---

## File Structure

Created:
- `art/blender/live_motion.py`: reads glTF binaries; writes and reads the "SRM1" motion file.
- `art/blender/tests/test_live_motion.py`: tests for it.
- `art/blender/export_live_stage.py`: Blender job: `-- stage` or `-- mix <mix>`.
- `art/blender/export_live_stage.sh`: runs the stage job and all 12 mix jobs, one at a time.
- `app/src/main/assets/demo/live/`: `stage.glb`, `stage.json`, `screen-{monochrome,warm,electric}.png`, `mix/<arrangement>-<member>.motion`, `mix/<arrangement>-<member>.tracks.json` (generated, committed).
- `app/src/main/java/dev/digitalrain/synthwave/core/stage/MotionClip.kt`: motion file parser and per-node pose.
- `app/src/main/java/dev/digitalrain/synthwave/core/stage/StageData.kt`: `stage.json` and `tracks.json` models, `sampleTrack`, camera names.
- `app/src/main/java/dev/digitalrain/synthwave/core/stage/StageClock.kt`
- `app/src/main/java/dev/digitalrain/synthwave/core/stage/StageBackend.kt`
- `app/src/main/java/dev/digitalrain/synthwave/core/stage/StageEngine.kt`: `StageRequest`, `StageEngine` interface.
- `app/src/main/java/dev/digitalrain/synthwave/core/stage/StageScene.kt`: Filament objects for the stage (render thread only).
- `app/src/main/java/dev/digitalrain/synthwave/core/stage/FilamentStage.kt`: render thread, surfaces, frame loop.
- `app/src/main/java/dev/digitalrain/synthwave/core/stage/LiveStageController.kt`
- `app/src/main/java/dev/digitalrain/synthwave/feature/booking/LiveStageViews.kt`: SurfaceView and TextureView composables.
- Tests: `app/src/test/java/dev/digitalrain/synthwave/core/stage/{MotionClipTest,StageDataTest,StageClockTest,StageBackendTest,LiveStageControllerTest}.kt`, `app/src/androidTest/java/dev/digitalrain/synthwave/core/stage/FilamentStageTest.kt`.

Modified: `art/blender/render_mix_performances.py` (one constant), `tools/validate_demo_assets.py` and its test, `app/src/main/assets/demo/manifest.json`, `core/model/MediaModels.kt`, `core/data/AssetCatalog.kt`, `SignalRoomApp.kt`, `feature/booking/BookingScreen.kt`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, tests that mention videos.

Deleted (Task 6): `core/audio/BroadcastVideoController.kt`, `feature/booking/MemberCam.kt`, `test/.../feature/booking/MemberCamTest.kt`, `assets/demo/visual/performance/*.mp4`, `assets/demo/visual/loop-*.mp4`.

---

### Task 1: Motion file converter

**Files:**
- Create: `art/blender/live_motion.py`
- Test: `art/blender/tests/test_live_motion.py`

**Interfaces:**
- Produces: `read_glb(path) -> (dict, bytes)`, `motion_nodes(stage, mix, fps, frames) -> list[(name, rest10, channel, values)]`, `write_motion(path, fps, frames, nodes)`, `read_motion(path) -> (fps, frames, nodes)`. Channel 0 none, 1 translation (3 floats per frame), 2 rotation (4 floats per frame, x y z w). Rest is translation xyz, rotation xyzw, scale xyz.

- [ ] **Step 1: Write the failing tests**

```python
"""Contract for the live stage's per-mix motion file."""

import json
import struct
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import live_motion  # noqa: E402


def glb(nodes, animations=()):
    """A minimal glTF binary: animations are (node index, path, times, flat values)."""
    data = bytearray()
    accessors, views, anims = [], [], []

    def add(values, kind):
        views.append({"buffer": 0, "byteOffset": len(data), "byteLength": 4 * len(values)})
        data.extend(struct.pack(f"<{len(values)}f", *values))
        width = {"SCALAR": 1, "VEC3": 3, "VEC4": 4}[kind]
        accessors.append({"bufferView": len(views) - 1, "componentType": 5126,
                          "count": len(values) // width, "type": kind})
        return len(accessors) - 1

    for node, path, times, values in animations:
        sampler = {"input": add(times, "SCALAR"),
                   "output": add(values, "VEC3" if path == "translation" else "VEC4"),
                   "interpolation": "LINEAR"}
        anims.append({"channels": [{"sampler": 0, "target": {"node": node, "path": path}}], "samplers": [sampler]})
    gltf = {"asset": {"version": "2.0"}, "nodes": nodes, "accessors": accessors, "bufferViews": views,
            "buffers": [{"byteLength": len(data)}], "animations": anims}
    text = json.dumps(gltf).encode()
    text += b" " * (-len(text) % 4)
    data += b"\0" * (-len(data) % 4)
    body = struct.pack("<II", len(text), 0x4E4F534A) + text + struct.pack("<II", len(data), 0x004E4942) + bytes(data)
    return struct.pack("<4sII", b"glTF", 2, 12 + len(body)) + body


class LiveMotionTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.root = Path(self.directory.name)

    def tearDown(self):
        self.directory.cleanup()

    def write(self, name, content):
        path = self.root / name
        path.write_bytes(content)
        return live_motion.read_glb(path)

    def stage(self):
        return self.write("stage.glb", glb([{"name": "arm"}, {"name": "boot", "translation": [0, 0, 1]}, {"name": "drum"}]))

    def test_moving_and_moved_nodes_are_written_and_the_rest_left_out(self):
        mix = self.write("mix.glb", glb(
            [{"name": "arm", "rotation": [0, 0, 0, 1]}, {"name": "boot", "translation": [0, 0, 2]}, {"name": "drum"}],
            [(0, "translation", [0, 1 / 24, 2 / 24], [0, 0, 0, 1, 0, 0, 2, 0, 0])]))
        nodes = live_motion.motion_nodes(self.stage(), mix, 24, 3)
        self.assertEqual(["arm", "boot"], [name for name, _, _, _ in nodes])
        self.assertEqual([1, 0], [channel for _, _, channel, _ in nodes])
        self.assertEqual([0, 0, 0, 1, 0, 0, 2, 0, 0], nodes[0][3])
        self.assertEqual([0, 0, 2, 0, 0, 0, 1, 1, 1, 1], nodes[1][1])

    def test_motion_file_round_trips(self):
        nodes = [("arm", [1, 2, 3, 0, 0, 0, 1, 1, 1, 1], 2, [0, 0, 0, 1] * 3), ("boot", [0] * 6 + [1] * 4, 0, [])]
        path = self.root / "mix.motion"
        live_motion.write_motion(path, 24, 3, nodes)
        fps, frames, read = live_motion.read_motion(path)
        self.assertEqual((24, 3), (fps, frames))
        self.assertEqual([(n, [float(v) for v in r], c, [float(v) for v in vs]) for n, r, c, vs in nodes], read)
        self.assertEqual(b"SRM1", path.read_bytes()[:4])

    def test_keys_must_fall_on_every_frame(self):
        mix = self.write("mix.glb", glb([{"name": "arm"}, {"name": "boot", "translation": [0, 0, 1]}, {"name": "drum"}],
                                        [(0, "translation", [0, 0.05, 0.1], [0] * 9)]))
        with self.assertRaisesRegex(ValueError, "every frame"):
            live_motion.motion_nodes(self.stage(), mix, 24, 3)

    def test_mix_must_have_the_stage_nodes(self):
        mix = self.write("mix.glb", glb([{"name": "arm"}, {"name": "cymbal"}, {"name": "drum"}]))
        with self.assertRaisesRegex(ValueError, "different nodes"):
            live_motion.motion_nodes(self.stage(), mix, 24, 3)

    def test_matrix_nodes_are_refused(self):
        with self.assertRaisesRegex(ValueError, "matrix"):
            live_motion.node_trs({"name": "arm", "matrix": [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]})


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `art/audio/.venv/bin/python art/blender/tests/test_live_motion.py`
Expected: `ModuleNotFoundError: No module named 'live_motion'`

- [ ] **Step 3: Write the converter**

```python
"""Turn one mix's exported glTF into the app's motion file (app/.../core/stage/MotionClip.kt).

The app loads one shared stage file without animation. Each mix ships only what differs from it:
the per-frame animated channel of every moving node, and the rest transform of every node that sits
differently in this mix (the arrangements place boots, pedals and hands differently).

Format "SRM1", little-endian: fps, frames, node count (int32); per node: UTF-8 name length (int32),
name, rest translation xyz, rotation xyzw, scale xyz (10 float32), channel (int32: 0 none,
1 translation, 2 rotation) and, for a channel, frames x 3 or 4 float32.
"""

import json
import struct
from pathlib import Path

MAGIC = b"SRM1"
CHANNELS = {"translation": (1, 3), "rotation": (2, 4)}
WIDTHS = {0: 0, 1: 3, 2: 4}
COMPONENTS = {"SCALAR": 1, "VEC3": 3, "VEC4": 4}
FLOAT = 5126


def read_glb(path):
    data = Path(path).read_bytes()
    magic, version, _ = struct.unpack_from("<4sII", data, 0)
    if magic != b"glTF" or version != 2:
        raise ValueError(f"{path}: not a glTF 2 binary")
    json_length, json_type = struct.unpack_from("<II", data, 12)
    if json_type != 0x4E4F534A:
        raise ValueError(f"{path}: first chunk is not JSON")
    gltf = json.loads(data[20:20 + json_length])
    offset = 20 + json_length
    binary = b""
    if offset < len(data):
        bin_length, bin_type = struct.unpack_from("<II", data, offset)
        if bin_type != 0x004E4942:
            raise ValueError(f"{path}: second chunk is not BIN")
        binary = data[offset + 8:offset + 8 + bin_length]
    return gltf, binary


def node_trs(node):
    if "matrix" in node:
        raise ValueError(f"node {node.get('name')} has a matrix; export translation, rotation and scale")
    return [*node.get("translation", [0, 0, 0]), *node.get("rotation", [0, 0, 0, 1]), *node.get("scale", [1, 1, 1])]


def accessor_floats(gltf, binary, index):
    accessor = gltf["accessors"][index]
    if accessor["componentType"] != FLOAT:
        raise ValueError(f"accessor {index} is not float")
    view = gltf["bufferViews"][accessor["bufferView"]]
    width = COMPONENTS[accessor["type"]]
    if view.get("byteStride", width * 4) != width * 4:
        raise ValueError(f"accessor {index} is interleaved")
    start = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
    return list(struct.unpack_from(f"<{accessor['count'] * width}f", binary, start))


def motion_nodes(stage, mix, fps, frames):
    """(name, rest, channel, values) for each node this mix moves or places differently."""
    stage_gltf, _ = stage
    mix_gltf, mix_binary = mix
    stage_rest = {node["name"]: node_trs(node) for node in stage_gltf["nodes"]}
    names = [node["name"] for node in mix_gltf["nodes"]]
    if len(set(names)) != len(names):
        raise ValueError("node names must be unique")
    if set(names) != set(stage_rest):
        raise ValueError("the mix and the stage have different nodes")
    animated = {}
    for animation in mix_gltf.get("animations", []):
        for channel in animation["channels"]:
            sampler = animation["samplers"][channel["sampler"]]
            path = channel["target"]["path"]
            name = mix_gltf["nodes"][channel["target"]["node"]]["name"]
            if path not in CHANNELS:
                raise ValueError(f"{name}: no live mapping for animated {path}")
            if sampler.get("interpolation", "LINEAR") != "LINEAR":
                raise ValueError(f"{name}: keys must be linear")
            if name in animated:
                raise ValueError(f"{name} has more than one animated channel")
            times = accessor_floats(mix_gltf, mix_binary, sampler["input"])
            if len(times) != frames or any(abs(time - k / fps) > 1e-4 for k, time in enumerate(times)):
                raise ValueError(f"{name}: keys must fall on every frame from 0 at {fps} fps")
            animated[name] = (CHANNELS[path][0], accessor_floats(mix_gltf, mix_binary, sampler["output"]))
    nodes = []
    for node in mix_gltf["nodes"]:
        name, rest = node["name"], node_trs(node)
        if name in animated:
            nodes.append((name, rest, *animated[name]))
        elif any(abs(a - b) > 1e-6 for a, b in zip(rest, stage_rest[name])):
            nodes.append((name, rest, 0, []))
    return nodes


def write_motion(path, fps, frames, nodes):
    out = bytearray(MAGIC + struct.pack("<iii", fps, frames, len(nodes)))
    for name, rest, channel, values in nodes:
        encoded = name.encode("utf-8")
        if len(values) != frames * WIDTHS[channel]:
            raise ValueError(f"{name}: expected {frames * WIDTHS[channel]} values, got {len(values)}")
        out += struct.pack("<i", len(encoded)) + encoded + struct.pack("<10f", *rest) + struct.pack("<i", channel)
        out += struct.pack(f"<{len(values)}f", *values)
    Path(path).write_bytes(bytes(out))


def read_motion(path):
    data = Path(path).read_bytes()
    if data[:4] != MAGIC:
        raise ValueError(f"{path}: not a motion file")
    fps, frames, count = struct.unpack_from("<iii", data, 4)
    offset, nodes = 16, []
    for _ in range(count):
        (length,) = struct.unpack_from("<i", data, offset)
        name = data[offset + 4:offset + 4 + length].decode("utf-8")
        offset += 4 + length
        rest = list(struct.unpack_from("<10f", data, offset))
        (channel,) = struct.unpack_from("<i", data, offset + 40)
        offset += 44
        width = WIDTHS[channel] * frames
        nodes.append((name, rest, channel, list(struct.unpack_from(f"<{width}f", data, offset))))
        offset += 4 * width
    if offset != len(data):
        raise ValueError(f"{path}: trailing bytes")
    return fps, frames, nodes
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `art/audio/.venv/bin/python art/blender/tests/test_live_motion.py`
Expected: `Ran 5 tests ... OK`

- [ ] **Step 5: Commit**

```bash
git add art/blender/live_motion.py art/blender/tests/test_live_motion.py
git commit -m "live stage: convert a mix's glTF motion into a small per-mix file"
```

---

### Task 2: Export the live stage and list it in the asset manifest

**Files:**
- Modify: `art/blender/render_mix_performances.py:120-150` (`apply_concept` base levels)
- Create: `art/blender/export_live_stage.py`, `art/blender/export_live_stage.sh`
- Create (generated): `app/src/main/assets/demo/live/**`
- Modify: `tools/validate_demo_assets.py`, `tools/tests/test_validate_demo_assets.py`
- Modify: `app/src/main/assets/demo/manifest.json`, `app/src/main/java/dev/digitalrain/synthwave/core/model/MediaModels.kt`, `app/src/main/java/dev/digitalrain/synthwave/core/data/AssetCatalog.kt:49` (schema check)
- Test: `art/blender/tests/test_live_motion.py` (checked-in files), `app/src/test/java/dev/digitalrain/synthwave/core/data/AssetCatalogTest.kt`

**Interfaces:**
- Consumes: Task 1's `live_motion` functions.
- Produces: the files under `demo/live/`; manifest `schemaVersion` 3 with a `live` object; Kotlin `AssetManifest.live: LiveStageAssets`, `LiveStageAssets(stagePath, stageSha256, infoPath, infoSha256, screens: List<LiveScreenAsset>, mixes: List<LiveMixAsset>)`, `LiveScreenAsset(concept: ConceptId, path, sha256)`, `LiveMixAsset(arrangement: ArrangementId, member: MemberId, motionPath, motionSha256, tracksPath, tracksSha256)`.
- `stage.json` shape: `{"fps":24,"frames":480,"areaLights":[{"name","color":[r,g,b],"watts","position":[x,y,z],"direction":[x,y,z]}],"cameras":{"CAM | …":{"ymag"}},"concepts":{"MONOCHROME":{"suits":[[r,g,b]×4],"lights":{"<light name>":{"color":[r,g,b],"factor"}}}}}` (glTF axes, linear colours).
- `tracks.json` shape: `{"fps":24,"frames":480,"lights":[{"light","watts":[480]}],"emissive":[{"material","color":[r,g,b],"strength":[480]}]}`.

- [ ] **Step 1: Name the base light levels in `render_mix_performances.py`**

Add after `MIXES = …`:

```python
# Key and fill levels in the prepared scene; a concept's level is a factor of these.
BASE_ENERGY = {"Warm portrait key": 300, "Cool soft fill": 48}
```

and in `apply_concept` replace

```python
    for name, (color, energy), base in (("Warm portrait key", look["key"], 300), ("Cool soft fill", look["fill"], 48)):
        light = bpy.data.objects[name].data
        light.color = color
        _scale(light, "energy", energy / base)
```

with

```python
    for name, (color, energy) in (("Warm portrait key", look["key"]), ("Cool soft fill", look["fill"])):
        light = bpy.data.objects[name].data
        light.color = color
        _scale(light, "energy", energy / BASE_ENERGY[name])
```

- [ ] **Step 2: Write `art/blender/export_live_stage.py`**

```python
"""Export the live stage that the app draws with Filament (app/.../core/stage/).

One Blender process per job; export_live_stage.sh runs them all:
  -- stage   the shared stage without animation (stage.glb), the concept looks, area lights and
             camera sizes (stage.json) and the three rear-screen images (screen-<concept>.png)
  -- mix <arrangement>-<member>
             that mix's motion against stage.glb (mix/<mix>.motion, see live_motion.py) and its
             light and emission levels per frame (mix/<mix>.tracks.json)

Concepts are not applied here. Lights keep the prepared scene's levels and the app multiplies the
light tracks by each concept's factor, as apply_concept scales the keyframes.
"""

import json
import math
import sys
import tempfile
from pathlib import Path

import bpy
import numpy as np

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))
import live_motion  # noqa: E402
import render_mix_performances as rmp  # noqa: E402

OUT = ROOT.parents[1] / "app" / "src" / "main" / "assets" / "demo" / "live"
FPS, FRAMES = 24, 480
HAZE = "T3 bounded haze"
STAGE_MIX = "driving-frontman"  # every mix adds the same pedals, fingers and wrists
# Animated emission strength, and the input holding the colour it multiplies.
EMISSION_INPUTS = {("ShaderNodeBsdfPrincipled", "Emission Strength"): "Emission Color",
                   ("ShaderNodeEmission", "Strength"): "Color"}


def prepare(mix):
    rmp.apply_motion(mix)
    bpy.data.objects[HAZE].hide_set(True)  # Filament has no volumes; the haze box would draw solid
    for obj in [o for o in bpy.context.scene.objects if o.type == "FONT" and o.visible_get()]:
        bpy.ops.object.select_all(action="DESELECT")
        obj.select_set(True)
        bpy.context.view_layer.objects.active = obj
        bpy.ops.object.convert(target="MESH")  # glTF does not carry text objects
    bpy.context.scene.frame_set(1)


def export(path, animations):
    bpy.ops.export_scene.gltf(
        filepath=str(path), export_format="GLB", use_visible=True, export_apply=True,
        export_cameras=True, export_lights=True, export_animations=animations,
        export_animation_mode="SCENE", export_force_sampling=True, export_frame_range=True,
        export_pointer_animation=False,  # crashes on animated materials in Blender 5.2.2
        export_image_format="AUTO")


def concept_looks():
    looks = {}
    for name, look in rmp.CONCEPTS.items():
        lights = {light: {"color": list(color), "factor": energy / rmp.BASE_ENERGY[light]}
                  for light, (color, energy) in (("Warm portrait key", look["key"]), ("Cool soft fill", look["fill"]))}
        for light in ("Cyan rear rim", "Coral rear rim"):
            lights[light] = {"color": list(look["cyan" if light.startswith("Cyan") else "coral"]), "factor": look["rim"]}
        for side in ("left", "right"):
            lights[f"PERFORMANCE | {side} searchlight"] = {"color": list(look["beam"]), "factor": 1.0}
        looks[name.upper()] = {"suits": [list(rgb) for rgb in look["suits"]], "lights": lights}
    return looks


def save_screens():
    for name, look in rmp.CONCEPTS.items():
        image = bpy.data.images["SCREEN " + look["pattern"]]
        width, height = image.size
        pixels = np.empty(width * height * 4, dtype=np.float32)
        image.pixels.foreach_get(pixels)
        if look["saturation"] == 0:
            rgba = pixels.reshape(-1, 4)
            rgba[:, :3] = rgba[:, :3].max(axis=1, keepdims=True)  # Hue/Saturation at 0 keeps the HSV value
        out = bpy.data.images.new(f"live screen {name}", width, height, alpha=True)
        out.pixels.foreach_set(pixels)
        out.filepath_raw = str(OUT / f"screen-{name}.png")
        out.file_format = "PNG"
        out.save()


def area_lights():
    lights = []
    for obj in bpy.context.scene.objects:
        if obj.type == "LIGHT" and obj.visible_get() and obj.data.type == "AREA":
            m = obj.matrix_world
            # Blender is Z-up, glTF Y-up: (x, y, z) -> (x, z, -y). The light shines down its own -Z.
            direction = [-m[0][2], -m[2][2], m[1][2]]
            length = math.sqrt(sum(v * v for v in direction))
            lights.append({"name": obj.name, "color": list(obj.data.color), "watts": obj.data.energy,
                           "position": [m[0][3], m[2][3], -m[1][3]], "direction": [v / length for v in direction]})
    return lights


def camera_sizes(stage_path):
    gltf, _ = live_motion.read_glb(stage_path)
    return {node["name"]: {"ymag": gltf["cameras"][node["camera"]]["orthographic"]["ymag"]}
            for node in gltf["nodes"] if "camera" in node and node["name"].startswith("CAM | ")}


def tracks():
    frames = range(1, FRAMES + 1)
    lights = []
    for obj in bpy.context.scene.objects:
        if obj.type == "LIGHT" and obj.visible_get():
            for curve in rmp._fcurves(obj.data):
                if curve.data_path != "energy":
                    raise ValueError(f"{obj.name}: no live mapping for light track {curve.data_path}")
                lights.append({"light": obj.name, "watts": [round(curve.evaluate(f), 4) for f in frames]})
    emissive = []
    for material in bpy.data.materials:
        if material.node_tree is None:
            continue
        for curve in rmp._fcurves(material.node_tree):
            node = material.node_tree.nodes[curve.data_path.split('"')[1]]
            if node.bl_idname == "ShaderNodeVolumePrincipled":
                continue  # the haze, which the live stage leaves out
            socket = material.node_tree.path_resolve(curve.data_path.rsplit(".", 1)[0])
            color_input = EMISSION_INPUTS.get((node.bl_idname, socket.name))
            if color_input is None:
                raise ValueError(f"{material.name}: no live mapping for material track {curve.data_path}")
            source = node.inputs[color_input]
            color = [1.0, 1.0, 1.0] if source.is_linked else list(source.default_value[:3])
            # glTF keeps the colour normalised and moves its brightness into the strength.
            brightness = max(color) or 1.0
            emissive.append({"material": material.name, "color": [c / brightness for c in color],
                             "strength": [round(curve.evaluate(f) * brightness, 4) for f in frames]})
    return {"fps": FPS, "frames": FRAMES, "lights": lights, "emissive": emissive}


def export_stage():
    OUT.mkdir(parents=True, exist_ok=True)
    prepare(STAGE_MIX)
    stage = OUT / "stage.glb"
    export(stage, animations=False)
    save_screens()
    info = {"fps": FPS, "frames": FRAMES, "areaLights": area_lights(), "cameras": camera_sizes(stage),
            "concepts": concept_looks()}
    (OUT / "stage.json").write_text(json.dumps(info, indent=1) + "\n")
    print("LIVE_STAGE_EXPORTED", stage, stage.stat().st_size)


def export_mix(mix):
    if mix not in rmp.MIXES:
        raise SystemExit(f"Unknown mix {mix}")
    prepare(mix)
    (OUT / "mix").mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as directory:
        exported = Path(directory) / f"{mix}.glb"
        export(exported, animations=True)
        nodes = live_motion.motion_nodes(live_motion.read_glb(OUT / "stage.glb"), live_motion.read_glb(exported),
                                         FPS, FRAMES)
    live_motion.write_motion(OUT / "mix" / f"{mix}.motion", FPS, FRAMES, nodes)
    (OUT / "mix" / f"{mix}.tracks.json").write_text(json.dumps(tracks(), separators=(",", ":")) + "\n")
    print("LIVE_MIX_EXPORTED", mix, len(nodes))


if __name__ == "__main__":
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if args == ["stage"]:
        export_stage()
    elif len(args) == 2 and args[0] == "mix":
        export_mix(args[1])
    else:
        raise SystemExit("Expected -- stage | -- mix <arrangement>-<member>")
```

- [ ] **Step 3: Write `art/blender/export_live_stage.sh`**

```sh
#!/bin/sh
# Export the live stage and all 12 mixes for the app, one Blender process at a time.
set -eu
BLENDER=${BLENDER:-/Applications/Blender.app/Contents/MacOS/Blender}
cd "$(dirname "$0")/../.."
run() {
    "$BLENDER" --background art/blender/demo/signal-room-performance.blend --python-exit-code 1 \
        --python art/blender/export_live_stage.py -- "$@"
}
run stage
for arrangement in sparse driving layered; do
    for member in frontman keyboardist keytar drummer; do
        run mix "$arrangement-$member"
    done
done
```

Run: `chmod +x art/blender/export_live_stage.sh && uptime && art/blender/export_live_stage.sh 2>&1 | grep -E "LIVE_|Error|rror"`
Expected: one `LIVE_STAGE_EXPORTED … stage.glb <about 2.6 MB>` line and 12 `LIVE_MIX_EXPORTED <mix> <74-120>` lines, no errors. About 2 minutes in all.

- [ ] **Step 4: Check the export**

Add to `art/blender/tests/test_live_motion.py`, inside `LiveMotionTest`:

```python
    LIVE = Path(__file__).resolve().parents[3] / "app" / "src" / "main" / "assets" / "demo" / "live"

    @unittest.skipUnless(LIVE.exists(), "live stage not exported")
    def test_checked_in_mixes_fit_the_stage(self):
        stage, _ = live_motion.read_glb(self.LIVE / "stage.glb")
        names = {node["name"] for node in stage["nodes"]}
        self.assertNotIn("animations", stage)
        for arrangement in ("sparse", "driving", "layered"):
            for member in ("frontman", "keyboardist", "keytar", "drummer"):
                fps, frames, nodes = live_motion.read_motion(self.LIVE / "mix" / f"{arrangement}-{member}.motion")
                self.assertEqual((24, 480), (fps, frames))
                self.assertLessEqual({name for name, _, _, _ in nodes}, names)
                self.assertGreaterEqual(sum(1 for node in nodes if node[2]), 74)
                tracks = json.loads((self.LIVE / "mix" / f"{arrangement}-{member}.tracks.json").read_text())
                self.assertEqual(6, len(tracks["lights"]))
                self.assertEqual(3, len(tracks["emissive"]))
                self.assertTrue(all(len(t["watts"]) == 480 for t in tracks["lights"]))
        info = json.loads((self.LIVE / "stage.json").read_text())
        self.assertEqual({"MONOCHROME", "WARM", "ELECTRIC"}, set(info["concepts"]))
        self.assertEqual(4, len(info["areaLights"]))
        self.assertEqual({"CAM | Broadcast group", "CAM | Frontman fancam", "CAM | Producer fancam",
                          "CAM | Keytar fancam", "CAM | Drummer fancam"}, set(info["cameras"]) - {"CAM | Control room"})
        self.assertAlmostEqual(350 / 300, info["concepts"]["ELECTRIC"]["lights"]["Warm portrait key"]["factor"])
        self.assertAlmostEqual(0.4, info["concepts"]["MONOCHROME"]["lights"]["Cyan rear rim"]["factor"])
```

Run: `art/audio/.venv/bin/python art/blender/tests/test_live_motion.py`
Expected: `Ran 6 tests ... OK`. If the light or emissive counts differ, print the names from the tracks file and correct the expected counts only if the extra or missing track is explained by the scene (for example a light hidden in some arrangement); otherwise fix the export.

Then compare the warm screen image with the waveform image Blender renders (the screen's colours must not shift through the PNG round trip):

```bash
art/audio/.venv/bin/python -I - <<'EOF'
import json, struct, io
from PIL import Image
from pathlib import Path
live = Path("app/src/main/assets/demo/live")
data = (live / "stage.glb").read_bytes()
length = struct.unpack_from("<I", data, 12)[0]
gltf = json.loads(data[20:20 + length]); binary = data[20 + length + 8:]
for image in gltf.get("images", []):
    view = gltf["bufferViews"][image["bufferView"]]
    start = view.get("byteOffset", 0)
    embedded = Image.open(io.BytesIO(binary[start:start + view["byteLength"]])).convert("RGB")
    print("embedded", image.get("name"), embedded.size)
    for concept in ("monochrome", "warm", "electric"):
        screen = Image.open(live / f"screen-{concept}.png").convert("RGB")
        if screen.size == embedded.size:
            diff = sum(abs(a - b) for p, q in zip(screen.getdata(), embedded.getdata()) for a, b in zip(p, q))
            print(concept, "mean difference", round(diff / (3 * screen.size[0] * screen.size[1]), 2))
EOF
```

Expected: the embedded image is one of the three patterns, and the concept with that pattern differs from it by under 1 (mean, 0-255). A large difference means the PNG was written in the wrong colour space: save with `out.colorspace_settings.name = image.colorspace_settings.name` before `out.pixels.foreach_set` and export again.

- [ ] **Step 5: Teach the validator the live files (failing tests first)**

Add to `tools/tests/test_validate_demo_assets.py` (import `live_file_failures` next to the other names):

```python
    def test_live_files_are_checked_by_signature(self):
        glb = self.root / "stage.glb"
        glb.write_bytes(b"glTF" + (2).to_bytes(4, "little") + b"\0" * 12)
        self.assertEqual([], live_file_failures(glb, "glb"))
        glb.write_bytes(b"GIF89a")
        self.assertTrue(any("glTF" in f for f in live_file_failures(glb, "glb")))
        motion = self.root / "mix.motion"
        motion.write_bytes(b"SRM1" + b"\0" * 12)
        self.assertEqual([], live_file_failures(motion, "motion"))
        tracks = self.root / "mix.tracks.json"
        tracks.write_text('{"fps": 24, "frames": 480}')
        self.assertEqual([], live_file_failures(tracks, "json"))
        tracks.write_text('{"fps": 30, "frames": 480}')
        self.assertTrue(any("24 fps" in f for f in live_file_failures(tracks, "json")))
        png = self.root / "screen.png"
        png.write_bytes(b"\x89PNG\r\n\x1a\n" + b"\0" * 8)
        self.assertEqual([], live_file_failures(png, "png"))
        self.assertTrue(any("missing" in f for f in live_file_failures(self.root / "absent.png", "png")))

    def test_manifest_structure_requires_the_live_stage(self):
        failures = validate_manifest_structure({"schemaVersion": 3, "audio": [], "concepts": [], "performances": [],
                                                "members": [], "live": {"screens": [], "mixes": []}}, self.root)
        self.assertTrue(any("live stage path" in f for f in failures))
        self.assertTrue(any("12 live mixes" in f for f in failures))
        self.assertTrue(any("3 live screens" in f for f in failures))
```

Run: `python3 tools/tests/test_validate_demo_assets.py` (if it has no `__main__` guard: `cd tools && python3 -m unittest tests.test_validate_demo_assets`)
Expected: ImportError for `live_file_failures`.

- [ ] **Step 6: Implement the validator changes**

In `tools/validate_demo_assets.py`:

Add after `HEX_SHA256 = …`:

```python
LIVE_KINDS = {"glb": b"glTF", "motion": b"SRM1", "png": b"\x89PNG\r\n\x1a\n", "json": None}


def live_file_failures(path: Path, kind: str) -> list[str]:
    """The live stage's files are not media; check that each is present and of its kind."""
    if not path.is_file():
        return [f"missing file: {path}"]
    data = path.read_bytes()
    if kind == "json":
        try:
            content = json.loads(data)
        except json.JSONDecodeError as error:
            return [f"{path}: invalid JSON: {error}"]
        if not isinstance(content, dict) or content.get("fps") != 24 or content.get("frames") != 480:
            return [f"{path}: live data must be 24 fps, 480 frames"]
        return []
    signature = LIVE_KINDS[kind]
    if not data.startswith(signature):
        return [f"{path}: not a {'glTF' if kind == 'glb' else kind} file"]
    if kind == "glb" and int.from_bytes(data[4:8], "little") != 2:
        return [f"{path}: glTF version must be 2"]
    return []
```

In `_manifest_entries`, before the `controlRoomPath` line, add:

```python
    live = manifest.get("live")
    if isinstance(live, dict):
        entries.append((live.get("stagePath"), "glb", live.get("stageSha256", "")))
        entries.append((live.get("infoPath"), "json", live.get("infoSha256", "")))
        for item in live.get("screens", []):
            if isinstance(item, dict):
                entries.append((item.get("path"), "png", item.get("sha256", "")))
        for item in live.get("mixes", []):
            if isinstance(item, dict):
                entries.append((item.get("motionPath"), "motion", item.get("motionSha256", "")))
                entries.append((item.get("tracksPath"), "json", item.get("tracksSha256", "")))
```

In `validate_manifest_structure`: change the version check to `!= 3` / message `"manifest schemaVersion must be 3"`, and add before `if manifest.get("controlRoomPath") …`:

```python
    failures.extend(_live_structure_failures(manifest.get("live")))
```

with this function above `validate_manifest_structure`:

```python
def _live_structure_failures(live: object) -> list[str]:
    if not isinstance(live, dict):
        return ["manifest must describe the live stage"]
    failures = []
    if live.get("stagePath") != "live/stage.glb" or live.get("infoPath") != "live/stage.json":
        failures.append("live stage path must be live/stage.glb with live/stage.json")
    screens, mixes = live.get("screens"), live.get("mixes")
    if not isinstance(screens, list) or {(s.get("concept"), s.get("path")) for s in screens if isinstance(s, dict)} != \
            {(c, f"live/screen-{c.lower()}.png") for c in CONCEPTS} or len(screens) != 3:
        failures.append("manifest must list 3 live screens, one per concept")
    expected = {(a, m, f"live/mix/{a.lower()}-{m.lower()}.motion", f"live/mix/{a.lower()}-{m.lower()}.tracks.json")
                for a in ARRANGEMENTS for m in MEMBERS}
    if not isinstance(mixes, list) or len(mixes) != 12 or {
            (x.get("arrangement"), x.get("member"), x.get("motionPath"), x.get("tracksPath"))
            for x in mixes if isinstance(x, dict)} != expected:
        failures.append("manifest must list 12 live mixes, one per arrangement and member")
    return failures
```

In `validate_manifest`, replace the start of the per-entry loop body

```python
        path = root / relative
        failures = validate_file(path)
        if failures:
```

with

```python
        path = root / relative
        if kind in LIVE_KINDS:
            failures = live_file_failures(path, kind)
            if not failures:
                digest = _sha256(path)
                report["measurements"][relative] = {"bytes": path.stat().st_size, "sha256": digest}
                if check_hashes and claimed_hash != digest:
                    report["failures"].append(f"{relative}: SHA-256 mismatch")
            report["failures"].extend(failures)
            continue
        failures = validate_file(path)
        if failures:
```

In `_set_hashes`, add before the control room line:

```python
    live = manifest["live"]
    live["stageSha256"] = measurements[live["stagePath"]]["sha256"]
    live["infoSha256"] = measurements[live["infoPath"]]["sha256"]
    for item in live["screens"]:
        item["sha256"] = measurements[item["path"]]["sha256"]
    for item in live["mixes"]:
        item["motionSha256"] = measurements[item["motionPath"]]["sha256"]
        item["tracksSha256"] = measurements[item["tracksPath"]]["sha256"]
```

Run: the validator tests again. Expected: all pass (the existing structure test still finds its "36 performances" failure).

- [ ] **Step 7: List the live files in the manifest and the Kotlin model**

In `app/src/main/assets/demo/manifest.json`: set `"schemaVersion": 3` and add, after `"controlRoomSha256"`, a `"live"` object generated so the 12 mixes are in arrangement then member order:

```bash
art/audio/.venv/bin/python -I - <<'EOF'
import json
from pathlib import Path
path = Path("app/src/main/assets/demo/manifest.json")
manifest = json.loads(path.read_text())
manifest["schemaVersion"] = 3
manifest["live"] = {
    "stagePath": "live/stage.glb", "stageSha256": "", "infoPath": "live/stage.json", "infoSha256": "",
    "screens": [{"concept": c, "path": f"live/screen-{c.lower()}.png", "sha256": ""} for c in ("MONOCHROME", "WARM", "ELECTRIC")],
    "mixes": [{"arrangement": a, "member": m, "motionPath": f"live/mix/{a.lower()}-{m.lower()}.motion", "motionSha256": "",
               "tracksPath": f"live/mix/{a.lower()}-{m.lower()}.tracks.json", "tracksSha256": ""}
              for a in ("SPARSE", "DRIVING", "LAYERED") for m in ("FRONTMAN", "KEYBOARDIST", "KEYTAR", "DRUMMER")],
}
path.write_text(json.dumps(manifest, indent=2) + "\n")
EOF
PATH=/opt/homebrew/bin:$PATH python3 tools/validate_demo_assets.py --root app/src/main/assets/demo --report /private/tmp/claude-501/demo-assets.json --write-hashes
```

Expected: `Validated 76 demo assets` (12 audio + 3 stills + 3 loops + 36 performances + 4 members + control room + 17 live files).

In `MediaModels.kt` add:

```kotlin
/** One mix's motion and light levels for the live stage, from art/blender/export_live_stage.py. */
@Serializable
data class LiveMixAsset(
    val arrangement: ArrangementId,
    val member: MemberId,
    val motionPath: String,
    val motionSha256: String,
    val tracksPath: String,
    val tracksSha256: String,
)

/** The rear-screen image of one concept on the live stage. */
@Serializable
data class LiveScreenAsset(val concept: ConceptId, val path: String, val sha256: String)

/** The shared live stage and what each mix and concept adds to it. */
@Serializable
data class LiveStageAssets(
    val stagePath: String,
    val stageSha256: String,
    val infoPath: String,
    val infoSha256: String,
    val screens: List<LiveScreenAsset>,
    val mixes: List<LiveMixAsset>,
)
```

and the field `val live: LiveStageAssets,` at the end of `AssetManifest`. In `AssetCatalog.kt` change `require(manifest.schemaVersion == 2)` to `require(manifest.schemaVersion == 3)`.

- [ ] **Step 8: Run the unit tests and a build**

Run: `uptime && ./gradlew --no-watch-fs :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL (the asset validation task runs before merging assets).

- [ ] **Step 9: Commit**

```bash
git add art/blender/render_mix_performances.py art/blender/export_live_stage.py art/blender/export_live_stage.sh \
  art/blender/tests/test_live_motion.py app/src/main/assets/demo/live app/src/main/assets/demo/manifest.json \
  tools/validate_demo_assets.py tools/tests/test_validate_demo_assets.py \
  app/src/main/java/dev/digitalrain/synthwave/core/model/MediaModels.kt \
  app/src/main/java/dev/digitalrain/synthwave/core/data/AssetCatalog.kt
git commit -m "live stage: export the shared stage and each mix's motion, listed in the asset manifest"
```

---

### Task 3: Stage data in Kotlin: motion, tracks, looks, clock, backend choice

**Files:**
- Create: `app/src/main/java/dev/digitalrain/synthwave/core/stage/MotionClip.kt`, `StageData.kt`, `StageClock.kt`, `StageBackend.kt`, `StageEngine.kt`
- Modify: `app/src/main/java/dev/digitalrain/synthwave/core/data/AssetCatalog.kt` (add `liveStage`)
- Test: `app/src/test/java/dev/digitalrain/synthwave/core/stage/MotionClipTest.kt`, `StageDataTest.kt`, `StageClockTest.kt`, `StageBackendTest.kt`; `app/src/test/java/dev/digitalrain/synthwave/core/data/AssetCatalogTest.kt`

**Interfaces:**
- Consumes: Task 2's files and `AssetManifest.live`.
- Produces:
  - `class MotionClip(fps: Int, frames: Int, nodes: List<NodeMotion>)` with `duration: Float` and `MotionClip.parse(ByteArray)`.
  - `class NodeMotion(name: String, rest: FloatArray, channel: Int, values: FloatArray)`, constants `NodeMotion.NONE/TRANSLATION/ROTATION`, `fun matrixAt(time: Float, fps: Int, frames: Int, out: FloatArray, scratch: FloatArray)` (column-major 4×4 into `out`, `scratch` is a FloatArray(10)).
  - `fun trsMatrix(trs: FloatArray, out: FloatArray)`.
  - `StageInfo.parse(String)`, `StageInfo(fps, frames, areaLights: List<AreaLight>, cameras: Map<String, OrthoCamera>, concepts: Map<ConceptId, ConceptLook>)`, `AreaLight(name, color: List<Float>, watts: Float, position: List<Float>, direction: List<Float>)`, `OrthoCamera(ymag: Float)`, `ConceptLook(suits: List<List<Float>>, lights: Map<String, LightLook>)`, `LightLook(color: List<Float>, factor: Float)`.
  - `StageTracks.parse(String)`, `StageTracks(fps, frames, lights: List<LightTrack>, emissive: List<EmissiveTrack>)`, `LightTrack(light, watts: List<Float>)`, `EmissiveTrack(material, color: List<Float>, strength: List<Float>)`.
  - `fun sampleTrack(values: FloatArray, time: Float, fps: Int): Float`.
  - `const val BROADCAST_CAMERA = "CAM | Broadcast group"`, `fun fancamName(member: MemberId): String`.
  - `class StageClock(now: () -> Long = SystemClock::elapsedRealtime)` with `update(positionMs: Long, playing: Boolean)` and `positionMs(): Long`.
  - `enum class StageBackend { VULKAN, OPENGL }`, `fun chooseBackend(manufacturer: String, sdkInt: Int, vulkan11: Boolean, override: String): StageBackend`, `fun chooseBackend(context: Context): StageBackend`.
  - `data class StageRequest(stagePath, infoPath, motionPath, tracksPath, screenPath: String, concept: ConceptId, member: MemberId)` (paths from the assets root, e.g. `demo/live/stage.glb`), `interface StageEngine { suspend fun prepare(request: StageRequest): Boolean; fun unload() }`.
  - `AssetCatalog.liveStage(selection: Selection): StageRequest?`.

- [ ] **Step 1: Write the failing tests**

`MotionClipTest.kt`:

```kotlin
package dev.digitalrain.synthwave.core.stage

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionClipTest {
    private fun file(frames: Int, vararg nodes: Triple<String, Int, FloatArray>): ByteArray {
        val buffer = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("SRM1".toByteArray()).putInt(24).putInt(frames).putInt(nodes.size)
        for ((name, channel, values) in nodes) {
            val bytes = name.toByteArray()
            buffer.putInt(bytes.size).put(bytes)
            floatArrayOf(1f, 2f, 3f, 0f, 0f, 0f, 1f, 1f, 1f, 1f).forEach { buffer.putFloat(it) }
            buffer.putInt(channel)
            values.forEach { buffer.putFloat(it) }
        }
        return buffer.array().copyOf(buffer.position())
    }

    private fun pose(node: NodeMotion, time: Float, frames: Int = 2): FloatArray =
        FloatArray(16).also { node.matrixAt(time, 24, frames, it, FloatArray(10)) }

    @Test fun restPoseIsTheRestTranslation() {
        val clip = MotionClip.parse(file(2, Triple("boot", NodeMotion.NONE, FloatArray(0))))
        assertEquals(24, clip.fps)
        assertArrayEquals(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 1f, 2f, 3f, 1f),
            pose(clip.nodes.single(), 0.5f), 1e-6f)
    }

    @Test fun translationIsBlendedBetweenFrames() {
        val clip = MotionClip.parse(file(2, Triple("arm", NodeMotion.TRANSLATION, floatArrayOf(0f, 0f, 0f, 2f, 4f, 6f))))
        val m = pose(clip.nodes.single(), 0.5f / 24)
        assertArrayEquals(floatArrayOf(1f, 2f, 3f), floatArrayOf(m[12], m[13], m[14]), 1e-5f)
        val end = pose(clip.nodes.single(), 99f)
        assertArrayEquals(floatArrayOf(2f, 4f, 6f), floatArrayOf(end[12], end[13], end[14]), 1e-5f)
    }

    @Test fun rotationTakesTheShortWayAndStaysUnitLength() {
        val s = Math.sqrt(0.5).toFloat()
        // 0 then 90 degrees about z, the second written with the opposite sign (the same rotation).
        val clip = MotionClip.parse(file(2, Triple("head", NodeMotion.ROTATION, floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, -s, -s))))
        val m = pose(clip.nodes.single(), 1f / 24)
        // The x axis turns to y.
        assertArrayEquals(floatArrayOf(0f, 1f, 0f), floatArrayOf(m[0], m[1], m[2]), 1e-5f)
        val half = pose(clip.nodes.single(), 0.5f / 24)
        val length = Math.hypot(half[0].toDouble(), half[1].toDouble())
        assertEquals(1.0, length, 1e-5)
        assertTrue(half[1] > 0.6f) // about 45 degrees, not the long way round
    }

    @Test fun everyCheckedInMixParses() {
        for (arrangement in listOf("sparse", "driving", "layered")) for (member in listOf("frontman", "keyboardist", "keytar", "drummer")) {
            val bytes = requireNotNull(javaClass.classLoader?.getResource("demo/live/mix/$arrangement-$member.motion")).readBytes()
            val clip = MotionClip.parse(bytes)
            assertEquals(480, clip.frames)
            assertEquals(479f / 24f, clip.duration, 1e-6f)
            assertTrue(clip.nodes.count { it.channel != NodeMotion.NONE } >= 74)
        }
    }
}
```

`StageDataTest.kt`:

```kotlin
package dev.digitalrain.synthwave.core.stage

import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StageDataTest {
    private fun text(path: String) = requireNotNull(javaClass.classLoader?.getResource(path)).readText()

    @Test fun stageDescribesEveryConceptAndCamera() {
        val info = StageInfo.parse(text("demo/live/stage.json"))
        assertEquals(setOf(*ConceptId.entries.toTypedArray()), info.concepts.keys)
        for (look in info.concepts.values) {
            assertEquals(4, look.suits.size)
            assertEquals(6, look.lights.size)
        }
        assertEquals(4.65f, info.cameras.getValue(BROADCAST_CAMERA).ymag, 0.01f)
        for (member in MemberId.entries) assertTrue(fancamName(member) in info.cameras)
        assertEquals(350f / 300f, info.concepts.getValue(ConceptId.ELECTRIC).lights.getValue("Warm portrait key").factor, 1e-4f)
        assertEquals(4, info.areaLights.size)
    }

    @Test fun everyMixHasItsLightAndEmissionLevels() {
        for (arrangement in listOf("sparse", "driving", "layered")) for (member in listOf("frontman", "keyboardist", "keytar", "drummer")) {
            val tracks = StageTracks.parse(text("demo/live/mix/$arrangement-$member.tracks.json"))
            assertEquals(480, tracks.frames)
            assertTrue(tracks.lights.all { it.watts.size == 480 })
            assertTrue(tracks.emissive.all { it.strength.size == 480 && it.color.size == 3 })
        }
    }

    @Test fun tracksAreBlendedBetweenFramesAndHeldAtTheEnds() {
        val values = floatArrayOf(0f, 10f, 20f)
        assertEquals(5f, sampleTrack(values, 0.5f / 24, 24), 1e-5f)
        assertEquals(0f, sampleTrack(values, -1f, 24), 0f)
        assertEquals(20f, sampleTrack(values, 5f, 24), 0f)
    }
}
```

`StageClockTest.kt`:

```kotlin
package dev.digitalrain.synthwave.core.stage

import org.junit.Assert.assertEquals
import org.junit.Test

class StageClockTest {
    private var now = 1_000L
    private val clock = StageClock { now }

    @Test fun carriesThePositionForwardWhilePlaying() {
        clock.update(9_250, playing = true)
        now += 30
        assertEquals(9_280, clock.positionMs())
    }

    @Test fun holdsThePositionWhilePaused() {
        clock.update(9_250, playing = false)
        now += 500
        assertEquals(9_250, clock.positionMs())
    }

    @Test fun stopsCarryingForwardWhenSamplesStop() {
        clock.update(9_250, playing = true)
        now += 5_000
        assertEquals(9_450, clock.positionMs())
    }
}
```

`StageBackendTest.kt`:

```kotlin
package dev.digitalrain.synthwave.core.stage

import org.junit.Assert.assertEquals
import org.junit.Test

class StageBackendTest {
    @Test fun vulkanOnlyOnRecentGooglePhonesWithVulkan11() {
        assertEquals(StageBackend.VULKAN, chooseBackend("Google", 34, vulkan11 = true, override = ""))
        assertEquals(StageBackend.OPENGL, chooseBackend("Google", 30, vulkan11 = true, override = ""))
        assertEquals(StageBackend.OPENGL, chooseBackend("Google", 34, vulkan11 = false, override = ""))
        assertEquals(StageBackend.OPENGL, chooseBackend("samsung", 34, vulkan11 = true, override = ""))
    }

    @Test fun debugPropertyOverridesTheList() {
        assertEquals(StageBackend.OPENGL, chooseBackend("Google", 34, vulkan11 = true, override = "opengl"))
        assertEquals(StageBackend.VULKAN, chooseBackend("samsung", 34, vulkan11 = true, override = "vulkan"))
    }
}
```

Add to `AssetCatalogTest.kt`:

```kotlin
    @Test
    fun liveStageNamesTheShowsFilesOrNothingIfOneIsMissing() {
        val selection = Selection(ArrangementId.DRIVING, MemberId.KEYTAR, ConceptId.WARM)
        val request = AssetCatalog(manifest, AssetOpener { true }).liveStage(selection)
        assertEquals(StageRequest("demo/live/stage.glb", "demo/live/stage.json", "demo/live/mix/driving-keytar.motion",
            "demo/live/mix/driving-keytar.tracks.json", "demo/live/screen-warm.png", ConceptId.WARM, MemberId.KEYTAR), request)
        val missing = AssetCatalog(manifest, AssetOpener { it != "live/mix/driving-keytar.motion" })
        assertEquals(null, missing.liveStage(selection))
    }
```

(import `dev.digitalrain.synthwave.core.stage.StageRequest`).

- [ ] **Step 2: Run them to see them fail**

Run: `uptime && ./gradlew --no-watch-fs :app:testDebugUnitTest --tests 'dev.digitalrain.synthwave.core.stage.*' --tests '*AssetCatalogTest'`
Expected: compilation errors for the missing classes.

- [ ] **Step 3: Write `MotionClip.kt`**

```kotlin
package dev.digitalrain.synthwave.core.stage

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * One mix's motion from art/blender/live_motion.py: what differs from the shared stage file. Each
 * moving node has one channel with a key per frame; the others only sit somewhere else.
 */
class MotionClip(val fps: Int, val frames: Int, val nodes: List<NodeMotion>) {
    /** Time of the last key, in seconds. */
    val duration: Float get() = (frames - 1).toFloat() / fps

    companion object {
        private const val MAGIC = 0x314D5253 // "SRM1", little-endian

        fun parse(bytes: ByteArray): MotionClip {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            require(buffer.int == MAGIC) { "Not a motion file" }
            val fps = buffer.int
            val frames = buffer.int
            val count = buffer.int
            require(fps > 0 && frames > 0 && count >= 0) { "Bad motion header" }
            val nodes = List(count) {
                val name = ByteArray(buffer.int).also { buffer.get(it) }.decodeToString()
                val rest = FloatArray(10) { buffer.float }
                val channel = buffer.int
                val width = when (channel) {
                    NodeMotion.NONE -> 0
                    NodeMotion.TRANSLATION -> 3
                    NodeMotion.ROTATION -> 4
                    else -> throw IllegalArgumentException("Unknown channel $channel for $name")
                }
                NodeMotion(name, rest, channel, FloatArray(frames * width) { buffer.float })
            }
            require(!buffer.hasRemaining()) { "Trailing bytes in motion file" }
            return MotionClip(fps, frames, nodes)
        }
    }
}

/** A node's rest transform (translation, rotation xyzw, scale) and its animated channel, if any. */
class NodeMotion(val name: String, val rest: FloatArray, val channel: Int, val values: FloatArray) {
    /** Writes this node's local transform at [time] seconds to [out], column-major. */
    fun matrixAt(time: Float, fps: Int, frames: Int, out: FloatArray, scratch: FloatArray) {
        rest.copyInto(scratch)
        if (channel != NONE) {
            val position = (time * fps).coerceIn(0f, (frames - 1).toFloat())
            val index = position.toInt()
            val next = minOf(index + 1, frames - 1)
            val f = position - index
            if (channel == TRANSLATION) {
                for (i in 0 until 3) scratch[i] = values[index * 3 + i] * (1 - f) + values[next * 3 + i] * f
            } else {
                val a = index * 4
                val b = next * 4
                var dot = 0f
                for (i in 0 until 4) dot += values[a + i] * values[b + i]
                val sign = if (dot < 0) -1f else 1f // q and -q are the same turn; blend the near one
                var length = 0f
                for (i in 0 until 4) {
                    val v = values[a + i] * (1 - f) + sign * values[b + i] * f
                    scratch[3 + i] = v
                    length += v * v
                }
                val scale = 1f / sqrt(length)
                for (i in 3 until 7) scratch[i] *= scale
            }
        }
        trsMatrix(scratch, out)
    }

    companion object {
        const val NONE = 0
        const val TRANSLATION = 1
        const val ROTATION = 2
    }
}

/** Translation, rotation (x y z w) and scale to a column-major 4x4 matrix. */
fun trsMatrix(trs: FloatArray, out: FloatArray) {
    val x = trs[3]; val y = trs[4]; val z = trs[5]; val w = trs[6]
    val sx = trs[7]; val sy = trs[8]; val sz = trs[9]
    out[0] = (1 - 2 * (y * y + z * z)) * sx; out[1] = 2 * (x * y + z * w) * sx; out[2] = 2 * (x * z - y * w) * sx; out[3] = 0f
    out[4] = 2 * (x * y - z * w) * sy; out[5] = (1 - 2 * (x * x + z * z)) * sy; out[6] = 2 * (y * z + x * w) * sy; out[7] = 0f
    out[8] = 2 * (x * z + y * w) * sz; out[9] = 2 * (y * z - x * w) * sz; out[10] = (1 - 2 * (x * x + y * y)) * sz; out[11] = 0f
    out[12] = trs[0]; out[13] = trs[1]; out[14] = trs[2]; out[15] = 1f
}
```

- [ ] **Step 4: Write `StageData.kt`**

```kotlin
package dev.digitalrain.synthwave.core.stage

import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val stageJson = Json { ignoreUnknownKeys = false }

const val BROADCAST_CAMERA = "CAM | Broadcast group"

/** The camera in the stage file that follows [member] for the member cam. */
fun fancamName(member: MemberId): String = when (member) {
    MemberId.FRONTMAN -> "CAM | Frontman fancam"
    MemberId.KEYBOARDIST -> "CAM | Producer fancam"
    MemberId.KEYTAR -> "CAM | Keytar fancam"
    MemberId.DRUMMER -> "CAM | Drummer fancam"
}

/** stage.json from art/blender/export_live_stage.py. Colours are linear; positions in glTF axes. */
@Serializable
data class StageInfo(
    val fps: Int,
    val frames: Int,
    val areaLights: List<AreaLight>,
    val cameras: Map<String, OrthoCamera>,
    val concepts: Map<ConceptId, ConceptLook>,
) {
    companion object {
        fun parse(json: String): StageInfo = stageJson.decodeFromString(json)
    }
}

/** A Blender area light, which glTF can't carry; the app stands a soft spot light in its place. */
@Serializable
data class AreaLight(
    val name: String,
    val color: List<Float>,
    val watts: Float,
    val position: List<Float>,
    val direction: List<Float>,
)

@Serializable
data class OrthoCamera(val ymag: Float)

/** A concept's suit colours and, per light, its colour and its level as a factor of the export's. */
@Serializable
data class ConceptLook(val suits: List<List<Float>>, val lights: Map<String, LightLook>)

@Serializable
data class LightLook(val color: List<Float>, val factor: Float)

/** One mix's light levels (Blender watts) and emission strengths, one value per frame. */
@Serializable
data class StageTracks(val fps: Int, val frames: Int, val lights: List<LightTrack>, val emissive: List<EmissiveTrack>) {
    companion object {
        fun parse(json: String): StageTracks = stageJson.decodeFromString(json)
    }
}

@Serializable
data class LightTrack(val light: String, val watts: List<Float>)

/** [strength] is glTF emissive strength, already multiplied by the brightness of [color]. */
@Serializable
data class EmissiveTrack(val material: String, val color: List<Float>, val strength: List<Float>)

/** [values] (one per frame at [fps]) at [time] seconds, blended between frames, held at the ends. */
fun sampleTrack(values: FloatArray, time: Float, fps: Int): Float {
    val position = (time * fps).coerceIn(0f, (values.size - 1).toFloat())
    val index = position.toInt()
    val next = minOf(index + 1, values.size - 1)
    return values[index] + (values[next] - values[index]) * (position - index)
}
```

- [ ] **Step 5: Write `StageClock.kt`**

```kotlin
package dev.digitalrain.synthwave.core.stage

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicReference

/**
 * The show position for the live stage, from the app's audio samples (every 50 ms) carried forward
 * between them while the audio plays. One sample object, so a reader on the render thread never
 * pairs a new position with an old time.
 */
class StageClock(private val now: () -> Long = SystemClock::elapsedRealtime) {
    private class Sample(val positionMs: Long, val atMs: Long, val playing: Boolean)

    private val sample = AtomicReference(Sample(0, 0, false))

    fun update(positionMs: Long, playing: Boolean) {
        sample.set(Sample(positionMs, now(), playing))
    }

    fun positionMs(): Long {
        val current = sample.get()
        if (!current.playing) return current.positionMs
        // If samples stop arriving, stop after a few samples' worth rather than run ahead of the audio.
        return current.positionMs + (now() - current.atMs).coerceIn(0, MAX_CARRY_MS)
    }

    private companion object {
        const val MAX_CARRY_MS = 200L
    }
}
```

- [ ] **Step 6: Write `StageBackend.kt` and `StageEngine.kt`**

```kotlin
package dev.digitalrain.synthwave.core.stage

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

enum class StageBackend { VULKAN, OPENGL }

private const val VULKAN_1_1 = 0x401000

/**
 * Vulkan only where it is known to work: weak Vulkan drivers tend to draw garbage rather than fail
 * to start. Measured on a Pixel 7 Pro (Android 16). `adb shell setprop debug.signalroom.backend
 * vulkan|opengl` overrides the list on the next launch.
 */
fun chooseBackend(manufacturer: String, sdkInt: Int, vulkan11: Boolean, override: String): StageBackend = when (override) {
    "vulkan" -> StageBackend.VULKAN
    "opengl" -> StageBackend.OPENGL
    else -> if (manufacturer.equals("Google", ignoreCase = true) && sdkInt >= 31 && vulkan11) StageBackend.VULKAN
        else StageBackend.OPENGL
}

fun chooseBackend(context: Context): StageBackend = chooseBackend(
    Build.MANUFACTURER,
    Build.VERSION.SDK_INT,
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, VULKAN_1_1),
    systemProperty("debug.signalroom.backend"),
)

private fun systemProperty(name: String): String = runCatching {
    Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, name) as String
}.getOrDefault("")
```

```kotlin
package dev.digitalrain.synthwave.core.stage

import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId

/** One show's live stage files, as paths from the root of the app's assets. */
data class StageRequest(
    val stagePath: String,
    val infoPath: String,
    val motionPath: String,
    val tracksPath: String,
    val screenPath: String,
    val concept: ConceptId,
    val member: MemberId,
)

/** What the broadcast screen needs from the live stage; a fake stands in for it in unit tests. */
interface StageEngine {
    /** Loads [request] and draws it once off screen. False if the stage can't be drawn. */
    suspend fun prepare(request: StageRequest): Boolean

    /** Frees the engine and everything it holds; the next [prepare] starts again. */
    fun unload()
}
```

- [ ] **Step 7: Add `liveStage` to `AssetCatalog`**

Add after `visualPlayback`:

```kotlin
    /** The live stage files for this show, or null if any of them can't be opened. */
    fun liveStage(selection: Selection): StageRequest? {
        val live = manifest.live
        val mix = live.mixes.singleOrNull { it.arrangement == selection.arrangement && it.member == selection.member }
            ?: return null
        val screen = live.screens.singleOrNull { it.concept == selection.concept } ?: return null
        val paths = listOf(live.stagePath, live.infoPath, mix.motionPath, mix.tracksPath, screen.path)
        if (!paths.all(opener::canOpen)) return null
        return StageRequest("demo/${live.stagePath}", "demo/${live.infoPath}", "demo/${mix.motionPath}",
            "demo/${mix.tracksPath}", "demo/${screen.path}", selection.concept, selection.member)
    }
```

(import `dev.digitalrain.synthwave.core.stage.StageRequest`).

- [ ] **Step 8: Run the tests to see them pass**

Run: `uptime && ./gradlew --no-watch-fs :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/dev/digitalrain/synthwave/core/stage app/src/main/java/dev/digitalrain/synthwave/core/data/AssetCatalog.kt \
  app/src/test/java/dev/digitalrain/synthwave/core/stage app/src/test/java/dev/digitalrain/synthwave/core/data/AssetCatalogTest.kt
git commit -m "live stage: read motion, light levels and concept looks; audio-driven clock; backend choice"
```

---

### Task 4: Draw the stage with Filament on its own thread

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Create: `app/src/main/java/dev/digitalrain/synthwave/core/stage/StageScene.kt`, `FilamentStage.kt`
- Test: `app/src/androidTest/java/dev/digitalrain/synthwave/core/stage/FilamentStageTest.kt`

**Interfaces:**
- Consumes: everything from Task 3.
- Produces: `class FilamentStage(context: Context, clock: StageClock, backend: StageBackend) : StageEngine` with `attachMain(surface: Surface, width: Int, height: Int)`, `detachMain()` (blocks), `attachCam(surface: Surface, width: Int, height: Int)`, `detachCam()` (blocks), `suspend fun capture(main: Boolean, width: Int, height: Int, time: Float): ByteArray` (RGBA, bottom row first), `release()`.

Reference for the Filament calls: `git show spike/realtime-3d:app/src/main/java/dev/digitalrain/synthwave/spike/FilamentStage.kt` (it compiled and drew the show; its light constants and exposure carry over).

- [ ] **Step 1: Add Filament, pinned**

`gradle/libs.versions.toml`, in `[versions]`:

```toml
# Pinned: Filament 1.76 and later need compileSdk 37, which AGP 8.13.2 does not support.
filament = "1.75.1"
```

in `[libraries]`:

```toml
filament-android = { group = "com.google.android.filament", name = "filament-android", version.ref = "filament" }
gltfio-android = { group = "com.google.android.filament", name = "gltfio-android", version.ref = "filament" }
```

`app/build.gradle.kts` dependencies: `implementation(libs.filament.android)` and `implementation(libs.gltfio.android)`.

- [ ] **Step 2: Write the failing device test**

```kotlin
package dev.digitalrain.synthwave.core.stage

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.digitalrain.synthwave.core.data.loadAndroidAssetCatalog
import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import dev.digitalrain.synthwave.core.model.Selection
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilamentStageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val catalog = loadAndroidAssetCatalog(context.assets)

    private fun request(arrangement: ArrangementId, member: MemberId, concept: ConceptId) =
        requireNotNull(catalog.liveStage(Selection(arrangement, member, concept)))

    private fun withStage(backend: StageBackend, block: suspend (FilamentStage) -> Unit) = runBlocking {
        val stage = FilamentStage(context, StageClock(), backend)
        try { block(stage) } finally { stage.release() }
    }

    /** Share of pixels that are not near black. */
    private fun lit(pixels: ByteArray): Float {
        var count = 0
        for (i in pixels.indices step 4) {
            if ((pixels[i].toInt() and 255) + (pixels[i + 1].toInt() and 255) + (pixels[i + 2].toInt() and 255) > 30) count++
        }
        return count * 4f / pixels.size
    }

    /** Mean difference per colour channel, 0-255. */
    private fun difference(a: ByteArray, b: ByteArray): Float {
        var total = 0L
        for (i in a.indices) if (i % 4 != 3) total += abs((a[i].toInt() and 255) - (b[i].toInt() and 255))
        return total / (a.size * 0.75f)
    }

    private fun save(pixels: ByteArray, width: Int, height: Int, name: String) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels))
        val upright = Bitmap.createBitmap(bitmap, 0, 0, width, height, Matrix().apply { preScale(1f, -1f) }, false)
        File(requireNotNull(context.getExternalFilesDir(null)), name).outputStream().use {
            upright.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun everyShowLoadsAndDrawsTheBand() = withStage(StageBackend.VULKAN) { stage ->
        for (arrangement in ArrangementId.entries) for (member in MemberId.entries) {
            assertTrue("$arrangement $member", stage.prepare(request(arrangement, member, ConceptId.WARM)))
        }
        val early = stage.capture(main = true, 270, 480, 2f)
        val late = stage.capture(main = true, 270, 480, 10f)
        assertTrue("lit ${lit(early)}", lit(early) > 0.2f)
        assertTrue("moved ${difference(early, late)}", difference(early, late) > 1f)
    }

    @Test fun conceptsLookDifferent() = withStage(StageBackend.VULKAN) { stage ->
        val frames = ConceptId.entries.map { concept ->
            assertTrue(stage.prepare(request(ArrangementId.DRIVING, MemberId.FRONTMAN, concept)))
            stage.capture(main = true, 540, 960, 10f).also { save(it, 540, 960, "live-${concept.name.lowercase()}-10s.png") }
        }
        for (i in frames.indices) for (j in i + 1 until frames.size) {
            assertTrue("concepts $i $j", difference(frames[i], frames[j]) > 3f)
        }
    }

    @Test fun memberCamDrawsTheFeaturedMember() = withStage(StageBackend.VULKAN) { stage ->
        for (member in MemberId.entries) {
            assertTrue(stage.prepare(request(ArrangementId.LAYERED, member, ConceptId.ELECTRIC)))
            val cam = stage.capture(main = false, 280, 360, 10f)
            save(cam, 280, 360, "live-cam-${member.name.lowercase()}-10s.png")
            assertTrue("$member lit ${lit(cam)}", lit(cam) > 0.2f)
        }
    }

    @Test fun openGlDrawsTheSameShow() = withStage(StageBackend.OPENGL) { stage ->
        assertTrue(stage.prepare(request(ArrangementId.SPARSE, MemberId.DRUMMER, ConceptId.MONOCHROME)))
        assertTrue(lit(stage.capture(main = true, 270, 480, 10f)) > 0.2f)
    }

    @Test fun unloadThenPrepareStartsAgain() = withStage(StageBackend.VULKAN) { stage ->
        assertTrue(stage.prepare(request(ArrangementId.SPARSE, MemberId.KEYTAR, ConceptId.WARM)))
        stage.unload()
        assertTrue(stage.prepare(request(ArrangementId.SPARSE, MemberId.KEYTAR, ConceptId.WARM)))
        assertTrue(lit(stage.capture(main = true, 270, 480, 10f)) > 0.2f)
    }
}
```

Check before running on the phone: tell the user you are about to use the Pixel and wait if they are using it.

Run: `uptime && ANDROID_SERIAL=2B011FDH300KNR ./gradlew --no-watch-fs :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.digitalrain.synthwave.core.stage.FilamentStageTest`
Expected: compilation error, `FilamentStage` does not exist.

- [ ] **Step 3: Write `StageScene.kt`**

```kotlin
package dev.digitalrain.synthwave.core.stage

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.util.Log
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Renderer
import com.google.android.filament.SwapChain
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.ToneMapper
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.TextureHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max

internal const val STAGE_TAG = "LiveStage"
// Blender watts to candela, chosen by eye against the rendered videos.
private const val AREA_CANDELA_PER_WATT = 0.15f
private const val SPOT_CANDELA_PER_WATT = 0.22f
private const val SCREEN_MATERIAL = "REAR SCREEN | replaceable feed"

/** Picture settings: the spike's, kept until the device check (Task 7) shows a cheaper set looks the same. */
internal object StageQuality {
    const val MSAA = true
    const val BLOOM = true
    const val SHADOW_MAP_SIZE = 1024
}

private object FilamentLibraries {
    val ready: Unit by lazy {
        Filament.init()
        Gltfio.init()
    }
}

/**
 * The stage's Filament objects: engine, scene, the broadcast and member-cam views, and the current
 * mix and concept. Only the render thread in [FilamentStage] touches it.
 */
internal class StageScene(private val assets: AssetManager, backend: StageBackend, stagePath: String, infoPath: String) {
    val engine: Engine
    val mainView: View
    val camView: View
    private val scene: com.google.android.filament.Scene
    private val provider: UbershaderProvider
    private val loader: AssetLoader
    private val asset: FilamentAsset
    private val info: StageInfo
    private val indirectLight: IndirectLight
    private val colorGrading: ColorGrading
    private val restPose = HashMap<Int, FloatArray>() // entity -> local transform as exported
    private val lights = HashMap<String, Int>() // light name -> entity
    private val areaLights = HashSet<String>()
    private val materials = HashMap<String, MutableList<MaterialInstance>>()
    private val screens = HashMap<String, Texture>()
    private val sampler = TextureSampler(TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR,
        TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE)
    private val broadcastCamera: Camera
    private var memberCamera: Camera? = null
    private var memberCameraName: String? = null

    private class MovingNode(val instance: Int, val motion: NodeMotion)
    private class LightLevel(val instance: Int, val name: String, val watts: FloatArray, var scale: Float)
    private class Emission(val materials: List<MaterialInstance>, val strength: FloatArray)

    private var clip: MotionClip? = null
    private var moving = emptyList<MovingNode>()
    private var levels = emptyList<LightLevel>()
    private var emissions = emptyList<Emission>()
    var mixPath: String? = null
        private set
    private val matrix = FloatArray(16)
    private val trs = FloatArray(10)

    val duration: Float get() = clip?.duration ?: 0f

    init {
        FilamentLibraries.ready
        engine = createEngine(backend)
        scene = engine.createScene()
        colorGrading = ColorGrading.Builder().toneMapper(ToneMapper.Filmic()).build(engine)
        mainView = engine.createView().apply {
            scene = this@StageScene.scene
            colorGrading = this@StageScene.colorGrading
            bloomOptions = bloomOptions.apply { enabled = StageQuality.BLOOM; strength = 0.12f }
            multiSampleAntiAliasingOptions = multiSampleAntiAliasingOptions.apply {
                enabled = StageQuality.MSAA; sampleCount = 2
            }
        }
        // The inset is small: no shadows, bloom or MSAA.
        camView = engine.createView().apply {
            scene = this@StageScene.scene
            colorGrading = this@StageScene.colorGrading
            isShadowingEnabled = false
        }
        indirectLight = IndirectLight.Builder().irradiance(1, floatArrayOf(0.03f, 0.036f, 0.054f)).intensity(1f).build(engine)
        scene.indirectLight = indirectLight
        provider = UbershaderProvider(engine)
        loader = AssetLoader(engine, provider, EntityManager.get())
        asset = requireNotNull(loader.createAsset(readBuffer(stagePath))) { "Stage file did not load" }
        ResourceLoader(engine).apply { loadResources(asset); destroy() }
        asset.releaseSourceData()
        scene.addEntities(asset.entities)
        info = StageInfo.parse(readText(infoPath))
        val transforms = engine.transformManager
        for (entity in asset.entities) {
            val instance = transforms.getInstance(entity)
            if (instance != 0) restPose[entity] = transforms.getTransform(instance, FloatArray(16))
        }
        for (entity in asset.lightEntities) asset.getName(entity)?.let { lights[it] = entity }
        for (mi in asset.instance.materialInstances) materials.getOrPut(mi.name) { mutableListOf() }.add(mi)
        for (area in info.areaLights) addAreaLight(area)
        broadcastCamera = camera(BROADCAST_CAMERA)
        mainView.camera = broadcastCamera
    }

    private fun createEngine(backend: StageBackend): Engine {
        if (backend == StageBackend.VULKAN) {
            runCatching { return Engine.create(Engine.Backend.VULKAN) }
                .onFailure { Log.w(STAGE_TAG, "Vulkan did not start; using OpenGL ES", it) }
        }
        return Engine.create(Engine.Backend.OPENGL)
    }

    private fun readBuffer(path: String): ByteBuffer {
        val bytes = assets.open(path).use { it.readBytes() }
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { flip() }
    }

    private fun readText(path: String): String = assets.open(path).bufferedReader().use { it.readText() }

    private fun camera(name: String): Camera {
        val entity = asset.cameraEntities.firstOrNull { asset.getName(it) == name } ?: error("No camera $name in the stage")
        return requireNotNull(engine.getCameraComponent(entity)) { "$name has no camera" }.apply {
            setExposure(1f, 1.2f, 100f) // exposure 1: light values reach the screen unscaled
        }
    }

    /** glTF has no area lights: a wide soft spot light at the same place and aim stands in. */
    private fun addAreaLight(area: AreaLight) {
        val entity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.SPOT)
            .color(area.color[0], area.color[1], area.color[2])
            .intensityCandela(area.watts * AREA_CANDELA_PER_WATT)
            .position(area.position[0], area.position[1], area.position[2])
            .direction(area.direction[0], area.direction[1], area.direction[2])
            .spotLightCone(0.4f * 1.22f, 1.22f)
            .falloff(20f)
            .castShadows(area.name.startsWith("Warm portrait"))
            .shadowOptions(LightManager.ShadowOptions().apply { mapSize = StageQuality.SHADOW_MAP_SIZE })
            .build(engine, entity)
        scene.addEntity(entity)
        lights[area.name] = entity
        areaLights += area.name
    }

    /** Puts every node back where the stage file has it, then applies [motion] and [tracks]. */
    fun useMix(path: String, motion: MotionClip, tracks: StageTracks) {
        val transforms = engine.transformManager
        for ((entity, local) in restPose) transforms.setTransform(transforms.getInstance(entity), local)
        val moved = ArrayList<MovingNode>()
        for (node in motion.nodes) {
            val entity = asset.getFirstEntityByName(node.name)
            require(entity != 0) { "Motion names ${node.name}, which the stage does not have" }
            val instance = transforms.getInstance(entity)
            node.matrixAt(0f, motion.fps, motion.frames, matrix, trs)
            transforms.setTransform(instance, matrix)
            if (node.channel != NodeMotion.NONE) moved += MovingNode(instance, node)
        }
        val lightManager = engine.lightManager
        levels = tracks.lights.mapNotNull { track ->
            lights[track.light]?.let { entity ->
                val perWatt = if (track.light in areaLights) AREA_CANDELA_PER_WATT else SPOT_CANDELA_PER_WATT
                LightLevel(lightManager.getInstance(entity), track.light, track.watts.toFloatArray(), perWatt)
            } ?: null.also { Log.w(STAGE_TAG, "No light ${track.light} in the stage") }
        }
        emissions = tracks.emissive.mapNotNull { track ->
            materials[track.material]?.let { instances ->
                for (mi in instances) mi.setParameter("emissiveFactor", track.color[0], track.color[1], track.color[2])
                Emission(instances, track.strength.toFloatArray())
            }
        }
        moving = moved
        clip = motion
        mixPath = path
    }

    /** Suit colours, light colours and levels, and the rear-screen image of [concept]. */
    fun useConcept(concept: ConceptId, screenPath: String) {
        val look = info.concepts.getValue(concept)
        look.suits.forEachIndexed { index, color ->
            materials["SUIT $index"]?.forEach { it.setParameter("baseColorFactor", color[0], color[1], color[2], 1f) }
        }
        val lightManager = engine.lightManager
        for ((name, light) in look.lights) {
            lights[name]?.let { lightManager.setColor(lightManager.getInstance(it), light.color[0], light.color[1], light.color[2]) }
        }
        for (level in levels) {
            val perWatt = if (level.name in areaLights) AREA_CANDELA_PER_WATT else SPOT_CANDELA_PER_WATT
            level.scale = perWatt * (look.lights[level.name]?.factor ?: 1f)
        }
        val screen = screens.getOrPut(screenPath) { loadTexture(screenPath) }
        materials[SCREEN_MATERIAL]?.forEach { it.setParameter("emissiveMap", screen, sampler) }
    }

    private fun loadTexture(path: String): Texture {
        val bitmap = assets.open(path).use { BitmapFactory.decodeStream(it) }
        val levels = floor(log2(max(bitmap.width, bitmap.height).toFloat())).toInt() + 1
        val texture = Texture.Builder().width(bitmap.width).height(bitmap.height).levels(levels)
            .sampler(Texture.Sampler.SAMPLER_2D).format(Texture.InternalFormat.SRGB8_A8).build(engine)
        TextureHelper.setBitmap(engine, texture, 0, bitmap)
        texture.generateMipmaps(engine)
        return texture
    }

    fun useMember(member: MemberId) {
        val name = fancamName(member)
        memberCamera = camera(name)
        memberCameraName = name
        camView.camera = memberCamera
    }

    /** Poses the band and sets the lights for [time] seconds into the show. */
    fun pose(time: Float) {
        val motion = clip ?: return
        val transforms = engine.transformManager
        for (node in moving) {
            node.motion.matrixAt(time, motion.fps, motion.frames, matrix, trs)
            transforms.setTransform(node.instance, matrix)
        }
        val lightManager = engine.lightManager
        for (level in levels) lightManager.setIntensityCandela(level.instance, sampleTrack(level.watts, time, motion.fps) * level.scale)
        for (emission in emissions) {
            val strength = sampleTrack(emission.strength, time, motion.fps)
            for (mi in emission.materials) mi.setParameter("emissiveStrength", strength)
        }
    }

    /** Sizes [view]'s viewport and its orthographic camera to [width] x [height], keeping the camera's height. */
    fun fit(main: Boolean, width: Int, height: Int) {
        val view = if (main) mainView else camView
        val name = if (main) BROADCAST_CAMERA else memberCameraName ?: return
        val camera = if (main) broadcastCamera else memberCamera ?: return
        view.viewport = Viewport(0, 0, width, height)
        val halfHeight = info.cameras.getValue(name).ymag.toDouble()
        val halfWidth = halfHeight * width / height
        camera.setProjection(Camera.Projection.ORTHO, -halfWidth, halfWidth, -halfHeight, halfHeight, 0.1, 1000.0)
    }

    /**
     * Draws one view at [time] into an off-screen swap chain. With [readBack], returns its RGBA pixels
     * (bottom row first); either way Vulkan has built the pipelines the view needs.
     */
    fun drawOffScreen(main: Boolean, width: Int, height: Int, time: Float, readBack: Boolean): ByteArray? {
        pose(time)
        fit(main, width, height)
        val renderer = engine.createRenderer()
        val chain = engine.createSwapChain(width, height, SwapChain.CONFIG_READABLE)
        val pixels = if (readBack) ByteBuffer.allocateDirect(width * height * 4) else null
        try {
            var drawn = false
            for (attempt in 0 until 3) {
                if (renderer.beginFrame(chain, 0)) {
                    renderer.render(if (main) mainView else camView)
                    if (pixels != null) renderer.readPixels(0, 0, width, height,
                        Texture.PixelBufferDescriptor(pixels, Texture.Format.RGBA, Texture.Type.UBYTE, 1, 0, 0, width, null, null))
                    renderer.endFrame()
                    drawn = true
                    break
                }
                engine.flushAndWait()
            }
            engine.flushAndWait()
            check(drawn) { "The renderer skipped every off-screen frame" }
        } finally {
            engine.destroySwapChain(chain)
            engine.destroyRenderer(renderer)
            engine.flushAndWait()
        }
        return pixels?.let { ByteArray(it.capacity()).also { bytes -> it.rewind(); it.get(bytes) } }
    }

    fun destroy() {
        for (texture in screens.values) engine.destroyTexture(texture)
        for (name in areaLights) lights[name]?.let { engine.destroyEntity(it); EntityManager.get().destroy(it) }
        scene.removeEntities(asset.entities)
        loader.destroyAsset(asset)
        loader.destroy()
        provider.destroyMaterials()
        provider.destroy()
        engine.destroyIndirectLight(indirectLight)
        engine.destroyColorGrading(colorGrading)
        engine.destroyView(mainView)
        engine.destroyView(camView)
        engine.destroyScene(scene)
        engine.destroy()
    }
}
```

If a Filament call above does not compile against 1.75.1 (a renamed method or a different parameter type), look it up in the spike file named above or in the 1.75.1 sources jar under `~/.gradle/caches/modules-2/files-2.1/com.google.android.filament/`, fix it, and note the change in the commit message.

- [ ] **Step 4: Write `FilamentStage.kt`**

```kotlin
package dev.digitalrain.synthwave.core.stage

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import com.google.android.filament.Renderer
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private const val FRAME_RATE = 30
private const val FRAME_INTERVAL_NANOS = 1_000_000_000L / FRAME_RATE
private const val VSYNC_SLACK_NANOS = 4_000_000L

/**
 * The live stage, drawn by Filament. One thread, "stage-render", makes every Filament call; the
 * public methods post to it. Detaching a surface waits until that thread has destroyed its swap
 * chain, since the surface is gone once the view's destroy callback returns.
 */
class FilamentStage(
    context: Context,
    private val clock: StageClock,
    private val backend: StageBackend,
) : StageEngine, Choreographer.FrameCallback {
    private val assets = context.applicationContext.assets
    private val thread = HandlerThread("stage-render").apply { start() }
    private val handler = Handler(thread.looper)

    /** A surface the stage draws into: the broadcast SurfaceView or the member-cam TextureView. */
    private class Target(val main: Boolean) {
        var surface: Surface? = null
        var width = 0
        var height = 0
        var swapChain: SwapChain? = null
        var renderer: Renderer? = null
    }

    // Render thread only.
    private var stage: StageScene? = null
    private val mainTarget = Target(main = true)
    private val camTarget = Target(main = false)
    private var ticking = false
    private var lastFrameNanos = 0L
    private var lastTime = -1f
    private val stats = FrameStats()

    override suspend fun prepare(request: StageRequest): Boolean = suspendCancellableCoroutine { continuation ->
        val posted = handler.post {
            val drawn = runCatching { prepareOnRenderThread(request) }
                .onFailure { Log.e(STAGE_TAG, "Live stage failed to load", it) }.isSuccess
            if (continuation.isActive) continuation.resume(drawn)
        }
        if (!posted && continuation.isActive) continuation.resume(false)
    }

    private fun prepareOnRenderThread(request: StageRequest) {
        val start = SystemClock.elapsedRealtime()
        val scene = stage ?: StageScene(assets, backend, request.stagePath, request.infoPath).also { stage = it }
        if (scene.mixPath != request.motionPath) {
            val motion = MotionClip.parse(assets.open(request.motionPath).use { it.readBytes() })
            val tracks = StageTracks.parse(assets.open(request.tracksPath).bufferedReader().use { it.readText() })
            scene.useMix(request.motionPath, motion, tracks)
        }
        scene.useConcept(request.concept, request.screenPath)
        scene.useMember(request.member)
        // Draw both views once now (during the count-in), so Vulkan builds its pipelines before the show.
        scene.drawOffScreen(main = true, 540, 960, 0f, readBack = false)
        scene.drawOffScreen(main = false, 280, 360, 0f, readBack = false)
        for (target in listOf(mainTarget, camTarget)) if (target.surface != null) bind(target, scene)
        lastTime = -1f
        if (mainTarget.surface != null || camTarget.surface != null) startTicking()
        Log.i(STAGE_TAG, "Prepared ${request.motionPath} ${request.concept} in ${SystemClock.elapsedRealtime() - start} ms")
    }

    /** Test and look-check hook: one view at [time] seconds, as RGBA bytes, bottom row first. */
    suspend fun capture(main: Boolean, width: Int, height: Int, time: Float): ByteArray = suspendCancellableCoroutine { continuation ->
        handler.post {
            val scene = checkNotNull(stage) { "Prepare the stage first" }
            val pixels = scene.drawOffScreen(main, width, height, time, readBack = true)!!
            for (target in listOf(mainTarget, camTarget)) if (target.surface != null) bind(target, scene)
            lastTime = -1f
            if (continuation.isActive) continuation.resume(pixels)
        }
    }

    fun attachMain(surface: Surface, width: Int, height: Int) {
        handler.post { attach(mainTarget, surface, width, height) }
    }

    fun detachMain() = detachAndWait(mainTarget)

    fun attachCam(surface: Surface, width: Int, height: Int) {
        handler.post { attach(camTarget, surface, width, height) }
    }

    fun detachCam() = detachAndWait(camTarget)

    private fun attach(target: Target, surface: Surface, width: Int, height: Int) {
        if (target.surface !== surface) {
            releaseGpu(target)
            target.surface = surface
        }
        target.width = width
        target.height = height
        stage?.let { bind(target, it) }
        lastTime = -1f
        startTicking()
    }

    private fun bind(target: Target, scene: StageScene) {
        val surface = target.surface ?: return
        if (target.swapChain == null) target.swapChain = scene.engine.createSwapChain(surface)
        if (target.renderer == null) target.renderer = scene.engine.createRenderer().apply {
            clearOptions = clearOptions.apply { clearColor = floatArrayOf(0f, 0f, 0f, 1f); clear = true }
        }
        scene.fit(target.main, target.width, target.height)
        if (target.main && Build.VERSION.SDK_INT >= 30) {
            surface.setFrameRate(FRAME_RATE.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
        }
    }

    private fun detachAndWait(target: Target) {
        val done = CountDownLatch(1)
        val posted = handler.post {
            releaseGpu(target)
            target.surface = null
            if (mainTarget.surface == null && camTarget.surface == null) stopTicking()
            done.countDown()
        }
        if (posted) done.await()
    }

    private fun releaseGpu(target: Target) {
        val scene = stage
        if (scene != null) {
            target.swapChain?.let { scene.engine.destroySwapChain(it); scene.engine.flushAndWait() }
            target.renderer?.let { scene.engine.destroyRenderer(it) }
        }
        target.swapChain = null
        target.renderer = null
    }

    override fun unload() {
        handler.post { unloadOnRenderThread() }
    }

    private fun unloadOnRenderThread() {
        stopTicking()
        releaseGpu(mainTarget)
        releaseGpu(camTarget)
        stage?.destroy()
        stage = null
    }

    /** Frees everything and stops the render thread; the stage can't be used afterwards. */
    fun release() {
        handler.post { unloadOnRenderThread() }
        thread.quitSafely()
    }

    private fun startTicking() {
        if (ticking) return
        ticking = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun stopTicking() {
        ticking = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!ticking) return
        Choreographer.getInstance().postFrameCallback(this)
        val scene = stage ?: return
        if (frameTimeNanos - lastFrameNanos < FRAME_INTERVAL_NANOS - VSYNC_SLACK_NANOS) return
        val time = (clock.positionMs() / 1000f).coerceIn(0f, scene.duration)
        if (time == lastTime) return // paused: the last frame stays on screen
        val start = System.nanoTime()
        scene.pose(time)
        val drawn = draw(mainTarget, scene.mainView, frameTimeNanos)
        draw(camTarget, scene.camView, frameTimeNanos)
        if (drawn) {
            stats.add(frameTimeNanos, lastFrameNanos, System.nanoTime() - start)
            lastTime = time
            lastFrameNanos = frameTimeNanos
        }
    }

    private fun draw(target: Target, view: View, frameTimeNanos: Long): Boolean {
        val chain = target.swapChain ?: return false
        val renderer = target.renderer ?: return false
        if (!renderer.beginFrame(chain, frameTimeNanos)) return false
        renderer.render(view)
        renderer.endFrame()
        return true
    }

    /** Frame interval and render-thread time, logged every 10 s for the device check. */
    private class FrameStats {
        private val intervals = ArrayList<Float>()
        private val work = ArrayList<Float>()
        private var reportAt = 0L

        fun add(frameNanos: Long, previousNanos: Long, workNanos: Long) {
            if (previousNanos != 0L) intervals += (frameNanos - previousNanos) / 1e6f
            work += workNanos / 1e6f
            val now = SystemClock.elapsedRealtime()
            if (now < reportAt) return
            reportAt = now + 10_000
            if (intervals.isNotEmpty()) {
                val i = intervals.sorted()
                val w = work.sorted()
                Log.i(STAGE_TAG, "frames=%d interval p50=%.1f p99=%.1f ms work p50=%.2f max=%.2f ms".format(
                    i.size, i[i.size / 2], i[minOf(i.size - 1, (i.size * 0.99).toInt())], w[w.size / 2], w.last()))
            }
            intervals.clear()
            work.clear()
        }
    }
}
```

`clearColor`: the spike assigned `doubleArrayOf(...)` here; use whichever type 1.75.1 declares.

- [ ] **Step 5: Run the device tests**

Tell the user you are using the Pixel. Then:

Run: `uptime && ANDROID_SERIAL=2B011FDH300KNR ./gradlew --no-watch-fs :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.digitalrain.synthwave.core.stage.FilamentStageTest`
Expected: 5 tests pass. Pull the saved frames and look at them:
`adb -s 2B011FDH300KNR pull /sdcard/Android/data/dev.digitalrain.synthwave/files/ <scratchpad>/live-frames/`
Each `live-<concept>-10s.png` shows the band lit in that concept's colours; each `live-cam-<member>-10s.png` shows that member. If a test fails on lighting levels or a blank picture, check `adb logcat -s LiveStage` first.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/dev/digitalrain/synthwave/core/stage \
  app/src/androidTest/java/dev/digitalrain/synthwave/core/stage
git commit -m "live stage: draw the stage with Filament on its own render thread"
```

---

### Task 5: LiveStageController

**Files:**
- Create: `app/src/main/java/dev/digitalrain/synthwave/core/stage/LiveStageController.kt`
- Test: `app/src/test/java/dev/digitalrain/synthwave/core/stage/LiveStageControllerTest.kt`

**Interfaces:**
- Consumes: `AssetCatalog.liveStage`, `AssetCatalog.concept`, `StageEngine`, `StageClock`, `PlaybackStatus` (`core.audio`).
- Produces: `enum class StageStatus { IDLE, LOADING, READY, FALLBACK }`, `data class StageState(concept: ConceptId? = null, status: StageStatus = IDLE, stillPath: String? = null)`, `class LiveStageController(catalog: AssetCatalog, engine: StageEngine, clock: StageClock)` with `state: StateFlow<StageState>`, `suspend fun load(selection: Selection)`, `fun sync(positionMs: Long, status: PlaybackStatus)`, `fun unload()`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package dev.digitalrain.synthwave.core.stage

import dev.digitalrain.synthwave.core.audio.PlaybackStatus
import dev.digitalrain.synthwave.core.data.AssetCatalog
import dev.digitalrain.synthwave.core.data.AssetOpener
import dev.digitalrain.synthwave.core.data.loadAssetManifest
import dev.digitalrain.synthwave.core.model.ArrangementId
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.MemberId
import dev.digitalrain.synthwave.core.model.Selection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveStageControllerTest {
    private val manifest by lazy {
        loadAssetManifest(requireNotNull(javaClass.classLoader?.getResource("demo/manifest.json")).readText())
    }
    private val warmKeytar = Selection(ArrangementId.DRIVING, MemberId.KEYTAR, ConceptId.WARM)
    private val electricDrummer = Selection(ArrangementId.SPARSE, MemberId.DRUMMER, ConceptId.ELECTRIC)

    private class FakeEngine(var drawn: Boolean = true) : StageEngine {
        val prepared = mutableListOf<StageRequest>()
        val gates = ArrayDeque<CompletableDeferred<Unit>>()
        var unloads = 0
        override suspend fun prepare(request: StageRequest): Boolean {
            prepared += request
            gates.removeFirstOrNull()?.await()
            return drawn
        }
        override fun unload() { unloads++ }
    }

    private fun controller(engine: StageEngine, opener: AssetOpener = AssetOpener { true }, clock: StageClock = StageClock { 0 }) =
        LiveStageController(AssetCatalog(manifest, opener), engine, clock)

    @Test fun readyOnceTheEngineHasDrawnTheShow() = runBlocking {
        val engine = FakeEngine()
        val stage = controller(engine)
        stage.load(warmKeytar)
        assertEquals(StageState(ConceptId.WARM, StageStatus.READY, "visual/concept-warm.webp"), stage.state.value)
        assertEquals("demo/live/mix/driving-keytar.motion", engine.prepared.single().motionPath)
    }

    @Test fun engineFailureFallsBackToTheStill() = runBlocking {
        val stage = controller(FakeEngine(drawn = false))
        stage.load(warmKeytar)
        assertEquals(StageState(ConceptId.WARM, StageStatus.FALLBACK, "visual/concept-warm.webp"), stage.state.value)
    }

    @Test fun missingFileFallsBackWithoutTheEngine() = runBlocking {
        val engine = FakeEngine()
        val stage = controller(engine, AssetOpener { !it.endsWith(".motion") })
        stage.load(warmKeytar)
        assertEquals(StageStatus.FALLBACK, stage.state.value.status)
        assertTrue(engine.prepared.isEmpty())
    }

    @Test fun laterLoadWins() = runBlocking {
        val engine = FakeEngine()
        val slow = CompletableDeferred<Unit>()
        engine.gates += slow
        val stage = controller(engine)
        val first = launch(start = CoroutineStart.UNDISPATCHED) { stage.load(warmKeytar) }
        assertEquals(StageStatus.LOADING, stage.state.value.status)
        stage.load(electricDrummer)
        slow.complete(Unit)
        first.join()
        assertEquals(StageState(ConceptId.ELECTRIC, StageStatus.READY, "visual/concept-electric.webp"), stage.state.value)
    }

    @Test fun unloadFreesTheEngineAndReturnsToIdle() = runBlocking {
        val engine = FakeEngine()
        val stage = controller(engine)
        stage.load(warmKeytar)
        stage.unload()
        assertEquals(StageState(), stage.state.value)
        assertEquals(1, engine.unloads)
    }

    @Test fun syncFeedsTheClockAndPauseHoldsIt() {
        var now = 0L
        val clock = StageClock { now }
        val stage = controller(FakeEngine(), clock = clock)
        stage.sync(9_250, PlaybackStatus.PLAYING)
        now += 40
        assertEquals(9_290, clock.positionMs())
        stage.sync(9_300, PlaybackStatus.PAUSED)
        now += 1_000
        assertEquals(9_300, clock.positionMs())
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `uptime && ./gradlew --no-watch-fs :app:testDebugUnitTest --tests '*LiveStageControllerTest'`
Expected: compilation error, `LiveStageController` unresolved.

- [ ] **Step 3: Write `LiveStageController.kt`**

```kotlin
package dev.digitalrain.synthwave.core.stage

import dev.digitalrain.synthwave.core.audio.PlaybackStatus
import dev.digitalrain.synthwave.core.data.AssetCatalog
import dev.digitalrain.synthwave.core.model.ConceptId
import dev.digitalrain.synthwave.core.model.Selection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class StageStatus { IDLE, LOADING, READY, FALLBACK }

data class StageState(
    val concept: ConceptId? = null,
    val status: StageStatus = StageStatus.IDLE,
    /** The concept's still: shown until the stage is ready, and in its place if it can't be drawn. */
    val stillPath: String? = null,
)

/**
 * The band drawn live for the selected show. Audio position is authoritative: [sync] passes it to
 * the clock the render thread reads each frame. Call from the main thread.
 */
class LiveStageController(
    private val catalog: AssetCatalog,
    private val engine: StageEngine,
    val clock: StageClock,
) {
    private val mutableState = MutableStateFlow(StageState())
    val state: StateFlow<StageState> = mutableState.asStateFlow()
    private var loads = 0

    suspend fun load(selection: Selection) {
        val load = ++loads
        val still = catalog.concept(selection.concept).stillPath
        val request = catalog.liveStage(selection)
        if (request == null) {
            mutableState.value = StageState(selection.concept, StageStatus.FALLBACK, still)
            return
        }
        mutableState.value = StageState(selection.concept, StageStatus.LOADING, still)
        val drawn = engine.prepare(request)
        if (load != loads) return // a later load or unload took over
        mutableState.value = StageState(selection.concept, if (drawn) StageStatus.READY else StageStatus.FALLBACK, still)
    }

    fun sync(positionMs: Long, status: PlaybackStatus) {
        clock.update(positionMs, status == PlaybackStatus.PLAYING)
    }

    /** Frees the engine's memory; the next [load] starts it again. */
    fun unload() {
        loads++
        engine.unload()
        mutableState.value = StageState()
    }
}
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `uptime && ./gradlew --no-watch-fs :app:testDebugUnitTest --tests '*LiveStageControllerTest'`
Expected: 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dev/digitalrain/synthwave/core/stage/LiveStageController.kt \
  app/src/test/java/dev/digitalrain/synthwave/core/stage/LiveStageControllerTest.kt
git commit -m "live stage: controller with loading, ready and still-image states"
```

---

### Task 6: Switch the broadcast screen to the live stage and remove the videos

**Files:**
- Create: `app/src/main/java/dev/digitalrain/synthwave/feature/booking/LiveStageViews.kt`
- Modify: `app/src/main/java/dev/digitalrain/synthwave/SignalRoomApp.kt`, `feature/booking/BookingScreen.kt`, `core/data/AssetCatalog.kt`, `core/model/MediaModels.kt`, `app/src/main/assets/demo/manifest.json`, `tools/validate_demo_assets.py`, `tools/tests/test_validate_demo_assets.py`, `app/build.gradle.kts` (drop media3-ui), `gradle/libs.versions.toml`
- Delete: `core/audio/BroadcastVideoController.kt`, `feature/booking/MemberCam.kt`, `app/src/test/.../feature/booking/MemberCamTest.kt`, `app/src/main/assets/demo/visual/performance/` (36 files), `app/src/main/assets/demo/visual/loop-*.mp4`
- Test: `app/src/test/.../core/data/AssetCatalogTest.kt`, `app/src/androidTest/.../feature/booking/BroadcastVisualTest.kt`, `app/src/androidTest/.../core/audio/MediaPlaybackTest.kt`, `app/src/androidTest/.../feature/booking/OfflineDemoTest.kt`

**Interfaces:**
- Consumes: `FilamentStage`, `LiveStageController`, `StageState`, `StageStatus`, `StageClock`, `chooseBackend(context)`.
- Produces: `BookingScreen(…, stage: StageState, liveStage: FilamentStage, …)` replacing `visual`, `videoPlayer`, `camPlayer`; test tags `live-stage` and `member-cam`; inset content description `"<Member> fancam inset"` in both states.

- [ ] **Step 1: Change the instrumented tests first**

In `BroadcastVisualTest.kt`, replace the first test with:

```kotlin
    @Test fun pausedBroadcastShowsTheFrozenLiveStage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val content = context.assets.open("content/late-night.json").bufferedReader().use { loadContent(it.readText()) }
        val selection = Selection(ArrangementId.SPARSE, MemberId.FRONTMAN, ConceptId.MONOCHROME)
        val prep = Preparation(1, 1, 1, false)
        val store = MemoryStore()
        runBlocking {
            store.save(newRun(content).copy(phase = Phase.BROADCAST, selection = selection,
                preparation = prep, result = scoreBooking(content, selection, prep)))
        }
        compose.setContent { SignalRoomApp(store) }
        // A restored broadcast starts paused; the stage still loads and holds its frame.
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("live-stage").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Selected stage concept").assertDoesNotExist()
        compose.onNodeWithContentDescription("Frontman fancam inset").assertExists()
    }
```

In `MediaPlaybackTest.kt`, delete `bundledVideoFollowsAudioClockAndRemainsSilent` and its now-unused imports (`BroadcastVideoController`, `VideoStatus`, `abs` if unused). The live stage's device coverage is `FilamentStageTest`.

In `OfflineDemoTest.kt`, replace

```kotlin
                assertTrue(catalog.visualPlayback(selection) is
                    dev.digitalrain.synthwave.core.data.VisualPlayback.Performance)
```

with

```kotlin
                assertNotNull(catalog.liveStage(selection))
```

- [ ] **Step 2: Change the unit tests**

In `AssetCatalogTest.kt`: delete `missingOrFailedVideoFallsBackToTheLoopThenItsMatchingStill` and `decoderErrorMapsAudioToBlockingId`; in `missingStillAndUnknownIdsFailExplicitly` replace `missingStill.visualPlayback(Selection(ArrangementId.SPARSE, MemberId.FRONTMAN, ConceptId.WARM))` with `missingStill.concept(ConceptId.WARM)` and delete the final `try { incomplete.recoveryForDecoderError(…) … }` block. Delete `app/src/test/java/dev/digitalrain/synthwave/feature/booking/MemberCamTest.kt`.

In `tools/tests/test_validate_demo_assets.py`: delete `test_ninety_five_frame_video_is_rejected`, `test_video_with_audio_track_is_rejected` and `test_performance_must_be_twenty_seconds_at_540_by_960`, drop `inspect_video`, `performance_metric_failures` and `video_metric_failures` from the import, change `"schemaVersion": 3` to `4` in `test_manifest_structure_requires_the_live_stage`, and in `test_manifest_structure_rejects_unsafe_and_duplicate_paths` change the asserted `"36 performances"` failure to `"schemaVersion must be 4"` if that test's manifest still uses an older version (read the test and keep it asserting a failure the new code produces).

- [ ] **Step 3: Remove the videos from the manifest, validator and catalog**

```bash
git rm -q -r app/src/main/assets/demo/visual/performance app/src/main/assets/demo/visual/loop-monochrome.mp4 \
  app/src/main/assets/demo/visual/loop-warm.mp4 app/src/main/assets/demo/visual/loop-electric.mp4
art/audio/.venv/bin/python -I - <<'EOF'
import json
from pathlib import Path
path = Path("app/src/main/assets/demo/manifest.json")
manifest = json.loads(path.read_text())
manifest["schemaVersion"] = 4
del manifest["performances"]
for concept in manifest["concepts"]:
    del concept["loopPath"], concept["loopSha256"]
path.write_text(json.dumps(manifest, indent=2) + "\n")
EOF
```

`tools/validate_demo_assets.py`:
- delete `inspect_video`, `video_metric_failures` and `performance_metric_failures`;
- in `_manifest_entries`, delete the `loopPath` line and the `performances` loop;
- in `validate_manifest_structure`: version `!= 4` / `"manifest schemaVersion must be 4"`; delete `performances = …`, its count check, the `performances` member of the `all(isinstance …)` tuple, and the whole `seen_performances` block; in the concept check drop `or item.get("loopPath") != f"visual/loop-{slug}.mp4"`;
- in `validate_manifest`, delete the `elif kind == "video"` and `elif kind == "performance"` branches;
- in `_set_hashes`, delete the `loopSha256` line and the `performances` loop.

`MediaModels.kt`: delete `loopPath` and `loopSha256` from `ConceptAssets`, delete `PerformanceAsset`, and delete `performances` from `AssetManifest`.

`AssetCatalog.kt`: `require(manifest.schemaVersion == 4)`; delete `VisualPlayback`, `AssetRecovery`, `visualPlayback` and `recoveryForDecoderError`.

Run: `PATH=/opt/homebrew/bin:$PATH python3 tools/validate_demo_assets.py --root app/src/main/assets/demo --report /private/tmp/claude-501/demo-assets.json` and the validator tests.
Expected: `Validated 37 demo assets` (12 audio, 3 concept stills, 4 member stills, control room, 17 live files); validator tests pass.

- [ ] **Step 4: Write `LiveStageViews.kt`**

```kotlin
package dev.digitalrain.synthwave.feature.booking

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import dev.digitalrain.synthwave.core.stage.FilamentStage

/** The live stage in a SurfaceView; Compose overlays (timer, inset, count-in) draw above it. */
@Composable
internal fun LiveStageView(stage: FilamentStage, modifier: Modifier = Modifier) {
    AndroidView(factory = { context ->
        SurfaceView(context).apply {
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) = Unit
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    stage.attachMain(holder.surface, width, height)
                }
                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    stage.detachMain()
                }
            })
        }
    }, modifier = modifier)
}

/** The featured member's fancam from the same scene, in a TextureView so the inset can clip it. */
@Composable
internal fun LiveMemberCam(stage: FilamentStage, modifier: Modifier = Modifier) {
    AndroidView(factory = { context ->
        TextureView(context).apply {
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                private var surface: Surface? = null

                override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                    surface = Surface(texture).also { stage.attachCam(it, width, height) }
                }

                override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                    surface?.let { stage.attachCam(it, width, height) }
                }

                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                    stage.detachCam()
                    surface?.release()
                    surface = null
                    return true
                }

                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
            }
        }
    }, modifier = modifier)
}
```

- [ ] **Step 5: Use it in `BookingScreen.kt`**

Parameters of `BookingScreen`: replace `visual: VideoState, videoPlayer: ExoPlayer, camPlayer: ExoPlayer,` with `stage: StageState, liveStage: FilamentStage,`; pass `stage, liveStage` instead of `visual, videoPlayer, camPlayer` to `BroadcastScreen`, and change `BroadcastScreen`'s parameters the same way. Remove the imports of `ExoPlayer`, `PlayerView`, `VideoState`, `VideoStatus`; add `dev.digitalrain.synthwave.core.stage.FilamentStage`, `dev.digitalrain.synthwave.core.stage.StageState`, `dev.digitalrain.synthwave.core.stage.StageStatus`, `androidx.compose.ui.semantics.contentDescription`, `androidx.compose.ui.semantics.semantics`.

Replace the stage box's contents from `if (visual.status == VideoStatus.READY && playback.status == PlaybackStatus.PLAYING) {` through the end of the inset `Box` with:

```kotlin
        // Ready means drawn: while the show is paused the stage holds its last frame.
        if (stage.status == StageStatus.READY) {
            LiveStageView(liveStage, Modifier.fillMaxSize().testTag("live-stage"))
        } else {
            SceneBackdrop("demo/${stage.stillPath ?: catalog.concept(snapshot.selection.concept).stillPath}",
                "Selected stage concept", Modifier.fillMaxSize())
        }
        if (stage.status == StageStatus.FALLBACK) {
            Text("Still image: live stage unavailable", Modifier.align(Alignment.TopStart).background(Panel).padding(6.dp))
        }
        Text(timeLeft(playback.positionMs), Modifier.align(Alignment.TopEnd).background(Panel).padding(6.dp),
            fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(140.dp, 180.dp).background(Panel).clipToBounds()) {
            val name = content.members.getValue(snapshot.selection.member).displayName
            if (stage.status == StageStatus.READY) {
                LiveMemberCam(liveStage, Modifier.fillMaxSize().testTag("member-cam")
                    .semantics { contentDescription = "$name fancam inset" })
            } else {
                SceneBackdrop("demo/${catalog.member(snapshot.selection.member).stillPath}", "$name fancam inset",
                    Modifier.fillMaxSize())
            }
            Text("${name.uppercase()} CAM",
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Panel).padding(3.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }
```

(Keep the count-in overlay that follows unchanged. Read the current lines 119-147 before editing so the replacement starts and ends at the right braces.)

- [ ] **Step 6: Wire it in `SignalRoomApp.kt`**

Replace `import dev.digitalrain.synthwave.core.audio.BroadcastVideoController` with imports of `FilamentStage`, `LiveStageController`, `StageClock`, `StageStatus`, `chooseBackend` (package `core.stage`), plus `android.content.ComponentCallbacks2` and `android.content.res.Configuration`.

Replace

```kotlin
    val video = remember { BroadcastVideoController(context, catalog) }
```

with

```kotlin
    val stageClock = remember { StageClock() }
    val liveStage = remember { FilamentStage(context, stageClock, chooseBackend(context)) }
    val stage = remember { LiveStageController(catalog, liveStage, stageClock) }
```

and `val visual by video.state.collectAsState()` with `val stageState by stage.state.collectAsState()`.

Replace the lifecycle `DisposableEffect(lifecycleOwner) { … }` with:

```kotlin
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) audio.onBackground()
            // The stage may have been freed while the app was away; bring it back for the show.
            if (event == Lifecycle.Event.ON_START && model.state.value.phase == Phase.BROADCAST &&
                stage.state.value.status == StageStatus.IDLE
            ) scope.launch { stage.load(model.state.value.selection) }
        }
        // The stage holds a few hundred MB; give it back when the system asks.
        val memory = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) stage.unload()
            }
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = stage.unload()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        context.registerComponentCallbacks(memory)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            context.unregisterComponentCallbacks(memory)
            audio.close()
            stage.unload()
            liveStage.release()
        }
    }
```

In the phase `LaunchedEffect(snapshot.phase, snapshot.attemptId)`, change the broadcast branch to load the stage before the count-in, and free it in every other phase:

```kotlin
        if (snapshot.phase == Phase.BROADCAST) {
            // Loads during the count-in, so the first frame is ready when the show starts.
            launch { stage.load(snapshot.selection) }
            if (shouldCountIn(restoredFromStore, snapshot.viewingCompleted, snapshot.broadcastPositionMs)) {
                audio.reset()
                try {
                    for (n in 3 downTo 1) { countIn = n; delay(1_000) }
                } finally {
                    countIn = 0
                }
            }
            val inForeground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            val audioLoad = launch {
                audio.startBroadcast(catalog.audio(snapshot.selection), snapshot.broadcastPositionMs,
                    autoPlay = !restoredFromStore && inForeground)
                broadcastLoadedAttempt.intValue = snapshot.attemptId
            }
            audioLoad.join()
        } else {
            stage.unload()
            if (snapshot.phase == Phase.VERDICT || snapshot.phase == Phase.RECAP) {
                audio.pause()
            } else if (snapshot.phase == Phase.BRIEF) {
                audio.reset()
            }
        }
```

In the 50 ms loop replace `video.sync(…)` with `stage.sync(audio.state.value.positionMs, audio.state.value.status)`.

In the `BookingScreen(` call replace `visual = visual, videoPlayer = video.player, camPlayer = video.camPlayer,` with `stage = stageState, liveStage = liveStage,`.

- [ ] **Step 7: Delete the video code and the unused dependency**

```bash
git rm -q app/src/main/java/dev/digitalrain/synthwave/core/audio/BroadcastVideoController.kt \
  app/src/main/java/dev/digitalrain/synthwave/feature/booking/MemberCam.kt \
  app/src/test/java/dev/digitalrain/synthwave/feature/booking/MemberCamTest.kt
grep -rn "media3.ui\|PlayerView\|BroadcastVideoController\|camFocus\|VisualPlayback" app/src || echo "no references left"
```

If `media3.ui` has no references left, remove `implementation(libs.media3.ui)` from `app/build.gradle.kts` and the `media3-ui` line from `gradle/libs.versions.toml`.

- [ ] **Step 8: Build and run the unit tests**

Run: `uptime && ./gradlew --no-watch-fs :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Run the instrumented tests on the Pixel**

Tell the user you are using the Pixel. Then:

Run: `uptime && ANDROID_SERIAL=2B011FDH300KNR ./gradlew --no-watch-fs :app:connectedDebugAndroidTest`
Expected: all instrumented tests pass, including `BroadcastVisualTest`, `FilamentStageTest`, `OfflineDemoTest`, `BroadcastLifecycleTest`, `CountInRestoreTest`. A failure in a test that checks captions or buttons over the stage means an overlay is behind the SurfaceView: check with a screenshot before changing code.

- [ ] **Step 10: Commit**

```bash
git add -A app/src tools gradle/libs.versions.toml app/build.gradle.kts
git status --short | grep -v '^?? art/' # nothing else should be staged
git commit -m "live stage: the broadcast screen draws the band live; performance and loop videos removed"
```

---

### Task 7: Device check, picture settings and notes

**Files:**
- Modify: `app/src/main/java/dev/digitalrain/synthwave/core/stage/StageScene.kt` (`StageQuality`, only if a cheaper set looks the same)
- Create: `docs/live-stage.md` (results and how to export)
- Test: manual measurements on the Pixel 7 Pro, recorded in `docs/live-stage.md`

**Interfaces:**
- Consumes: the release APK, `S/build_install.sh` and `S/run_show.sh` from the scratchpad (they build, sign with the debug key, install, and play one show while printing frame lines and per-thread CPU).

- [ ] **Step 1: Release build on the phone**

Tell the user you are about to drive the Pixel; wait if they are using it. Update `S/run_show.sh` to grep `LiveStage` instead of `FSPIKE`, then run `S/build_install.sh`.

- [ ] **Step 2: Measure the show**

For Driving / Frontman / Warm, play the show twice with `S/run_show.sh` and record: frame interval p50/p99 from the `LiveStage` log, render-thread work p50/max, whole-app CPU (cores, `top -H` aggregate), the main thread's share, PSS during the show and on the end screen 10 s later (`adb shell dumpsys meminfo dev.digitalrain.synthwave | grep TOTAL`), the `Prepared … in N ms` line, and APK size. Targets: 30 fps with p99 under 40 ms; CPU near the spike's 1.12 cores or lower; main thread well under the spike's 0.19-0.22; PSS on the end screen near the video app's 249 MB.

- [ ] **Step 3: Try the cheaper picture settings**

One change at a time, each with a rebuild, one run, and a screenshot at the same moment (`adb exec-out screencap -p`): `MSAA = false`; then `BLOOM = false`; then `SHADOW_MAP_SIZE = 512`. Keep a change only if the screenshot looks the same as the spike's side by side (send both to the user if unsure) and it saves memory or CPU. Commit the kept values with the numbers in the message.

- [ ] **Step 4: Check the cases the tests can't**

On the phone, with the release build:
1. Pause mid-show: the frame holds; resume continues from the audio position.
2. Home button mid-show, wait 10 s, return: the stage comes back at the audio position (from `IDLE` if it was trimmed; force it once with `adb shell am send-trim-memory dev.digitalrain.synthwave RUNNING_CRITICAL` while away).
3. Retry and "Start new demo" twice quickly: the stage always matches the chosen mix and concept.
4. All three concepts and all four member cams by eye against frames from the old videos (`git show main:app/src/main/assets/demo/visual/performance/<concept>-<mix>.mp4 > S/old.mp4` then `ffmpeg -ss 10 -i S/old.mp4 -frames:v 1 S/old-10s.png`), using the `live-*.png` frames from Task 4. Fancam framing against the old crop.
5. `adb shell setprop debug.signalroom.backend opengl`, relaunch, play one show: it draws; record its CPU. Clear the property afterwards.

Fix anything that fails before writing the notes; each fix is its own commit with a test where one can be written.

- [ ] **Step 5: Write `docs/live-stage.md`**

Contents: what the live stage is and where its files come from (`art/blender/export_live_stage.sh`; the `.blend` lives in the untracked `art/blender/demo/`), how a costume or stage change reaches the app (edit in Blender, run the export script, run the validator with `--write-hashes`, build), the backend allowlist and the debug property, the measured numbers from Steps 2-3 against the video app and the spike, and what is still missing (haze, light beams).

- [ ] **Step 6: Restore the phone and commit**

Reinstall nothing extra; clear any test properties (`debug.signalroom.*`); tell the user you are done with the phone.

```bash
git add docs/live-stage.md app/src/main/java/dev/digitalrain/synthwave/core/stage/StageScene.kt
git commit -m "live stage: device measurements and notes"
```

Then push the branch and open a PR to main with a merge-commit merge planned (not squash); do not merge until the user says "merge".
