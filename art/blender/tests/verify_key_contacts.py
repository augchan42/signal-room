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

checked = held_checked = grip_checked = 0
for mix in renderer.MIXES:
    bpy.ops.wm.open_mainfile(filepath=str(ROOT / "demo/signal-room-performance.blend"))
    renderer.apply_motion(mix)
    events = motion.load_events(renderer.EVENTS / (mix + ".json"))
    worst = 0
    for name, notes in hands.playing_notes(events).items():
        for index, note in enumerate(notes):
            frame = math.floor(note["time"] * 24 + 1e-6) + 1
            end = frame + math.floor(note["dur"] * 24 + 1e-6)
            if index + 1 < len(notes):
                following = math.floor(notes[index+1]["time"] * 24 + 1e-6) + 1
                end = min(end, max(frame+1, following-2))
            for held_frame in range(frame, min(480,end)+1):
                bpy.context.scene.frame_set(held_frame)
                bpy.context.view_layer.update()
                finger = bpy.data.objects[name + " finger 0 distal"]
                tip = finger.matrix_world @ Vector((0,-.040,-.017))
                keytar = name.startswith("03")
                key = bpy.data.objects[hands.key_for_note(note["note"], keytar)]
                local = Vector((0,min(v.co.y for v in key.data.vertices),0) if keytar
                               else (0,0,max(v.co.z for v in key.data.vertices)))
                goal = key.matrix_world @ local
                gap = (tip-goal).length
                assert gap < .0011, f"{mix} {name} {note} frame {held_frame}: held finger misses key by {gap:.3f}m"
                worst = max(worst,gap)
                held_checked += 1
            checked += 1
    for frame in range(1,481):
        bpy.context.scene.frame_set(frame)
        bpy.context.view_layer.update()
        finger = bpy.data.objects["03 Oversized blazer hand.001 finger 0 distal"]
        strip = bpy.data.objects["Keytar touch strip"]
        target = strip.matrix_world @ Vector((0,min(v.co.y for v in strip.data.vertices),0))
        gap = (finger.matrix_world @ Vector((0,-.040,-.017)) - target).length
        assert gap < .0011, f"{mix} keytar neck grip leaves strip at {frame}: {gap}"
        grip_checked += 1
    bpy.context.scene.frame_set(349)  # 14.5 seconds: the re-entry raised-hand pose
    bpy.context.view_layer.update()
    hand = bpy.data.objects["02 Long vest hand.001"].matrix_world.translation
    head = bpy.data.objects["02 Long vest head"]
    crown = max((head.matrix_world @ v.co).z for v in head.data.vertices)
    assert hand.z > crown + .03, f"{mix} contact fitting removed the raised-hand gesture"
    print("KEY_CONTACTS_VERIFIED", mix, worst, flush=True)
print("ALL_KEY_CONTACTS_VERIFIED", checked, "held frames", held_checked, "neck grip frames", grip_checked, flush=True)
