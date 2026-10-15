"""Export the live stage that the app draws with Filament (app/.../core/stage/).

One Blender process per job; export_live_stage.sh runs them all:
  -- stage   the shared stage without animation (stage.glb), the concept looks, area lights and
             camera sizes (stage.json) and the three rear-screen images (screen-<concept>.png)
  -- mix <arrangement>-<member>
             that mix's motion against stage.glb (mix/<mix>.motion, see live_motion.py) and its
             light and emission levels per frame (mix/<mix>.tracks.json)

Concepts are not applied here. Lights keep the prepared scene's levels and the app multiplies the
light tracks by each concept's factor, as apply_concept scales the keyframes.
"""

import json
import math
import sys
import tempfile
from pathlib import Path

import bpy
import numpy as np

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))
import live_motion  # noqa: E402
import render_mix_performances as rmp  # noqa: E402

OUT = ROOT.parents[1] / "app" / "src" / "main" / "assets" / "demo" / "live"
FPS, FRAMES = 24, 480
HAZE = "T3 bounded haze"
STAGE_MIX = "driving-frontman"  # every mix adds the same pedals, fingers and wrists
# Animated emission strength, and the input holding the colour it multiplies.
EMISSION_INPUTS = {("ShaderNodeBsdfPrincipled", "Emission Strength"): "Emission Color",
                   ("ShaderNodeEmission", "Strength"): "Color"}


def prepare(mix):
    rmp.apply_motion(mix)
    bpy.data.objects[HAZE].hide_set(True)  # Filament has no volumes; the haze box would draw solid
    for obj in [o for o in bpy.context.scene.objects if o.type == "FONT" and o.visible_get()]:
        bpy.ops.object.select_all(action="DESELECT")
        obj.select_set(True)
        bpy.context.view_layer.objects.active = obj
        bpy.ops.object.convert(target="MESH")  # glTF does not carry text objects
    bpy.context.scene.frame_set(1)


def export(path, animations):
    bpy.ops.export_scene.gltf(
        filepath=str(path), export_format="GLB", use_visible=True, export_apply=True,
        export_cameras=True, export_lights=True, export_animations=animations,
        export_animation_mode="SCENE", export_force_sampling=True, export_frame_range=True,
        export_pointer_animation=False,  # crashes on animated materials in Blender 5.2.2
        export_image_format="AUTO")


def concept_looks():
    looks = {}
    for name, look in rmp.CONCEPTS.items():
        lights = {light: {"color": list(color), "factor": energy / rmp.BASE_ENERGY[light]}
                  for light, (color, energy) in (("Warm portrait key", look["key"]), ("Cool soft fill", look["fill"]))}
        for light in ("Cyan rear rim", "Coral rear rim"):
            lights[light] = {"color": list(look["cyan" if light.startswith("Cyan") else "coral"]), "factor": look["rim"]}
        for side in ("left", "right"):
            lights[f"PERFORMANCE | {side} searchlight"] = {"color": list(look["beam"]), "factor": 1.0}
        looks[name.upper()] = {"suits": [list(rgb) for rgb in look["suits"]], "lights": lights}
    return looks


def save_screens():
    for name, look in rmp.CONCEPTS.items():
        image = bpy.data.images["SCREEN " + look["pattern"]]
        width, height = image.size
        pixels = np.empty(width * height * 4, dtype=np.float32)
        image.pixels.foreach_get(pixels)
        if look["saturation"] == 0:
            rgba = pixels.reshape(-1, 4)
            rgba[:, :3] = rgba[:, :3].max(axis=1, keepdims=True)  # Hue/Saturation at 0 keeps the HSV value
        out = bpy.data.images.new(f"live screen {name}", width, height, alpha=True)
        out.pixels.foreach_set(pixels)
        out.filepath_raw = str(OUT / f"screen-{name}.png")
        out.file_format = "PNG"
        out.save()


