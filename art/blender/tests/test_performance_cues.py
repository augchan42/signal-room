"""Musical cue timing for the twenty-second performance test."""

import sys
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from performance_cues import cue_at, beat_frame, bar_frame  # noqa: E402


class PerformanceCueTest(unittest.TestCase):
    def test_audio_grid_maps_to_blender_frames(self):
        self.assertEqual(beat_frame(0), 1)
        self.assertEqual(beat_frame(1), 13)
        self.assertEqual(bar_frame(2), 97)
        self.assertEqual(bar_frame(10), 481)

    def test_intro_and_phrase_have_different_lighting_states(self):
        self.assertEqual(cue_at(1).name, "intro")
        self.assertEqual(cue_at(97).name, "reveal")
        self.assertEqual(cue_at(193).name, "groove")
        self.assertEqual(cue_at(289).name, "search")
        self.assertEqual(cue_at(385).name, "finale")
        self.assertLess(cue_at(1).beam_power, cue_at(97).beam_power)
        self.assertLess(cue_at(97).beam_power, cue_at(385).beam_power)
        self.assertLess(cue_at(1).rim_scale, cue_at(385).rim_scale)
        self.assertGreater(cue_at(289).sweep_width, cue_at(97).sweep_width)

    def test_lighting_develops_across_the_full_song_instead_of_repeating_every_four_seconds(self):
        self.assertNotEqual(cue_at(1), cue_at(97))
        self.assertNotEqual(cue_at(97), cue_at(193))
        self.assertNotEqual(cue_at(193), cue_at(289))
        self.assertNotEqual(cue_at(289), cue_at(385))

    def test_cue_boundaries_are_half_open(self):
        self.assertEqual(cue_at(96).name, "intro")
        self.assertEqual(cue_at(97).name, "reveal")
        self.assertEqual(cue_at(480).name, "finale")
        with self.assertRaises(ValueError):
            cue_at(0)
        with self.assertRaises(ValueError):
            cue_at(481)


if __name__ == "__main__":
    unittest.main()
