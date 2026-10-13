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


if __name__ == "__main__":
    unittest.main()
