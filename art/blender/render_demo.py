"""Prepare a four-second Signal Room scene and render concept media.

Run in Blender 5.2 with `--python render_demo.py -- prepare|stills|loops`.
The source MVP blend is never overwritten.
"""

import bpy
import subprocess
import sys
from pathlib import Path

from bpy_extras.anim_utils import animdata_get_channelbag_for_assigned_slot


ROOT = Path(__file__).resolve().parent
OUT = ROOT / "demo"
OUT.mkdir(exist_ok=True)
VISUAL = ROOT.parent.parent / "app" / "src" / "main" / "assets" / "demo" / "visual"
VISUAL.mkdir(parents=True, exist_ok=True)
exec(compile((ROOT / "mvp_controls.py").read_text(), str(ROOT / "mvp_controls.py"), "exec"))
scene = bpy.context.scene

CONCEPTS = {
    "monochrome": {"tone": "neutral", "pattern": "signal-bars"},
    "warm": {"tone": "warm", "pattern": "waveform"},
    "electric": {"tone": "cool", "pattern": "test-pattern"},
}
MEMBERS = {
    "frontman": "frontman",
    "keyboardist": "producer",
    "keytar": "keytar",
    "drummer": "drummer",
}
MEMBER_ROOTS = {
    "frontman": "PERFORMER 1 | 01 Cropped jacket",
    "keyboardist": "PERFORMER 2 | 02 Long vest",
    "keytar": "PERFORMER 3 | 03 Oversized blazer",
    "drummer": "PERFORMER 4 | 04 Sleeveless",
}
def _fcurves(data):
    if data is None or data.animation_data is None:
        return []
    bag = animdata_get_channelbag_for_assigned_slot(data.animation_data)
    return list(bag.fcurves) if bag else []


RIM_ENERGIES = {
    name: [
        (point.co.y, point.handle_left.y, point.handle_right.y)
        for curve in _fcurves(bpy.data.objects[name].data)
        for point in curve.keyframe_points
    ]
    for name in ("Cyan rear rim", "Coral rear rim")
} if scene.frame_end == 96 else {}


def _retime_object(obj):
    for curve in _fcurves(obj):
        for point in curve.keyframe_points:
            point.co.x = 1 + (point.co.x - 1) * 96 / 120
            point.handle_left.x = 1 + (point.handle_left.x - 1) * 96 / 120
            point.handle_right.x = 1 + (point.handle_right.x - 1) * 96 / 120
        curve.update()


def _rebuild_pulse(data, period, trough_offset):
    for curve in _fcurves(data):
        high = curve.evaluate(1)
        low = curve.evaluate(1 + trough_offset)
        curve.keyframe_points.clear()
        for peak in range(1, 98, period):
            curve.keyframe_points.insert(peak, high)
            if peak + trough_offset < 97:
                curve.keyframe_points.insert(peak + trough_offset, low)
        curve.update()


def prepare():
    assert scene.frame_end == 120, "Open the corrected five-second MVP source scene"
    for obj in bpy.data.objects:
        if obj.name.startswith(("PERFORMER", "HEAD TURN", "CAM |")):
            _retime_object(obj)
    for obj in bpy.data.objects:
        if obj.type == "LIGHT" and ("rim" in obj.name or "T3 beam" in obj.name):
            _rebuild_pulse(obj.data, 12, 4)
    for name in ("Signal coral", "Electric cyan"):
        _rebuild_pulse(bpy.data.materials[name].node_tree, 24, 5)
    _rebuild_pulse(bpy.data.materials["REAR SCREEN | replaceable feed"].node_tree, 12, 3)
    scene.frame_start = 1
    scene.frame_end = 96
    scene["loop_seconds"] = 4
    scene.render.fps = 24
    scene.render.resolution_x = 1080
    scene.render.resolution_y = 1920
    scene.render.resolution_percentage = 100
    set_state(tier=1, camera_name="group", tone="neutral", pattern="waveform",
              encore=False, special=False)
    scene.frame_set(1)
    bpy.context.view_layer.update()
    target = OUT / "signal-room-demo.blend"
    bpy.ops.wm.save_as_mainfile(filepath=str(target))
    print("PREPARED", target)


def _set_suit(index, color):
    material = bpy.data.materials[f"SUIT {index}"]
    material.diffuse_color = (*color, 1)
    principled = next(node for node in material.node_tree.nodes if node.type == "BSDF_PRINCIPLED")
    principled.inputs["Base Color"].default_value = (*color, 1)


def _scale_energy(light_name, factor):
    light = bpy.data.objects[light_name].data
    curves = [curve for curve in _fcurves(light) if curve.data_path == "energy"]
    if curves:
        for curve in curves:
            for point in curve.keyframe_points:
                point.co.y *= factor
                point.handle_left.y *= factor
                point.handle_right.y *= factor
            curve.update()
    else:
        light.energy *= factor


