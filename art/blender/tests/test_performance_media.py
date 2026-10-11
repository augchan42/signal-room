"""Contract for the playable 20-second browser review clip."""

import json
import subprocess
import unittest
from html.parser import HTMLParser
from pathlib import Path


MEDIA = Path(__file__).resolve().parents[1] / "demo" / "performance-monochrome.webm"
REVIEW = MEDIA.parent / "review.html"


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


if __name__ == "__main__":
    unittest.main()
