"""Verify the prepared four-second Blender scene and keyboard geometry."""

import bpy
import json
from pathlib import Path
from mathutils import Vector


ROOT = Path(__file__).resolve().parent
scene = bpy.context.scene
kit = bpy.data.collections["SIGNAL ROOM - concept 01"]


def transforms(frame):
    scene.frame_set(frame)
    bpy.context.view_layer.update()
    return {
        obj.name: [round(value, 5) for row in obj.matrix_world for value in row]
        for obj in kit.objects
        if obj.name.startswith(("PERFORMER", "HEAD TURN", "CAM |"))
    }


def pulse_energy(frame):
    scene.frame_set(frame)
    return bpy.data.objects["Cyan rear rim"].data.energy


assert scene.render.resolution_x == 1080 and scene.render.resolution_y == 1920
assert scene.render.fps == 24 and scene.frame_start == 1 and scene.frame_end == 96
assert scene["loop_seconds"] == 4
assert transforms(1) == transforms(97), "Body/camera transform loop does not close"
assert transforms(1) != transforms(49), "Body/camera motion is absent"
assert abs(pulse_energy(1) - pulse_energy(13)) < 1e-4, "Beat pulse drifted from 120 BPM"
assert pulse_energy(1) > pulse_energy(7), "First displayed frame is not a beat peak"

for prefix in ("BORG main synthesizer", "BORG upper synthesizer"):
    keys = [obj.location.y for obj in scene.objects if obj.name.startswith(prefix + " white key")]
    panel = bpy.data.objects[prefix + " control panel"]
    assert keys and sum(keys) / len(keys) > panel.location.y, "Keyboard faces away from player"
for chassis in (bpy.data.objects["BORG main synthesizer chassis"],
                bpy.data.objects["BORG upper synthesizer chassis"]):
    assert (chassis.rotation_euler.to_matrix() @ Vector((0, 0, 1))).y > .1

assert not any(obj.get("role") == "audience" and not obj.hide_render for obj in kit.objects)
assert not any(obj.get("role") == "screen-echo" and not obj.hide_render for obj in kit.objects)
assert scene.camera.name == "CAM | Broadcast group"

report = {
    "dimensions": [1080, 1920],
    "fps": 24,
    "displayed_frames": 96,
    "closure_frame": 97,
    "beat_frames": [1, 13],
    "beat_peak_energy": round(pulse_energy(1), 4),
    "beat_trough_energy": round(pulse_energy(7), 4),
    "keyboard_orientation": "toward performer",
    "tier": 1,
}
output = ROOT / "demo" / "verification.json"
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))
