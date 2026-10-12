"""Contract for the playable 20-second browser review clip."""

import json
import subprocess
import unittest
from html.parser import HTMLParser
from pathlib import Path

import numpy as np


MEDIA = Path(__file__).resolve().parents[1] / "demo" / "performance-monochrome.webm"
REVIEW = MEDIA.parent / "review.html"
AUDIO = MEDIA.parents[1].parent / "audio" / "probe" / "final" / "bright-cue-candidate.mp3"


class PerformanceSource(HTMLParser):
    def __init__(self):
        super().__init__()
        self.video_attrs = None
        self.source = None
        self.in_video = False

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "video" and attrs.get("id") == "performance-preview":
            self.in_video = True
            self.video_attrs = attrs
        elif tag == "source" and self.in_video:
            self.source = attrs

    def handle_endtag(self, tag):
        if tag == "video":
            self.in_video = False


class PerformanceMediaTest(unittest.TestCase):
    def test_review_page_offers_the_performance_with_sound(self):
        page = PerformanceSource()
        page.feed(REVIEW.read_text())
        self.assertIsNotNone(page.video_attrs)
        self.assertIn("controls", page.video_attrs)
        self.assertNotIn("muted", page.video_attrs)
        self.assertEqual(page.source, {
            "src": "/art/blender/demo/performance-monochrome.webm",
            "type": "video/webm",
        })

    def test_review_clip_has_full_length_video_and_music(self):
        self.assertTrue(MEDIA.is_file(), MEDIA)
        result = subprocess.run([
            "ffprobe", "-v", "error", "-count_frames", "-show_entries",
            "format=duration:stream=codec_type,codec_name,width,height,avg_frame_rate,nb_read_frames",
            "-of", "json", str(MEDIA),
        ], capture_output=True, text=True, check=True)
        data = json.loads(result.stdout)
        video = [stream for stream in data["streams"] if stream["codec_type"] == "video"]
        audio = [stream for stream in data["streams"] if stream["codec_type"] == "audio"]
        self.assertEqual(len(video), 1)
        self.assertEqual(len(audio), 1)
        self.assertEqual(video[0]["codec_name"], "vp9")
        self.assertEqual(audio[0]["codec_name"], "opus")
        self.assertEqual((video[0]["width"], video[0]["height"]), (540, 960))
        self.assertEqual(video[0]["avg_frame_rate"], "24/1")
        self.assertEqual(int(video[0]["nb_read_frames"]), 480)
        # Opus/WebM may include a few milliseconds of encoder padding.
        self.assertLess(abs(float(data["format"]["duration"]) - 20.0), 0.05)

    def test_review_clip_uses_the_approved_bright_cue(self):
        def decode(path):
            result = subprocess.run([
                "ffmpeg", "-v", "error", "-i", str(path), "-map", "0:a:0",
                "-f", "f32le", "-acodec", "pcm_f32le", "-ar", "48000", "-ac", "2", "-",
            ], capture_output=True, check=True)
            return np.frombuffer(result.stdout, dtype="<f4").reshape(-1, 2)

        start, end = 4 * 48000, 12 * 48000
        source = decode(AUDIO)[start:end].astype(np.float64)
        preview = decode(MEDIA)[start:end].astype(np.float64)
        self.assertEqual(source.shape, preview.shape)
        correlation = np.sum(source * preview) / np.sqrt(np.sum(source ** 2) * np.sum(preview ** 2))
        self.assertGreater(float(correlation), 0.985)


if __name__ == "__main__":
    unittest.main()
