"""Run in background Blender against every mix; checks geometry, not joint angles."""
import sys
from pathlib import Path
import bpy
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
import performance_motion as motion
import performance_contacts as contacts
import render_mix_performances as renderer

source = ROOT / "demo/signal-room-performance.blend"
checked = 0
for mix in renderer.MIXES:
    bpy.ops.wm.open_mainfile(filepath=str(source))
    renderer.apply_motion(mix)
    bpy.context.scene.frame_set(1)
    bpy.context.view_layer.update()
    tips = {}
    for side, suffix in (("left", ""), ("right", ".001")):
        stick = bpy.data.objects["Drumstick " + side]
        hand = bpy.data.objects["04 Sleeveless hand" + suffix].matrix_world.translation
        zs = [v.co.z for v in stick.data.vertices]
        tips[side] = max((Vector((0, 0, z)) for z in (min(zs), max(zs))),
                         key=lambda p: (stick.matrix_world @ p - hand).length)
    events = motion.load_events(renderer.EVENTS / (mix + ".json"))
    worst = 0.0
    for contact in contacts.drum_contacts(events):
        bpy.context.scene.frame_set(contact["frame"])
        bpy.context.view_layer.update()
        stick = bpy.data.objects["Drumstick " + contact["side"]]
        pad = bpy.data.objects[f"Electronic octagonal pad {contact['pad']} rubber"]
        top = max(v.co.z for v in pad.data.vertices)
        target = pad.matrix_world @ Vector((0, 0, top + .001))
        gap = (stick.matrix_world @ tips[contact["side"]] - target).length
        assert gap < .008, f"{mix} {contact}: stick tip misses assigned pad by {gap:.3f} m"
        worst = max(worst, gap)
        checked += 1
    print("DRUM_CONTACTS_VERIFIED", mix, worst, flush=True)
print("ALL_DRUM_CONTACTS_VERIFIED", checked, flush=True)
