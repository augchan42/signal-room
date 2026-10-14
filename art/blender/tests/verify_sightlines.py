"""Run in background Blender: no drum, BORG or keytar hit is hidden from the camera by the singer."""
import math
import sys
from collections import Counter
from pathlib import Path
import bpy
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
import performance_motion as motion
import performance_contacts as contacts
import performance_hands as hands
import render_mix_performances as renderer

source = ROOT / "demo/signal-room-performance.blend"
checked = 0
for mix in renderer.MIXES:
    bpy.ops.wm.open_mainfile(filepath=str(source))
    renderer.apply_motion(mix)
    for obj in bpy.data.objects:  # volumes and light cones are not opaque in the render
        if any(word in obj.name.lower() for word in ("haze", "beam", "cone")):
            obj.hide_viewport = True
    scene = bpy.context.scene
    camera = scene.camera.matrix_world.translation.copy()
    events = motion.load_events(renderer.EVENTS / (mix + ".json"))
    targets = []
    for contact in contacts.drum_contacts(events):
        pad = bpy.data.objects[f"Electronic octagonal pad {contact['pad']} rubber"]
        targets.append(("drums", contact["frame"], pad, Vector((0, 0, max(v.co.z for v in pad.data.vertices) + .003))))
    for name, notes in hands.playing_notes(events).items():
        keytar = name.startswith("03")
        for note in notes:
            key = bpy.data.objects[hands.key_for_note(note["note"], keytar)]
            local = (Vector((0, min(v.co.y for v in key.data.vertices) - .003, 0)) if keytar
                     else Vector((0, 0, max(v.co.z for v in key.data.vertices) + .003)))
            targets.append(("keytar" if keytar else "borg", math.floor(note["time"] * 24 + 1e-6) + 1, key, local))
    hidden = Counter()
    for part, frame, obj, local in sorted(targets, key=lambda target: target[1]):
        scene.frame_set(frame)
        point = obj.matrix_world @ local
        ray = point - camera
        hit, _, _, _, blocker, _ = scene.ray_cast(bpy.context.evaluated_depsgraph_get(), camera,
                                                  ray.normalized(), distance=ray.length - .006)
        if hit and blocker.name.startswith(("01 ", "Lead vocal")):
            hidden[part] += 1
        checked += 1
    assert not hidden, f"{mix}: the singer hides {dict(hidden)} of {len(targets)} hits"
    print("SIGHTLINES_VERIFIED", mix, len(targets), flush=True)
print("ALL_SIGHTLINES_VERIFIED", checked, flush=True)
