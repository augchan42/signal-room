"""Build a 20-second monochrome performance from the prepared four-second scene.

Run with Blender 5.2:
  blender --background art/blender/demo/signal-room-demo.blend \
    --python-exit-code 1 --python art/blender/prepare_performance.py

The source .blend and Android assets are not modified.
"""

import bpy
import math
import sys
from pathlib import Path
from mathutils import Matrix, Vector
from bpy_extras.anim_utils import animdata_get_channelbag_for_assigned_slot


ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))
from performance_cues import DISPLAYED_FRAMES, beat_frame, cue_at  # noqa: E402
import performance_motion  # noqa: E402


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
    if obj.name.startswith(("HEAD TURN", "CAM |")):
        _cycle(obj)
# The four-second demo swayed each whole figure, feet included. Here the feet stay planted and
# the sway moves to a hip pivot (see performance_motion.py).
for obj in bpy.data.objects:
    if obj.name.startswith("PERFORMER"):
        obj.animation_data_clear()
for name in ("Signal coral", "Electric cyan", "REAR SCREEN | replaceable feed"):
    _cycle(bpy.data.materials[name].node_tree)


def _parent_keep(obj, parent):
    matrix = obj.matrix_world.copy()
    obj.parent = parent
    obj.matrix_world = matrix


def _joint_position(upper_arm, end):
    zs = [vertex.co.z for vertex in upper_arm.data.vertices]
    return upper_arm.matrix_world @ Vector((0, 0, min(zs) if end == "shoulder" else max(zs)))


def _wrist_position(forearm, elbow):
    """The forearm's far end: its vertices furthest from the elbow pivot."""
    points = [forearm.matrix_world @ vertex.co for vertex in forearm.data.vertices]
    far = max((point - elbow.location).length for point in points)
    end = [point for point in points if (point - elbow.location).length > far - 0.02]
    return sum(end, Vector()) / len(end)


def _arm(prefix, suffix, stick=None, wrist=False):
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
    end = elbow
    if wrist:  # a wrist snap carries ghost notes and absorbs chord weight (ADR 0001)
        end = bpy.data.objects.new("PERFORMANCE | " + prefix + suffix + " wrist", None)
        scene.collection.objects.link(end)
        end.location = _wrist_position(forearm, elbow)
        bpy.context.view_layer.update()
        _parent_keep(end, elbow)
    _parent_keep(hand, end)
    if stick:
        _parent_keep(bpy.data.objects[stick], end)
    return shoulder, elbow


def _torso(root, prefix, keep_on_legs=("boot", "trouser")):
    """Hip pivot carrying everything above the belt; a neck pivot for nods carries the head."""
    belt = bpy.data.objects[prefix + " belt"]
    neck = bpy.data.objects[prefix + " neck"]
    torso = bpy.data.objects.new("PERFORMANCE | " + prefix + " torso", None)
    nod = bpy.data.objects.new("PERFORMANCE | " + prefix + " nod", None)
    scene.collection.objects.link(torso)
    scene.collection.objects.link(nod)
    torso.location = belt.matrix_world.translation
    neck_zs = [neck.matrix_world @ vertex.co for vertex in neck.data.vertices]
    nod.location = max(neck_zs, key=lambda co: co.z)
    bpy.context.view_layer.update()
    _parent_keep(torso, root)
    _parent_keep(nod, torso)
    for child in list(root.children):
        if child in (torso,) or any(word in child.name for word in keep_on_legs):
            continue
        _parent_keep(child, nod if child.name.startswith("HEAD TURN") else torso)
    return torso


# Minimal joint controls keep each upper arm, forearm, hand and drumstick together.
_arm("01 Cropped jacket", "")
_arm("02 Long vest", "")
_arm("02 Long vest", ".001", wrist=True)
_arm("03 Oversized blazer", "")
_arm("03 Oversized blazer", ".001")
_arm("04 Sleeveless", "", stick="Drumstick left", wrist=True)
_arm("04 Sleeveless", ".001", stick="Drumstick right", wrist=True)

