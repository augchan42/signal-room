"""Build a 20-second monochrome performance from the prepared four-second scene.

Run with Blender 5.2:
  blender --background art/blender/demo/signal-room-demo.blend \
    --python-exit-code 1 --python art/blender/prepare_performance.py

The source .blend and Android assets are not modified.
"""

import bpy
import sys
from pathlib import Path
from mathutils import Vector
from bpy_extras.anim_utils import animdata_get_channelbag_for_assigned_slot


ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))
from performance_cues import DISPLAYED_FRAMES, beat_frame, cue_at  # noqa: E402


scene = bpy.context.scene
assert scene.frame_start == 1 and scene.frame_end == 96 and scene.render.fps == 24
scene.frame_set(1)
bpy.context.view_layer.update()


def _fcurves(data):
    animation = data.animation_data
    if animation is None:
        return []
    bag = animdata_get_channelbag_for_assigned_slot(animation)
    return list(bag.fcurves) if bag else []


def _cycle(data):
    for curve in _fcurves(data):
        if not any(modifier.type == "CYCLES" for modifier in curve.modifiers):
            curve.modifiers.new("CYCLES")


for obj in bpy.data.objects:
    if obj.name.startswith(("PERFORMER", "HEAD TURN", "CAM |")):
        _cycle(obj)
for name in ("Signal coral", "Electric cyan", "REAR SCREEN | replaceable feed"):
    _cycle(bpy.data.materials[name].node_tree)


def _parent_keep(obj, parent):
    matrix = obj.matrix_world.copy()
    obj.parent = parent
    obj.matrix_world = matrix


def _joint_position(upper_arm, end):
    zs = [vertex.co.z for vertex in upper_arm.data.vertices]
    return upper_arm.matrix_world @ Vector((0, 0, min(zs) if end == "shoulder" else max(zs)))


