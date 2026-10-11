import subprocess
import unittest
from pathlib import Path

import numpy as np


ASSETS = Path(__file__).resolve().parents[3] / "app" / "src" / "main" / "assets" / "demo" / "audio"
ARRANGEMENTS = ("sparse", "driving", "layered")
MEMBERS = ("frontman", "keyboardist", "keytar", "drummer")


def decode(identifier):
    path = ASSETS / f"{identifier}.m4a"
    result = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", str(path), "-f", "f32le", "-acodec", "pcm_f32le",
         "-ar", "48000", "-ac", "2", "-"],
        check=True, capture_output=True,
    )
    return np.frombuffer(result.stdout, dtype="<f4").reshape(-1, 2)


class EncodedVariantsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.audio = {
            f"{arrangement}-{member}": decode(f"{arrangement}-{member}")
            for arrangement in ARRANGEMENTS for member in MEMBERS
        }

    def test_every_decoded_mix_has_exact_length_and_finite_samples(self):
        for identifier, pcm in self.audio.items():
            with self.subTest(identifier=identifier):
                self.assertEqual((960000, 2), pcm.shape)
                self.assertTrue(np.isfinite(pcm).all())

    def test_arrangement_and_member_choices_change_matched_phrase(self):
        for member in MEMBERS:
            for first, second in (("sparse", "driving"), ("sparse", "layered"), ("driving", "layered")):
                self._assert_distinct(f"{first}-{member}", f"{second}-{member}")
        for arrangement in ARRANGEMENTS:
            for member in MEMBERS[1:]:
                self._assert_distinct(f"{arrangement}-frontman", f"{arrangement}-{member}")

    def _assert_distinct(self, first, second):
        start, end = 4 * 48000, 12 * 48000
        left = self.audio[first][start:end]
        right = self.audio[second][start:end]
        difference = float(np.sqrt(np.mean((left - right) ** 2)))
        reference = float(np.sqrt(np.mean(left ** 2)))
        with self.subTest(first=first, second=second):
            self.assertGreater(difference / reference, 0.10)


if __name__ == "__main__":
    unittest.main()
