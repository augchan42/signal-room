"""Spike (throwaway): export one performance to glTF for a real-time 3D test.

Applies a mix's motion and a concept's look exactly as render_mix_performances.py does, then
exports the visible scene with its 480-frame animation baked into one glTF animation.

  blender --background art/blender/demo/signal-room-performance.blend --python-exit-code 1 \
    --python spikes/realtime-3d/export_gltf.py -- driving-frontman warm
"""
import sys
from pathlib import Path

import bpy

ROOT = Path(__file__).resolve().parents[2]
sys.argv = [a for a in sys.argv]  # render_mix_performances reads its own args only under __main__
sys.path.insert(0, str(ROOT / "art" / "blender"))
import render_mix_performances as rmp  # noqa: E402

mix, concept = sys.argv[sys.argv.index("--") + 1:]
rmp.apply_motion(mix)
rmp.apply_concept(concept)

# Text objects are not exported by glTF; convert the visible ones to meshes.
for obj in [o for o in bpy.context.scene.objects if o.type == "FONT" and o.visible_get()]:
    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.convert(target="MESH")

# Animated light and material values, which glTF animation here does not carry: sample every
# animated float per frame into a side file that the real-time player applies itself.
import json
from bpy_extras.anim_utils import animdata_get_channelbag_for_assigned_slot


def _curves(idblock):
    if idblock is None or idblock.animation_data is None:
        return []
    bag = animdata_get_channelbag_for_assigned_slot(idblock.animation_data)
    return list(bag.fcurves) if bag else []


tracks = {}
scene = bpy.context.scene
for obj in scene.objects:
    if obj.type == "LIGHT" and obj.visible_get():
        for c in _curves(obj.data):
            tracks[f"light|{obj.name}|{c.data_path}|{c.array_index}"] = [round(c.evaluate(f), 4) for f in range(1, 481)]
for mat in bpy.data.materials:
    if mat.node_tree:
        for c in _curves(mat.node_tree):
            tracks[f"material|{mat.name}|{c.data_path}|{c.array_index}"] = [round(c.evaluate(f), 4) for f in range(1, 481)]
lights = {o.name: dict(type=o.data.type, color=list(o.data.color), energy=o.data.energy,
                       size=getattr(o.data, "size", 0), spot_size=getattr(o.data, "spot_size", 0),
                       matrix=[list(r) for r in o.matrix_world])
          for o in scene.objects if o.type == "LIGHT" and o.visible_get()}
side = ROOT / "spikes" / "realtime-3d" / "export" / f"{concept}-{mix}.tracks.json"
side.write_text(json.dumps(dict(fps=24, frames=480, lights=lights, tracks=tracks)))
print("TRACKS", len(tracks), sorted({k.split("|")[0] + "|" + k.split("|")[1] for k in tracks}))

out = ROOT / "spikes" / "realtime-3d" / "export" / f"{concept}-{mix}.glb"
bpy.ops.export_scene.gltf(
    filepath=str(out),
    export_format="GLB",
    use_visible=True,
    export_apply=True,
    export_cameras=True,
    export_lights=True,
    export_animations=True,
    export_animation_mode="SCENE",
    export_force_sampling=True,
    export_frame_range=True,
    export_pointer_animation=False,  # crashes on animated materials in Blender 5.2.2
    export_image_format="AUTO",
)
print("GLTF_EXPORTED", out, out.stat().st_size)
