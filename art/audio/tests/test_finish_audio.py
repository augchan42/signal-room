import sys
import unittest
from pathlib import Path


AUDIO_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(AUDIO_DIR))
import finish_audio  # noqa: E402


class FinishAudioTest(unittest.TestCase):
    def test_extracts_loudnorm_measurement_json_from_ffmpeg_log(self):
        log = 'frame=10\n[Parsed_loudnorm_0] {\n "input_i": "-16.20",\n "input_tp": "-3.40",\n "input_lra": "5.00",\n "input_thresh": "-27.10",\n "output_i": "-14.00",\n "output_tp": "-1.50",\n "output_lra": "4.10",\n "output_thresh": "-24.00",\n "normalization_type": "linear",\n "target_offset": "0.00"\n}\n'

        measured = finish_audio.parse_loudnorm(log)

        self.assertEqual("-16.20", measured["input_i"])
        self.assertEqual("-3.40", measured["input_tp"])
        self.assertEqual("0.00", measured["target_offset"])

    def test_rejects_bad_decoded_duration_loudness_and_peak(self):
        metrics = {
            "codec": "aac", "profile": "LC", "sampleRate": 48000, "channels": 2,
            "decodedFrames": 950000, "integratedLufs": -16.0, "truePeakDbtp": -0.5,
        }

        issues = finish_audio.validate_metrics(metrics)

        self.assertTrue(any("duration" in issue for issue in issues))
        self.assertTrue(any("loudness" in issue for issue in issues))
        self.assertTrue(any("peak" in issue for issue in issues))

    def test_accepts_compliant_decoded_mix(self):
        metrics = {
            "codec": "aac", "profile": "LC", "sampleRate": 48000, "channels": 2,
            "decodedFrames": 960000, "integratedLufs": -14.2, "truePeakDbtp": -1.3,
        }

        self.assertEqual([], finish_audio.validate_metrics(metrics))


if __name__ == "__main__":
    unittest.main()
