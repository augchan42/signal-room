"""Render the app's performance videos: each mix's own motion in each concept's lighting.

The prepared performance scene is keyed to the approved cue. For one mix and one concept this
script moves every motion key by the difference between that mix's motion and the cue's
(`performance_motion.channels`, with `demo/mix-events/<mix>.json` from `render_mixes.py --events`),
sets the concept's colours and light levels (the same values as `render_demo.apply_concept`),
renders 480 frames and encodes a silent H.264 MP4 for the app, which plays the mix's audio with it.

Each concept is rendered in its own lighting. Rendering once and grading the other concepts in the
compositor was tried and set aside: EEVEE gives no per-light passes, and between concepts the rim
lights change about 3x while the key changes about 15%, which no single colour grade reproduces.

Run with Blender 5.2, one mix and concept per process:
  blender --background art/blender/demo/signal-room-performance.blend --python-exit-code 1 \
    --python art/blender/render_mix_performances.py -- driving-keytar warm
"""

import bpy
import subprocess
import sys
from pathlib import Path

from bpy_extras.anim_utils import animdata_get_channelbag_for_assigned_slot


ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))
import performance_motion  # noqa: E402

EVENTS = ROOT / "demo" / "mix-events"
FRAMES = ROOT / "demo" / "frames" / "mixes"
VISUAL = ROOT.parents[1] / "app" / "src" / "main" / "assets" / "demo" / "visual" / "performance"
scene = bpy.context.scene
assert (scene.frame_start, scene.frame_end, scene.render.fps) == (1, 480, 24), "Open the performance scene"

SAMPLES = 16  # 64 looked the same at 540 x 960 apart from a little haze grain, at about 3x the time
# Base light levels in the prepared scene: key 300 and fill 48 at frame 1, rims at their demo
# baseline. Concept values follow render_demo.apply_concept.
CONCEPTS = {
    "monochrome": dict(suits=[(.025 + i * .012, .029 + i * .012, .036 + i * .012) for i in range(4)],
                       pattern="signal-bars", saturation=0, key=((1, .74, .57), 300), fill=((.42, .57, .75), 48),
                       cyan=(.30, .40, .52), coral=(.55, .39, .34), rim=.4, beam=(.42, .57, .70)),
    "warm": dict(suits=[(.39, .065, .055), (.19, .07, .06), (.62, .46, .30), (.09, .035, .04)],
                 pattern="waveform", saturation=1, key=((1, .73, .48), 330), fill=((.66, .52, .43), 85),
                 cyan=(.16, .57, .74), coral=(1, .31, .20), rim=.7, beam=(1, .62, .38)),
    "electric": dict(suits=[(.08, .10, .16), (.018, .025, .04), (.46, .51, .53), (.015, .025, .04)],
                     pattern="test-pattern", saturation=1, key=((1, .72, .55), 350), fill=((.21, .62, 1), 130),
                     cyan=(.04, .7, 1), coral=(1, .12, .07), rim=1.2, beam=(.25, .65, 1)),
}
MIXES = [f"{a}-{m}" for a in ("sparse", "driving", "layered") for m in ("frontman", "keyboardist", "keytar", "drummer")]


def _fcurves(data):
    if data is None or data.animation_data is None:
        return []
    bag = animdata_get_channelbag_for_assigned_slot(data.animation_data)
    return list(bag.fcurves) if bag else []


def _scale(data, path, factor):
    for curve in _fcurves(data):
        if curve.data_path == path:
            for point in curve.keyframe_points:
                point.co.y *= factor
                point.handle_left.y *= factor
                point.handle_right.y *= factor
            curve.update()


