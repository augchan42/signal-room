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
                        expected = (base[part] or render_mixes.MUTED_FEATURE) * render_mixes.FEATURE
                        self.assertAlmostEqual(expected, gains[part])
                for part in set(base) - set(parts):
                    self.assertEqual(base[part], gains[part])

    def test_every_part_has_a_level_in_every_arrangement(self):
        for gains in render_mixes.ARRANGEMENT_GAINS.values():
            self.assertEqual(set(bright_cue.PARTS) | {"verb"}, set(gains))

    def test_arrangements_follow_their_descriptions(self):
        sparse, driving, layered = (render_mixes.ARRANGEMENT_GAINS[a] for a in render_mixes.ARRANGEMENTS)
        self.assertEqual(0, sparse["counter"])
        self.assertEqual(0, sparse["arp"])
        self.assertGreater(layered["harmony"], 0)
        self.assertLess(sparse["pads"], driving["pads"])
        self.assertGreater(driving["bass"], layered["bass"])
        self.assertGreater(driving["hats"], sparse["hats"])
        self.assertGreater(layered["pads"], driving["pads"])
        self.assertGreater(layered["counter"], 0)

    def test_keyboardist_gets_a_counterline_even_where_the_arrangement_has_none(self):
        self.assertGreater(render_mixes.mix_gains("sparse", "keyboardist")["counter"], 0)
        self.assertEqual(0, render_mixes.mix_gains("sparse", "frontman")["counter"])


class VariantTest(unittest.TestCase):
    def test_default_events_are_the_approved_cue(self):
        self.assertEqual(bright_cue.drum_events(), bright_cue.drum_events("full", False))
        self.assertEqual(bright_cue.bass_events(), bright_cue.bass_events("full"))

    def test_sparse_drums_are_half_time_with_no_hats(self):
        sparse = bright_cue.drum_events("sparse")
        count = lambda kind: sum(1 for k, _, _ in sparse if k == kind)
        self.assertEqual(0, count("ghost") + count("hat") + count("open_hat"))
        for kind, when, _ in sparse:
            step = round((when % bright_cue.BAR) / bright_cue.STEP)
            if kind == "kick":
                self.assertEqual(0, step)
            if kind == "snare" and when < bright_cue.at(10):
                self.assertEqual(8, step)

    def test_driving_kick_plays_every_beat_from_bar_1_through_the_breakdown(self):
        kicks = {when for kind, when, _ in bright_cue.drum_events("driving") if kind == "kick"}
        for bar in range(1, 10):
            for beat in range(4):
                self.assertIn(bright_cue.at(bar, beat * 4), kicks)

    def test_drummer_fills_add_hits_in_bars_4_7_8_and_9(self):
        extra = set(bright_cue.drum_events(fills=True)) - set(bright_cue.drum_events())
        bars = {int(when // bright_cue.BAR) + 1 for _, when, _ in extra}
        self.assertEqual({4, 7, 8, 9}, bars)

    def test_bass_styles_change_the_line(self):
        full = bright_cue.bass_events()
        self.assertEqual(9, len(bright_cue.bass_events("sparse")))
        self.assertGreater(len(bright_cue.bass_events("driving")), len(full))
        self.assertEqual(sorted(bright_cue.bass_events("driving")), bright_cue.bass_events("driving"))

    def test_keytar_answers_start_only_where_the_hook_does_not(self):
        hook_onsets = {round(start, 4) for start, _, _ in bright_cue.lead_events()}
        for bar, step, _, _ in bright_cue.ANSWERS:
            self.assertNotIn(round(bright_cue.at(bar, step), 4), hook_onsets)

    def test_harmony_is_a_scale_third_under_the_hook(self):
        for _, _, note in bright_cue.lead_events():
            self.assertIn(note - bright_cue.third_below(note), (3, 4))

    def test_keytar_solo_fills_bar_7(self):
        self.assertEqual(list(range(16)), [step for bar, step, _, _ in bright_cue.ANSWERS_SOLO])

    def test_every_member_plays_something_of_their_own(self):
        for member in render_mixes.MEMBERS:
            self.assertTrue(render_mixes.MEMBER_VARIANTS[member])


class SpotlightTest(unittest.TestCase):
    def test_only_the_featured_member_keeps_full_level_in_bar_7(self):
        parts = {name: np.ones((bright_cue.FRAMES, 2)) for name in bright_cue.PARTS}
        out = render_mixes.spotlight(parts, "keytar")
        middle = round(bright_cue.at(7, 8) * bright_cue.RATE)
        before = round(bright_cue.at(6, 8) * bright_cue.RATE)
        self.assertEqual(1, out["answers"][middle, 0])
        self.assertAlmostEqual(render_mixes.SPOTLIGHT_OTHERS, out["lead"][middle, 0])
        self.assertAlmostEqual(.5, out["pads"][middle, 0])
        self.assertAlmostEqual(1, out["lead"][before, 0])


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