def apply_concept(concept_id, camera_name="group"):
    if concept_id not in CONCEPTS:
        raise ValueError(concept_id)
    concept = CONCEPTS[concept_id]
    for name, values in RIM_ENERGIES.items():
        curves = _fcurves(bpy.data.objects[name].data)
        points = [point for curve in curves for point in curve.keyframe_points]
        assert len(points) == len(values)
        for point, (y, left_y, right_y) in zip(points, values):
            point.co.y = y
            point.handle_left.y = left_y
            point.handle_right.y = right_y
        for curve in curves:
            curve.update()
    set_state(tier=1, camera_name=camera_name, tone=concept["tone"],
              pattern=concept["pattern"], encore=False, special=False)
    key = bpy.data.objects["Warm portrait key"].data
    fill = bpy.data.objects["Cool soft fill"].data
    cyan = bpy.data.objects["Cyan rear rim"].data
    coral = bpy.data.objects["Coral rear rim"].data
    screen_nodes = bpy.data.materials["REAR SCREEN | replaceable feed"].node_tree
    image_node = next(node for node in screen_nodes.nodes if node.type == "TEX_IMAGE")
    emission_node = next(node for node in screen_nodes.nodes if node.type == "EMISSION")
    saturation = screen_nodes.nodes.get("Demo concept saturation")
    if saturation is None:
        saturation = screen_nodes.nodes.new("ShaderNodeHueSaturation")
        saturation.name = "Demo concept saturation"
        screen_nodes.links.new(image_node.outputs["Color"], saturation.inputs["Color"])
        screen_nodes.links.new(saturation.outputs["Color"], emission_node.inputs["Color"])
    saturation.inputs["Saturation"].default_value = 0 if concept_id == "monochrome" else 1
    if concept_id == "monochrome":
        for index in range(4):
            base = .025 + index * .012
            _set_suit(index, (base, base + .004, base + .011))
        key.color = (1, .74, .57)
        key.energy = 300
        fill.energy = 48
        fill.color = (.42, .57, .75)
        cyan.color = (.30, .40, .52)
        coral.color = (.55, .39, .34)
        rim_scale = .4
    elif concept_id == "warm":
        key.color = (1, .73, .48)
        key.energy = 330
        fill.energy = 85
        fill.color = (.66, .52, .43)
        cyan.color = (.16, .57, .74)
        coral.color = (1, .31, .20)
        rim_scale = .7
    else:
        key.color = (1, .72, .55)
        key.energy = 350
        fill.energy = 130
        fill.color = (.21, .62, 1)
        cyan.color = (.04, .7, 1)
        coral.color = (1, .12, .07)
        rim_scale = 1.2
    for name in ("Cyan rear rim", "Coral rear rim"):
        _scale_energy(name, rim_scale)
    scene["demo_concept"] = concept_id
    scene.frame_set(1)
    bpy.context.view_layer.update()


def _render_still(name):
    scene.render.engine = "BLENDER_EEVEE"
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "WEBP"
    scene.render.image_settings.color_mode = "RGB"
    scene.render.image_settings.quality = 88
    scene.render.filepath = str(VISUAL / f"{name}.webp")
    scene.frame_set(1)
    bpy.ops.render.render(write_still=True)
    print("RENDERED", scene.render.filepath)


def _isolate_member(member_id):
    selected = MEMBER_ROOTS[member_id]
    for root_name in MEMBER_ROOTS.values():
        root = bpy.data.objects[root_name]
        stack = [root]
        while stack:
            obj = stack.pop()
            obj.hide_render = root_name != selected
            stack.extend(obj.children)


def render_stills():
    assert scene.frame_end == 96, "Open the prepared four-second demo scene"
    for concept_id in CONCEPTS:
        apply_concept(concept_id)
        _render_still(f"concept-{concept_id}")
    for member_id, camera_name in MEMBERS.items():
        apply_concept("monochrome", camera_name)
        _isolate_member(member_id)
        _render_still(f"member-{member_id}")
    apply_concept("warm", "control")
    _render_still("control-room")


def render_loops(only_concept=None):
    assert scene.frame_end == 96, "Open the prepared four-second demo scene"
    for concept_id in ([only_concept] if only_concept else CONCEPTS):
        apply_concept(concept_id)
        scene.render.engine = "BLENDER_EEVEE"
        scene.render.resolution_percentage = 100
        scene.render.image_settings.file_format = "JPEG"
        scene.render.image_settings.color_mode = "RGB"
        scene.render.image_settings.quality = 95
        frame_dir = OUT / "frames" / concept_id
        frame_dir.mkdir(parents=True, exist_ok=True)
        scene.render.filepath = str(frame_dir / "f-")
        scene.frame_set(1)
        bpy.ops.render.render(animation=True)
        target = VISUAL / f"loop-{concept_id}.mp4"
        subprocess.run([
            "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
            "-framerate", "24", "-start_number", "1",
            "-i", str(frame_dir / "f-%04d.jpg"),
            "-frames:v", "96", "-vf", "scale=in_range=full:out_range=limited,format=yuv420p",
            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-color_range", "tv",
            "-crf", "20", "-preset", "medium", "-an", "-movflags", "+faststart",
            str(target),
        ], check=True)
        print("RENDERED", target)


def main():
    parser_args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if parser_args == ["prepare"]:
        prepare()
    elif parser_args == ["stills"]:
        render_stills()
    elif parser_args == ["loops"]:
        render_loops()
    elif len(parser_args) == 2 and parser_args[0] == "loops" and parser_args[1] in CONCEPTS:
        render_loops(parser_args[1])
    else:
        raise SystemExit("Expected -- prepare|stills|loops")


if __name__ == "__main__":
    main()