def apply_motion(mix):
    cue = performance_motion.channels(performance_motion.load_events())
    own = performance_motion.channels(performance_motion.load_events(EVENTS / f"{mix}.json"))
    assert cue.keys() == own.keys()
    for control, axes in own.items():
        obj = bpy.data.objects["PERFORMANCE | " + control]
        curves = {curve.array_index: curve for curve in _fcurves(obj) if curve.data_path == "rotation_euler"}
        for axis, track in axes.items():
            curve = curves["xyz".index(axis)]
            for point in curve.keyframe_points:
                frame = round(point.co.x)
                delta = track[frame - 1] - cue[control][axis][frame - 1]
                point.co.y += delta
                point.handle_left.y += delta
                point.handle_right.y += delta
            curve.update()


def _screen_nodes():
    screen = bpy.data.materials["REAR SCREEN | replaceable feed"].node_tree
    image_node = next(n for n in screen.nodes if n.type == "TEX_IMAGE" and n.name == "Image Texture")
    emission = next(n for n in screen.nodes if n.type == "EMISSION")
    return screen, image_node, emission


def apply_concept(concept):
    look = CONCEPTS[concept]
    for index, rgb in enumerate(look["suits"]):
        material = bpy.data.materials[f"SUIT {index}"]
        material.diffuse_color = (*rgb, 1)
        next(n for n in material.node_tree.nodes if n.type == "BSDF_PRINCIPLED").inputs["Base Color"].default_value = (*rgb, 1)
    screen, image_node, emission = _screen_nodes()
    image_node.image = bpy.data.images["SCREEN " + look["pattern"]]
    saturation = screen.nodes.get("Demo concept saturation")
    if saturation is None:
        saturation = screen.nodes.new("ShaderNodeHueSaturation")
        saturation.name = "Demo concept saturation"
        screen.links.new(image_node.outputs["Color"], saturation.inputs["Color"])
        screen.links.new(saturation.outputs["Color"], emission.inputs["Color"])
    saturation.inputs["Saturation"].default_value = look["saturation"]
    for name, (color, energy), base in (("Warm portrait key", look["key"], 300), ("Cool soft fill", look["fill"], 48)):
        light = bpy.data.objects[name].data
        light.color = color
        _scale(light, "energy", energy / base)
    for name in ("Cyan rear rim", "Coral rear rim"):
        light = bpy.data.objects[name].data
        light.color = look["cyan" if name.startswith("Cyan") else "coral"]
        _scale(light, "energy", look["rim"])
    for side in ("left", "right"):
        bpy.data.objects[f"PERFORMANCE | {side} searchlight"].data.color = look["beam"]
    scene["demo_concept"] = concept


def render(mix, concept):
    frames = FRAMES / f"{concept}-{mix}"
    frames.mkdir(parents=True, exist_ok=True)
    scene.render.engine = "BLENDER_EEVEE"
    scene.eevee.taa_render_samples = SAMPLES
    scene.render.resolution_percentage = 50  # 540 x 960
    scene.render.image_settings.file_format = "JPEG"
    scene.render.image_settings.color_mode = "RGB"
    scene.render.image_settings.quality = 92
    scene.render.filepath = str(frames / "f-")
    scene.frame_set(1)
    bpy.ops.render.render(animation=True)
    VISUAL.mkdir(parents=True, exist_ok=True)
    target = VISUAL / f"{concept}-{mix}.mp4"
    subprocess.run([
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
        "-framerate", "24", "-start_number", "1", "-i", str(frames / "f-%04d.jpg"),
        "-frames:v", "480", "-vf", "scale=in_range=full:out_range=limited,format=yuv420p",
        "-c:v", "libx264", "-pix_fmt", "yuv420p", "-color_range", "tv",
        "-crf", "23", "-preset", "medium", "-an", "-movflags", "+faststart", str(target),
    ], check=True)
    print("MIX_PERFORMANCE_RENDERED", target)


if __name__ == "__main__":
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if len(args) != 2 or args[0] not in MIXES or args[1] not in CONCEPTS:
        raise SystemExit("Expected -- <arrangement>-<member> monochrome|warm|electric")
    apply_motion(args[0])
    apply_concept(args[1])
    render(*args)