def area_lights():
    lights = []
    for obj in bpy.context.scene.objects:
        if obj.type == "LIGHT" and obj.visible_get() and obj.data.type == "AREA":
            m = obj.matrix_world
            # Blender is Z-up, glTF Y-up: (x, y, z) -> (x, z, -y). The light shines down its own -Z.
            direction = [-m[0][2], -m[2][2], m[1][2]]
            length = math.sqrt(sum(v * v for v in direction))
            lights.append({"name": obj.name, "color": list(obj.data.color), "watts": obj.data.energy,
                           "position": [m[0][3], m[2][3], -m[1][3]], "direction": [v / length for v in direction]})
    return lights


def camera_sizes(stage_path):
    gltf, _ = live_motion.read_glb(stage_path)
    return {node["name"]: {"ymag": gltf["cameras"][node["camera"]]["orthographic"]["ymag"]}
            for node in gltf["nodes"] if "camera" in node and node["name"].startswith("CAM | ")}


def tracks():
    frames = range(1, FRAMES + 1)
    lights = []
    for obj in bpy.context.scene.objects:
        if obj.type == "LIGHT" and obj.visible_get():
            for curve in rmp._fcurves(obj.data):
                if curve.data_path != "energy":
                    raise ValueError(f"{obj.name}: no live mapping for light track {curve.data_path}")
                lights.append({"light": obj.name, "watts": [round(curve.evaluate(f), 4) for f in frames]})
    emissive = []
    for material in bpy.data.materials:
        if material.node_tree is None:
            continue
        for curve in rmp._fcurves(material.node_tree):
            node = material.node_tree.nodes[curve.data_path.split('"')[1]]
            if node.bl_idname == "ShaderNodeVolumePrincipled":
                continue  # the haze, which the live stage leaves out
            socket = material.node_tree.path_resolve(curve.data_path.rsplit(".", 1)[0])
            color_input = EMISSION_INPUTS.get((node.bl_idname, socket.name))
            if color_input is None:
                raise ValueError(f"{material.name}: no live mapping for material track {curve.data_path}")
            source = node.inputs[color_input]
            color = [1.0, 1.0, 1.0] if source.is_linked else list(source.default_value[:3])
            # glTF keeps the colour normalised and moves its brightness into the strength.
            brightness = max(color) or 1.0
            emissive.append({"material": material.name, "color": [c / brightness for c in color],
                             "strength": [round(curve.evaluate(f) * brightness, 4) for f in frames]})
    return {"fps": FPS, "frames": FRAMES, "lights": lights, "emissive": emissive}


def export_stage():
    OUT.mkdir(parents=True, exist_ok=True)
    prepare(STAGE_MIX)
    stage = OUT / "stage.glb"
    export(stage, animations=False)
    save_screens()
    info = {"fps": FPS, "frames": FRAMES, "areaLights": area_lights(), "cameras": camera_sizes(stage),
            "concepts": concept_looks()}
    (OUT / "stage.json").write_text(json.dumps(info, indent=1) + "\n")
    print("LIVE_STAGE_EXPORTED", stage, stage.stat().st_size)


def export_mix(mix):
    if mix not in rmp.MIXES:
        raise SystemExit(f"Unknown mix {mix}")
    prepare(mix)
    (OUT / "mix").mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as directory:
        exported = Path(directory) / f"{mix}.glb"
        export(exported, animations=True)
        nodes = live_motion.motion_nodes(live_motion.read_glb(OUT / "stage.glb"), live_motion.read_glb(exported),
                                         FPS, FRAMES)
    live_motion.write_motion(OUT / "mix" / f"{mix}.motion", FPS, FRAMES, nodes)
    (OUT / "mix" / f"{mix}.tracks.json").write_text(json.dumps(tracks(), separators=(",", ":")) + "\n")
    print("LIVE_MIX_EXPORTED", mix, len(nodes))


if __name__ == "__main__":
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if args == ["stage"]:
        export_stage()
    elif len(args) == 2 and args[0] == "mix":
        export_mix(args[1])
    else:
        raise SystemExit("Expected -- stage | -- mix <arrangement>-<member>")
