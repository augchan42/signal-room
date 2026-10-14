import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import performance_hands as hands
import performance_pedals as pedals


class PerformerContactTest(unittest.TestCase):
    def test_notes_select_the_physical_pitch_class_on_each_instrument(self):
        cases = ((36, False, "BORG main synthesizer white key 00"),
                 (37, False, "BORG main synthesizer black key 00"),
                 (60, False, "BORG main synthesizer white key 14"),
                 (65, True, "Keytar white key 17"),
                 (72, True, "Keytar white key 14"),
                 (70, True, "Keytar black key 12"))
        for note, keytar, expected in cases:
            self.assertEqual(hands.key_for_note(note, keytar), expected)

    def test_raised_keyboard_hand_does_not_also_play_the_reentry_chord(self):
        events = dict(lead=[], arp=[], bass=[], answers=[],
                      chords=[dict(time=12, chord="Am"), dict(time=14, chord="F"),
                              dict(time=16, chord="G")])
        notes = hands.playing_notes(events)["02 Long vest hand.001"]
        self.assertEqual([(n["time"], n["note"]) for n in notes], [(12,57),(16,55),(17,55)])
        self.assertLess(notes[0]["time"] + notes[0]["dur"], 13.4)

    def test_pedals_press_on_the_earlier_audio_frame_and_release_between_hits(self):
        events = dict(drums=[dict(time=.53, kind="kick"), dict(time=1.03, kind="open_hat")],
                      chords=[dict(time=.53)])
        tracks = pedals.pedal_tracks(events)
        self.assertEqual(len(tracks["kick"]), 480)
        self.assertAlmostEqual(tracks["kick"][12], -.10)
        self.assertAlmostEqual(tracks["hi-hat"][24], -.22)
        self.assertAlmostEqual(tracks["kick"][20], -.22)
        self.assertAlmostEqual(tracks["sustain"][12], -.10)
        self.assertLess(tracks["sustain"][24], -.10)
        self.assertGreater(tracks["sustain"][24], -.13)
        self.assertAlmostEqual(tracks["sustain"][72], -.22)

    def test_hi_hat_closes_on_the_next_closed_hat_strike(self):
        events = dict(drums=[dict(time=1, kind="open_hat"), dict(time=1.125, kind="hat")], chords=[])
        track = pedals.pedal_tracks(events)["hi-hat"]
        self.assertAlmostEqual(track[24], -.22)
        self.assertAlmostEqual(track[27], -.10)

    def test_finger_holds_then_lifts_before_travelling_to_the_next_key(self):
        notes = [dict(time=4, dur=.375, note=76), dict(time=4.375, dur=.25, note=72)]
        # Hold E5 at 4.25, lift during the last two frames, arrive on C5 at 4.375.
        self.assertEqual(hands.note_pose(4.25, notes), (0,0,0,0))
        old, new, blend, lift = hands.note_pose(4+8/24, notes)
        self.assertEqual((old,new), (0,1))
        self.assertGreater(blend, 0)
        self.assertLess(blend, 1)
        self.assertGreater(lift, .01)
        self.assertEqual(hands.note_pose(4.375, notes), (1,1,0,0))

    def test_short_gap_does_not_jump_finger_height_when_travel_begins(self):
        notes = [dict(time=3.75, dur=.25, note=84), dict(time=4.125, dur=.1, note=45)]
        before = hands.note_pose(4.005-.00001, notes)[3]
        after = hands.note_pose(4.005+.00001, notes)[3]
        self.assertLess(abs(after-before), .0002)


if __name__ == "__main__":
    unittest.main()
