"""Turn one mix's exported glTF into the app's motion file (app/.../core/stage/MotionClip.kt).

The app loads one shared stage file without animation. Each mix ships only what differs from it:
the per-frame animated channel of every moving node, and the rest transform of every node that sits
differently in this mix (the arrangements place boots, pedals and hands differently).

Format "SRM1", little-endian: fps, frames, node count (int32); per node: UTF-8 name length (int32),
name, rest translation xyz, rotation xyzw, scale xyz (10 float32), channel (int32: 0 none,
1 translation, 2 rotation) and, for a channel, frames x 3 or 4 float32.
"""

import json
import struct
from pathlib import Path

MAGIC = b"SRM1"
CHANNELS = {"translation": (1, 3), "rotation": (2, 4)}
WIDTHS = {0: 0, 1: 3, 2: 4}
COMPONENTS = {"SCALAR": 1, "VEC3": 3, "VEC4": 4}
FLOAT = 5126


def read_glb(path):
    data = Path(path).read_bytes()
    magic, version, _ = struct.unpack_from("<4sII", data, 0)
    if magic != b"glTF" or version != 2:
        raise ValueError(f"{path}: not a glTF 2 binary")
    json_length, json_type = struct.unpack_from("<II", data, 12)
    if json_type != 0x4E4F534A:
        raise ValueError(f"{path}: first chunk is not JSON")
    gltf = json.loads(data[20:20 + json_length])
    offset = 20 + json_length
    binary = b""
    if offset < len(data):
        bin_length, bin_type = struct.unpack_from("<II", data, offset)
        if bin_type != 0x004E4942:
            raise ValueError(f"{path}: second chunk is not BIN")
        binary = data[offset + 8:offset + 8 + bin_length]
    return gltf, binary


def node_trs(node):
    if "matrix" in node:
        raise ValueError(f"node {node.get('name')} has a matrix; export translation, rotation and scale")
    return [*node.get("translation", [0, 0, 0]), *node.get("rotation", [0, 0, 0, 1]), *node.get("scale", [1, 1, 1])]


def accessor_floats(gltf, binary, index):
    accessor = gltf["accessors"][index]
    if accessor["componentType"] != FLOAT:
        raise ValueError(f"accessor {index} is not float")
    view = gltf["bufferViews"][accessor["bufferView"]]
    width = COMPONENTS[accessor["type"]]
    if view.get("byteStride", width * 4) != width * 4:
        raise ValueError(f"accessor {index} is interleaved")
    start = view.get("byteOffset", 0) + accessor.get("byteOffset", 0)
    return list(struct.unpack_from(f"<{accessor['count'] * width}f", binary, start))


def motion_nodes(stage, mix, fps, frames):
    """(name, rest, channel, values) for each node this mix moves or places differently."""
    stage_gltf, _ = stage
    mix_gltf, mix_binary = mix
    stage_rest = {node["name"]: node_trs(node) for node in stage_gltf["nodes"]}
    names = [node["name"] for node in mix_gltf["nodes"]]
    if len(set(names)) != len(names):
        raise ValueError("node names must be unique")
    if set(names) != set(stage_rest):
        raise ValueError("the mix and the stage have different nodes")
    animated = {}
    for animation in mix_gltf.get("animations", []):
        for channel in animation["channels"]:
            sampler = animation["samplers"][channel["sampler"]]
            path = channel["target"]["path"]
            name = mix_gltf["nodes"][channel["target"]["node"]]["name"]
            if path not in CHANNELS:
                raise ValueError(f"{name}: no live mapping for animated {path}")
            if sampler.get("interpolation", "LINEAR") != "LINEAR":
                raise ValueError(f"{name}: keys must be linear")
            if name in animated:
                raise ValueError(f"{name} has more than one animated channel")
            times = accessor_floats(mix_gltf, mix_binary, sampler["input"])
            # Blender's first frame is 1, so keys start at 1/fps; the first key is the show's start.
            if len(times) != frames or any(abs(time - times[0] - k / fps) > 1e-4 for k, time in enumerate(times)):
                raise ValueError(f"{name}: keys must fall on every frame at {fps} fps")
            animated[name] = (CHANNELS[path][0], accessor_floats(mix_gltf, mix_binary, sampler["output"]))
    nodes = []
    for node in mix_gltf["nodes"]:
        name, rest = node["name"], node_trs(node)
        if name in animated:
            nodes.append((name, rest, *animated[name]))
        elif any(abs(a - b) > 1e-6 for a, b in zip(rest, stage_rest[name])):
            nodes.append((name, rest, 0, []))
    return nodes


def write_motion(path, fps, frames, nodes):
    out = bytearray(MAGIC + struct.pack("<iii", fps, frames, len(nodes)))
    for name, rest, channel, values in nodes:
        encoded = name.encode("utf-8")
        if len(values) != frames * WIDTHS[channel]:
            raise ValueError(f"{name}: expected {frames * WIDTHS[channel]} values, got {len(values)}")
        out += struct.pack("<i", len(encoded)) + encoded + struct.pack("<10f", *rest) + struct.pack("<i", channel)
        out += struct.pack(f"<{len(values)}f", *values)
    Path(path).write_bytes(bytes(out))


def read_motion(path):
    data = Path(path).read_bytes()
    if data[:4] != MAGIC:
        raise ValueError(f"{path}: not a motion file")
    fps, frames, count = struct.unpack_from("<iii", data, 4)
    offset, nodes = 16, []
    for _ in range(count):
        (length,) = struct.unpack_from("<i", data, offset)
        name = data[offset + 4:offset + 4 + length].decode("utf-8")
        offset += 4 + length
        rest = list(struct.unpack_from("<10f", data, offset))
        (channel,) = struct.unpack_from("<i", data, offset + 40)
        offset += 44
        width = WIDTHS[channel] * frames
        nodes.append((name, rest, channel, list(struct.unpack_from(f"<{width}f", data, offset))))
        offset += 4 * width
    if offset != len(data):
        raise ValueError(f"{path}: trailing bytes")
    return fps, frames, nodes
