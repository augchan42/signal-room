#!/usr/bin/env python3
"""Twelve app mixes from the approved bright cue: three arrangements x four featured members.

Every mix keeps the tempo, harmony, hook and effects of `probe/final/bright-cue-candidate.mp3`.
An arrangement changes the drum and bass patterns and each part's level. The featured member adds
their own playing (fills, answers, an octave double or an open arpeggio), their parts are raised,
and they take the breakdown (bar 7) with the rest of the band pulled back. Writes 48 kHz stereo WAV
masters to `masters/`; `finish_audio.py` encodes them.
"""

from __future__ import annotations

import argparse
import time
from pathlib import Path

import numpy as np
from scipy.ndimage import minimum_filter1d, uniform_filter1d

import bright_cue
from finish_audio import ARRANGEMENTS, HERE, MEMBERS, TARGET_LUFS, measure_loudness


STEMS = HERE / "masters" / "stems"
LEAD_WAV = STEMS / "lead-a7.wav"  # Surge XT JX-10 Double Brass + Anthemish 1 layer, see SOUND_DIRECTION_NOTES.md
PAD_WAV = STEMS / "pad-juno60-open.wav"  # Surge XT Juno-60 Strings, open voicing

# What each arrangement and featured member plays differently (see bright_cue.render_parts).
ARRANGEMENT_VARIANTS = {
    "sparse": {"drums": "sparse", "bass": "sparse"},  # half-time drums, held bass, no arpeggio
    "driving": {"drums": "driving", "bass": "driving"},  # four-on-the-floor from bar 1, 16th bass
    "layered": {"harmony": True},  # a third under the hook, pad and counterline up
}
MEMBER_VARIANTS = {
    "frontman": {"octave": True, "octave_from": 7},  # the hook doubled an octave up from the breakdown
    "keyboardist": {"arp_open": True},  # the arpeggio stays bright through the breakdown
    "keytar": {"answers": True, "answers_solo": True},  # answer phrases, and a run in the breakdown
    "drummer": {"fills": True},  # fills in bars 4, 8 and 9 and a drum break in bar 7
}

# Part levels (1.0 = the approved cue). "verb" scales the plate return.
ARRANGEMENT_GAINS = {
    # exposed lead over held bass and a quiet pad, with more room
    "sparse": {"drums": .8, "hats": 1.0, "snares": .75, "bass": .9, "pads": .35, "arp": 0,
               "lead": 1.15, "double": 0, "counter": 0, "answers": 1.0, "octave": 1.0, "harmony": 0,
               "fx": .5, "verb": 1.6},
    # active bass and drums, pads pulled back, drier
    "driving": {"drums": 1.2, "hats": 1.4, "snares": 1.1, "bass": 1.3, "pads": .4, "arp": 1.25,
                "lead": 1.0, "double": 1.0, "counter": 0, "answers": 1.0, "octave": 1.0, "harmony": 0,
                "fx": 1.0, "verb": .6},
    # pad, harmony and counterline around the lead, softer drums
    "layered": {"drums": .7, "hats": .55, "snares": .75, "bass": 1.0, "pads": 2.0, "arp": .8,
                "lead": 1.0, "double": 1.4, "counter": 1.3, "answers": 1.0, "octave": 1.0, "harmony": 1.0,
                "fx": 1.0, "verb": 1.6},
}

# Parts raised for the featured member.
MEMBER_PARTS = {
    "frontman": ("lead", "double", "octave"),
    "keyboardist": ("arp", "counter"),
    "keytar": ("answers",),
    "drummer": ("drums", "hats", "snares"),
}
FEATURE = 2.0  # +6 dB on the featured member's parts
MUTED_FEATURE = .8  # level for a featured part the arrangement mutes (sparse arpeggio, counterline)

# The featured member takes the breakdown: other parts drop to SPOTLIGHT_REST in bar 7.
SPOTLIGHT_BAR = 7
SPOTLIGHT_REST = {"pads": .5, "fx": 1.0}  # every other part not featured: SPOTLIGHT_OTHERS
SPOTLIGHT_OTHERS = .2


def mix_variant(arrangement: str, member: str) -> dict:
    return {**ARRANGEMENT_VARIANTS[arrangement], **MEMBER_VARIANTS[member]}


def mix_gains(arrangement: str, member: str) -> dict[str, float]:
    gains = dict(ARRANGEMENT_GAINS[arrangement])
    for part in MEMBER_PARTS[member]:
        gains[part] = (gains[part] or MUTED_FEATURE) * FEATURE
    return gains