def _arm(prefix, suffix, strength, phase, shoulder_axis="x", elbow_axis="x", stick=None):
    upper = bpy.data.objects[prefix + " upper arm" + suffix]
    forearm = bpy.data.objects[prefix + " forearm" + suffix]
    hand = bpy.data.objects[prefix + " hand" + suffix]
    root = upper.parent
    shoulder = bpy.data.objects.new("PERFORMANCE | " + prefix + suffix + " shoulder", None)
    elbow = bpy.data.objects.new("PERFORMANCE | " + prefix + suffix + " elbow", None)
    scene.collection.objects.link(shoulder)
    scene.collection.objects.link(elbow)
    shoulder.location = _joint_position(upper, "shoulder")
    elbow.location = _joint_position(upper, "elbow")
    bpy.context.view_layer.update()
    _parent_keep(shoulder, root)
    _parent_keep(elbow, shoulder)
    _parent_keep(upper, shoulder)
    _parent_keep(forearm, elbow)
    _parent_keep(hand, elbow)
    if stick:
        _parent_keep(bpy.data.objects[stick], elbow)
    for frame in range(1, DISPLAYED_FRAMES + 2, 6):
        active = ((frame - 1 + phase) // 6) % 2
        section_scale = 0.35 if frame < 97 else 1.0
        shoulder.rotation_euler["xyz".index(shoulder_axis)] = strength * 0.46 * active * section_scale
        elbow.rotation_euler["xyz".index(elbow_axis)] = -strength * active * section_scale
        shoulder.keyframe_insert(data_path="rotation_euler", frame=frame)
        elbow.keyframe_insert(data_path="rotation_euler", frame=frame)
    return shoulder, elbow


# Minimal joint controls keep each upper arm, forearm, hand and drumstick together.
_arm("01 Cropped jacket", "", 0.42, 0, shoulder_axis="z", elbow_axis="x")
_arm("02 Long vest", "", 0.56, 0)
_arm("02 Long vest", ".001", 0.48, 6)
_arm("03 Oversized blazer", "", 0.42, 0, shoulder_axis="y", elbow_axis="z")
_arm("03 Oversized blazer", ".001", 0.32, 6, shoulder_axis="y", elbow_axis="z")
_arm("04 Sleeveless", "", 0.66, 0, stick="Drumstick left")
_arm("04 Sleeveless", ".001", 0.62, 6, stick="Drumstick right")


# The original 4-second rim pulse is replaced with a 20-second cue track.
rim_lights = [bpy.data.objects[name].data for name in ("Cyan rear rim", "Coral rear rim")]
scene.frame_set(1)
rim_baselines = [light.energy for light in rim_lights]
for light in rim_lights:
    light.animation_data_clear()
for beat in range(40):
    frame = beat_frame(beat)
    cue = cue_at(frame)
    for light, baseline in zip(rim_lights, rim_baselines):
        light.energy = baseline * cue.rim_scale
        light.keyframe_insert(data_path="energy", frame=frame)
        light.energy = baseline * cue.rim_scale * 0.72
        light.keyframe_insert(data_path="energy", frame=frame + 6)

face = bpy.data.objects["Warm portrait key"].data
fill = bpy.data.objects["Cool soft fill"].data
for frame, face_energy, fill_energy in (
    (1, 300, 48), (97, 305, 54), (193, 310, 60),
    (289, 315, 66), (385, 320, 72), (481, 300, 48),
):
    face.energy = face_energy
    face.keyframe_insert(data_path="energy", frame=frame)
    fill.energy = fill_energy
    fill.keyframe_insert(data_path="energy", frame=frame)


# A modest amount of stage haze lets two camera-facing beams read as searchlights.
haze = bpy.data.objects["T3 bounded haze"]
haze.hide_render = False
haze.hide_viewport = False
haze_material = haze.data.materials[0].copy()
haze_material.name = "PERFORMANCE | light haze"
haze.data.materials[0] = haze_material
volume_node = next(node for node in haze_material.node_tree.nodes if node.type == "PRINCIPLED_VOLUME")
for frame, density in ((1, 0.012), (96, 0.018), (97, 0.022), (481, 0.022)):
    volume_node.inputs["Density"].default_value = density
    volume_node.inputs["Density"].keyframe_insert("default_value", frame=frame)


def _searchlight(name, x, sign):
    data = bpy.data.lights.new(name, "SPOT")
    data.color = (0.42, 0.57, 0.70)
    data.spot_size = 0.17
    data.spot_blend = 0.10
    light = bpy.data.objects.new(name, data)
    scene.collection.objects.link(light)
    light.location = (x, 0.3, 3.65)
    keys = (
        (1, (sign * 2.5, 0.7, 0.5)),
        (97, (sign * 0.85, 0.65, 0.65)),
        (193, (-sign * 0.35, 0.35, 0.75)),
        (289, (-sign * 1.15, 0.15, 0.95)),
        (385, (sign * 0.20, -0.30, 1.15)),
        (481, (sign * 2.5, 0.7, 0.5)),
    )
    for frame, point in keys:
        target = Vector(point)
        light.rotation_euler = (target - light.location).to_track_quat("-Z", "Y").to_euler()
        light.keyframe_insert(data_path="rotation_euler", frame=frame)
        power = 280 * cue_at(min(frame, DISPLAYED_FRAMES)).beam_power
        if frame == 481:
            power = 0
        data.energy = power
        data.keyframe_insert(data_path="energy", frame=frame)
    return light


_searchlight("PERFORMANCE | left searchlight", -2.35, -1)
_searchlight("PERFORMANCE | right searchlight", 2.35, 1)

camera = scene.camera
camera.data.ortho_scale = 9.3
camera.rotation_euler = (Vector((0, 0, 1.7)) - camera.location).to_track_quat("-Z", "Y").to_euler()

scene.frame_end = DISPLAYED_FRAMES
scene["performance_seconds"] = 20
scene["performance_mix"] = "driving-frontman"
scene.frame_set(1)
bpy.context.view_layer.update()
target = ROOT / "demo" / "signal-room-performance.blend"
bpy.ops.wm.save_as_mainfile(filepath=str(target))
print("PERFORMANCE_PREPARED", target)
