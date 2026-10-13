"""Verify the transformed fingertip touches its assigned key at each note."""
import math
import sys
from pathlib import Path
import bpy
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
import performance_motion as motion
import performance_hands as hands
import render_mix_performances as renderer

checked = 0
for mix in renderer.MIXES:
    bpy.ops.wm.open_mainfile(filepath=str(ROOT / "demo/signal-room-performance.blend"))
    renderer.apply_motion(mix)
    events = motion.load_events(renderer.EVENTS / (mix + ".json"))
    worst = 0
    for name, notes in hands.playing_notes(events).items():
        for note in notes:
            frame = math.floor(note["time"] * 24 + 1e-6) + 1
            bpy.context.scene.frame_set(frame)
            bpy.context.view_layer.update()
            finger = bpy.data.objects[name + " finger 0 distal"]
            tip = finger.matrix_world @ Vector((0,-.040,-.017))
            keytar = name.startswith("03")
            key = bpy.data.objects[hands.key_for_note(note["note"], keytar)]
            local = Vector((0,-.012,0) if keytar else (0,0,max(v.co.z for v in key.data.vertices)))
            goal = key.matrix_world @ local
            gap = (tip-goal).length
            assert gap < .008, f"{mix} {name} {note}: finger misses key by {gap:.3f}m"
            worst = max(worst,gap)
            checked += 1
    print("KEY_CONTACTS_VERIFIED", mix, worst, flush=True)
print("ALL_KEY_CONTACTS_VERIFIED", checked, flush=True)
