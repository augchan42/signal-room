"""Check the browser previews without requiring Blender to render the scene."""

import json
import subprocess
import unittest
from html.parser import HTMLParser
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
DEMO = ROOT / "art" / "blender" / "demo"
CONCEPTS = ("monochrome", "warm", "electric")


class VideoSources(HTMLParser):
    def __init__(self):
        super().__init__()
        self.videos = []
        self.current_video = None

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "video":
            self.current_video = {"attrs": attrs, "sources": []}
            self.videos.append(self.current_video)
        elif tag == "source" and self.current_video is not None:
            self.current_video["sources"].append(attrs)

    def handle_endtag(self, tag):
        if tag == "video":
            self.current_video = None


class ReviewPreviewTest(unittest.TestCase):
    def test_review_prefers_webm_and_retains_android_mp4_fallback(self):
        page = VideoSources()
        page.feed((DEMO / "review.html").read_text())
        self.assertEqual(len(page.videos), len(CONCEPTS))
        for video, concept in zip(page.videos, CONCEPTS):
            self.assertNotIn("src", video["attrs"])
            self.assertEqual(video["sources"], [
                {"src": f"/art/blender/demo/loop-{concept}.webm", "type": "video/webm"},
                {"src": f"/app/src/main/assets/demo/visual/loop-{concept}.mp4", "type": "video/mp4"},
            ])

    def test_review_previews_are_video_only_vp9_four_second_loops(self):
        for concept in CONCEPTS:
            with self.subTest(concept=concept):
                path = DEMO / f"loop-{concept}.webm"
                self.assertTrue(path.is_file(), path)
                probe = subprocess.run([
                    "ffprobe", "-v", "error", "-count_frames", "-show_entries",
                    "format=duration:stream=codec_type,codec_name,width,height,avg_frame_rate,nb_read_frames",
                    "-of", "json", str(path),
                ], capture_output=True, text=True, check=True)
                data = json.loads(probe.stdout)
                self.assertEqual(len(data["streams"]), 1)
                stream = data["streams"][0]
                self.assertEqual(stream["codec_type"], "video")
                self.assertEqual(stream["codec_name"], "vp9")
                self.assertEqual((stream["width"], stream["height"]), (360, 640))
                self.assertEqual(stream["avg_frame_rate"], "24/1")
                self.assertEqual(int(stream["nb_read_frames"]), 96)
                self.assertAlmostEqual(float(data["format"]["duration"]), 4.0, places=2)


if __name__ == "__main__":
    unittest.main()
