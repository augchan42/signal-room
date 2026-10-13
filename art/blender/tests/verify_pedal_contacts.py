"""Check planted heels, sole/pedal contact, and audio-frame pedal positions."""
import math
import sys
from pathlib import Path
import bpy
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
import performance_motion as motion
import render_mix_performances as renderer

specs = (("kick", "04 Sleeveless boot"),
         ("hi-hat", "04 Sleeveless boot.001"),
         ("sustain", "02 Long vest boot"))
checked = opens_checked = 0
for mix in renderer.MIXES:
    bpy.ops.wm.open_mainfile(filepath=str(ROOT / "demo/signal-room-performance.blend"))
    scene = bpy.context.scene
    scene.frame_set(1)
    original = {}
    for kind, name in specs:
        boot = bpy.data.objects[name]
        lo = Vector([min(v.co[i] for v in boot.data.vertices) for i in range(3)])
        hi = Vector([max(v.co[i] for v in boot.data.vertices) for i in range(3)])
        heel, toe = Vector((0, hi.y, lo.z)), Vector((0, lo.y + .06, lo.z))
        original[kind] = (heel, toe, boot.matrix_world @ heel)
    renderer.apply_motion(mix)
    for kind, name in specs:
        assert bpy.data.objects.get("PERFORMANCE | " + kind + " pedal"), f"Missing {kind} foot pedal"
    for frame in range(1, 481):
        scene.frame_set(frame)
        bpy.context.view_layer.update()
        for kind, name in specs:
            boot = bpy.data.objects[name]
            pedal = bpy.data.objects["PERFORMANCE | " + kind + " pedal"]
            heel, toe, planted = original[kind]
            assert (boot.matrix_world @ heel - planted).length < .001, f"{mix} {kind} heel slides at {frame}"
            local_toe = pedal.matrix_world.inverted() @ (boot.matrix_world @ toe)
            top = max(v.co.z for v in pedal.data.vertices)
            assert abs(local_toe.z - top) < .001, f"{mix} {kind} sole leaves pedal at {frame}"
            assert abs(local_toe.x) < .08 and abs(local_toe.y) < .06, f"{mix} {kind} toe outside pedal"
            checked += 1
    events = motion.load_events(renderer.EVENTS / (mix + ".json"))
    for hit in (d for d in events["drums"] if d["kind"] == "kick"):
        scene.frame_set(math.floor(hit["time"] * 24 + 1e-6) + 1)
        pivot = bpy.data.objects["PERFORMANCE | kick heel"]
        assert abs(pivot.rotation_euler.x + .10) < .001, f"{mix} kick pedal misses beat {hit}"
    opens = [d for d in events["drums"] if d["kind"] == "open_hat"]
    for hit in opens:
        scene.frame_set(math.floor(hit["time"] * 24 + 1e-6) + 1)
        pivot = bpy.data.objects["PERFORMANCE | hi-hat heel"]
        assert pivot.rotation_euler.x < -.20, f"{mix} hi-hat pedal not open at {hit}"
        opens_checked += 1
    for kind in ("kick", "hi-hat", "sustain"):
        angles = []
        for frame in range(1, 481):
            scene.frame_set(frame)
            angles.append(bpy.data.objects["PERFORMANCE | " + kind + " heel"].rotation_euler.x)
        if kind != "hi-hat" or opens:
            assert max(angles) - min(angles) > .08, f"{mix} {kind} pedal does not move"
        else:
            assert max(angles) - min(angles) < .001, f"{mix} hi-hat should remain closed"
        assert max(abs(b-a) for a,b in zip(angles,angles[1:])) < .13, f"{mix} {kind} pedal jumps"
    print("PEDAL_CONTACTS_VERIFIED", mix, flush=True)
assert opens_checked > 0, "No open-hi-hat events checked in the twelve mixes"
print("ALL_PEDAL_CONTACTS_VERIFIED", checked, "open hats", opens_checked, flush=True)
