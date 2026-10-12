import sys
import unittest
from pathlib import Path

import numpy as np


AUDIO_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(AUDIO_DIR))
import bright_cue  # noqa: E402
import render_mixes  # noqa: E402


class MixGainsTest(unittest.TestCase):
    def test_featured_member_parts_are_raised(self):
        for arrangement in render_mixes.ARRANGEMENTS:
            base = render_mixes.ARRANGEMENT_GAINS[arrangement]
            for member, parts in render_mixes.MEMBER_PARTS.items():
                gains = render_mixes.mix_gains(arrangement, member)
                for part in parts:
                    with self.subTest(arrangement=arrangement, member=member, part=part):
                        if base[part]:
                            self.assertAlmostEqual(base[part] * render_mixes.FEATURE, gains[part])
                for part in set(base) - set(parts):
                    self.assertEqual(base[part], gains[part])

    def test_every_part_has_a_level_in_every_arrangement(self):
        for gains in render_mixes.ARRANGEMENT_GAINS.values():
            self.assertEqual(set(bright_cue.PARTS) | {"verb"}, set(gains))

    def test_arrangements_follow_their_descriptions(self):
        sparse, driving, layered = (render_mixes.ARRANGEMENT_GAINS[a] for a in render_mixes.ARRANGEMENTS)
        self.assertEqual(0, sparse["counter"])
        self.assertLess(sparse["pads"], driving["pads"])
        self.assertGreater(driving["bass"], layered["bass"])
        self.assertGreater(driving["hats"], sparse["hats"])
        self.assertGreater(layered["pads"], driving["pads"])
        self.assertGreater(layered["counter"], 0)

    def test_keyboardist_gets_a_counterline_even_where_the_arrangement_has_none(self):
        self.assertGreater(render_mixes.mix_gains("sparse", "keyboardist")["counter"], 0)
        self.assertEqual(0, render_mixes.mix_gains("sparse", "frontman")["counter"])


class LimiterTest(unittest.TestCase):
    def test_limit_holds_the_ceiling_and_leaves_quiet_audio_alone(self):
        audio = np.full((48000, 2), .1)
        audio[2400] = .9
        out = render_mixes.limit(audio, .5)
        self.assertLessEqual(np.abs(out).max(), .5 + 1e-9)
        self.assertAlmostEqual(.1, out[0, 0])
        self.assertAlmostEqual(.1, out[-1, 0], places=3)


if __name__ == "__main__":
    unittest.main()
