"""Contract for the live stage's per-mix motion file."""

import json
import struct
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import live_motion  # noqa: E402


def glb(nodes, animations=()):
    """A minimal glTF binary: animations are (node index, path, times, flat values)."""
    data = bytearray()
    accessors, views, anims = [], [], []

    def add(values, kind):
        views.append({"buffer": 0, "byteOffset": len(data), "byteLength": 4 * len(values)})
        data.extend(struct.pack(f"<{len(values)}f", *values))
        width = {"SCALAR": 1, "VEC3": 3, "VEC4": 4}[kind]
        accessors.append({"bufferView": len(views) - 1, "componentType": 5126,
                          "count": len(values) // width, "type": kind})
        return len(accessors) - 1

    for node, path, times, values in animations:
        sampler = {"input": add(times, "SCALAR"),
                   "output": add(values, "VEC3" if path == "translation" else "VEC4"),
                   "interpolation": "LINEAR"}
        anims.append({"channels": [{"sampler": 0, "target": {"node": node, "path": path}}], "samplers": [sampler]})
    gltf = {"asset": {"version": "2.0"}, "nodes": nodes, "accessors": accessors, "bufferViews": views,
            "buffers": [{"byteLength": len(data)}], "animations": anims}
    text = json.dumps(gltf).encode()
    text += b" " * (-len(text) % 4)
    data += b"\0" * (-len(data) % 4)
    body = struct.pack("<II", len(text), 0x4E4F534A) + text + struct.pack("<II", len(data), 0x004E4942) + bytes(data)
    return struct.pack("<4sII", b"glTF", 2, 12 + len(body)) + body


class LiveMotionTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.root = Path(self.directory.name)

    def tearDown(self):
        self.directory.cleanup()

    def write(self, name, content):
        path = self.root / name
        path.write_bytes(content)
        return live_motion.read_glb(path)

    def stage(self):
        return self.write("stage.glb", glb([{"name": "arm"}, {"name": "boot", "translation": [0, 0, 1]}, {"name": "drum"}]))

    def test_moving_and_moved_nodes_are_written_and_the_rest_left_out(self):
        mix = self.write("mix.glb", glb(
            [{"name": "arm", "rotation": [0, 0, 0, 1]}, {"name": "boot", "translation": [0, 0, 2]}, {"name": "drum"}],
            [(0, "translation", [0, 1 / 24, 2 / 24], [0, 0, 0, 1, 0, 0, 2, 0, 0])]))
        nodes = live_motion.motion_nodes(self.stage(), mix, 24, 3)
        self.assertEqual(["arm", "boot"], [name for name, _, _, _ in nodes])
        self.assertEqual([1, 0], [channel for _, _, channel, _ in nodes])
        self.assertEqual([0, 0, 0, 1, 0, 0, 2, 0, 0], nodes[0][3])
        self.assertEqual([0, 0, 2, 0, 0, 0, 1, 1, 1, 1], nodes[1][1])

    def test_motion_file_round_trips(self):
        nodes = [("arm", [1, 2, 3, 0, 0, 0, 1, 1, 1, 1], 2, [0, 0, 0, 1] * 3), ("boot", [0] * 6 + [1] * 4, 0, [])]
        path = self.root / "mix.motion"
        live_motion.write_motion(path, 24, 3, nodes)
        fps, frames, read = live_motion.read_motion(path)
        self.assertEqual((24, 3), (fps, frames))
        self.assertEqual([(n, [float(v) for v in r], c, [float(v) for v in vs]) for n, r, c, vs in nodes], read)
        self.assertEqual(b"SRM1", path.read_bytes()[:4])

    def test_keys_must_fall_on_every_frame(self):
        mix = self.write("mix.glb", glb([{"name": "arm"}, {"name": "boot", "translation": [0, 0, 1]}, {"name": "drum"}],
                                        [(0, "translation", [0, 0.05, 0.1], [0] * 9)]))
        with self.assertRaisesRegex(ValueError, "every frame"):
            live_motion.motion_nodes(self.stage(), mix, 24, 3)

    def test_mix_must_have_the_stage_nodes(self):
        mix = self.write("mix.glb", glb([{"name": "arm"}, {"name": "cymbal"}, {"name": "drum"}]))
        with self.assertRaisesRegex(ValueError, "different nodes"):
            live_motion.motion_nodes(self.stage(), mix, 24, 3)

    def test_matrix_nodes_are_refused(self):
        with self.assertRaisesRegex(ValueError, "matrix"):
            live_motion.node_trs({"name": "arm", "matrix": [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]})

    LIVE = Path(__file__).resolve().parents[3] / "app" / "src" / "main" / "assets" / "demo" / "live"

    @unittest.skipUnless(LIVE.exists(), "live stage not exported")
    def test_checked_in_mixes_fit_the_stage(self):
        stage, _ = live_motion.read_glb(self.LIVE / "stage.glb")
        names = {node["name"] for node in stage["nodes"]}
        self.assertNotIn("animations", stage)
        for arrangement in ("sparse", "driving", "layered"):
            for member in ("frontman", "keyboardist", "keytar", "drummer"):
                fps, frames, nodes = live_motion.read_motion(self.LIVE / "mix" / f"{arrangement}-{member}.motion")
                self.assertEqual((24, 480), (fps, frames))
                self.assertLessEqual({name for name, _, _, _ in nodes}, names)
                self.assertGreaterEqual(sum(1 for node in nodes if node[2]), 74)
                tracks = json.loads((self.LIVE / "mix" / f"{arrangement}-{member}.tracks.json").read_text())
                self.assertEqual(6, len(tracks["lights"]))
                self.assertEqual(3, len(tracks["emissive"]))
                self.assertTrue(all(len(t["watts"]) == 480 for t in tracks["lights"]))
        info = json.loads((self.LIVE / "stage.json").read_text())
        self.assertEqual({"MONOCHROME", "WARM", "ELECTRIC"}, set(info["concepts"]))
        self.assertEqual(4, len(info["areaLights"]))
        self.assertEqual({"CAM | Broadcast group", "CAM | Frontman fancam", "CAM | Producer fancam",
                          "CAM | Keytar fancam", "CAM | Drummer fancam"}, set(info["cameras"]) - {"CAM | Control room"})
        self.assertAlmostEqual(350 / 300, info["concepts"]["ELECTRIC"]["lights"]["Warm portrait key"]["factor"])
        self.assertAlmostEqual(0.4, info["concepts"]["MONOCHROME"]["lights"]["Cyan rear rim"]["factor"])


if __name__ == "__main__":
    unittest.main()
