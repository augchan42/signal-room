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


# Feet stay planted: the hip pivot carries the sway, not the whole figure.
for name in ("01 Cropped jacket boot", "02 Long vest boot", "03 Oversized blazer boot", "04 Sleeveless boot"):
    drift = max((position(name, frame) - position(name, 1)).length for frame in range(1, 481, 12))
    assert drift < 0.002, f"Feet slide: {name}: {drift:.4f}m"

# Each playing hand travels during the drop (bars 3-6, frames 97-288).
for name in ("02 Long vest hand", "02 Long vest hand.001", "03 Oversized blazer hand", "04 Sleeveless hand", "04 Sleeveless hand.001"):
    path = [position(name, frame) for frame in range(97, 289, 2)]
    centre = sum(path, Vector()) / len(path)
    reach = max((point - centre).length for point in path)
    assert reach > 0.03, f"Hand barely moves while playing: {name}: {reach:.4f}m"

# The snare hand is lower on each backbeat than just before it.
for frame in (109, 133, 157, 181):  # beats 2 and 4 of bars 3 and 4
    assert position("Drumstick left", frame).z < position("Drumstick left", frame - 2).z, f"No snare stroke at {frame}"

# The singer's free hand rises for the drop and comes down for the breakdown.
assert position("01 Cropped jacket hand", 97).z - position("01 Cropped jacket hand", 85).z > 0.15, "Singer arm stays down on the drop"
assert position("01 Cropped jacket hand", 300).z < position("01 Cropped jacket hand", 360).z, "Singer arm should be lower in the breakdown than at the re-entry"

# The keytar neck goes up on the drop with the neck hand still on it (in the keytar's own frame).
def local(name, frame, frame_of="PERFORMANCE | Keytar tilt"):
    scene.frame_set(frame)
    bpy.context.view_layer.update()
    return bpy.data.objects[frame_of].matrix_world.inverted() @ bpy.data.objects[name].matrix_world.translation

assert position("Keytar neck", 97).z - position("Keytar neck", 85).z > 0.1, "Keytar neck stays down on the drop"
for frame in (91, 97, 457, 480):
    slip = (local("03 Oversized blazer hand.001", frame) - local("03 Oversized blazer hand.001", 1)).length
    assert slip < 0.03, f"Neck hand leaves the keytar neck at {frame}: {slip:.3f}m"

# Both keytar hands play from in front of the instrument, not behind it.
# (a hand is about 0.13 m deep, so its centre sits a few cm in front of the face it plays)
for frame in range(1, 481, 2):
    keys_face = local("Keytar white key 05", frame).y
    neck_face = local("Keytar neck", frame).y - 0.06  # the neck is 0.12 m deep
    assert local("03 Oversized blazer hand", frame).y < keys_face - 0.03, f"Keys hand is behind the keys at {frame}"
    assert local("03 Oversized blazer hand.001", frame).y < neck_face - 0.02, f"Neck hand is behind the neck at {frame}"

# The upper BORG tier and the backing mic are out of the shot.
for name in ("BORG upper synthesizer chassis", "Backing vocal mic stand"):
    assert bpy.data.objects[name].hide_render, f"{name} should be hidden"

# The keyboard hook hand slides along the keys, and the free hand goes up at the re-entry.
path = [position("02 Long vest hand", frame) for frame in range(97, 289, 2)]
assert max(p.x for p in path) - min(p.x for p in path) > 0.15, "Keyboard hook hand stays in one place"
assert position("02 Long vest hand.001", 349).z - position("02 Long vest head", 349).z > 0.1, "Free hand not raised at the re-entry"

# The singer's hair swings after the drop downbeat.
tips = [position("01 Cropped jacket hair tuft 1 2", frame) for frame in range(97, 121)]
assert max((p - tips[0]).length for p in tips) > 0.03, "Hair does not swing"

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

print("PERFORMANCE_SCENE_VERIFIED 480 frames, planted feet, music-driven hands, keytar neck raise, hair swing, two timed moving spots")