def spotlight(parts: dict[str, np.ndarray], member: str) -> dict[str, np.ndarray]:
    """Pull every part but the featured member's back in bar 7, with 50 ms ramps."""
    total = len(parts["lead"])
    start, end = (round(bright_cue.at(bar) * bright_cue.RATE) for bar in (SPOTLIGHT_BAR, SPOTLIGHT_BAR + 1))
    ramp = round(.05 * bright_cue.RATE)
    window = np.zeros(total)
    window[start:end] = 1
    window = uniform_filter1d(window, ramp)
    out = dict(parts)
    for name in bright_cue.PARTS:
        if name in MEMBER_PARTS[member]:
            continue
        rest = SPOTLIGHT_REST.get(name, SPOTLIGHT_OTHERS)
        out[name] = parts[name] * (1 - (1 - rest) * window)[:, None]
    return out


PEAK_AFTER_LOUDNORM = -3.0  # dBTP; finish_audio allows -1.0, and AAC encoding adds up to about 1.2 dB


def limit(audio: np.ndarray, ceiling: float, lookahead: float = .002, release: float = .08) -> np.ndarray:
    """Peak limiter: gain reaches its floor before each peak and recovers over `release` seconds."""
    n = round(lookahead * bright_cue.RATE)
    need = np.minimum(1, ceiling / (np.abs(audio).max(1) + 1e-12))
    gain = uniform_filter1d(minimum_filter1d(need, 2 * n + 1), n + 1)
    a = np.exp(-1 / (release * bright_cue.RATE))
    out = np.empty_like(gain)
    level = 1.0
    for i, target in enumerate(gain.tolist()):
        level = target if target < level else target + (level - target) * a
        out[i] = level
    return audio * np.minimum(out, gain)[:, None]


def fit_peaks(audio: np.ndarray, scratch: Path) -> tuple[np.ndarray, float]:
    """Limit until the true peak after loudness normalization is below PEAK_AFTER_LOUDNORM.

    Returns the audio and the total gain reduction in dB at the limiter ceiling.
    """
    reduction = 0.0
    for _ in range(6):
        bright_cue.write(scratch, audio)
        measured = measure_loudness(scratch)
        peak = float(measured["input_tp"]) + TARGET_LUFS - float(measured["input_i"])
        if peak <= PEAK_AFTER_LOUDNORM:
            return audio, reduction
        step = peak - PEAK_AFTER_LOUDNORM + .3
        audio = limit(audio, np.abs(audio).max() * 10 ** (-step / 20))
        audio = audio * (.9 / np.abs(audio).max())
        reduction += step
    raise ValueError(f"{scratch}: peaks still too high after limiting")


def configure() -> None:
    """The approved cue's recipe (see SOUND_DIRECTION_NOTES.md, final candidate)."""
    for path in (LEAD_WAV, PAD_WAV):
        if not path.is_file():
            raise FileNotFoundError(f"{path}: render it with surge_lead.py (README)")
    bright_cue.LEAD_WAV = LEAD_WAV
    bright_cue.PAD_WAV = PAD_WAV
    bright_cue.PAD_MOTION = True
    bright_cue.PAD_CLEAN = True


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--all", action="store_true", help="Render all twelve masters")
    parser.add_argument("--arrangement", choices=ARRANGEMENTS)
    parser.add_argument("--member", choices=MEMBERS)
    parser.add_argument("--out", type=Path, default=HERE / "masters")
    args = parser.parse_args()
    if args.all:
        pairs = [(a, m) for a in ARRANGEMENTS for m in MEMBERS]
    elif args.arrangement and args.member:
        pairs = [(args.arrangement, args.member)]
    else:
        parser.error("Use --all or --arrangement and --member")
    configure()
    start = time.time()
    for arrangement, member in pairs:
        path = args.out / f"{arrangement}-{member}.wav"
        parts = spotlight(bright_cue.render_parts(mix_variant(arrangement, member)), member)
        audio, reduction = fit_peaks(bright_cue.mix_parts(parts, True, mix_gains(arrangement, member)), path)
        bright_cue.write(path, audio)
        print(f"{path} (limited {reduction:.1f} dB)" if reduction else path)
    print(f"{len(pairs)} masters in {time.time() - start:.1f} s")


if __name__ == "__main__":
    main()
