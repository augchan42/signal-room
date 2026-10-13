"""Build-gate tests with actual short FFmpeg media fixtures."""

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from validate_demo_assets import (  # noqa: E402
    audio_metric_failures,
    inspect_audio,
    inspect_video,
    performance_metric_failures,
    validate_file,
    validate_manifest,
    validate_manifest_structure,
    video_metric_failures,
)


class DemoAssetValidationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def ffmpeg(self, *arguments):
        subprocess.run(
            ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", *map(str, arguments)],
            check=True,
            capture_output=True,
        )

    def test_missing_file_blocks_validation(self):
        failures = validate_file(self.root / "missing.m4a")
        self.assertTrue(any("missing" in failure for failure in failures))

    def test_corrupt_audio_blocks_validation(self):
        path = self.root / "corrupt.m4a"
        path.write_bytes(b"not an AAC file")
        self.assertTrue(any("decode" in failure or "probe" in failure for failure in validate_file(path)))

    def test_wrong_channel_count_is_detected_from_real_audio(self):
        path = self.root / "mono.m4a"
        self.ffmpeg("-f", "lavfi", "-i", "sine=frequency=440:duration=1", "-ac", "1", "-c:a", "aac", path)
        metrics = inspect_audio(path)
        self.assertEqual(metrics["channels"], 1)
        self.assertTrue(any("stereo" in failure for failure in audio_metric_failures(metrics)))

    def test_short_audio_is_measured_from_decoded_samples(self):
        path = self.root / "short.m4a"
        self.ffmpeg("-f", "lavfi", "-i", "sine=frequency=440:duration=1", "-ac", "2", "-ar", "48000", "-c:a", "aac", path)
        metrics = inspect_audio(path)
        self.assertLess(metrics["decodedFrames"], 960000)
        self.assertTrue(any("duration" in failure for failure in audio_metric_failures(metrics)))

    def test_excessive_true_peak_is_detected_from_real_audio(self):
        path = self.root / "hot.m4a"
        self.ffmpeg("-f", "lavfi", "-i", "sine=frequency=440:duration=1", "-af", "volume=20", "-ac", "2", "-ar", "48000", "-c:a", "aac", path)
        metrics = inspect_audio(path)
        self.assertGreater(metrics["truePeakDbtp"], -1)
        self.assertTrue(any("true peak" in failure for failure in audio_metric_failures(metrics)))

    def test_ninety_five_frame_video_is_rejected(self):
        path = self.root / "short.mp4"
        self.ffmpeg("-f", "lavfi", "-i", "color=c=black:s=64x64:r=24", "-frames:v", "95", "-c:v", "libx264", "-pix_fmt", "yuv420p", path)
        metrics = inspect_video(path)
        self.assertEqual(metrics["frames"], 95)
        self.assertTrue(any("96 frames" in failure for failure in video_metric_failures(metrics)))

    def test_video_with_audio_track_is_rejected(self):
        path = self.root / "not-silent.mp4"
        self.ffmpeg(
            "-f", "lavfi", "-i", "color=c=black:s=64x64:r=24:d=1",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=1",
            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", path,
        )
        metrics = inspect_video(path)
        self.assertEqual(metrics["audioStreams"], 1)
        self.assertTrue(any("silent" in failure for failure in video_metric_failures(metrics)))

    def test_audio_metric_boundaries_are_not_rounded(self):
        good = {
            "codec": "aac", "profile": "LC", "sampleRate": 48000, "channels": 2,
            "decodedFrames": 960000, "integratedLufs": -14.0, "truePeakDbtp": -1.0,
        }
        self.assertEqual(audio_metric_failures(good), [])
        self.assertTrue(any("duration" in failure for failure in audio_metric_failures({**good, "decodedFrames": 962401})))
        self.assertTrue(any("loudness" in failure for failure in audio_metric_failures({**good, "integratedLufs": -12.999})))
        self.assertTrue(any("true peak" in failure for failure in audio_metric_failures({**good, "truePeakDbtp": -.999})))

    def test_manifest_structure_rejects_unsafe_and_duplicate_paths(self):
        manifest = {
            "schemaVersion": 2,
            "audio": [{"id": "sparse-frontman", "path": "../escape.m4a", "arrangement": "SPARSE", "member": "FRONTMAN", "sha256": ""}] * 12,
            "concepts": [], "performances": [], "members": [], "controlRoomPath": "visual/control-room.webp", "controlRoomSha256": "",
        }
        failures = validate_manifest_structure(manifest, self.root)
        self.assertTrue(any("unsafe" in failure for failure in failures))
        self.assertTrue(any("duplicate" in failure for failure in failures))
        self.assertTrue(any("3 concepts" in failure for failure in failures))
        self.assertTrue(any("36 performances" in failure for failure in failures))

    def test_performance_must_be_twenty_seconds_at_540_by_960(self):
        path = self.root / "performance.mp4"
        self.ffmpeg("-f", "lavfi", "-i", "color=c=black:s=540x960:r=24", "-frames:v", "96", "-c:v", "libx264", "-pix_fmt", "yuv420p", path)
        failures = performance_metric_failures(inspect_video(path))
        self.assertTrue(any("480 frames" in failure for failure in failures))
        self.assertFalse(any("540 x 960" in failure for failure in failures))
        good = {"codec": "h264", "pixelFormat": "yuv420p", "colorRange": "tv", "width": 540, "height": 960,
                "fps": 24, "frames": 480, "duration": 20.0, "audioStreams": 0}
        self.assertEqual([], performance_metric_failures(good))
        self.assertTrue(any("540 x 960" in f for f in performance_metric_failures({**good, "width": 1080, "height": 1920})))

    def test_failed_validation_never_repairs_manifest(self):
        manifest_path = self.root / "manifest.json"
        original = json.dumps({
            "schemaVersion": 2, "audio": [], "concepts": [], "performances": [], "members": [],
            "controlRoomPath": "visual/control-room.webp", "controlRoomSha256": "",
        })
        manifest_path.write_text(original)
        report = validate_manifest(self.root)
        self.assertFalse(report["ok"])
        self.assertEqual(manifest_path.read_text(), original)

        result = subprocess.run(
            [sys.executable, str(Path(__file__).resolve().parents[1] / "validate_demo_assets.py"),
             "--root", str(self.root), "--report", str(self.root / "report.json"), "--write-hashes"],
            capture_output=True,
            text=True,
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(manifest_path.read_text(), original)

    def test_non_object_manifest_reports_error(self):
        (self.root / "manifest.json").write_text("[]")
        report = validate_manifest(self.root)
        self.assertFalse(report["ok"])
        self.assertTrue(any("object" in failure for failure in report["failures"]))


if __name__ == "__main__":
    unittest.main()
