import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import performance_motion as motion  # noqa: E402


class PerformanceMotionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.events = motion.load_events()
        cls.channels = motion.channels(cls.events)

    def test_events_match_the_cue(self):
        self.assertEqual(self.events["bpm"], 120)
        self.assertEqual(self.events["seconds"], 20.0)
        self.assertEqual([c["chord"] for c in self.events["chords"]], ["Am", "F", "Am", "F", "C", "G", "Am", "F", "G", "Am"])

    def test_every_track_covers_480_frames_with_plausible_angles(self):
        for control, axes in self.channels.items():
            for axis, track in axes.items():
                self.assertEqual(len(track), motion.FRAMES, (control, axis))
                # raised arms: the singer's fist overhead and the keyboard player's free hand at the re-entry
                poses = {("01 Cropped jacket shoulder", "y"): 2.6, ("02 Long vest.001 shoulder", "x"): 2.3}
                limit = poses.get((control, axis), 1.3)
                self.assertLess(max(abs(v) for v in track), limit, (control, axis))

    def test_no_frame_to_frame_jumps(self):
        for control, axes in self.channels.items():
            for axis, track in axes.items():
                biggest = max(abs(b - a) for a, b in zip(track, track[1:]))
                # a drum stick can come down in a single frame; everything else moves slower
                limit = 0.7 if control.startswith("04 ") and ("elbow" in control or "wrist" in control) else 0.35
                self.assertLess(biggest, limit, (control, axis))

    def test_snare_hand_lands_on_the_snare(self):
        elbow = self.channels["04 Sleeveless elbow"]["x"]
        for snare in (d["time"] for d in self.events["drums"] if d["kind"] == "snare"):
            frame = round(snare * motion.FPS)
            self.assertGreater(elbow[frame], 0.2, snare)

    def test_strokes_accelerate_into_the_snare_and_peak_on_its_frame(self):
        # docs/adr/0001-performer-motion.md: speed up into contact, never land after the audio
        for name in ("04 Sleeveless elbow", "04 Sleeveless wrist"):
            track = self.channels[name]["x"]
            for snare in (d["time"] for d in self.events["drums"] if d["kind"] == "snare"):
                f = round(snare * motion.FPS)
                last, before = track[f] - track[f - 1], track[f - 1] - track[f - 2]
                self.assertGreater(last, before, (name, snare))
                self.assertGreaterEqual(track[f], track[f + 1], (name, snare))

    def test_ghost_notes_lift_less_than_snare_accents(self):
        elbow = self.channels["04 Sleeveless elbow"]["x"]
        def drop(kind):  # height gained from the wind-up to the hit
            hits = [round(d["time"] * motion.FPS) for d in self.events["drums"] if d["kind"] == kind]
            return max(elbow[f] - min(elbow[f - 3:f]) for f in hits)
        self.assertLess(drop("ghost"), 0.5 * drop("snare"))

    def test_contacts_never_land_after_their_frame(self):
        self.assertEqual(motion.contact(1.0), 1.0)
        self.assertEqual(motion.contact(1.03), 1.0)  # 0.7 of a frame late: use the earlier frame

    def test_keyboard_chord_wrist_gives_after_each_chord(self):
        wrist = self.channels["02 Long vest.001 wrist"]["x"]
        for chord in self.events["chords"]:
            if chord["bar"] == 8:  # the free hand is raised at the re-entry
                continue
            f = round(chord["time"] * motion.FPS)
            self.assertGreater(wrist[f + 1], wrist[f - 3] + 0.03, chord)

    def test_keyboard_hand_is_down_on_hook_notes(self):
        elbow = self.channels["02 Long vest elbow"]["x"]
        for note in self.events["lead"]:
            frame = round(note["time"] * motion.FPS)
            self.assertGreater(elbow[frame], 0.1, note)

    def test_keyboard_hook_hand_travels_and_waits_above_the_keys(self):
        reach = self.channels["02 Long vest shoulder"]["z"]
        self.assertGreater(max(reach) - min(reach), 0.5)  # hook spread across the keyboard
        elbow = self.channels["02 Long vest elbow"]["x"]
        self.assertLess(elbow[round(motion.at(2, 12) * motion.FPS)], -0.3)  # hovering before the hook

    def test_keyboard_free_hand_goes_up_at_the_reentry_only(self):
        shoulder = self.channels["02 Long vest.001 shoulder"]["x"]
        self.assertLess(shoulder[round((motion.at(8) + 0.5) * motion.FPS)], -2.0)
        self.assertGreater(min(shoulder[:round(motion.at(8) * motion.FPS) - 24]), -0.05)

    def test_keytar_neck_goes_up_on_the_big_moments_with_the_neck_hand(self):
        tilt = self.channels["Keytar tilt"]["y"]
        neck = self.channels["03 Oversized blazer.001 shoulder"]["y"]
        for when in motion.FLIPS:
            f = round(when * motion.FPS)
            self.assertLess(tilt[f], -0.35, when)
            self.assertGreater(neck[f], 0.3, when)
        self.assertEqual(tilt[round(motion.at(7) * motion.FPS)], 0.0)

    def test_hair_swings_after_the_big_moments_and_settles_in_the_breakdown(self):
        tip = self.channels["01 Cropped jacket hair 1 2"]["y"]
        root = self.channels["01 Cropped jacket hair 1 0"]["y"]
        drop = round(motion.at(3) * motion.FPS)
        self.assertGreater(max(map(abs, tip[drop:drop + 24])), 0.15)
        breakdown = tip[round(motion.at(7, 4) * motion.FPS):round(motion.at(8) * motion.FPS) - 6]
        self.assertLess(max(map(abs, breakdown)), 0.05)
        # overlapping action: the tip peaks after the root
        peak = lambda track: max(range(drop, drop + 24), key=lambda f: abs(track[f]))
        self.assertGreater(peak(tip), peak(root))

    def test_breakdown_is_calmer_than_reentry(self):
        lean = self.channels["02 Long vest nod"]["x"]
        breakdown = lean[12 * 24:14 * 24]
        reentry = lean[14 * 24:18 * 24]
        self.assertLess(max(map(abs, breakdown)), 0.5 * max(map(abs, reentry)))

    def test_everyone_holds_the_final_stab(self):
        stab = round(self.events["final_stab"] * motion.FPS)
        for control in ("01 Cropped jacket torso", "02 Long vest torso", "03 Oversized blazer torso", "04 Sleeveless torso"):
            tail = self.channels[control]["x"][stab + 6:]
            self.assertGreater(max(map(abs, tail)), 0.05, control)


if __name__ == "__main__":
    unittest.main()