for number, prefix in enumerate(("01 Cropped jacket", "02 Long vest", "03 Oversized blazer", "04 Sleeveless"), 1):
    torso = _torso(bpy.data.objects[f"PERFORMER {number} | {prefix}"], prefix)
    if prefix == "03 Oversized blazer":
        # the keytar hangs on its strap and moves with the player; it tilts about the keys hand
        # so the neck can be raised without the keys leaving that hand
        for obj in bpy.data.objects:  # higher and closer, so both hands play from the front
            if obj.name.startswith("Keytar ") and obj.parent is None:
                obj.location += Vector(performance_motion.KEYTAR_OFFSET)
        tilt = bpy.data.objects.new("PERFORMANCE | Keytar tilt", None)
        scene.collection.objects.link(tilt)
        tilt.location = performance_motion.KEYTAR_KEYS_GRIP
        bpy.context.view_layer.update()
        _parent_keep(tilt, torso)
        for obj in bpy.data.objects:
            if obj.name.startswith("Keytar ") and obj.parent is None and obj.name != "Keytar shoulder strap":
                _parent_keep(obj, tilt)
            elif obj.name == "Keytar shoulder strap":
                _parent_keep(obj, torso)

# From the high front camera the upper BORG tier hid the main keys and both hands, and the backing
# mic stood in front of the keytar neck. Both are left out of the performance.
for obj in bpy.data.objects:
    if obj.name.startswith(("BORG upper synthesizer", "Synth upper tier", "Backing vocal mic")):
        obj.hide_render = obj.hide_viewport = True


def _hair(prefix, head_turn, tufts=performance_motion.HAIR_TUFTS):
    """A tall new-wave top: tufts rising from the crown, each a chain of segments on its own pivots.

    The original hair cap stays as the short sides. performance_motion swings each chain with a lag.
    """
    head = bpy.data.objects[prefix + " head"]
    cap = bpy.data.objects[prefix + " hair cap"]
    centre = head.matrix_world.translation
    crown = max((head.matrix_world @ v.co).z for v in head.data.vertices)
    material = cap.data.materials[0] if cap.data.materials else None
    fringe = bpy.data.objects[prefix + " swept fringe"]
    fringe.hide_render = fringe.hide_viewport = True  # the top replaces the fringe
    for tuft, (bx, by, side_lean, back_lean, size) in enumerate(tufts):
        base = Vector((centre.x + bx, centre.y + by, crown - 0.03))
        direction = (Matrix.Rotation(side_lean, 3, "Y") @ Matrix.Rotation(-back_lean, 3, "X")) @ Vector((0, 0, 1))
        parent = head_turn
        style = performance_motion.HAIR
        width, thick = style["width"] * size, style["thick"] * size
        for segment, length in enumerate(performance_motion.HAIR_SEGMENTS):
            length *= size
            pivot = bpy.data.objects.new(f"PERFORMANCE | {prefix} hair {tuft} {segment}", None)
            scene.collection.objects.link(pivot)
            pivot.location = base
            bpy.context.view_layer.update()
            _parent_keep(pivot, parent)
            across = direction.cross(Vector((0, 1, 0))).normalized()
            depth = direction.cross(across).normalized()
            tip_w, tip_t = width * style["taper"], thick * style["taper"]
            corners = []
            for along, w, t in ((0.0, width, thick), (length, tip_w, tip_t)):
                for a in (-0.5, 0.5):
                    for d in (-0.5, 0.5):
                        corners.append(tuple(base + direction * along + across * (w * a) + depth * (t * d)))
            mesh = bpy.data.meshes.new(f"{prefix} hair tuft {tuft} {segment}")
            faces = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 2, 6, 4), (1, 5, 7, 3), (0, 4, 5, 1), (2, 3, 7, 6)]
            mesh.from_pydata(corners, [], faces)
            mesh.update()
            if material:
                mesh.materials.append(material)
            strip = bpy.data.objects.new(f"{prefix} hair tuft {tuft} {segment}", mesh)
            scene.collection.objects.link(strip)
            _parent_keep(strip, pivot)
            base = base + direction * (length * 0.85)
            direction = (Matrix.Rotation(-style["curl"], 3, "X") @ direction).normalized()  # each segment sweeps back
            parent = pivot
            width, thick = tip_w, tip_t


_hair("01 Cropped jacket", bpy.data.objects["HEAD TURN 0"])

# Joint angles follow the cue's drum hits, hook notes, bass notes and chord changes.
for control, axes in performance_motion.channels().items():
    obj = bpy.data.objects["PERFORMANCE | " + control]
    rest = obj.rotation_euler.copy()
    for axis, track in axes.items():
        index = "xyz".index(axis)
        for frame, angle in enumerate(track, 1):
            obj.rotation_euler[index] = rest[index] + angle
            obj.keyframe_insert(data_path="rotation_euler", index=index, frame=frame)
    for curve in _fcurves(obj):
        for key in curve.keyframe_points:
            key.interpolation = "LINEAR"


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
