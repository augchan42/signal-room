"""Run in Blender against the 20-second performance scene."""

import bpy
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Vector


scene = bpy.context.scene
assert scene.render.fps == 24
assert (scene.frame_start, scene.frame_end) == (1, 480), "Performance must span the full song"


def position(name, frame):
    scene.frame_set(frame)
    bpy.context.view_layer.update()
    return bpy.data.objects[name].matrix_world.translation.copy()


for name in (
    "01 Cropped jacket hand",
    "02 Long vest hand",
    "03 Oversized blazer hand",
    "04 Sleeveless hand",
):
    displacement = (position(name, 97) - position(name, 103)).length
    assert displacement > 0.12, f"Hand gesture too small for portrait: {name}: {displacement:.4f}m"

scene.frame_set(289)
stage_bottom = world_to_camera_view(scene, scene.camera, Vector((0, -1.7, 0.0))).y
stage_top = world_to_camera_view(scene, scene.camera, Vector((0, 1.17, 3.68))).y
assert stage_top - stage_bottom > 0.47, "The band is too small in the portrait frame"

for name in ("PERFORMANCE | left searchlight", "PERFORMANCE | right searchlight"):
    light = bpy.data.objects[name]
    assert light.type == "LIGHT" and light.data.type == "SPOT"
    scene.frame_set(1)
    assert light.data.energy < 1, f"Searchlight should wait through the intro: {name}"
    scene.frame_set(97)
    assert light.data.energy > 50, f"Searchlight does not enter with the phrase: {name}"
    direction_at_reveal = light.matrix_world.to_quaternion() @ Vector((0, 0, -1))
    scene.frame_set(289)
    direction_at_search = light.matrix_world.to_quaternion() @ Vector((0, 0, -1))
    assert direction_at_reveal.angle(direction_at_search) > 0.10, f"No searchlight sweep: {name}"

face = bpy.data.objects["Warm portrait key"].data
scene.frame_set(1)
intro_face = face.energy
scene.frame_set(385)
assert face.energy > 0 and face.energy >= intro_face * 0.75, "Keep faces readable"

print("PERFORMANCE_SCENE_VERIFIED 480 frames, four gestures, two timed moving spots")
