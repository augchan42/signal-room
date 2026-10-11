import copy
import json
import sys
import unittest
from pathlib import Path

import numpy as np


AUDIO_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(AUDIO_DIR))
import render_theme  # noqa: E402


class RenderThemeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.theme = json.loads((AUDIO_DIR / "theme.json").read_text())

    def test_twenty_seconds_has_exact_sample_count(self):
        self.assertEqual(render_theme.frame_count(20, 48000), 960000)

    def test_theme_is_ten_bars_with_events_inside_excerpt(self):
        self.assertEqual(self.theme["bpm"], 120)
        self.assertEqual(self.theme["bars"], 10)
        self.assertEqual(self.theme["introBars"], 2)
        self.assertEqual(len(self.theme["chords"]), 10)
        self.assertEqual(render_theme.validate_theme(self.theme), [])
        self.assertEqual([chord["symbol"] for chord in self.theme["chords"]][2:],
                         ["Am", "F", "C", "G", "Am", "F", "E", "Am"])

    def test_rejects_note_that_crosses_final_bar(self):
        invalid = copy.deepcopy(self.theme)
        invalid["melody"].append({"tick": 19100, "duration": 200, "note": 69})
        self.assertTrue(any("melody" in issue for issue in render_theme.validate_theme(invalid)))

    def test_short_pcm_is_finite_repeatable_and_stereo(self):
        fixture = self._short_fixture()
        first = render_theme.render_pcm(fixture, "sparse", "frontman", sample_rate=8000,
                                        duration_seconds=0.75)
        second = render_theme.render_pcm(fixture, "sparse", "frontman", sample_rate=8000,
                                         duration_seconds=0.75)

        self.assertEqual(first.shape, (6000, 2))
        self.assertTrue(np.isfinite(first).all())
        np.testing.assert_array_equal(first, second)

    def test_arrangement_and_featured_member_change_actual_pcm(self):
        fixture = self._short_fixture()
        sparse_front = render_theme.render_pcm(fixture, "sparse", "frontman", sample_rate=8000,
                                               duration_seconds=0.75)
        driving_front = render_theme.render_pcm(fixture, "driving", "frontman", sample_rate=8000,
                                                duration_seconds=0.75)
        sparse_drums = render_theme.render_pcm(fixture, "sparse", "drummer", sample_rate=8000,
                                               duration_seconds=0.75)

        self.assertGreater(float(np.max(np.abs(sparse_front - driving_front))), 0.01)
        self.assertGreater(float(np.max(np.abs(sparse_front - sparse_drums))), 0.01)

    def _short_fixture(self):
        fixture = copy.deepcopy(self.theme)
        fixture["chords"] = [fixture["chords"][0]]
        fixture["melody"] = [{"tick": 0, "duration": 480, "note": 69}]
        fixture["answers"] = [{"tick": 240, "duration": 240, "note": 72}]
        return fixture


if __name__ == "__main__":
    unittest.main()
